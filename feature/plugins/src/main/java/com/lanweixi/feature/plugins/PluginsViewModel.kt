package com.lanweixi.feature.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.plugin.InstalledPlugin
import com.lanweixi.core.data.plugin.PluginHost
import com.lanweixi.core.data.plugin.PluginInstallResult
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermissions
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PluginsUiState(
    val loading: Boolean = true,
    val plugins: List<InstalledPlugin> = emptyList(),
    val installing: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** 详情页：权限勾选状态 + 危险动作确认。 */
data class PluginDetailUiState(
    val manifest: PluginManifest? = null,
    val dirPath: String = "",
    val enabled: Boolean = true,
    val grants: Set<String> = emptySet(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * P137：插件管理。
 *
 * 安装只做「解压 + 校验 + 登记 + 授权」，不加载任何插件代码；
 * 授权默认全不勾，用户逐项点。
 */
class PluginsViewModel(
    private val host: PluginHost,
    /** 解压与落盘在 IO 上做；单测注入 TestDispatcher 才能等到结果。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _list = MutableStateFlow(PluginsUiState())
    val list: StateFlow<PluginsUiState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(PluginDetailUiState())
    val detail: StateFlow<PluginDetailUiState> = _detail.asStateFlow()

    init {
        refresh()
    }

    fun consumeNotice() = _list.update { it.copy(notice = null) }

    fun refresh() {
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { host.installed() }
                .onSuccess { rows -> _list.update { it.copy(loading = false, plugins = rows) } }
                .onFailure { e -> _list.update { it.copy(loading = false, error = e.message ?: "读取失败") } }
        }
    }

    /** 安装本地 zip。成功后把结果带到详情页，让用户授权。 */
    fun install(zipFile: File, onInstalled: () -> Unit) {
        if (_list.value.installing) return
        _list.update { it.copy(installing = true, error = null) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { host.installZip(zipFile) }
            when (result) {
                is PluginInstallResult.Failure -> _list.update {
                    it.copy(installing = false, error = result.errors.joinToString("；"))
                }
                is PluginInstallResult.Success -> {
                    _list.update {
                        it.copy(installing = false, notice = "已安装 ${result.manifest.name}")
                    }
                    refresh()
                    _list.update { it.copy(installing = false, error = null) }
                    openDetail(
                        manifest = result.manifest,
                        dirPath = result.dirPath,
                        warnings = result.warnings,
                    )
                    onInstalled()
                }
            }
        }
    }

    /** 安装内置示例插件（空状态里的「载入示例」）。 */
    fun installDemo(onInstalled: () -> Unit) {
        if (_list.value.installing) return
        _list.update { it.copy(installing = true, error = null) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { host.installBundled("demo_plugin") }
            when (result) {
                is PluginInstallResult.Failure -> _list.update {
                    it.copy(installing = false, error = result.errors.joinToString("；"))
                }
                is PluginInstallResult.Success -> {
                    refresh()
                    openDetail(result.manifest, result.dirPath, result.warnings)
                    _list.update { it.copy(installing = false, notice = "已载入示例插件") }
                    onInstalled()
                }
            }
        }
    }

    fun open(pluginId: String) {
        viewModelScope.launch {
            val found = host.installed().firstOrNull { it.manifest.id == pluginId }
            if (found == null) {
                _detail.value = PluginDetailUiState(error = "插件不存在")
            } else {
                _detail.value = PluginDetailUiState(
                    manifest = found.manifest,
                    dirPath = found.dirPath,
                    enabled = found.enabled,
                    grants = found.grants,
                )
            }
        }
    }

    private fun openDetail(manifest: PluginManifest, dirPath: String, warnings: List<String>) {
        _detail.value = PluginDetailUiState(
            manifest = manifest,
            dirPath = dirPath,
            enabled = true,
            grants = emptySet(),
            warnings = warnings,
        )
    }

    // ───────── 授权 ─────────

    fun toggleGrant(permissionId: String, granted: Boolean) {
        val manifest = _detail.value.manifest ?: return
        if (!PluginPermissions.isKnown(permissionId)) return
        if (granted) host.grant(manifest.id, permissionId) else host.revoke(manifest.id, permissionId)
        val nextGrants = if (granted) {
            _detail.value.grants + permissionId
        } else {
            _detail.value.grants - permissionId
        }
        _detail.update { it.copy(grants = nextGrants) }
        refresh()
    }

    fun grantAll() {
        val manifest = _detail.value.manifest ?: return
        manifest.permissions.forEach { host.grant(manifest.id, it.permissionId) }
        _detail.update { it.copy(grants = manifest.permissions.map { it.permissionId }.toSet()) }
        refresh()
    }

    fun revokeAll() {
        val manifest = _detail.value.manifest ?: return
        manifest.permissions.forEach { host.revoke(manifest.id, it.permissionId) }
        _detail.update { it.copy(grants = emptySet()) }
        refresh()
    }

    /** 列表页直接开关（不需要先打开详情）。 */
    fun toggleEnabled(pluginId: String, enabled: Boolean) {
        host.setEnabled(pluginId, enabled)
        refresh()
    }

    /** 详情页开关。 */
    fun setEnabled(enabled: Boolean) {
        val manifest = _detail.value.manifest ?: return
        host.setEnabled(manifest.id, enabled)
        _detail.update { it.copy(enabled = enabled) }
        refresh()
    }

    /**
     * 卸载。`deleteSandbox` 由用户二选一，没有默认值——
     * 删沙箱会连插件数据一起没，不能替用户做决定。
     */
    fun uninstall(deleteSandbox: Boolean, onDone: () -> Unit) {
        val manifest = _detail.value.manifest ?: return
        host.uninstall(manifest.id, deleteSandbox)
        _detail.value = PluginDetailUiState()
        refresh()
        onDone()
    }
}

class PluginsViewModelFactory(
    private val host: PluginHost,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PluginsViewModel(host) as T
}