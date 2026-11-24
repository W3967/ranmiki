package com.lanweixi.core.data.plugin

import java.io.File

/**
 * P137：宿主对插件的全部能力。
 *
 * 定成接口是为了让 UI 层可测——ViewModel 只依赖这 6 个方法，
 * 测试里给个 Fake 就行，不需要真的落盘。
 */
interface PluginHost {
    fun installed(): List<InstalledPlugin>
    fun installZip(zipFile: File): PluginInstallResult

    /** 安装内置示例插件（assets 目录名）。 */
    fun installBundled(assetDir: String): PluginInstallResult
    fun uninstall(id: String, deleteSandbox: Boolean)
    fun setEnabled(id: String, enabled: Boolean)
    fun grant(id: String, permissionId: String)
    fun revoke(id: String, permissionId: String)
}

class PluginHostImpl(
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
) : PluginHost {
    override fun installed() = registry.installed()
    override fun installZip(zipFile: File) = installer.installZip(zipFile)
    override fun installBundled(assetDir: String) = installer.installBundled(assetDir)
    override fun uninstall(id: String, deleteSandbox: Boolean) = registry.uninstall(id, deleteSandbox)
    override fun setEnabled(id: String, enabled: Boolean) = registry.setEnabled(id, enabled)
    override fun grant(id: String, permissionId: String) {
        registry.grant(id, permissionId)
    }
    override fun revoke(id: String, permissionId: String) = registry.revoke(id, permissionId)
}