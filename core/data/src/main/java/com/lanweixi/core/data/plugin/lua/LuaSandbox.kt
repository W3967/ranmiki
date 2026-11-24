package com.lanweixi.core.data.plugin.lua

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaClosure
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseMathLib

/**
 * P216：Lua 执行预算耗尽。
 *
 * **故意继承 [Error] 而不是 LuaError / RuntimeException**：
 * LuaJ 的 `pcall` 只捕获 `LuaError` 和 `Exception`（`BaseLib$pcall` 的异常表），
 * 继承 Exception 会被插件自己的 `pcall` 吞掉；插件再套一层
 * `while true do pcall(function() while true do end end) end` 就能继续卡死宿主线程。
 * 继承 Error 可以穿透 pcall，直接终止这一次执行。
 */
class LuaBudgetExceededError(message: String) : Error(message)

/** 单次脚本执行的预算（`while true do end` 之类的兜底）。 */
object LuaBudget {
    /** 2M 条指令≈一两次简单插件调用的几百倍余量。 */
    const val MAX_INSTRUCTIONS = 3_000_000L

    /** 墙钟上限：卡在网络/宿主 API 上也别超过这个时间。 */
    const val MAX_MILLIS = 8_000L
}

/**
 * P216：按指令数 + 墙钟时间限制脚本。
 *
 * LuaJ 的 `LuaClosure.execute` 在 `globals.debuglib != null` 时逐条调用 [onInstruction]，
 * 这里借它做计数，超限抛 [LuaBudgetExceededError]。
 * `DebugLib` 本身**不 load 进 globals**（插件拿不到 `debug.sethook`，改不了预算），
 * 只作为预算器挂在 `globals.debuglib` 上。
 */
class LuaInstructionLimiter(
    private val maxInstructions: Long = LuaBudget.MAX_INSTRUCTIONS,
    private val maxMillis: Long = LuaBudget.MAX_MILLIS,
    private val nanoClock: () -> Long = { System.nanoTime() },
) : DebugLib() {

    @Volatile private var remaining = maxInstructions
    @Volatile private var deadlineNanos = 0L

    /** 每次执行（加载 main.lua / 调用一个工具函数）前重置预算。 */
    fun reset() {
        remaining = maxInstructions
        deadlineNanos = nanoClock() + maxMillis * 1_000_000L
    }

    override fun onInstruction(pc: Int, v: Varargs, top: Int) {
        val left = --remaining
        if (left < 0L) {
            throw LuaBudgetExceededError(
                "插件执行超过 $maxInstructions 条指令，已中止（疑似死循环）",
            )
        }
        // 每 1024 条查一次钟，避免逐条读时钟拖慢正常脚本
        if ((left and 0x3FFL) == 0L && nanoClock() > deadlineNanos) {
            throw LuaBudgetExceededError("插件执行超过 ${maxMillis / 1000} 秒，已中止")
        }
    }

    /** 不需要调用栈；不覆写会走到 DebugLib 内部（没 load 过，globals 为空会 NPE）。 */
    override fun onCall(f: LuaFunction) = Unit

    override fun onCall(f: LuaClosure, v: Varargs, r: Array<LuaValue>) = Unit

    override fun onReturn() = Unit

    override fun traceback(level: Int): String = ""
}

/**
 * P140：Lua 沙箱。
 *
 * **不要用 `JsePlatform.standardGlobals()`**——它会加载 `LuajavaLib`，
 * 插件一句 `luajava.bindClass("java.lang.Runtime")` 就能执行任意命令。
 * 这里只注册纯计算类的库，文件 / 系统 / Java 反射一概不给。
 */
object LuaSandbox {

    fun newGlobals(
        maxInstructions: Long = LuaBudget.MAX_INSTRUCTIONS,
        maxMillis: Long = LuaBudget.MAX_MILLIS,
    ): Globals {
        val globals = Globals()
        // 顺序有讲究：TableLib 初始化时要访问 package 表，所以先装 PackageLib
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())
        globals.load(Bit32Lib())

