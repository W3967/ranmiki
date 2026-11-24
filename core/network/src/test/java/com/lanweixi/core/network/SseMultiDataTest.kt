package com.lanweixi.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多行 data 块的解析回归：OpenAI 兼容流常把多条事件塞进同一个传输块。
 */
class SseMultiDataTest {

    @Test
    fun `parseEvents splits multi data block`() {
        val block = buildString {
            append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\u55ef\"},\"finish_reason\":null}]}\n")
            append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\u3002\"},\"finish_reason\":null}]}\n")
            append("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n")
            append("data: [DONE]")
        }
        val events = SseParser.parseEvents(block)
        println("DEBUG events=" + events.map { it::class.simpleName })
        assertEquals(2, events.count { it is SseEvent.Delta })
        assertTrue("should contain EndOfStream", events.any { it is SseEvent.EndOfStream })
    }
}
