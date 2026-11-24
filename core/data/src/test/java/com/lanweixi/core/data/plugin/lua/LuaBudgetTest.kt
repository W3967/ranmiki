package com.lanweixi.core.data.plugin.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P216：Lua 沙箱的执行预算。
 *
 * 这组用例都不该挂住测试线程：一旦限制失效，跑测试会直接卡死。
 */
class LuaBudgetTest {

    @Test
    fun `top level infinite loop is stopped by the instruction budget`() {
        val globals = LuaSandbox.newGlobals(maxInstructions = 20_000, maxMillis = 60_000)

        val error = runCatching { LuaSandbox.load(globals, "while true do end", "loop") }.exceptionOrNull()

        assertTrue("应被预算终止，实际：$error", error is LuaBudgetExceededError)
    }

    @Test
    fun `pcall cannot swallow the budget error`() {
        // 这是最恶心的绕过写法：错误被 pcall 吞掉就永远出不来
        val globals = LuaSandbox.newGlobals(maxInstructions = 20_000, maxMillis = 60_000)

        val error = runCatching {
            LuaSandbox.load(
                globals,
                "while true do pcall(function() while true do end end) end",
                "loop_in_pcall",
            )
        }.exceptionOrNull()

        assertTrue("pcall 不该能吞掉超限，实际：$error", error is LuaBudgetExceededError)
    }

    @Test
    fun `long but finite script is not killed`() {
        val globals = LuaSandbox.newGlobals(maxInstructions = 300_000, maxMillis = 60_000)

        val value = LuaSandbox.load(
            globals,
            "local s = 0\nfor i = 1, 1000 do s = s + i end\nreturn s",
            "sum",
        )

        assertEquals(500500.0, value.tonumber().todouble(), 0.0)
    }

    @Test
    fun `budget resets between executions`() {
        val globals = LuaSandbox.newGlobals(maxInstructions = 20_000, maxMillis = 60_000)
        assertTrue(
            runCatching { LuaSandbox.load(globals, "while true do end", "a") }
                .exceptionOrNull() is LuaBudgetExceededError,
        )

        // 上次超限不能连累下一次调用（LuaSandbox.load 会 resetBudget）
        val value = LuaSandbox.load(globals, "return 1 + 1", "b")

        assertEquals(2.0, value.tonumber().todouble(), 0.0)
    }

    @Test
    fun `wall clock budget also stops execution`() {
        var now = 0L
        val limiter = LuaInstructionLimiter(
            maxInstructions = 100_000_000L,
            maxMillis = 100L,
            nanoClock = { now },
        )
        limiter.reset()
        now = 200L * 1_000_000L // 过了 200ms

        val error = runCatching {
            // 每 1024 条查一次钟：循环到那个点就会抛
            repeat(2048) { limiter.onInstruction(it, org.luaj.vm2.LuaValue.NIL, 0) }
        }.exceptionOrNull()

        assertTrue("墙钟超时也要中止，实际：$error", error is LuaBudgetExceededError)
    }
}
