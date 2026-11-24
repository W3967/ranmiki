package com.lanweixi.domain.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件清单校验与版本范围。
 */
class PluginManifestValidatorTest {

    private fun sample(
        id: String = "com.lanweixi.plugin.demo",
        version: String = "1.0.0",
        pluginType: List<PluginType> = listOf(PluginType.FUNCTION, PluginType.UI),
        permissions: List<PluginPermission> = listOf(
            PluginPermission(PluginPermissions.CORE_DB_READ_NOTE, "读取笔记用于摘要"),
        ),
        functions: List<PluginFunction> = listOf(
            PluginFunction("get_note_summary", "读取笔记并生成摘要"),
        ),
        uiEntries: List<PluginUiEntry> = listOf(
            PluginUiEntry("demo_stat_page", "笔记统计面板"),
        ),
    ) = PluginManifest(
        id = id,
        name = "Demo 示例插件",
        version = version,
        author = "LanWeixi",
        description = "演示插件",
        pluginType = pluginType,
        permissions = permissions,
        functions = functions,
        uiEntries = uiEntries,
    )

    @Test
    fun validManifestPasses() {
        val result = PluginManifestValidator.validate(sample())
        assertTrue(result.errors.joinToString(), result.ok)
    }

    @Test
    fun badIdRejected() {
        val result = PluginManifestValidator.validate(sample(id = "Com.Example/Plugin"))
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("id") })
    }

    // ── P215：id 不能当路径用 ─────────────────────────────

    @Test
    fun pathTraversalIdsAreRejected() {
        // 安装器会 File(pluginsRoot, id).deleteRecursively()，'..' 能删掉 filesDir 甚至整个应用私有目录
        listOf("..", "../..", "../plugins", ".hidden", "a..b", "com..example", "com.example.", "com/example", "-lead", "x".repeat(200))
            .forEach { bad ->
                val result = PluginManifestValidator.validate(sample(id = bad))
                assertFalse("id 应被拒绝：$bad", result.ok)
                assertTrue("错误里要点出 id：$bad", result.errors.any { it.contains("id") })
            }
    }

    @Test
    fun normalReverseDomainIdsAreAccepted() {
        listOf("com.example.demo", "com.lanweixi.plugin.demo", "com.example.my-plugin").forEach { good ->
            val result = PluginManifestValidator.validate(sample(id = good))
            assertTrue("id 应通过：$good（${result.errors.joinToString()}）", result.ok)
        }
    }

    @Test
    fun badVersionRejected() {
        val result = PluginManifestValidator.validate(sample(version = "1.0"))
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("version") })
    }

    @Test
    fun unknownPermissionRejected() {
        val result = PluginManifestValidator.validate(
            sample(
                permissions = listOf(
                    PluginPermission("core_db:read:everything", "读全库"),
                ),
            ),
        )
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("未知权限") })
    }

    @Test
    fun permissionWithoutReasonRejected() {
        val result = PluginManifestValidator.validate(
            sample(
                permissions = listOf(
                    PluginPermission(PluginPermissions.NETWORK, ""),
                ),
            ),
        )
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("reason") })
    }

    @Test
    fun duplicateFunctionRejected() {
        val result = PluginManifestValidator.validate(
            sample(
                functions = listOf(
                    PluginFunction("dup", "一"),
                    PluginFunction("dup", "二"),
                ),
            ),
        )
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("function 名称重复") })
    }

    @Test
    fun emptyPluginTypeRejected() {
        val result = PluginManifestValidator.validate(sample(pluginType = emptyList()))
        assertFalse(result.ok)
    }

    @Test
    fun functionTypeWithoutFunctionsIsWarningOnly() {
        val result = PluginManifestValidator.validate(
            sample(pluginType = listOf(PluginType.FUNCTION), functions = emptyList())
                .copy(uiEntries = emptyList()),
        )
        assertTrue(result.errors.joinToString(), result.ok)
        assertTrue(result.warnings.any { it.contains("functions") })
    }

    @Test
    fun versionRangeChecks() {
        assertTrue(PluginManifestValidator.inRange("1.2.0", ">=1.0.0"))
        assertFalse(PluginManifestValidator.inRange("0.9.0", ">=1.0.0"))
        assertTrue(PluginManifestValidator.inRange("1.5.0", ">=1.0.0 <2.0.0"))
        assertFalse(PluginManifestValidator.inRange("2.0.0", ">=1.0.0 <2.0.0"))
        assertTrue(PluginManifestValidator.inRange("1.9.9", "^1.0.0"))
        assertFalse(PluginManifestValidator.inRange("2.0.0", "^1.0.0"))
        // 解析不了按满足处理，避免误杀
        assertTrue(PluginManifestValidator.inRange("bad", ">=1.0.0"))
    }

    @Test
    fun missingDependencyDetected() {
        val manifest = sample().copy(
            dependencies = listOf(PluginDependency("com.lanweixi.plugin.helper", ">=1.0.0")),
        )
        val missing = PluginManifestValidator.dependenciesSatisfied(manifest, emptyMap())
        assertEquals(1, missing.size)
        assertEquals("com.lanweixi.plugin.helper", missing[0].pluginId)
    }

    @Test
    fun satisfiedDependencyEmpty() {
        val manifest = sample().copy(
            dependencies = listOf(PluginDependency("com.lanweixi.plugin.helper", ">=1.0.0")),
        )
        val missing = PluginManifestValidator.dependenciesSatisfied(
            manifest,
            mapOf("com.lanweixi.plugin.helper" to "1.2.0"),
        )
        assertTrue(missing.isEmpty())
    }
}
