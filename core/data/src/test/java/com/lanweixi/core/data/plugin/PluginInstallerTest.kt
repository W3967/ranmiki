package com.lanweixi.core.data.plugin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P137：zip 安装链路验收（Robolectric 真落盘）。
 *
 * 覆盖：正常安装、缺 manifest、依赖不满足、同 id 覆盖、zip slip。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PluginInstallerTest {

    private lateinit var context: Context
    private lateinit var registry: PluginRegistry
    private lateinit var installer: PluginInstaller

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个用例从干净状态开始
        context.getSharedPreferences("lanweixi_plugins", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("lanweixi_plugin_grants", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "plugins").deleteRecursively()
        registry = PluginRegistry(context)
        installer = PluginInstaller(context, registry)
    }

    private fun manifestJson(
        id: String = "com.demo.plugin",
        version: String = "1.0.0",
        dependencies: String = "[]",
        permissions: String = """[{"permissionId":"network","reason":"要联网"}]""",
    ) = """
        {
          "id": "$id",
          "name": "演示插件",
          "version": "$version",
          "minHostVersion": "1.0.0",
          "author": "tester",
          "description": "测试插件",
          "pluginType": ["function"],
          "dependencies": $dependencies,
          "permissions": $permissions,
          "functions": [{"name":"demo.hello","description":"打招呼","sensitive":false}]
        }
    """.trimIndent()

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = File(context.cacheDir, "test_${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `installs plugin and registers it without granting`() {
        val zip = zipOf("plugin.manifest.json" to manifestJson(), "README.md" to "# hi")

        val result = installer.installZip(zip)

        assertTrue("应安装成功：$result", result is PluginInstallResult.Success)
        val success = result as PluginInstallResult.Success
        assertEquals("com.demo.plugin", success.manifest.id)
        assertTrue(File(success.dirPath, "plugin.manifest.json").isFile)
        assertTrue(File(success.dirPath, "README.md").isFile)

        val installed = registry.installed()
        assertEquals(1, installed.size)
        assertEquals("com.demo.plugin", installed[0].manifest.id)
        assertTrue("安装后不应自动授权", installed[0].grants.isEmpty())
        assertTrue(installed[0].enabled)
    }

    @Test
    fun `manifest inside one level subdirectory is accepted`() {
        val zip = zipOf("demo/plugin.manifest.json" to manifestJson(), "demo/README.md" to "# hi")
        val result = installer.installZip(zip)
        assertTrue(result is PluginInstallResult.Success)
        assertEquals("com.demo.plugin", (result as PluginInstallResult.Success).manifest.id)
    }

    @Test
    fun `missing manifest is rejected`() {
        val zip = zipOf("README.md" to "# no manifest")
        val result = installer.installZip(zip)
        assertTrue(result is PluginInstallResult.Failure)
        val message = (result as PluginInstallResult.Failure).errors.joinToString()
        assertTrue("提示应说明缺 manifest", message.contains("plugin.manifest.json"))
        assertTrue(registry.installed().isEmpty())
    }

    @Test
    fun `unsatisfied dependency is rejected`() {
        val zip = zipOf(
            "plugin.manifest.json" to manifestJson(
                dependencies = """[{"pluginId":"com.base.plugin","versionRange":">=1.0.0"}]""",
            ),
        )
        val result = installer.installZip(zip)
        assertTrue(result is PluginInstallResult.Failure)
        val message = (result as PluginInstallResult.Failure).errors.joinToString()
        assertTrue("提示应说明缺依赖", message.contains("com.base.plugin"))
        assertTrue(registry.installed().isEmpty())
    }

    @Test
    fun `satisfied dependency is accepted`() {
        val base = zipOf("plugin.manifest.json" to manifestJson(id = "com.base.plugin"))
        assertTrue(installer.installZip(base) is PluginInstallResult.Success)

        val child = zipOf(
            "plugin.manifest.json" to manifestJson(
                id = "com.child.plugin",
                dependencies = """[{"pluginId":"com.base.plugin","versionRange":">=1.0.0"}]""",
            ),
        )
        val result = installer.installZip(child)
        assertTrue("依赖已满足应装成功：$result", result is PluginInstallResult.Success)
        assertEquals(2, registry.installed().size)
    }

    @Test
    fun `reinstall replaces previous copy`() {
        val first = zipOf("plugin.manifest.json" to manifestJson(version = "1.0.0"), "old.txt" to "x")
        assertTrue(installer.installZip(first) is PluginInstallResult.Success)
        val dirPath = registry.installed()[0].dirPath

        val second = zipOf("plugin.manifest.json" to manifestJson(version = "2.0.0"), "new.txt" to "y")
        assertTrue(installer.installZip(second) is PluginInstallResult.Success)

        assertEquals(1, registry.installed().size)
        assertEquals("2.0.0", registry.installed()[0].manifest.version)
        assertTrue("旧文件应被清掉", !File(dirPath, "old.txt").exists())
        assertTrue(File(dirPath, "new.txt").isFile)
    }

    @Test
    fun `zip slip is rejected`() {
        val zip = zipOf("../../evil.txt" to "boom", "plugin.manifest.json" to manifestJson())
        val result = installer.installZip(zip)
        assertTrue(result is PluginInstallResult.Failure)
        assertTrue(registry.installed().isEmpty())
    }

    // ── P216：zip bomb 资源上限 ─────────────────────────

    @Test
    fun `too many entries is rejected`() {
        val limited = PluginInstaller(context, registry, ExtractionLimits(maxEntries = 2))
        val zip = zipOf(
            "plugin.manifest.json" to manifestJson(),
            "a.txt" to "1",
            "b.txt" to "2",
        )

        val result = limited.installZip(zip)

        assertTrue(result is PluginInstallResult.Failure)
        val message = (result as PluginInstallResult.Failure).errors.joinToString()
        assertTrue("应说明条目太多：$message", message.contains("条目太多"))
        assertTrue(registry.installed().isEmpty())
    }

    @Test
    fun `single oversized entry is rejected while extracting`() {
        val limited = PluginInstaller(context, registry, ExtractionLimits(maxEntryBytes = 2048))
        // 高压缩比：磁盘上只有几十字节，解压后 4KB
        val zip = zipOf(
            "plugin.manifest.json" to manifestJson(),
            "bomb.txt" to "A".repeat(4096),
        )

        val result = limited.installZip(zip)

        assertTrue(result is PluginInstallResult.Failure)
        val message = (result as PluginInstallResult.Failure).errors.joinToString()
        assertTrue("应说明单文件太大：$message", message.contains("单个文件太大"))
    }

    @Test
    fun `total extracted size is capped`() {
        val limited = PluginInstaller(context, registry, ExtractionLimits(maxTotalBytes = 1024))
        val zip = zipOf(
            "plugin.manifest.json" to manifestJson(),
            "a.txt" to "B".repeat(2048),
        )

        val result = limited.installZip(zip)

        assertTrue(result is PluginInstallResult.Failure)
        val message = (result as PluginInstallResult.Failure).errors.joinToString()
        assertTrue("应说明总大小超限：$message", message.contains("总大小"))
        assertTrue(registry.installed().isEmpty())
    }

    @Test
    fun `grant and revoke survive reinstalling`() {
        val zip = zipOf("plugin.manifest.json" to manifestJson())
        installer.installZip(zip)
        registry.grant("com.demo.plugin", "network")
        assertEquals(setOf("network"), registry.grantsOf("com.demo.plugin"))

        val again = zipOf("plugin.manifest.json" to manifestJson(version = "1.1.0"))
        installer.installZip(again)
        assertEquals("覆盖安装不该丢授权", setOf("network"), registry.installed()[0].grants)
    }

    @Test
    fun `uninstall with deleteSandbox removes directory`() {
        val zip = zipOf("plugin.manifest.json" to manifestJson())
        val success = installer.installZip(zip) as PluginInstallResult.Success
        val dir = File(success.dirPath)
        assertTrue(dir.isDirectory)

        registry.uninstall("com.demo.plugin", deleteSandbox = true)
        assertTrue(registry.installed().isEmpty())
        assertTrue("目录应被删除", !dir.exists())
    }

    @Test
    fun `uninstall keeping sandbox leaves directory`() {
        val zip = zipOf("plugin.manifest.json" to manifestJson())
        val success = installer.installZip(zip) as PluginInstallResult.Success
        val dir = File(success.dirPath)

        registry.uninstall("com.demo.plugin", deleteSandbox = false)
        assertTrue(registry.installed().isEmpty())
        assertTrue("目录应保留", dir.isDirectory)
    }
}