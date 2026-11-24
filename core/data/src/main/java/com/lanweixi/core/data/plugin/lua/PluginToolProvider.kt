package com.lanweixi.core.data.plugin.lua

import com.lanweixi.core.data.plugin.PluginRegistry
import com.lanweixi.domain.tools.ToolExecutor
import java.io.File

/**
 * P140：把所有启用的插件加载成工具。
 *
 * 每次调用都现读磁盘、现编译——插件数量是个位数，几十毫秒的代价换来
 * 「装完/卸完立刻生效」，比维护缓存失效可靠。
 */
class PluginToolProvider(
    private val registry: PluginRegistry,
    private val hostApis: (granted: Set<String>, pluginId: String) -> LuaHostApis,
) {

    /** 成功加载的工具 + 各插件的问题（问题不阻塞其它插件）。 */
    fun loadAll(): Pair<List<ToolExecutor>, Map<String, List<String>>> {
        val executors = mutableListOf<ToolExecutor>()
        val problems = mutableMapOf<String, List<String>>()

        registry.installed().forEach { plugin ->
            if (!plugin.enabled) return@forEach
            val dir = File(plugin.dirPath)
            if (!dir.isDirectory) {
                problems[plugin.manifest.id] = listOf("插件目录不存在")
                return@forEach
            }
            runCatching {
                LuaPluginLoader(hostApis(plugin.grants, plugin.manifest.id))
                    .load(dir, plugin.manifest, plugin.grants)
            }.onSuccess { result ->
                executors += result.executors
                if (result.errors.isNotEmpty()) problems[plugin.manifest.id] = result.errors
            }.onFailure { e ->
                problems[plugin.manifest.id] = listOf("加载失败：" + (e.message ?: "未知原因"))
            }
        }
        return executors to problems
    }

    fun executorsOnly(): List<ToolExecutor> = loadAll().first

    fun executorsByName(): Map<String, ToolExecutor> =
        executorsOnly().associateBy { it.spec.name }
}