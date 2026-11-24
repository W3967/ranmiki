package com.lanweixi.feature.plugins

import com.lanweixi.core.data.plugin.InstalledPlugin
import com.lanweixi.core.data.plugin.PluginHost
import com.lanweixi.core.data.plugin.PluginInstallResult
import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermission
import com.lanweixi.domain.plugin.PluginType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P137：插件管理 ViewModel 单测（Fake 宿主，不落盘）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun manifest(
        id: String = "com.demo.plugin",
        permissions: List<PluginPermission> = emptyList(),
        sensitive: Boolean = false,
    ) = PluginManifest(
        id = id,
        name = "演示插件",
        version = "1.0.0",
        minHostVersion = "1.0.0",
        author = "tester",
        description = "测试用插件",
        pluginType = listOf(PluginType.FUNCTION),
        permissions = permissions,
        functions = listOf(
            PluginFunction(name = "demo.do", description = "做点事", sensitive = sensitive),
        ),
    )

    private class FakeHost(
        var plugins: MutableList<InstalledPlugin> = mutableListOf(),
        var installResult: PluginInstallResult? = null,
    ) : PluginHost {
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()
        val uninstallCalls = mutableListOf<Pair<String, Boolean>>()
        val grants = mutableMapOf<String, MutableSet<String>>()

        override fun installed() = plugins.toList()

        override fun installZip(zipFile: File): PluginInstallResult {
            val result = installResult ?: PluginInstallResult.Failure(listOf("no result"))
            if (result is PluginInstallResult.Success) {
                plugins.add(
                    0,
                    InstalledPlugin(
                        manifest = result.manifest,
                        dirPath = result.dirPath,
                        installedAt = 1L,
                        enabled = true,
                        grants = emptySet(),
                    ),
                )
            }
            return result
        }

        override fun installBundled(assetDir: String): PluginInstallResult =
            installResult ?: PluginInstallResult.Failure(listOf("no bundled result"))

        override fun uninstall(id: String, deleteSandbox: Boolean) {
            uninstallCalls += id to deleteSandbox
            plugins.removeAll { it.manifest.id == id }
        }

        override fun setEnabled(id: String, enabled: Boolean) {
            enabledCalls += id to enabled
            val i = plugins.indexOfFirst { it.manifest.id == id }
            if (i >= 0) plugins[i] = plugins[i].copy(enabled = enabled)
        }

        override fun grant(id: String, permissionId: String) {
            grants.getOrPut(id) { mutableSetOf() } += permissionId
        }

        override fun revoke(id: String, permissionId: String) {
            grants[id]?.remove(permissionId)
        }
    }

    @Test
    fun `init loads plugins`() = runTest {
        val host = FakeHost(
            mutableListOf(
                InstalledPlugin(manifest(), "/tmp/p", 1L, true, emptySet()),
            ),
        )
        val vm = PluginsViewModel(host, dispatcher)
        assertEquals(1, vm.list.value.plugins.size)
        assertFalse(vm.list.value.loading)
    }

    @Test
    fun `install success refreshes and opens detail without granting`() = runTest {
        val host = FakeHost()
        val granted = mutableListOf<PluginPermission>()
        val m = manifest(permissions = granted)
        host.installResult = PluginInstallResult.Success(m, "/tmp/installed", listOf("缺少图标"))
        val vm = PluginsViewModel(host, dispatcher)

        var navigated = false
        vm.install(File("/tmp/a.zip")) { navigated = true }

        assertTrue(navigated)
        assertEquals(1, vm.list.value.plugins.size)
        assertEquals(m, vm.detail.value.manifest)
        assertEquals("/tmp/installed", vm.detail.value.dirPath)
        assertEquals(listOf("缺少图标"), vm.detail.value.warnings)
        assertTrue("安装后不应自动授权", vm.detail.value.grants.isEmpty())
        assertNull(vm.list.value.error)
    }

    @Test
    fun `install failure surfaces errors and does not navigate`() = runTest {
        val host = FakeHost()
        host.installResult = PluginInstallResult.Failure(listOf("缺少依赖插件：com.x 1.0.0"))
        val vm = PluginsViewModel(host, dispatcher)

        var navigated = false
        vm.install(File("/tmp/a.zip")) { navigated = true }

        assertFalse(navigated)
        assertTrue(vm.list.value.plugins.isEmpty())
        assertNotNull(vm.list.value.error)
        assertTrue(vm.list.value.error!!.contains("缺少依赖插件"))
    }

    @Test
    fun `grant and revoke update the selection`() = runTest {
        val host = FakeHost(
            mutableListOf(
                InstalledPlugin(
                    manifest(
                        permissions = listOf(
                            PluginPermission("network", "要联网"),
                            PluginPermission("search:index", "要建索引"),
                        ),
                    ),
                    "/tmp/p", 1L, true, emptySet(),
                ),
            ),
        )
        val vm = PluginsViewModel(host, dispatcher)
        vm.open("com.demo.plugin")

        vm.toggleGrant("network", true)
        assertEquals(setOf("network"), vm.detail.value.grants)
        assertEquals(setOf("network"), host.grants["com.demo.plugin"])

        vm.toggleGrant("search:index", true)
        assertEquals(2, vm.detail.value.grants.size)

        vm.toggleGrant("network", false)
        assertEquals(setOf("search:index"), vm.detail.value.grants)
    }

    @Test
    fun `grant all and revoke all`() = runTest {
        val host = FakeHost(
            mutableListOf(
                InstalledPlugin(
                    manifest(
                        permissions = listOf(
                            PluginPermission("network", "要联网"),
                            PluginPermission("log:subscribe_host", "要读日志"),
                        ),
                    ),
                    "/tmp/p", 1L, true, emptySet(),
                ),
            ),
        )
        val vm = PluginsViewModel(host, dispatcher)
        vm.open("com.demo.plugin")

        vm.grantAll()
        assertEquals(2, vm.detail.value.grants.size)

        vm.revokeAll()
        assertTrue(vm.detail.value.grants.isEmpty())
    }

    @Test
    fun `unknown permission id is rejected`() = runTest {
        val host = FakeHost(
            mutableListOf(InstalledPlugin(manifest(), "/tmp/p", 1L, true, emptySet())),
        )
        val vm = PluginsViewModel(host, dispatcher)
        vm.open("com.demo.plugin")

        vm.toggleGrant("hack:everything", true)
        assertTrue("白名单外的权限不能被授权", vm.detail.value.grants.isEmpty())
        assertTrue(host.grants.isEmpty())
    }

    @Test
    fun `uninstall passes the sandbox choice through`() = runTest {
        val host = FakeHost(
            mutableListOf(InstalledPlugin(manifest(), "/tmp/p", 1L, true, emptySet())),
        )
        val vm = PluginsViewModel(host, dispatcher)
        vm.open("com.demo.plugin")

        var done = false
        vm.uninstall(deleteSandbox = true) { done = true }

        assertTrue(done)
        assertEquals(listOf("com.demo.plugin" to true), host.uninstallCalls)
        assertTrue(vm.list.value.plugins.isEmpty())
        assertNull(vm.detail.value.manifest)
    }

    @Test
    fun `toggle from list does not need detail`() = runTest {
        val host = FakeHost(
            mutableListOf(InstalledPlugin(manifest(), "/tmp/p", 1L, true, emptySet())),
        )
        val vm = PluginsViewModel(host, dispatcher)
        vm.toggleEnabled("com.demo.plugin", false)
        assertEquals(listOf("com.demo.plugin" to false), host.enabledCalls)
        assertFalse(vm.list.value.plugins[0].enabled)
    }
}