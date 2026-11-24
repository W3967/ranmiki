package com.lanweixi.core.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T03 解析层单测：四种契约事件 + OpenAI 兼容格式。
 */
class SseParserTest {

    @Test
    fun `parses start event`() {
        val event = SseParser.parsePayload("""{"type":"start","model":"deepseek-chat","memories":2,"vitals":true}""")
        assertTrue(event is SseEvent.Start)
        event as SseEvent.Start
        assertEquals("deepseek-chat", event.model)
        assertEquals(2, event.memories)
        assertTrue(event.vitals)
    }

    @Test
    fun `parses delta event`() {
        val event = SseParser.parsePayload("""{"type":"delta","text":"在呢。"}""")
        assertTrue(event is SseEvent.Delta)
        assertEquals("在呢。", (event as SseEvent.Delta).text)
    }

    @Test
    fun `parses done event with usage`() {
        val event = SseParser.parsePayload(
            """{"type":"done","reply":"在呢。","latencyMs":420,"usage":{"prompt_tokens":120,"completion_tokens":8}}"""
        )
        assertTrue(event is SseEvent.Done)
        event as SseEvent.Done
        assertEquals("在呢。", event.reply)
        assertEquals(420L, event.latencyMs)
        assertEquals(120, event.promptTokens)
        assertEquals(8, event.completionTokens)
    }

    @Test
    fun `parses error event`() {
        val event = SseParser.parsePayload("""{"type":"error","error":"上游繁忙"}""")
        assertTrue(event is SseEvent.Error)
        assertEquals("上游繁忙", (event as SseEvent.Error).message)
    }

    @Test
    fun `parses openai compatible delta`() {
        val event = SseParser.parsePayload(
            """{"choices":[{"index":0,"delta":{"content":"嗨"},"finish_reason":null}]}"""
        )
        assertTrue(event is SseEvent.Delta)
        assertEquals("嗨", (event as SseEvent.Delta).text)
    }

    @Test
    fun `parses openai finish reason and done marker`() {
        assertTrue(
            SseParser.parsePayload(
                """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
            ) is SseEvent.EndOfStream
        )
        assertTrue(SseParser.parsePayload("[DONE]") is SseEvent.EndOfStream)
    }

    @Test
    fun `parseBlock joins data lines and ignores comments`() {
        val block = ": keep-alive\ndata: {\"type\":\"delta\",\ndata: \"text\":\"嗯\"}"
        val event = SseParser.parseBlock(block)
        assertTrue(event is SseEvent.Delta)
        assertEquals("嗯", (event as SseEvent.Delta).text)
    }

    @Test
    fun `invalid payload returns null`() {
        assertNull(SseParser.parsePayload("not json"))
        assertNull(SseParser.parsePayload(""))
        assertNull(SseParser.parseBlock("event: ping"))
    }

    @Test
    fun `unicode and emoji survive parsing`() {
        val arr = JSONArray().put(JSONObject().put("content", "啊…😿"))
        val json = JSONObject().put("choices", arr).put("delta", JSONObject())
        val event = SseParser.parsePayload(
            JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", "啊…😿")))).toString()
        )
        assertTrue(event is SseEvent.Delta)
        assertEquals("啊…😿", (event as SseEvent.Delta).text)
    }

    // ── P211：流式用量帧 ─────────────────────────────────────

    @Test
    fun `parses openai usage frame`() {
        val event = SseParser.parsePayload(
            """{"choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":300}}""",
        )

        assertTrue("应该是 Usage 事件，实际 $event", event is SseEvent.Usage)
        event as SseEvent.Usage
        assertEquals(1200, event.promptTokens)
        assertEquals(300, event.completionTokens)
    }

    @Test
    fun `usage frame without prompt tokens still parses`() {
        val event = SseParser.parsePayload("""{"usage":{"completion_tokens":5}}""")

        assertTrue(event is SseEvent.Usage)
        assertNull((event as SseEvent.Usage).promptTokens)
        assertEquals(5, event.completionTokens)
    }
}
