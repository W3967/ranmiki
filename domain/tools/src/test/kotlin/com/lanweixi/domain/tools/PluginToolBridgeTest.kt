package com.lanweixi.domain.tools

import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermission
import com.lanweixi.domain.plugin.PluginType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P138：工具契约与插件桥接的单测。 */
class PluginToolBridgeTest {

    private fun manifest(
        functions: List<PluginFunction> = emptyList(),
        permissions: List<PluginPermission> = emptyList(),
    ) = PluginManifest(
        id = "com.demo.plugin",
        name = "演示插件",
        version = "1.0.0",
        author = "tester",
        description = "测试",
        pluginType = listOf(PluginType.FUNCTION),
        permissions = permissions,
        functions = functions,
    )

    @Test
    fun `disabled plugin exposes no tools`() {
        val m = manifest(functions = listOf(PluginFunction("a.b", "做点事")))
        assertTrue(PluginToolBridge.specsOf(m, enabled = false).isEmpty())
        assertEquals(1, PluginToolBridge.specsOf(m, enabled = true).size)
    }

    @Test
    fun `functions become tool specs with source and plugin id`() {
        val m = manifest(
            functions = listOf(
                PluginFunction("demo.search", "搜东西", sensitive = false, params = mapOf("type" to "object")),
                PluginFunction("demo.delete", "删东西", sensitive = true),
            ),
        )
        val specs = PluginToolBridge.specsOf(m, enabled = true)

        assertEquals(2, specs.size)
        assertEquals("demo_search", specs[0].name)
        assertEquals("搜东西", specs[0].description)
        assertEquals(mapOf<String, Any?>("type" to "object"), specs[0].parameters)
        assertEquals(ToolSource.PLUGIN, specs[0].source)
        assertEquals("com.demo.plugin", specs[0].pluginId)
        assertEquals("demo.search", specs[0].originalName)
        assertEquals(false, specs[0].sensitive)
        assertEquals(true, specs[1].sensitive)
    }

    @Test
    fun `name is sanitized to model safe charset`() {
        val m = manifest(functions = listOf(PluginFunction("插件.搜索", "中文名")))
        val spec = PluginToolBridge.specsOf(m, enabled = true).first()
        assertTrue("只应保留 ASCII 安全字符：" + spec.name, spec.name.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertTrue(spec.name.length <= 64)
    }

    @Test
    fun `blank description falls back to plugin name`() {
        val m = manifest(functions = listOf(PluginFunction("demo.x", "")))
        val spec = PluginToolBridge.specsOf(m, enabled = true).first()
        assertTrue(spec.description.contains("演示插件"))
    }

    @Test
    fun `blank function name is skipped`() {
        val m = manifest(functions = listOf(PluginFunction("", "没名字")))
        assertTrue(PluginToolBridge.specsOf(m, enabled = true).isEmpty())
    }

    @Test
    fun `sensitive function without permissions gets a warning`() {
        val m = manifest(
            functions = listOf(PluginFunction("demo.danger", "危险操作", sensitive = true)),
        )
        assertEquals(1, PluginToolBridge.warningsOf(m).size)

        val ok = manifest(
            functions = listOf(PluginFunction("demo.danger", "危险操作", sensitive = true)),
            permissions = listOf(PluginPermission("core_db:write:note", "要写笔记")),
        )
        assertTrue(PluginToolBridge.warningsOf(ok).isEmpty())
    }

    @Test
    fun `non sensitive function needs no warning`() {
        val m = manifest(functions = listOf(PluginFunction("demo.read", "只读")))
        assertTrue(PluginToolBridge.warningsOf(m).isEmpty())
    }

    // ── P217：sensitive 由宿主按能力强制 ───────────────────

    @Test
    fun `plugin with write permission forces all its tools sensitive`() {
        val m = manifest(
            functions = listOf(PluginFunction("demo.x", "悄悄写一条", sensitive = false)),
            permissions = listOf(
                PluginPermission(com.lanweixi.domain.plugin.PluginPermissions.CORE_DB_WRITE_NOTE, "写笔记"),
            ),
        )

        val spec = PluginToolBridge.specsOf(m, enabled = true).single()

        assertTrue("有写权限的插件，工具必须强制确认（不能靠 manifest 自声明绕过）", spec.sensitive)
    }

    @Test
    fun `plugin with network permission forces all its tools sensitive`() {
        val m = manifest(
            functions = listOf(PluginFunction("demo.net", "联网查点东西", sensitive = false)),
            permissions = listOf(
                PluginPermission(com.lanweixi.domain.plugin.PluginPermissions.NETWORK, "联网"),
            ),
        )

        assertTrue(PluginToolBridge.specsOf(m, enabled = true).single().sensitive)
    }

    @Test
    fun `read only plugin keeps its declared flag`() {
        val m = manifest(
            functions = listOf(PluginFunction("demo.read", "查笔记", sensitive = false)),
            permissions = listOf(
                PluginPermission(com.lanweixi.domain.plugin.PluginPermissions.CORE_DB_READ_NOTE, "读笔记"),
            ),
        )

        assertEquals(
            "只读插件不该每次查台账都弹窗",
            false,
            PluginToolBridge.specsOf(m, enabled = true).single().sensitive,
        )
    }

    @Test
    fun `sanitize handles empty and long names`() {
        assertEquals("tool", sanitizeToolName(""))
        assertEquals("___", sanitizeToolName("插件名").take(3))
        assertEquals(64, sanitizeToolName("a".repeat(100)).length)
    }

    @Test
    fun `tool result defaults to non error`() {
        val r = ToolResult(callId = "1", name = "x", content = "ok")
        assertEquals(false, r.isError)
        assertNull(null)
    }
}