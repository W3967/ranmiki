package com.lanweixi.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P06a：提示词预算重构的单测。
 * 覆盖：稳定段稳定、空动态段省略、历史裁到 8 条、摘要截断、预算守卫、确定性、maxTokens 默认值。
 */
class PromptBuilderTest {

    private val builder = PromptBuilder()

    private fun history(n: Int) = (1..n).map { PromptHistoryItem(if (it % 2 == 0) "assistant" else "user", "历史第${it}条") }

    private fun request(
        persona: String = "你是兰未晞。",
        dynamic: PromptDynamic = PromptDynamic(),
        history: List<PromptHistoryItem> = emptyList(),
        userText: String = "在吗",
        memoryBlock: String? = null,
        maxTokens: Int = PromptBudgetGuard.MAX_TOKENS_DEFAULT,
    ) = PromptRequest(persona, dynamic, history, userText, memoryBlock, maxTokens)

    @Test
    fun `stable segment is identical across inputs`() {
        val a = builder.stableSegment("你是兰未晞。")
        val b = builder.stableSegment("你是兰未晞。")
        assertEquals(a, b)
        assertTrue(a.contains("【说话方式】"))

        // build 出的 system 前缀也必须就是稳定段
        val r1 = builder.build(request(dynamic = PromptDynamic(timeLine = "2026年11月4日"))) as PromptBuildResult.Built
        val r2 = builder.build(request(history = history(3), userText = "另一句")) as PromptBuildResult.Built
        assertTrue(r1.messages.first().content.startsWith(a))
        assertTrue(r2.messages.first().content.startsWith(a))
    }

    @Test
    fun `empty dynamic segment is omitted`() {
        val built = builder.build(request()) as PromptBuildResult.Built
        val system = built.messages.first().content
        assertEquals(builder.stableSegment("你是兰未晞。"), system)
        assertFalse("空动态段不应留下标题", system.contains("【现在的真实时间】"))
    }

    @Test
    fun `dynamic segment keeps only non-empty parts`() {
        val built = builder.build(
            request(dynamic = PromptDynamic(timeLine = "10:30"))
        ) as PromptBuildResult.Built
        val system = built.messages.first().content
        assertTrue(system.contains("【现在的真实时间】10:30"))
    }

    @Test
    fun `history keeps only last eight`() {
        val built = builder.build(request(history = history(9))) as PromptBuildResult.Built
        val historyMessages = built.messages.drop(1).dropLast(1)
        assertEquals(8, historyMessages.size)
        assertEquals("历史第2条", historyMessages.first().content)
        assertEquals("历史第9条", historyMessages.last().content)
    }

    @Test
    fun `plot summary is clipped to 300 chars`() {
        val long = "摘".repeat(360)
        val segment = builder.dynamicSegment(PromptDynamic(plotSummary = long))
        val body = segment.substringAfter("【前情摘要】")
        assertEquals(300, body.length)
    }

    @Test
    fun `memory block is appended once with double newline`() {
        val built = builder.build(request(userText = "在吗", memoryBlock = "【你记得的、和现在有关的事】\n· 热茶")) as PromptBuildResult.Built
        val user = built.messages.last()
        assertEquals("user", user.role)
        assertEquals("在吗\n\n【你记得的、和现在有关的事】\n· 热茶", user.content)

        val empty = builder.build(request(userText = "在吗", memoryBlock = "   ")) as PromptBuildResult.Built
        assertEquals("在吗", empty.messages.last().content)
    }

    @Test
    fun `default max tokens is 900`() {
        val built = builder.build(request()) as PromptBuildResult.Built
        assertEquals(900, built.maxTokens)
        assertEquals(900, PromptBudgetGuard.MAX_TOKENS_DEFAULT)
    }

    @Test
    fun `same input builds identical output twice`() {
        val dyn = PromptDynamic(
            timeLine = "2026年11月4日 星期三 10:30",
            recentEvent = "今天一起吃了面",
            plotSummary = "前情：一起去看海。",
        )
        val req = request(dynamic = dyn, history = history(8), userText = "在吗", memoryBlock = "记忆块")
        val a = builder.build(req) as PromptBuildResult.Built
        val b = builder.build(req) as PromptBuildResult.Built
        assertEquals(a.messages, b.messages)
        assertEquals(a.budget, b.budget)
        assertEquals(a.maxTokens, b.maxTokens)
    }

