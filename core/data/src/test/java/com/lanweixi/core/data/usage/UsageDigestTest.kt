package com.lanweixi.core.data.usage

import com.lanweixi.core.data.entity.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P211：token 用量汇总（纯计算）。 */
class UsageDigestTest {

    private fun usage(prompt: Int, completion: Int, at: Long = 1L) = EventEntity(
        type = UsageDigest.EVENT_TYPE,
        payload = "$prompt,$completion",
        at = at,
    )

    @Test
    fun `sums prompt and completion across events`() {
        val totals = UsageDigest.sumOf(
            listOf(usage(100, 20), usage(300, 40)),
        )

        assertEquals(400L, totals.promptTokens)
        assertEquals(60L, totals.completionTokens)
        assertEquals(460L, totals.total)
    }

    @Test
    fun `ignores events of other types`() {
        val totals = UsageDigest.sumOf(
            listOf(
                usage(100, 20),
                EventEntity(type = "task_done", payload = "写周报", at = 2L),
                EventEntity(type = "conversation", payload = "hi", at = 3L),
            ),
        )

        assertEquals(120L, totals.total)
    }

    @Test
    fun `bad payload counts as zero`() {
        val totals = UsageDigest.sumOf(
            listOf(
                EventEntity(type = UsageDigest.EVENT_TYPE, payload = "", at = 1L),
                EventEntity(type = UsageDigest.EVENT_TYPE, payload = "abc", at = 2L),
                usage(10, 5),
            ),
        )

        assertEquals(15L, totals.total)
    }

    @Test
    fun `parse handles missing completion`() {
        assertEquals(120 to 0, UsageDigest.parsePayload("120"))
        assertEquals(0 to 0, UsageDigest.parsePayload(""))
    }

    @Test
    fun `format is human readable`() {
        assertEquals("0", UsageDigest.format(0))
        assertEquals("999", UsageDigest.format(999))
        assertEquals("1.5k", UsageDigest.format(1500))
        assertEquals("2.0M", UsageDigest.format(2_000_000))
        assertTrue(UsageDigest.Totals().isEmpty)
    }
}
