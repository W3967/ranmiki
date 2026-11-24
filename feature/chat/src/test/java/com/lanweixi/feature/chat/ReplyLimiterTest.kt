package com.lanweixi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P67：回复条数硬规则（文档 8.2「硬规则，不只是提示词」）。
 */
class ReplyLimiterTest {

    @Test
    fun singleLineUnchanged() {
        assertEquals("在", ReplyLimiter.limit("在", 1))
        assertEquals("在", ReplyLimiter.limit("在", 4))
    }

    @Test
    fun trimsToTarget() {
        val reply = "第一条\n第二条\n第三条"
        assertEquals("第一条\n第二条", ReplyLimiter.limit(reply, 2))
        assertEquals("第一条", ReplyLimiter.limit(reply, 1))
    }

    @Test
    fun targetZeroMeansNoLimit() {
        val reply = "a\nb\nc"
        assertEquals(reply, ReplyLimiter.limit(reply, 0))
    }

    @Test
    fun blankLinesAreIgnoredWhenCounting() {
        val reply = "第一条\n\n第二条\n\n\n第三条"
        assertEquals(3, ReplyLimiter.count(reply))
        assertEquals("第一条\n第二条", ReplyLimiter.limit(reply, 2))
    }

    @Test
    fun fewerThanTargetKeepsAll() {
        val reply = "一\n二"
        assertEquals(reply, ReplyLimiter.limit(reply, 4))
    }

    @Test
    fun blankReplyStaysBlank() {
        assertEquals("", ReplyLimiter.limit("", 2))
        assertEquals("   ", ReplyLimiter.limit("   ", 2))
    }
}
