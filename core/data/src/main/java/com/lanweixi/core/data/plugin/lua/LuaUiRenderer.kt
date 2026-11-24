package com.lanweixi.core.data.plugin.lua

import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginUiEntry
import com.lanweixi.domain.plugin.PluginUiNode
import com.lanweixi.domain.plugin.PluginUiPage
import java.io.File
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * P165：把插件的 Lua 描述树转成宿主能渲染的节点树。
 *
 * 插件界面的约定：`uiEntries[].entryId` 对应 main.lua 里的 `page_<entryId>` 函数，
 * 该函数返回一个数组，每项是一张带 `type` 字段的表。
 *
 * 单节点解析失败**不打断整页**——记一条提示继续渲染其余节点，
 * 免得插件某一行写错就整页白屏。
 */
class LuaUiRenderer(
    private val apis: LuaHostApis,
) {

    /** 插件的界面入口能不能打开（main.lua 里有对应函数）。 */
    fun probe(pluginDir: File, manifest: PluginManifest, entry: PluginUiEntry): Boolean {
        val globals = prepare(pluginDir, manifest) ?: return false
        return runCatching { globals.get(pageFunction(entry.entryId)).isfunction() }.getOrDefault(false)
    }

    /**
     * 渲染一页。
     *
     * @param action 点击按钮后要执行的 Lua 函数名；首次打开传空
     */
    fun render(
        pluginDir: File,
        manifest: PluginManifest,
        entry: PluginUiEntry,
        action: String? = null,
    ): PluginUiPage {
        val globals = prepare(pluginDir, manifest)
            ?: return PluginUiPage(error = "main.lua 加载失败")
        val fnName = action?.takeIf { it.isNotBlank() } ?: pageFunction(entry.entryId)
        val fn = globals.get(fnName)
        if (!fn.isfunction()) {
            return PluginUiPage(error = "插件没有提供函数 " + fnName)
        }
        val result = try {
            fn.invoke(LuaValue.tableOf()).arg1()
        } catch (e: LuaError) {
            return PluginUiPage(error = "页面执行出错：" + (e.message ?: "未知"))
        } catch (e: Exception) {
            return PluginUiPage(error = "页面执行失败：" + (e.message ?: "未知"))
        }
        if (!result.istable()) {
            return PluginUiPage(error = "page 函数应返回一个数组，实际返回 " + result.typename())
        }
        return PluginUiPage(nodes = parseNodes(result.checktable()))
    }

    private fun pageFunction(entryId: String): String = "page_" + entryId

    /** 建沙箱、装宿主 API、执行 main.lua。 */
    private fun prepare(pluginDir: File, manifest: PluginManifest): Globals? {
        val script = File(pluginDir, "main.lua")
        if (!script.isFile) return null
        return try {
            val globals = LuaSandbox.newGlobals()
            apis.installInto(globals)
            LuaSandbox.load(globals, script.readText(), manifest.id)
            globals
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNodes(array: LuaTable): List<PluginUiNode> {
        val out = mutableListOf<PluginUiNode>()
        val count = array.length()
        for (i in 1..count) {
            val item = array.get(i)
            if (!item.istable()) continue
            parseNode(item.checktable())?.let { out.add(it) }
        }
        return out
    }

    private fun parseNode(node: LuaTable): PluginUiNode? = when (node.get("type").tojstring()) {
        "title" -> PluginUiNode.Title(node.get("text").tojstring().take(MAX_TEXT))
        "text" -> PluginUiNode.Text(node.get("text").tojstring().take(MAX_TEXT))
        "notice" -> PluginUiNode.Notice(node.get("text").tojstring().take(MAX_TEXT))
        "card" -> PluginUiNode.Card(
            title = node.get("title").tojstring().take(200),
            body = node.get("body").tojstring().take(MAX_TEXT),
        )
        "button" -> {
            val action = node.get("action").tojstring()
            if (action.isBlank()) null else {
                PluginUiNode.Button(
                    text = node.get("text").tojstring().take(80),
                    action = action.take(80),
                )
            }
        }
        "list" -> {
            val items = node.get("items")
            if (!items.istable()) null else {
                val list = items.checktable()
                PluginUiNode.BulletList(
                    (1..list.length()).mapNotNull { idx ->
                        val v = list.get(idx)
                        if (v.isnil()) null else v.tojstring().take(300)
                    },
                )
            }
        }
        "divider" -> PluginUiNode.Divider
        else -> null // 未知类型静默跳过，插件升级不会崩宿主
    }

    private companion object {
        const val MAX_TEXT = 2_000
    }
}
