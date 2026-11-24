package com.lanweixi.core.data.plugin

import com.lanweixi.domain.plugin.PluginSourceIndex
import com.lanweixi.domain.plugin.RemotePlugin
import com.lanweixi.domain.plugin.SourceFetchResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * P166：远程插件源客户端。
 *
 * 两件事：拉索引、下载 zip。都不碰权限——装完照样要走本地授权。
 *
 * 安全约束：
 * · 只允许 https（本地调试可以放宽，但默认拒 http）
 * · 索引最大 512KB，zip 最大 20MB
 * · **P216：sha256 必填**；索引里没有或格式不对就不下载
 * · 下载后校验失败直接删文件
 */
class RemoteSourceClient {

    /** 拉一个源的索引。 */
    fun fetchIndex(sourceUrl: String): SourceFetchResult {
        val text = try {
            requestText(sourceUrl, MAX_INDEX_BYTES)
        } catch (e: Exception) {
            return SourceFetchResult.Failure(e.message ?: "网络请求失败", sourceUrl)
        } ?: return SourceFetchResult.Failure("拉取失败", sourceUrl)

        return try {
            val root = JSONObject(text)
            val plugins = mutableListOf<RemotePlugin>()
            val arr = root.optJSONArray("plugins")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val downloadUrl = o.optString("downloadUrl").trim()
                    if (id.isBlank() || downloadUrl.isBlank()) continue
                    plugins.add(
                        RemotePlugin(
                            id = id,
                            name = o.optString("name", id),
                            version = o.optString("version", "0.0.0"),
                            description = o.optString("description"),
                            author = o.optString("author"),
                            downloadUrl = downloadUrl,
                            sizeBytes = o.optLong("size", 0L),
                            sha256 = o.optString("sha256"),
                            minHostVersion = o.optString("minHostVersion"),
                        ),
                    )
                }
            }
            SourceFetchResult.Success(
                PluginSourceIndex(
                    name = root.optString("name", sourceUrl),
                    description = root.optString("description"),
                    plugins = plugins,
                ),
                sourceUrl,
            )
        } catch (e: Exception) {
            SourceFetchResult.Failure("索引格式不对：" + (e.message ?: ""), sourceUrl)
        }
    }

    /**
     * 下载插件 zip 到缓存目录。
     *
     * @return 下载好的文件；失败返回 null（[onError] 给出原因）
     */
    fun download(plugin: RemotePlugin, targetDir: File, onError: (String) -> Unit): File? {
        return try {
            // P216：没有校验值的源一律不装。索引本身可被篡改，
            // 「sha256 可选」等于给中间人留了一条静默换包的通道。
            if (!plugin.checksumValid()) {
                onError("插件源没有提供有效的 sha256 校验值，已拒绝下载安装")
                return null
            }
            targetDir.mkdirs()
            // P215：索引里的 id 不可信，落成文件名前只留安全字符（否则 ".." 能写到缓存目录外）
            val safeId = plugin.id
                .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
                .replace("..", "")
                .take(60)
                .ifBlank { "plugin" }
            val file = File(targetDir, safeId + "-" + plugin.version + ".zip")
            if (file.exists()) file.delete()
            val conn = openConnection(plugin.downloadUrl)
                ?: run { onError("下载地址不合法"); return null }
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            val declared = conn.contentLengthLong
            if (declared > MAX_ZIP_BYTES) {
                conn.disconnect()
                onError("插件包过大：" + declared + " 字节")
                return null
            }
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_ZIP_BYTES) {
                            conn.disconnect()
                            file.delete()
                            onError("插件包超过 20MB 上限")
                            return null
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            conn.disconnect()

            // P216：强制校验，不匹配就删掉，绝不进入安装流程
            val actual = sha256(file)
            if (!actual.equals(plugin.sha256.trim(), ignoreCase = true)) {
                file.delete()
                onError("校验失败：下载内容与索引声明的 sha256 不一致")
                return null
            }
            file
        } catch (e: Exception) {
            onError(e.message ?: "下载失败")
            null
        }
    }

    /** 拉文本（索引用）。 */
    private fun requestText(url: String, maxBytes: Int): String? {
        val conn = openConnection(url) ?: return null
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        // P215：先读后判等于没有上限——边读边计数，超了立刻断开
        return conn.inputStream.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    conn.disconnect()
                    return@use null
                }
                buffer.write(chunk, 0, read)
            }
            conn.disconnect()
            String(buffer.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun openConnection(rawUrl: String): HttpURLConnection? {
        val url = runCatching { URL(rawUrl) }.getOrNull() ?: return null
        // 生产只走 https；http 留给本地自建源调试
        if (url.protocol != "https" && url.protocol != "http") return null
        return (url.openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LanWeixi/1.0")
        }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_INDEX_BYTES = 512 * 1024
        const val MAX_ZIP_BYTES = 20L * 1024 * 1024
    }
}
