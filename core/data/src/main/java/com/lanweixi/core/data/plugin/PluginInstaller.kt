package com.lanweixi.core.data.plugin

import android.content.Context
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginManifestValidator
import java.io.File
import java.util.zip.ZipInputStream

/** 安装结果。 */
sealed interface PluginInstallResult {
    data class Success(
        val manifest: PluginManifest,
        val dirPath: String,
        val warnings: List<String>,
    ) : PluginInstallResult

    data class Failure(val errors: List<String>) : PluginInstallResult
}

/**
 * P216：解压资源上限（zip bomb 兜底）。
 *
 * 插件包很小但解压后可能是几个 GB——以前 `zip.copyTo` 是无上限的，
 * 一个压缩比拉满的包就能把应用 OOM 掉。这里条目数、单文件、总量三道闸。
 */
data class ExtractionLimits(
    val maxEntries: Int = 200,
    val maxEntryBytes: Long = 10L * 1024 * 1024,
    val maxTotalBytes: Long = 30L * 1024 * 1024,
)

/**
 * P137：本地 zip 安装。
 *
 * 流程：解压到临时目录 → 找 manifest → 解析校验 → 依赖检查 → 落盘到
 * `filesDir/plugins/<pluginId>/` → 登记。
 *
 * 安全：
 * - 拒绝 `../` 之类越界路径（zip slip）；
 * - P216：解压有大小/条目上限（见 [ExtractionLimits]）；
 * - 只认 `plugin.manifest.json`，不执行包内任何代码（P2 只做安装与授权）。
 */
