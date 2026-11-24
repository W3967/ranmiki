package com.lanweixi.core.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * manifest 解析器（用 Robolectric 拿到真实的 `org.json` 实现）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PluginManifestParserTest {

    private val validJson = """
        {
          "id": "com.lanweixi.plugin.demo",
          "name": "Demo 示例插件",
          "version": "1.0.0",
          "minHostVersion": "1.0.0",
          "author": "LanWeiXi",
          "description": "演示 Function + UI 扩展的示例插件",
          "pluginType": ["function", "ui"],
          "dependencies": [
            { "pluginId": "com.lanweixi.plugin.helper", "versionRange": ">=1.0.0" }
          ],
          "permissions": [
            { "permissionId": "core_db:read:note", "reason": "读取笔记用于摘要统计" },
            { "permissionId": "search:index", "reason": "把摘要加进全局搜索" }
          ],
          "functions": [
            {
              "name": "get_note_summary",
              "description": "读取笔记并生成简短摘要",
              "sensitive": false,
              "params": {
                "type": "object",
                "properties": { "noteId": { "type": "string" } },
                "required": ["noteId"]
              }
            }
          ],
          "uiEntries": [
            { "entryId": "demo_stat_page", "title": "笔记统计面板", "placement": "page" }
          ],
          "configSchema": {
            "type": "object",
            "properties": { "autoSummary": { "type": "boolean", "default": true } }
          },
          "hotkeys": [
            { "actionId": "open_demo_stat_page", "label": "打开笔记统计面板" }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesValidManifest() {
        val result = PluginManifestParser.parse(validJson)
        assertTrue(result is PluginManifestParser.Result.Success)
        val success = result as PluginManifestParser.Result.Success
        assertTrue(success.errors.joinToString(), success.isValid)

        val m = success.manifest
        assertEquals("com.lanweixi.plugin.demo", m.id)
        assertEquals("1.0.0", m.version)
        assertEquals(2, m.pluginType.size)
        assertEquals(1, m.dependencies.size)
        assertEquals(2, m.permissions.size)
        assertEquals(1, m.functions.size)
        assertEquals("get_note_summary", m.functions[0].name)
        assertFalse(m.functions[0].sensitive)
        // params 原样保留（JSON Schema 交给 LLM）
        assertEquals("object", (m.functions[0].params["type"] as String))
        assertEquals(1, m.uiEntries.size)
        assertEquals("demo_stat_page", m.uiEntries[0].entryId)
        assertEquals(1, m.hotkeys.size)
        assertTrue(m.configSchema != null)
    }

    @Test
    fun invalidJsonFails() {
        val result = PluginManifestParser.parse("{not json")
        assertTrue(result is PluginManifestParser.Result.Failure)
    }

    @Test
    fun unknownPluginTypeFails() {
        val json = validJson.replace("\"pluginType\": [\"function\", \"ui\"]", "\"pluginType\": [\"magic\"]")
        val result = PluginManifestParser.parse(json)
        assertTrue(result is PluginManifestParser.Result.Failure)
    }

    @Test
    fun unknownPermissionIsInvalidButParsed() {
        val json = validJson.replace("\"core_db:read:note\"", "\"core_db:read:everything\"")
        val result = PluginManifestParser.parse(json)
        assertTrue(result is PluginManifestParser.Result.Success)
        val success = result as PluginManifestParser.Result.Success
        assertFalse(success.isValid)
        assertTrue(success.errors.any { it.contains("未知权限") })
    }

    @Test
    fun missingRequiredFieldIsInvalid() {
        val json = validJson.replace("\"author\": \"LanWeiXi\",", "")
        val result = PluginManifestParser.parse(json)
        assertTrue(result is PluginManifestParser.Result.Success)
        assertFalse((result as PluginManifestParser.Result.Success).isValid)
    }

    @Test
    fun parseDirectoryReadsManifest() {
        val dir = java.nio.file.Files.createTempDirectory("plugin-test").toFile()
        try {
            java.io.File(dir, "plugin.manifest.json").writeText(validJson)
            val result = PluginManifestParser.parseDirectory(dir)
            assertTrue(result is PluginManifestParser.Result.Success)
            assertEquals(
                "com.lanweixi.plugin.demo",
                (result as PluginManifestParser.Result.Success).manifest.id,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun parseDirectoryWithoutManifestFails() {
        val dir = java.nio.file.Files.createTempDirectory("plugin-empty").toFile()
        try {
            val result = PluginManifestParser.parseDirectory(dir)
            assertTrue(result is PluginManifestParser.Result.Failure)
        } finally {
            dir.deleteRecursively()
        }
    }
}
