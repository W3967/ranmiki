package com.lanweixi.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * P209：Anthropic 请求与流式解析。
 *
 * 用 MockWebServer 验三件事：
 * 1. 走 `/v1/messages`、带 `x-api-key` + `anthropic-version`；
 * 2. system 拆成独立字段、`role=tool` 的消息变成 `tool_result` 块；
 * 3. 流事件能还原出文本、工具调用和 token 用量。
 */
class AnthropicClientTest {

    private lateinit var server: MockWebServer
    private val client = ChatStreamClient(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    /** baseUrl 带 /anthropic，让协议判定命中（真实使用里是 api.anthropic.com）。 */
    private fun baseUrl(): String = server.url("/anthropic").toString().trimEnd('/')

    private fun request(
        messages: List<ChatMessage> = listOf(ChatMessage("user", "你好")),
        tools: List<ToolDeclaration> = emptyList(),
        toolChoice: String = "auto",
    ) = ChatStreamRequest(
        baseUrl = baseUrl(),
        apiKey = "sk-ant-test",
        model = "claude-3-5-sonnet-latest",
        messages = messages,
        maxTokens = 256,
        tools = tools,
        toolChoice = toolChoice,
    )

    private fun enqueueSse(body: String) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody(body),
        )
    }

    private class Collector : ChatStreamCallback {
        val deltas = mutableListOf<String>()
        val toolFragments = mutableListOf<ToolCallFragment>()
        val events = mutableListOf<SseEvent>()
        val errors = mutableListOf<ChatError>()
        var closed = false
        val done = CountDownLatch(1)
        val closedLatch = CountDownLatch(1)

        override fun onEvent(event: SseEvent) {
            events += event
            when (event) {
                is SseEvent.Delta -> deltas += event.text
                is SseEvent.Done -> done.countDown()
                else -> Unit
            }
        }

        override fun onError(error: ChatError) {
            errors += error
            done.countDown()
        }

        override fun onClosed() {
            closed = true
            closedLatch.countDown()
        }

        override fun onToolCalls(fragments: List<ToolCallFragment>) {
            toolFragments += fragments
        }
    }

    @Test
    fun `posts to messages endpoint with anthropic headers`() {
        enqueueSse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        val collector = Collector()
        val handle = client.open(request(), collector)
        handle.start()
        collector.closedLatch.await(5, TimeUnit.SECONDS)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/anthropic/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertEquals(null, recorded.getHeader("Authorization"))

        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("claude-3-5-sonnet-latest", body.getString("model"))
        assertEquals(256, body.getInt("max_tokens"))
        assertTrue("必须开流式", body.getBoolean("stream"))
    }

    @Test
    fun `system message becomes a top level field`() {
        enqueueSse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        val collector = Collector()
        client.open(
            request(
                messages = listOf(
                    ChatMessage("system", "你是兰未晞"),
                    ChatMessage("user", "在吗"),
                ),
            ),
            collector,
        ).start()
        collector.closedLatch.await(5, TimeUnit.SECONDS)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("你是兰未晞", body.getString("system"))
        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
    }

    @Test
    fun `tool calls and tool results become content blocks`() {
        enqueueSse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        val collector = Collector()
        client.open(
            request(
                messages = listOf(
                    ChatMessage("system", "系统"),
                    ChatMessage("user", "记一下：明天面试"),
                    ChatMessage(
                        role = "assistant",
                        content = "",
                        toolCalls = listOf(
                            ToolCallOut("c1", "host_propose_task", "{\"title\":\"面试\"}"),
                        ),
                    ),
                    ChatMessage("tool", "[工具结果]", toolCallId = "c1"),
                    ChatMessage("user", "好的"),
                ),
            ),
            collector,
        ).start()
        collector.closedLatch.await(5, TimeUnit.SECONDS)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val messages = body.getJSONArray("messages")
        // user / assistant(tool_use) / user(tool_result + text)
        assertEquals(3, messages.length())

        val assistant = messages.getJSONObject(1)
        assertEquals("assistant", assistant.getString("role"))
        val toolUse = assistant.getJSONArray("content").getJSONObject(0)
        assertEquals("tool_use", toolUse.getString("type"))
        assertEquals("c1", toolUse.getString("id"))
        assertEquals("host_propose_task", toolUse.getString("name"))
        assertEquals("面试", toolUse.getJSONObject("input").getString("title"))

        val lastUser = messages.getJSONObject(2)
        assertEquals("user", lastUser.getString("role"))
        val blocks = lastUser.getJSONArray("content")
        assertEquals("tool_result", blocks.getJSONObject(0).getString("type"))
        assertEquals("c1", blocks.getJSONObject(0).getString("tool_use_id"))
        assertEquals("好的", blocks.getJSONObject(1).getString("text"))
    }

    @Test
    fun `tools use input schema and required maps to any`() {
        enqueueSse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        val collector = Collector()
        client.open(
            request(
                tools = listOf(
                    ToolDeclaration(
                        name = "host_search_notes",
                        description = "查笔记",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to mapOf("query" to mapOf("type" to "string")),
                            "required" to listOf("query"),
                        ),
                    ),
                ),
                toolChoice = "required",
            ),
            collector,
        ).start()
        collector.closedLatch.await(5, TimeUnit.SECONDS)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val tools = body.getJSONArray("tools")
        val tool = tools.getJSONObject(0)
        assertEquals("host_search_notes", tool.getString("name"))
        val schema = tool.getJSONObject("input_schema")
        assertEquals("object", schema.getString("type"))
        assertEquals("any", body.getJSONObject("tool_choice").getString("type"))
    }

    @Test
    fun `stream events produce text, tool fragments and usage`() {
        enqueueSse(
            "event: message_start\n" +
                "data: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-3-5-sonnet-latest\",\"usage\":{\"input_tokens\":12}}}\n\n" +
                "event: content_block_start\n" +
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"呀\"}}\n\n" +
                "event: content_block_stop\n" +
                "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
                "event: message_delta\n" +
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}\n\n" +
                "event: message_stop\n" +
                "data: {\"type\":\"message_stop\"}\n\n",
        )
        val collector = Collector()
        client.open(request(), collector).start()
        assertTrue("应在 5 秒内完成", collector.done.await(5, TimeUnit.SECONDS))

        assertEquals(listOf("你好", "呀"), collector.deltas)
        val done = collector.events.filterIsInstance<SseEvent.Done>().single()
        assertEquals("你好呀", done.reply)
        assertEquals(12, done.promptTokens)
        assertEquals(5, done.completionTokens)
    }

    @Test
    fun `stream tool use fragments arrive with id and name`() {
        enqueueSse(
            "event: message_start\n" +
                "data: {\"type\":\"message_start\",\"message\":{\"model\":\"m\"}}\n\n" +
                "event: content_block_start\n" +
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"host_list_tasks\",\"input\":{}}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"includeDone\\\":\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"true}\"}}\n\n" +
                "event: content_block_stop\n" +
                "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
                "event: message_stop\n" +
                "data: {\"type\":\"message_stop\"}\n\n",
        )
        val collector = Collector()
        client.open(request(), collector).start()
        collector.closedLatch.await(5, TimeUnit.SECONDS)

        assertEquals("toolu_1", collector.toolFragments.first().id)
        assertEquals("host_list_tasks", collector.toolFragments.first().name)
        val args = collector.toolFragments.joinToString("") { it.arguments }
        assertEquals("{\"includeDone\":true}", args)
    }

    @Test
    fun `401 with nested error message maps to invalid key`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}",
            ),
        )
        val collector = Collector()
        client.open(request(), collector).start()
        collector.done.await(5, TimeUnit.SECONDS)

        val error = collector.errors.single()
        assertTrue("401 应映射成 InvalidKey，实际 $error", error is ChatError.InvalidKey)
        assertTrue("应带上上游原话", error.message.orEmpty().contains("invalid x-api-key"))
    }
}
