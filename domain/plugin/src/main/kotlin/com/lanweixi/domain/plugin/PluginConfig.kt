package com.lanweixi.domain.plugin

/**
 * P170：插件配置的声明式字段。
 *
 * manifest 里的 `configSchema` 是 JSON Schema 的一个很小子集，
 * 这里把它摊平成一组可以直接渲染成表单的字段。
 *
 * 只支持四种控件：单行文本、整数、开关、下拉。够用了——
 * 插件真需要复杂配置时，走「自定义配置页」（uiEntries 里再画一页）。
 */
enum class ConfigFieldType { TEXT, INTEGER, SWITCH, CHOICE }

data class PluginConfigField(
    val key: String,
    val title: String,
    val type: ConfigFieldType,
    val description: String = "",
    /** 一律用字符串存，读写时按 [type] 转换。 */
    val defaultValue: String = "",
    /** 仅 [ConfigFieldType.CHOICE] 用。 */
    val options: List<String> = emptyList(),
)

/**
 * 把 manifest 的 `configSchema`（已经解析成 Map 的 JSON）摊平。
 *
 * 不认识的结构直接跳过——schema 写错不该让插件装不上。
 */
object PluginConfigSchemaParser {

    fun parse(schema: Map<String, Any?>?): List<PluginConfigField> {
        if (schema == null) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Any?> ?: return emptyList()
        return properties.mapNotNull { (key, raw) ->
            @Suppress("UNCHECKED_CAST")
            val spec = raw as? Map<String, Any?> ?: return@mapNotNull null
            val typeText = (spec["type"] as? String).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val enumValues = (spec["enum"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val type = when {
                typeText == "integer" || typeText == "number" -> ConfigFieldType.INTEGER
                typeText == "boolean" -> ConfigFieldType.SWITCH
                enumValues.isNotEmpty() -> ConfigFieldType.CHOICE
                typeText == "string" -> ConfigFieldType.TEXT
                else -> return@mapNotNull null
            }
            PluginConfigField(
                key = key,
                title = (spec["title"] as? String) ?: key,
                type = type,
                description = (spec["description"] as? String).orEmpty(),
                defaultValue = spec["default"]?.toString().orEmpty(),
                options = enumValues,
            )
        }.sortedBy { it.key }
    }
}

/**
 * 一份实际的配置值（键 → 字符串值）。
 *
 * 只保存用户改过的项，没改的读 [PluginConfigField.defaultValue]。
 */
data class PluginConfigValues(
    val values: Map<String, String> = emptyMap(),
) {
    fun get(field: PluginConfigField): String =
        values[field.key] ?: field.defaultValue

    fun get(key: String, fallback: String = ""): String = values[key] ?: fallback

    fun getInt(key: String, fallback: Int = 0): Int = values[key]?.toIntOrNull() ?: fallback

    fun getBool(key: String, fallback: Boolean = false): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: fallback
}
