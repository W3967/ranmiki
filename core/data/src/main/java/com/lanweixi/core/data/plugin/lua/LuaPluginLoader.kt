package com.lanweixi.core.data.plugin.lua

import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.requiresToolConfirmation
import com.lanweixi.domain.tools.ToolCall
import com.lanweixi.domain.tools.ToolExecutor
import com.lanweixi.domain.tools.ToolResult
import com.lanweixi.domain.tools.ToolSource
import com.lanweixi.domain.tools.ToolSpec
import com.lanweixi.domain.tools.sanitizeToolName
import java.io.File
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

/** 加载结果：能执行的工具 + 加载过程中发现的问题。 */
data class LuaLoadResult(
    val executors: List<ToolExecutor>,
    val errors: List<String>,
)

/**
 * P140：把插件目录里的 `main.lua` 加载成可执行工具。
 *
 * 约定：
 * ```lua
 * -- main.lua
 * function demo_hello(args)
 *   return "你好，" .. (args.name or "陌生人")
 * end
 * ```
 * 函数名对应 `plugin.manifest.json` 里 `functions[].name` 的**原始名**。
 *
 * 加载失败不影响其它插件：单个插件的错误进 [LuaLoadResult.errors]。
 */
class LuaPluginLoader(
    private val apis: LuaHostApis,
    /** P216：单次脚本执行的指令/时间预算（测试可调小）。 */
    private val maxInstructions: Long = LuaBudget.MAX_INSTRUCTIONS,
    private val maxMillis: Long = LuaBudget.MAX_MILLIS,
) {

    /**
     * @param granted 用户已授权的权限集合（脚本调 API 时逐次检查）
     */
    fun load(pluginDir: File, manifest: PluginManifest, granted: Set<String>): LuaLoadResult {
        val errors = mutableListOf<String>()
        val script = File(pluginDir, SCRIPT_NAME)
        if (!script.isFile) {
            return LuaLoadResult(emptyList(), listOf("插件目录里没有 " + SCRIPT_NAME))
        }
        // P216：脚本本身也是资源入口——别让一个 50MB 的 main.lua 拖死编译
        if (script.length() > MAX_SCRIPT_BYTES) {
            return LuaLoadResult(
                emptyList(),
                listOf("$SCRIPT_NAME 太大（${script.length()} 字节，上限 $MAX_SCRIPT_BYTES）"),
            )
        }

        val globals: Globals = try {
            LuaSandbox.newGlobals(maxInstructions, maxMillis).also { apis.installInto(it) }
        } catch (e: Exception) {
            return LuaLoadResult(emptyList(), listOf("沙箱初始化失败：" + (e.message ?: "未知")))
        }

        try {
            LuaSandbox.load(globals, script.readText(Charsets.UTF_8), manifest.id)
        } catch (e: LuaBudgetExceededError) {
            // P216：顶层代码 while true do end 也要能终止，不能把加载线程卡死
            return LuaLoadResult(emptyList(), listOf("main.lua 执行超时（疑似死循环）：" + e.message))
        } catch (e: LuaError) {
            return LuaLoadResult(emptyList(), listOf("main.lua 执行失败：" + e.message))
        } catch (e: Exception) {
            return LuaLoadResult(emptyList(), listOf("main.lua 读取失败：" + (e.message ?: "未知")))
        }

        val executors = mutableListOf<ToolExecutor>()
        for (fn in manifest.functions) {
            val value = runCatching { globals.get(fn.name) }.getOrNull()
            if (value == null || !value.isfunction()) {
                errors += "main.lua 里没有函数 " + fn.name
                continue
            }
            executors += LuaToolExecutor(manifest, fn, value, globals)
        }
        return LuaLoadResult(executors, errors)
    }

    private companion object {
        const val SCRIPT_NAME = "main.lua"

        /** P216：main.lua 本体大小上限（正常插件脚本只有几 KB）。 */
        const val MAX_SCRIPT_BYTES = 512L * 1024L
    }
}

/**
 * 单个 Lua 函数的执行器。
 *
 * 同步执行（LuaJ 本身是同步的），调用方负责放到 IO 线程上。
 */
class LuaToolExecutor(
    private val manifest: PluginManifest,
    private val fn: PluginFunction,
    private val function: LuaValue,
    private val globals: Globals,
) : ToolExecutor {

    override val spec = ToolSpec(
        name = sanitizeToolName(fn.name),
        description = fn.description.ifBlank { "来自插件 " + manifest.name },
        parameters = fn.params,
        // P217：申请了写库/联网/读全文能力的插件，工具一律强制确认，
        // 不能靠 manifest 把 sensitive 写成 false 绕过弹窗
        sensitive = fn.sensitive || manifest.requiresToolConfirmation(),
        source = ToolSource.PLUGIN,
        pluginId = manifest.id,
        originalName = fn.name,
    )

    override suspend fun execute(call: ToolCall): ToolResult = try {
        // P216：每次调用都重置预算，前一次超限不会连累下一次
        LuaSandbox.resetBudget(globals)
        val args = LuaSandbox.jsonToLua(call.arguments) { json ->
            @Suppress("UNCHECKED_CAST")
            (JSONObject(json).toMapLoose())
        }
        val returned = function.invoke(args).arg1()
        ToolResult(
            callId = call.id,
            name = fn.name,
            content = LuaSandbox.luaToText(returned).take(MAX_OUTPUT_CHARS),
        )
    } catch (e: LuaBudgetExceededError) {
        // P216：`while true do end` 以前会把执行线程占死；现在终止并告诉用户/模型
        ToolResult(
            callId = call.id,
            name = fn.name,
            content = "插件执行超时，已中止：" + (e.message ?: "疑似死循环"),
            isError = true,
        )
    } catch (e: LuaError) {
        ToolResult(callId = call.id, name = fn.name, content = "插件脚本出错：" + e.message, isError = true)
    } catch (e: Exception) {
        ToolResult(
            callId = call.id,
            name = fn.name,
            content = "插件执行失败：" + (e.message ?: "未知原因"),
            isError = true,
        )
    }

    private companion object {
        /** 回填给模型的文本上限，避免插件吐出一大坨把上下文撑爆。 */
        const val MAX_OUTPUT_CHARS = 2_000
    }
}

/** `org.json:json` 没有 toMap，自己摊平（和 PluginManifestParser 里同一个思路）。 */
private fun JSONObject.toMapLoose(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = when (val v = opt(key)) {
            is JSONObject -> v.toMapLoose()
            is org.json.JSONArray -> (0 until v.length()).map { i ->
                when (val item = v.opt(i)) {
                    is JSONObject -> item.toMapLoose()
                    JSONObject.NULL -> null
                    else -> item
                }
            }
            JSONObject.NULL -> null
            else -> v
        }
    }
    return out
}