class PluginInstaller(
    context: Context,
    private val registry: PluginRegistry,
    /** P216：解压上限（测试可调小）。 */
    private val limits: ExtractionLimits = ExtractionLimits(),
) {
    private val appContext = context.applicationContext
    private val pluginsRoot = File(appContext.filesDir, "plugins")

    /**
     * P215：把插件 id 映射到 `pluginsRoot/<id>`，并确保结果**严格落在 pluginsRoot 内**。
     *
     * 修的是一个数据毁灭级问题：原来直接 `File(pluginsRoot, manifest.id)` 再 `deleteRecursively()`，
     * 而 id 校验曾放行 `..`。`id = ".."` 会删掉 filesDir（DataStore 里的 API Key 一起没），
     * `id = "../.."` 会删掉整个应用私有目录（databases：聊天记录、笔记、任务……）。
     * 现在 id 校验收紧了，这里再兜一层 canonicalPath，避免以后又有人放松校验。
     */
    private fun safeTarget(id: String): File? {
        if (id.isBlank() || id.contains('/') || id.contains('\\') || id.contains("..")) return null
        val root = runCatching { pluginsRoot.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(pluginsRoot, id).canonicalFile }.getOrNull() ?: return null
        val prefix = root.path + File.separator
        if (!target.path.startsWith(prefix)) return null
        return target
    }

    fun installZip(zipFile: File): PluginInstallResult {
        if (!zipFile.isFile) return PluginInstallResult.Failure(listOf("安装包不存在"))
        val staging = File(appContext.cacheDir, "plugin_staging_${System.currentTimeMillis()}")

        return try {
            val extracted = unzip(zipFile, staging)
            if (extracted.isEmpty()) {
                return PluginInstallResult.Failure(listOf("压缩包里没有文件"))
            }
            val manifestFile = findManifest(staging)
                ?: return PluginInstallResult.Failure(
                    listOf("压缩包里找不到 plugin.manifest.json（放在根目录或一层子目录里）"),
                )
            val pluginDir = manifestFile.parentFile
                ?: return PluginInstallResult.Failure(listOf("插件目录不合法"))

            when (val parsed = PluginManifestParser.parseDirectory(pluginDir)) {
                is PluginManifestParser.Result.Failure ->
                    return PluginInstallResult.Failure(parsed.errors)
                is PluginManifestParser.Result.Success -> {
                    if (!parsed.isValid) return PluginInstallResult.Failure(parsed.errors)

                    val manifest = parsed.manifest
                    val installedVersions = registry.installed()
                        .associate { it.manifest.id to it.manifest.version }
                    val missing = PluginManifestValidator.dependenciesSatisfied(manifest, installedVersions)
                    if (missing.isNotEmpty()) {
                        val detail = missing.joinToString("、") { "${it.pluginId} ${it.versionRange}" }
                        return PluginInstallResult.Failure(listOf("缺少依赖插件：$detail"))
                    }

                    // 同 id 覆盖安装：先清旧目录
                    val target = safeTarget(manifest.id)
                        ?: return PluginInstallResult.Failure(listOf("插件 id 不合法：${manifest.id}"))
                    if (target.exists()) target.deleteRecursively()
                    pluginsRoot.mkdirs()
                    if (!pluginDir.copyRecursively(target, overwrite = true)) {
                        return PluginInstallResult.Failure(listOf("写入插件目录失败"))
                    }

                    val registered = registry.install(target)
                    if (registered.isFailure) {
                        PluginInstallResult.Failure(
                            listOf(registered.exceptionOrNull()?.message ?: "登记失败"),
                        )
                    } else {
                        PluginInstallResult.Success(
                            manifest = manifest,
                            dirPath = target.absolutePath,
                            warnings = parsed.warnings,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            PluginInstallResult.Failure(listOf(e.message ?: "安装失败"))
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    /**
     * 安装内置示例插件（从 assets 复制）。
     *
     * 只用于「空状态里点一下看看插件长什么样」，不是通用安装通道——
     * 通用安装必须走用户选的 zip。
     */
    fun installBundled(assetDir: String): PluginInstallResult {
        return try {
            val files = appContext.assets.list(assetDir).orEmpty()
            android.util.Log.d("PluginInstaller", "bundled assets=" + files.toList())
            if (files.isEmpty()) return PluginInstallResult.Failure(listOf("内置插件资源缺失"))

            val staging = File(appContext.cacheDir, "bundled_" + System.currentTimeMillis())
            staging.mkdirs()
            files.forEach { name ->
                appContext.assets.open("$assetDir/$name").use { input ->
                    File(staging, name).outputStream().use { input.copyTo(it) }
                }
            }
            val manifestFile = File(staging, MANIFEST)
            if (!manifestFile.isFile) {
                return PluginInstallResult.Failure(listOf("内置插件缺少 " + MANIFEST))
            }
            val parsed = PluginManifestParser.parseDirectory(staging)
            if (parsed is PluginManifestParser.Result.Failure) {
                return PluginInstallResult.Failure(parsed.errors)
            }
            val success = parsed as PluginManifestParser.Result.Success
            if (!success.isValid) return PluginInstallResult.Failure(success.errors)

            val target = safeTarget(success.manifest.id)
                ?: return PluginInstallResult.Failure(listOf("插件 id 不合法：${success.manifest.id}"))
            if (target.exists()) target.deleteRecursively()
            pluginsRoot.mkdirs()
            if (!staging.copyRecursively(target, overwrite = true)) {
                return PluginInstallResult.Failure(listOf("写入插件目录失败"))
            }
            android.util.Log.d("PluginInstaller", "staging ok, target=" + target.absolutePath + " exists=" + target.isDirectory)
            val registered = registry.install(target)
            android.util.Log.d("PluginInstaller", "register result=" + registered)
            if (registered.isFailure) {
                PluginInstallResult.Failure(listOf(registered.exceptionOrNull()?.message ?: "登记失败"))
            } else {
                PluginInstallResult.Success(
                    manifest = success.manifest,
                    dirPath = target.absolutePath,
                    warnings = success.warnings,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PluginInstaller", "bundled install failed", e)
            PluginInstallResult.Failure(listOf(e.message ?: "内置插件安装失败"))
        }
    }

    /**
     * 解压；返回解出的文件列表。越界路径直接失败。
     *
     * P216：边读边计数，超上限立刻中止——不能先解完再看大小。
     */
    private fun unzip(zipFile: File, target: File): List<File> {
        target.mkdirs()
        val canonicalRoot = target.canonicalFile
        val out = mutableListOf<File>()
        val buffer = ByteArray(8 * 1024)
        var entryCount = 0
        var totalBytes = 0L

        zipFile.inputStream().use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > limits.maxEntries) {
                        throw IllegalArgumentException(
                            "压缩包条目太多（上限 ${limits.maxEntries} 个）",
                        )
                    }
                    val file = File(target, entry.name)
                    // zip slip 防护
                    if (!file.canonicalPath.startsWith(canonicalRoot.path + File.separator) &&
                        file.canonicalPath != canonicalRoot.path
                    ) {
                        throw IllegalArgumentException("压缩包里有越界路径：${entry.name}")
                    }
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        var entryBytes = 0L
                        file.outputStream().use { output ->
                            while (true) {
                                val n = zip.read(buffer)
                                if (n <= 0) break
                                entryBytes += n
                                totalBytes += n
                                if (entryBytes > limits.maxEntryBytes) {
                                    throw IllegalArgumentException(
                                        "压缩包里的单个文件太大（${entry.name}，上限 " +
                                            "${limits.maxEntryBytes / (1024 * 1024)}MB）",
                                    )
                                }
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw IllegalArgumentException(
                                        "解压后总大小超过上限（${limits.maxTotalBytes / (1024 * 1024)}MB）",
                                    )
                                }
                                output.write(buffer, 0, n)
                            }
                        }
                        out += file
                    }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    /** 在根目录或一层子目录里找 manifest。 */
    private fun findManifest(dir: File): File? {
        val direct = File(dir, MANIFEST)
        if (direct.isFile) return direct
        dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            val nested = File(sub, MANIFEST)
            if (nested.isFile) return nested
        }
        return null
    }

    companion object {
        const val MANIFEST = "plugin.manifest.json"
    }
}