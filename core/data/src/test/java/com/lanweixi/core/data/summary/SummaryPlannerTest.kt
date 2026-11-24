package com.lanweixi.core.data.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P210：上下文压缩的触发条件与切分。 */
class SummaryPlannerTest {

    private fun history(count: Int, startId: Long = 1L): List<SummaryPlanner.Candidate> =
        (0 until count).map { i ->
            SummaryPlanner.Candidate(
                id = startId + i,
                role = if (i % 2 == 0) "user" else "assistant",
                text = "消息${startId + i}",
            )
        }

    @Test
    fun `does nothing below the threshold`() {
        // 30 条正好不触发（要「超过」）
        assertNull(SummaryPlanner.plan(history(SummaryPlanner.THRESHOLD), 0L, 0))
        assertNull(SummaryPlanner.plan(history(SummaryPlanner.THRESHOLD - 5), 0L, 0))
    }

    @Test
    fun `compresses the head and keeps the tail`() {
        val plan = SummaryPlanner.plan(history(40), 0L, 0)!!

        // 40 条：保留最近 8 条，压前 32 条
        assertEquals(32, plan.toCompress.size)
        assertEquals(1L, plan.toCompress.first().id)
        assertEquals(32L, plan.toCompress.last().id)
        assertEquals(32L, plan.untilId)
        assertEquals(32, plan.coveredCount)
    }

    @Test
    fun `only compresses messages after the previous summary`() {
        // 已有摘要覆盖到 100，之后又来了 40 条
        val plan = SummaryPlanner.plan(history(40, startId = 101L), 100L, 100)!!

        assertEquals(101L, plan.toCompress.first().id)
        assertEquals(132L, plan.untilId)
        assertEquals(32, plan.toCompress.size)
        // 覆盖数要累加（100 + 32）
        assertEquals(132, plan.coveredCount)
    }

    @Test
    fun `caps a single batch`() {
        // 100 条新消息：一次只压 40 条，剩下的下次再压
        val plan = SummaryPlanner.plan(history(100), 0L, 0)!!

        assertEquals(SummaryPlanner.MAX_COMPRESS_BATCH, plan.toCompress.size)
        assertEquals(40L, plan.untilId)
    }

    @Test
    fun `untilId advances so the middle never gets skipped`() {
        val first = SummaryPlanner.plan(history(100), 0L, 0)!!
        val rest = (1..100).map {
            SummaryPlanner.Candidate(it.toLong(), "user", "x")
        }
        val second = SummaryPlanner.plan(rest, first.untilId, first.coveredCount)!!

        assertEquals(41L, second.toCompress.first().id)
        assertEquals(80L, second.untilId)
        assertTrue("两段必须首尾相接，不能跳过中间", second.toCompress.first().id == first.untilId + 1)
    }

    @Test
    fun `empty history is a no-op`() {
        assertNull(SummaryPlanner.plan(emptyList(), 0L, 0))
    }
}
