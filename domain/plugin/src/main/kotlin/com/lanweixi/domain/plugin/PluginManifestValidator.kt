package com.lanweixi.domain.plugin

/** 校验结果：`errors` 为空 = 通过。 */
data class ManifestValidation(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * 插件清单校验（对齐 `plugin.manifest.json` Schema v1）。
 *
 * 只做**结构和契约**校验，不碰权限授权状态——授权是运行时行为。
 */
object PluginManifestValidator {

    /**
     * P215：id 必须是反向域名风格（`com.example.plugin`）。
     *
     * 原正则 `^[a-z0-9.\-]+$` 放行了 `..`；而安装器会 `File(pluginsRoot, id).deleteRecursively()`，
     * 所以 `id = ".."` 能删掉 filesDir（含 DataStore 里的 API Key），`id = "../.."` 能删掉
     * 整个应用私有目录（含 databases 里的聊天记录、笔记）。这里收紧：
     * 以字母开头、段与段之间单点、不允许首尾点与连续点、不允许路径分隔符。
     */
    private val ID_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*(\\.[a-z][a-z0-9]*(-[a-z0-9]+)*)*$")
    private val SEMVER_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")

    /** P215：id 会变成目录名，长度也得有上限。 */
    private const val MAX_ID_CHARS = 64

    fun validate(manifest: PluginManifest): ManifestValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1) 必填与格式
        if (manifest.id.isBlank()) {
            errors += "id 不能为空"
        } else if (manifest.id.length > MAX_ID_CHARS) {
            errors += "id 太长（最多 $MAX_ID_CHARS 个字符）：${manifest.id.take(40)}…"
        } else if (!ID_PATTERN.matches(manifest.id)) {
            errors += "id 必须是反向域名风格（只允许小写字母、数字、点、短横线，且不能以点开头/结尾）：${manifest.id}"
        }
        if (manifest.name.isBlank()) errors += "name 不能为空"
        if (manifest.author.isBlank()) errors += "author 不能为空"
        if (manifest.description.isBlank()) errors += "description 不能为空"

        if (!SEMVER_PATTERN.matches(manifest.version)) {
            errors += "version 必须是语义化版本（如 1.0.0）：${manifest.version}"
        }
        if (!SEMVER_PATTERN.matches(manifest.minHostVersion)) {
            errors += "minHostVersion 必须是语义化版本：${manifest.minHostVersion}"
        }

        // 2) 插件类型
        if (manifest.pluginType.isEmpty()) {
            errors += "pluginType 不能为空（function / ui 至少选一个）"
        }
        if (PluginType.FUNCTION in manifest.pluginType && manifest.functions.isEmpty()) {
            warnings += "声明了 function 类型但没有定义 functions"
        }
        if (PluginType.UI in manifest.pluginType && manifest.uiEntries.isEmpty()) {
            warnings += "声明了 ui 类型但没有定义 uiEntries"
        }

        // 3) 权限：未知 id 直接拒绝
        val unknown = manifest.permissions.map { it.permissionId.trim() }
            .filter { !PluginPermissions.isKnown(it) }
        if (unknown.isNotEmpty()) {
            errors += "未知权限 id：${unknown.joinToString("、")}"
        }
        manifest.permissions.filter { it.permissionId.isBlank() || it.reason.isBlank() }
            .forEach { p ->
                errors += "权限 ${p.permissionId.ifBlank { "(空)" }} 缺少 reason（要告诉用户为什么需要）"
            }
        // 同一权限重复声明
        val dupPermissions = manifest.permissions.groupBy { it.permissionId.trim() }
            .filterValues { it.size > 1 }.keys
        if (dupPermissions.isNotEmpty()) {
            errors += "权限重复声明：${dupPermissions.joinToString("、")}"
        }

        // 4) Function 定义
        val dupFunctions = manifest.functions.groupBy { it.name.trim() }
            .filterValues { it.size > 1 }.keys
        if (dupFunctions.isNotEmpty()) {
            errors += "function 名称重复：${dupFunctions.joinToString("、")}"
        }
        manifest.functions.filter { it.name.isBlank() }.forEach {
            errors += "function 缺少 name"
        }
        manifest.functions.filter { it.description.isBlank() }.forEach {
            errors += "function ${it.name} 缺少 description（LLM 靠它决定是否调用）"
        }

        // 5) UI 入口
        val dupEntries = manifest.uiEntries.groupBy { it.entryId.trim() }
            .filterValues { it.size > 1 }.keys
        if (dupEntries.isNotEmpty()) {
            errors += "uiEntry entryId 重复：${dupEntries.joinToString("、")}"
        }
        manifest.uiEntries.filter { it.entryId.isBlank() || it.title.isBlank() }.forEach {
            errors += "uiEntry 的 entryId 和 title 都不能为空"
        }

        // 6) 依赖
        manifest.dependencies.filter { it.pluginId.isBlank() || it.versionRange.isBlank() }
            .forEach { errors += "依赖缺少 pluginId 或 versionRange" }

        // 7) 快捷键
        val dupHotkeys = manifest.hotkeys.groupBy { it.actionId.trim() }
            .filterValues { it.size > 1 }.keys
        if (dupHotkeys.isNotEmpty()) {
            errors += "快捷键 actionId 重复：${dupHotkeys.joinToString("、")}"
        }

        return ManifestValidation(errors, warnings)
    }

    /** 依赖是否满足（供运行时判断降级用）。 */
    fun dependenciesSatisfied(
        manifest: PluginManifest,
        installed: Map<String, String>,
    ): List<PluginDependency> =
        manifest.dependencies.filter { dep ->
            val version = installed[dep.pluginId]
            version == null || !inRange(version, dep.versionRange)
        }

    /**
     * 极简版本范围判断：支持 `>=1.0.0`、`>=1.0.0 <2.0.0`、`^1.0.0`。
     * 解析不了时按「满足」处理（宁可降级提示，也不误杀插件）。
     */
    fun inRange(version: String, range: String): Boolean {
        val v = parse(version) ?: return true
        val r = range.trim()
        if (r.isEmpty()) return true
        if (r.startsWith("^")) {
            val base = parse(r.removePrefix("^")) ?: return true
            return v.first() == base.first() && v >= base
        }
        val parts = r.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return true
        return parts.all { part ->
            val op = when {
                part.startsWith(">=") -> ">="
                part.startsWith("<=") -> "<="
                part.startsWith(">") -> ">"
                part.startsWith("<") -> "<"
                part.startsWith("=") -> "="
                else -> "="
            }
            val target = parse(part.removePrefix(op)) ?: return@all true
            when (op) {
                ">=" -> v >= target
                "<=" -> v <= target
                ">" -> v > target
                "<" -> v < target
                else -> v == target
            }
        }
    }

    private fun parse(version: String): List<Int>? {
        val m = SEMVER_PATTERN.matchEntire(version.trim()) ?: return null
        val parts = m.value.split(".").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 3) parts + listOf(0, 0) else null
    }

}

/** 语义化版本的比较（固定补零到 5 段，避免 [1,0,0] 与 [1,0,0,0,0] 不相等）。 */
private operator fun List<Int>.compareTo(other: List<Int>): Int {
    for (i in 0 until maxOf(size, other.size)) {
        val a = getOrElse(i) { 0 }
        val b = other.getOrElse(i) { 0 }
        if (a != b) return a.compareTo(b)
    }
    return 0
}