    @Test
    fun `stable budget guard rejects oversized persona`() {
        val result = builder.build(request(persona = "人".repeat(1300)))
        assertTrue(result is PromptBuildResult.Rejected)
        result as PromptBuildResult.Rejected
        assertEquals(RejectReason.STABLE_BUDGET, result.reason)
        assertTrue(result.actual > result.limit)
    }

    @Test
    fun `dynamic budget guard rejects oversized dynamic`() {
        val result = builder.build(request(dynamic = PromptDynamic(recentEvent = "累".repeat(320))))
        assertTrue(result is PromptBuildResult.Rejected)
        result as PromptBuildResult.Rejected
        assertEquals(RejectReason.DYNAMIC_BUDGET, result.reason)
    }

    @Test
    fun `total budget guard rejects oversized conversation`() {
        // 稳定段控制在 1200 以内，让总量先越界。
        // P56 加了人格保护段、P206 又加了写入纪律（稳定段底子约 560 token），
        // persona 相应压到 400：stable≈960 < 1200，total≈3200 > 3000。
        val result = builder.build(
            request(
                persona = "人".repeat(400),
                dynamic = PromptDynamic(recentEvent = "累".repeat(250)),
                history = (1..8).map { PromptHistoryItem("user", "历".repeat(250)) },
                userText = "在吗",
            )
        )
        assertTrue(result is PromptBuildResult.Rejected)
        result as PromptBuildResult.Rejected
        assertEquals(RejectReason.TOTAL_BUDGET, result.reason)
        assertTrue(result.actual > result.limit)
    }

    @Test
    fun `max tokens out of range is rejected explicitly`() {
        val low = builder.build(request(maxTokens = 700)) as PromptBuildResult.Rejected
        assertEquals(RejectReason.MAX_TOKENS_RANGE, low.reason)
        assertEquals(700, low.actual)
        assertEquals(800, low.limit) // 低于下界时 limit 指向下界

        val high = builder.build(request(maxTokens = 1200)) as PromptBuildResult.Rejected
        assertEquals(RejectReason.MAX_TOKENS_RANGE, high.reason)
        assertEquals(1200, high.actual)
        assertEquals(1000, high.limit) // 高于上界时 limit 指向上界
    }

    @Test
    fun `unknown history roles are dropped`() {
        val built = builder.build(
            request(
                history = listOf(
                    PromptHistoryItem("system", "系统注入"),
                    PromptHistoryItem("tool", "工具结果"),
                    PromptHistoryItem("user", "用户一"),
                    PromptHistoryItem("assistant", "助手一"),
                )
            )
        ) as PromptBuildResult.Built
        val historyMessages = built.messages.drop(1).dropLast(1)
        assertEquals(2, historyMessages.size)
        assertEquals(listOf("user", "assistant"), historyMessages.map { it.role })
        assertEquals(listOf("用户一", "助手一"), historyMessages.map { it.content })
    }

    @Test
    fun `token estimator counts cjk by char and latin by word`() {
        assertEquals(3, TokenEstimator.estimate("你好吗"))
        assertEquals(2, TokenEstimator.estimate("hello world"))
        assertEquals(0, TokenEstimator.estimate(""))
        assertTrue(TokenEstimator.estimate("你好 world") >= 3)
    }

    @Test
    fun `budget guard sample output`() {
        val ok = builder.build(
            request(
                dynamic = PromptDynamic(
                    timeLine = "2026年11月4日 星期三 10:30",
                ),
                history = history(8),
                userText = "在吗",
                memoryBlock = "【你记得的、和现在有关的事】\n· 他喜欢喝热茶",
            )
        ) as PromptBuildResult.Built
        println("[budget-ok] stable=${ok.budget.stable} dynamic=${ok.budget.dynamic} total=${ok.budget.total} maxTokens=${ok.maxTokens} messages=${ok.messages.size}")

        val stableBad = builder.build(request(persona = "人".repeat(1300))) as PromptBuildResult.Rejected
        println("[budget-reject] reason=${stableBad.reason} actual=${stableBad.actual} limit=${stableBad.limit} budget=$stableBad.budget")
    }
}