        // 明确不加载：LuajavaLib（能绑任意 Java 类）/ IoLib（文件）/ OsLib（执行命令）

        // BaseLib 自带 dofile / loadfile —— 那是文件读取，必须堵掉
        globals.set("dofile", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)
        // require 只允许已注册的 loader，不给搜索路径
        runCatching {
            globals.get("package").set("path", LuaValue.valueOf(""))
            globals.get("package").set("cpath", LuaValue.valueOf(""))
        }

        // P216：CPU/时间兜底。不 load DebugLib，只把它作为预算器挂上；
        // LuaClosure.execute 每条指令都会回来问一次。
        globals.debuglib = LuaInstructionLimiter(maxInstructions, maxMillis)

        // LuaJ 必须显式装上加载器与编译器，否则 load() 会抛 "No compiler."
        LoadState.install(globals)
        LuaC.install(globals)
        return globals
    }

    /** P216：重置这个 globals 的执行预算（加载脚本 / 每次工具调用前）。 */
    fun resetBudget(globals: Globals) {
        (globals.debuglib as? LuaInstructionLimiter)?.reset()
    }

    /**
     * 编译并执行一段脚本，返回脚本的执行结果。
     *
     * 语法错误以 [LuaError] 抛出，超预算以 [LuaBudgetExceededError] 终止。
     */
    fun load(globals: Globals, source: String, chunkName: String): LuaValue {
        resetBudget(globals)
        return globals.load(source, chunkName).call()
    }

    /**
     * JSON 字符串 → Lua table。
     *
     * 插件函数的入参就是模型给的 JSON，SDK 里让作者直接 `args.query` 取字段。
     */
    fun jsonToLua(json: String, parser: (String) -> Map<String, Any?>): LuaValue {
        val map = runCatching { parser(json.ifBlank { "{}" }) }.getOrElse { emptyMap() }
        return anyToLua(map)
    }

    fun anyToLua(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is String -> LuaValue.valueOf(value)
        is Map<*, *> -> {
            val table = LuaValue.tableOf()
            value.forEach { (k, v) -> if (k != null) table.set(LuaValue.valueOf(k.toString()), anyToLua(v)) }
            table
        }
        is List<*> -> {
            val table = LuaValue.tableOf()
            value.forEachIndexed { index, v -> table.set(index + 1, anyToLua(v)) }
            table
        }
        else -> LuaValue.valueOf(value.toString())
    }

    /** Lua 返回值 → 给模型看的文本。表会被转成紧凑 JSON 风格。 */
    fun luaToText(value: LuaValue): String = when {
        value.isnil() -> ""
        value.isstring() -> value.tojstring()
        value.isboolean() -> value.toboolean().toString()
        value.isnumber() -> value.tonumber().tojstring()
        value.istable() -> tableToText(value.checktable())
        else -> value.tojstring()
    }

    private fun tableToText(table: LuaValue): String {
        val sb = StringBuilder()
        val length = table.length()
        if (length > 0) {
            sb.append('[')
            for (i in 1..length) {
                if (i > 1) sb.append(", ")
                sb.append(wrapLuaValue(table.get(i)))
            }
            sb.append(']')
        } else {
            sb.append('{')
            var first = true
            var key: LuaValue = table.next(LuaValue.NIL).arg1()
            while (!key.isnil()) {
                if (!first) sb.append(", ")
                first = false
                sb.append('"').append(key.tojstring()).append("\": ")
                sb.append(wrapLuaValue(table.get(key)))
                key = table.next(key).arg1()
            }
            sb.append('}')
        }
        return sb.toString()
    }

    private fun wrapLuaValue(value: LuaValue): String = when {
        value.isstring() -> "\"" + value.tojstring().replace("\"", "\\\"") + "\""
        value.isnil() -> "null"
        value.isboolean() -> value.toboolean().toString()
        value.isnumber() -> value.tonumber().tojstring()
        value.istable() -> tableToText(value.checktable())
        else -> "\"" + value.tojstring() + "\""
    }
}