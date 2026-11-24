package com.lanweixi.domain.tools

import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermissions
import com.lanweixi.domain.plugin.requiresToolConfirmation

/**
 * 把插件清单里的 functions 摊成工具声明。
 *
 * 硬规则：
 * 1. 插件没启用不出工具；
 * 2. `sensitive` 的函数照实标出来，由调用方决定要不要（以及怎么）确认；
 * 3. **P217：插件申请了写库 / 联网 / 读全文等能力时，它的工具一律强制敏感**，
 *    不再信任 manifest 里的 `sensitive` 自声明。
 */
object PluginToolBridge {

    fun specsOf(manifest: PluginManifest, enabled: Boolean): List<ToolSpec> {
        if (!enabled) return emptyList()
        return manifest.functions.mapNotNull { fn -> specOf(manifest, fn) }
    }

    fun specsOf(plugins: List<Pair<PluginManifest, Boolean>>): List<ToolSpec> =
        plugins.flatMap { (manifest, enabled) -> specsOf(manifest, enabled) }

    private fun specOf(manifest: PluginManifest, fn: PluginFunction): ToolSpec? {
        if (fn.name.isBlank()) return null
        return ToolSpec(
            name = sanitizeToolName(fn.name),
            description = fn.description.ifBlank { "来自插件 ${manifest.name} 的能力" },
            parameters = fn.params,
            sensitive = fn.sensitive || manifest.requiresToolConfirmation(),
            source = ToolSource.PLUGIN,
            pluginId = manifest.id,
            originalName = fn.name,
        )
    }

    /**
     * 插件声明了敏感函数、但没申请 `core_db:write:*` 之类写权限时给个提醒——
     * 光靠 manifest 无法判断它要写什么，这里只校验「有没有申请过任何权限」。
     */
    fun warningsOf(manifest: PluginManifest): List<String> {
        val hasSensitive = manifest.functions.any { it.sensitive }
        if (hasSensitive && manifest.permissions.none { PluginPermissions.isKnown(it.permissionId) }) {
            return listOf("插件声明了敏感函数但没有申请任何权限，调用时会直接失败")
        }
        return emptyList()
    }
}