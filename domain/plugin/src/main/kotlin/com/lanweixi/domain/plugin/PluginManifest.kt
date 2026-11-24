package com.lanweixi.domain.plugin

/**
 * 插件类型（可同时多选）。
 *
 * - [FUNCTION]：给 LLM 用的 Function Call 工具
 * - [UI]：Compose 界面扩展
 */
enum class PluginType(val wireName: String) {
    FUNCTION("function"),
    UI("ui");

    companion object {
        fun fromWire(value: String): PluginType? =
            entries.firstOrNull { it.wireName == value.trim().lowercase() }
    }
}

/** 插件可选依赖：缺失/版本不匹配时插件仍加载，只降级相关功能。 */
data class PluginDependency(
    val pluginId: String,
    val versionRange: String,
)

/** 一条权限申请（权限 id + 给用户看的理由）。 */
data class PluginPermission(
    val permissionId: String,
    val reason: String,
)

/** Function Call 工具定义。 */
data class PluginFunction(
    val name: String,
    val description: String,
    /** true = 每次被 LLM 调用都弹窗确认；false = 预授权后自动调用。 */
    val sensitive: Boolean = false,
    /** JSON Schema 参数结构（原样保留，交给 LLM）。 */
    val params: Map<String, Any?> = emptyMap(),
)

/** Compose UI 入口。 */
data class PluginUiEntry(
    val entryId: String,
    val title: String,
    val placement: PluginPlacement = PluginPlacement.PAGE,
)

enum class PluginPlacement(val wireName: String) {
    PAGE("page"),
    SIDEBAR("sidebar"),
    SETTING("setting"),
    DIALOG("dialog");

    companion object {
        fun fromWire(value: String): PluginPlacement =
            entries.firstOrNull { it.wireName == value.trim().lowercase() } ?: PAGE
    }
}

/** 希望注册的快捷键（仅申请，宿主做冲突检测，最终由用户确认）。 */
data class PluginHotkey(
    val actionId: String,
    val label: String,
)

/**
 * 插件清单（对齐 `plugin.manifest.json` Schema v1）。
 *
 * 注意：`spaceId` **不在** manifest 里——它是运行时上下文，由宿主在回调时传入。
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val minHostVersion: String = "1.0.0",
    val author: String,
    val repo: String? = null,
    val icon: String? = null,
    val description: String,
    val readme: String? = null,
    val pluginType: List<PluginType>,
    val dependencies: List<PluginDependency> = emptyList(),
    val permissions: List<PluginPermission> = emptyList(),
    val functions: List<PluginFunction> = emptyList(),
    val uiEntries: List<PluginUiEntry> = emptyList(),
    /** 声明式配置 Schema；空表示插件自带自定义配置页。 */
    val configSchema: Map<String, Any?>? = null,
    val hotkeys: List<PluginHotkey> = emptyList(),
)
