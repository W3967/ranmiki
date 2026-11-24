package com.lanweixi.core.data.plugin

import com.lanweixi.domain.plugin.PluginConfigField
import com.lanweixi.domain.plugin.PluginConfigValues
import java.io.File
import org.json.JSONObject

/**
 * P170：插件配置的落盘。
 *
 * 存在插件自己的沙箱里（`filesDir/plugins/<id>/config.json`），
 * 卸载时跟着沙箱一起走——配置本来就是插件的一部分。
 *
 * 只保存用户改过的键，没改的读 schema 默认值。
 */
class PluginConfigStore {

    fun load(pluginDir: File): PluginConfigValues {
        val file = File(pluginDir, FILE_NAME)
        if (!file.isFile) return PluginConfigValues()
        return runCatching {
            val root = JSONObject(file.readText())
            val map = mutableMapOf<String, String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = root.optString(k)
            }
            PluginConfigValues(map)
        }.getOrElse { PluginConfigValues() }
    }

    fun save(pluginDir: File, values: Map<String, String>) {
        runCatching {
            val root = JSONObject()
            values.forEach { (k, v) -> root.put(k, v) }
            File(pluginDir, FILE_NAME).writeText(root.toString(2))
        }
    }

    /** 把一份配置按 schema 补齐默认值（用于首次展示）。 */
    fun withDefaults(
        fields: List<PluginConfigField>,
        stored: PluginConfigValues,
    ): Map<String, String> = fields.associate { it.key to stored.get(it) }

    private companion object {
        const val FILE_NAME = "config.json"
    }
}
