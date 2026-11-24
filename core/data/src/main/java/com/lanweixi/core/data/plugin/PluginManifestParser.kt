package com.lanweixi.core.data.plugin

import com.lanweixi.domain.plugin.PluginDependency
import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginHotkey
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginManifestValidator
import com.lanweixi.domain.plugin.PluginPermission
import com.lanweixi.domain.plugin.PluginPlacement
import com.lanweixi.domain.plugin.PluginType
import com.lanweixi.domain.plugin.PluginUiEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * `plugin.manifest.json` 解析器（Schema v1）。
 *
 * 放在 app 层的原因：domain 模块是纯 JVM，没有 `org.json`；
 * 解析完的 [PluginManifest] 和校验规则都在 domain:plugin 里，可独立测试。
 */
object PluginManifestParser {

    sealed interface Result {
        data class Success(
            val manifest: PluginManifest,
            val errors: List<String>,
            val warnings: List<String>,
        ) : Result {
            val isValid: Boolean get() = errors.isEmpty()
        }

        data class Failure(val errors: List<String>) : Result
    }

    fun parse(json: String): Result {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Result.Failure(listOf("不是合法的 JSON：${it.message}"))
        }

        val errors = mutableListOf<String>()

        fun str(key: String): String = root.optString(key, "").trim()
        fun optStr(key: String): String? = root.optString(key, "").trim().takeIf { it.isNotEmpty() }

        // pluginType
        val types = mutableListOf<PluginType>()
        root.optJSONArray("pluginType")?.let { arr ->
            for (i in 0 until arr.length()) {
                val wire = arr.optString(i, "")
                val type = PluginType.fromWire(wire)
                if (type == null) errors += "未知 pluginType：$wire" else types += type
            }
        }

        // dependencies
        val deps = root.optJSONArray("dependencies").toList { item ->
            PluginDependency(
                pluginId = item.optString("pluginId", "").trim(),
                versionRange = item.optString("versionRange", "").trim(),
            )
        }

        // permissions
        val permissions = root.optJSONArray("permissions").toList { item ->
            PluginPermission(
                permissionId = item.optString("permissionId", "").trim(),
                reason = item.optString("reason", "").trim(),
            )
        }

        // functions
        val functions = root.optJSONArray("functions").toList { item ->
            PluginFunction(
                name = item.optString("name", "").trim(),
                description = item.optString("description", "").trim(),
                sensitive = item.optBoolean("sensitive", false),
                params = item.optJSONObject("params")?.toAnyMap() ?: emptyMap(),
            )
        }

        // uiEntries
        val uiEntries = root.optJSONArray("uiEntries").toList { item ->
            PluginUiEntry(
                entryId = item.optString("entryId", "").trim(),
                title = item.optString("title", "").trim(),
                placement = PluginPlacement.fromWire(item.optString("placement", "page")),
            )
        }

        // hotkeys
        val hotkeys = root.optJSONArray("hotkeys").toList { item ->
            PluginHotkey(
                actionId = item.optString("actionId", "").trim(),
                label = item.optString("label", "").trim(),
            )
        }

        val manifest = PluginManifest(
            id = str("id"),
            name = str("name"),
            version = str("version"),
            minHostVersion = str("minHostVersion").ifBlank { "1.0.0" },
            author = str("author"),
            repo = optStr("repo"),
            icon = optStr("icon"),
            description = str("description"),
            readme = optStr("readme"),
            pluginType = types,
            dependencies = deps,
            permissions = permissions,
            functions = functions,
            uiEntries = uiEntries,
            configSchema = root.optJSONObject("configSchema")?.toAnyMap(),
            hotkeys = hotkeys,
        )

        if (errors.isNotEmpty()) return Result.Failure(errors)

        val validation = PluginManifestValidator.validate(manifest)
        return Result.Success(manifest, validation.errors, validation.warnings)
    }

    /** 从目录读 `plugin.manifest.json`。 */
    fun parseDirectory(dir: java.io.File): Result {
        val file = java.io.File(dir, "plugin.manifest.json")
        if (!file.isFile) {
            return Result.Failure(listOf("缺少 plugin.manifest.json（${file.absolutePath}）"))
        }
        return runCatching { parse(file.readText()) }.getOrElse {
            Result.Failure(listOf("读取 manifest 失败：${it.message}"))
        }
    }

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = mutableListOf<T>()
        for (i in 0 until length()) {
            optJSONObject(i)?.let { out += mapper(it) }
        }
        return out
    }

    /** `JSONObject` → 普通 Map（保留嵌套结构，给 LLM 当 schema 用）。 */
    private fun JSONObject.toMap(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = when (val v = opt(key)) {
                null, JSONObject.NULL -> null
                is JSONObject -> v.toMap()
                is JSONArray -> v.toAnyList()
                else -> v
            }
        }
        return out
    }

    private fun JSONArray.toAnyList(): List<Any?> {
        val out = mutableListOf<Any?>()
        for (i in 0 until length()) {
            out += when (val v = opt(i)) {
                null, JSONObject.NULL -> null
                is JSONObject -> v.toMap()
                is JSONArray -> v.toAnyList()
                else -> v
            }
        }
        return out
    }
}

/**
 * `org.json:json` 没有 Android 版 `JSONObject.toMap()`，这里自己摊平。
 *
 * 保留嵌套结构（configSchema / params 都是 JSON Schema 片段，不能拍平成字符串）。
 */
private fun JSONObject.toAnyMap(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = toAnyValue(opt(key))
    }
    return out
}

private fun toAnyValue(value: Any?): Any? = when (value) {
    is JSONObject -> value.toAnyMap()
    is JSONArray -> (0 until value.length()).map { toAnyValue(value.opt(it)) }
    JSONObject.NULL -> null
    else -> value
}
