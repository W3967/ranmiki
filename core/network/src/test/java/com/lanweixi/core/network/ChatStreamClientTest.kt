package com.lanweixi.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * T03 客户端验收：七场景（normal / hiccup / oversize / abort / mid_error / upstream / error）
 * + 取消语义。样例来自 DSH输入件/samples，按同一套 SSE 契约回放。
 */
class ChatStreamClientTest {

    private lateinit var server: MockWebServer
    private val client = ChatStreamClient(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request(path: String = "/api/chat/stream"): ChatStreamRequest = ChatStreamRequest(
        baseUrl = server.url(path).toString(),
        apiKey = "sk-test",
        messages = listOf(ChatMessage("user", "在吗")),
    )

    private fun enqueueSample(name: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "text/event-stream; charset=utf-8")
            .setBody(sample(name))

    /**
     * 样例文件里事件块之间没有空行（用 `# delay:` 行作分隔），
     * 这里按 mock_server.js 的语义重建成标准 SSE：每个 data 块以空行结尾。
     */
    private fun sample(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("sse/$name")
        assertNotNull("missing sample: $name", stream)
        val raw = stream!!.bufferedReader().readText()
        val events = mutableListOf<String>()
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                events += buf.toString()
                buf.setLength(0)
            }
        }

        for (line in raw.lineSequence()) {
            if (line.startsWith("#")) {
                flush()
                continue
            }
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line)
        }
        flush()
        return events.joinToString("\n\n") + "\n\n"
    }

    // ── 场景 1：normal ────────────────────────────────────────
    @Test
    fun `normal stream emits first delta under one second and done matches concatenation`() {
        server.enqueue(enqueueSample("normal.sse"))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue("stream did not finish in time", rec.done.await(5, TimeUnit.SECONDS))
        assertTrue("no delta received", rec.firstDeltaMs >= 0)
        assertTrue("first delta ${rec.firstDeltaMs}ms > 1000ms", rec.firstDeltaMs <= 1000)

        val done = rec.eventsSnapshot().filterIsInstance<SseEvent.Done>().singleOrNull()
        assertNotNull("missing done event", done)
        assertEquals(done!!.reply, rec.textSnapshot())
        assertTrue(rec.errorsSnapshot().isEmpty())
        assertTrue(rec.closed)
    }

    // ── 场景 2：hiccup（中途空档 + 换行） ─────────────────────
    @Test
    fun `hiccup keeps text and newlines intact`() {
        server.enqueue(enqueueSample("hiccup.sse"))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        val text = rec.textSnapshot()
        assertTrue("text should not be empty", text.isNotEmpty())
        assertTrue("newline from delta should be preserved", text.contains("\n"))
        assertTrue("no done event", rec.eventsSnapshot().any { it is SseEvent.Done })
        assertTrue(rec.errorsSnapshot().isEmpty())
    }

    // ── 场景 3：oversize（单 delta 3000 字） ──────────────────
    @Test
    fun `oversize single delta is delivered whole`() {
        server.enqueue(enqueueSample("oversize.sse"))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        assertTrue("oversize text too short: ${rec.textSnapshot().length}", rec.textSnapshot().length >= 3000)
        assertTrue(rec.errorsSnapshot().isEmpty())
    }

    // ── 场景 4：abort（收到部分后断连，无 done） ──────────────
    @Test
    fun `abort does not produce a done event`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sample("abort.sse"))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        assertFalse("abort must never look like success", rec.eventsSnapshot().any { it is SseEvent.Done })
    }

    // ── 场景 5：mid_error（输出两段后报错） ───────────────────
    @Test
    fun `mid_error keeps received text and surfaces retryable error`() {
        server.enqueue(enqueueSample("mid_error.sse"))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        assertTrue("should keep partial text", rec.textSnapshot().isNotEmpty())
        assertTrue("missing upstream error", rec.errorsSnapshot().any { it is ChatError.Upstream })
        assertFalse("mid_error must not emit done", rec.eventsSnapshot().any { it is SseEvent.Done })
    }

    // ── 场景 6：upstream（OpenAI 兼容格式） ───────────────────
    @Test
    fun `upstream openai compatible stream parses and synthesizes done`() {
        server.enqueue(enqueueSample("upstream_deepseek.sse"))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        assertTrue("no delta parsed from openai format", rec.textSnapshot().isNotEmpty())
        assertTrue(rec.errorsSnapshot().isEmpty())

        // 契约（收尾第 2 条）：有累计文本、未取消、未出错时，[DONE] 必须合成为完成，
        // 调用方据此才允许落库
        val done = rec.eventsSnapshot().filterIsInstance<SseEvent.Done>().singleOrNull()
        assertNotNull("upstream 流必须产生 Done", done)
        assertEquals(rec.textSnapshot(), done!!.reply)
    }

    // ── 场景 7：error（HTTP 401 + JSON body） ─────────────────
    @Test
    fun `http 401 maps to invalid key with json detail`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(sample("error_401.json"))
        )
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        val error = rec.errorsSnapshot().singleOrNull()
        assertNotNull("missing error", error)
        assertTrue("expected InvalidKey, got $error", error is ChatError.InvalidKey)
        assertTrue("detail should come from json body", error!!.message!!.contains("Invalid API key"))
        assertTrue(rec.textSnapshot().isEmpty())
    }

    // ── 取消语义 ──────────────────────────────────────────────
    @Test
    fun `cancel stops further callbacks and never closes`() {
        // 用裸 ServerSocket 手写响应：先发一段 delta，再长时间空档，
        // 保证"收到内容之后才取消"这个时序稳定可断言。
        val serverSocket = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val writer = Thread {
            runCatching {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = socket.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Connection: keep-alive\r\n" +
                            "\r\n").toByteArray(Charsets.UTF_8)
                    )
                    out.write("data: {\"type\":\"delta\",\"text\":\"一\"}\n\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                    Thread.sleep(3000)
                    out.write("data: {\"type\":\"delta\",\"text\":\"二\"}\n\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        }
        writer.isDaemon = true
        writer.start()

        try {
            val req = ChatStreamRequest(
                baseUrl = "http://127.0.0.1:$port/stream",
                apiKey = "sk-test",
                messages = listOf(ChatMessage("user", "在吗")),
            )
            val rec = Recorder()
            val handle = client.open(req, rec.callback)
            handle.start()

            val deadline = System.currentTimeMillis() + 3000
            while (rec.firstDeltaMs < 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertTrue("no delta before cancel", rec.firstDeltaMs >= 0)

            handle.cancel()
            assertTrue(handle.isCancelled)

            val eventsAtCancel = rec.eventsSnapshot().size
            Thread.sleep(500)

            assertEquals("no events after cancel", eventsAtCancel, rec.eventsSnapshot().size)
            assertFalse("cancel must not report closed", rec.closed)
            assertTrue("cancel must not report error", rec.errorsSnapshot().isEmpty())
        } finally {
            runCatching { serverSocket.close() }
        }
    }

    // ── 完全断连 ──────────────────────────────────────────────
    @Test
    fun `connection drop maps to network error`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        assertTrue(rec.errorsSnapshot().any { it is ChatError.Network })
        assertTrue(rec.textSnapshot().isEmpty())
    }

    private class Recorder {
        private val lock = Any()
        private val events = mutableListOf<SseEvent>()
        private val errors = mutableListOf<ChatError>()
        private val text = StringBuilder()

        @Volatile
        var closed = false

        @Volatile
        var firstDeltaMs = -1L

        private val startedAt = System.nanoTime()
        val done = CountDownLatch(1)

        private fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000

        val callback = object : ChatStreamCallback {
            override fun onEvent(event: SseEvent) {
                synchronized(lock) {
                    events += event
                    if (event is SseEvent.Delta) {
                        if (firstDeltaMs < 0) firstDeltaMs = elapsedMs()
                        text.append(event.text)
                    }
                }
            }

            override fun onError(error: ChatError) {
                synchronized(lock) { errors += error }
                done.countDown()
            }

            override fun onClosed() {
                closed = true
                done.countDown()
            }
        }

        fun eventsSnapshot(): List<SseEvent> = synchronized(lock) { events.toList() }

        fun errorsSnapshot(): List<ChatError> = synchronized(lock) { errors.toList() }

        fun textSnapshot(): String = synchronized(lock) { text.toString() }
    }

    @Test
    fun `endpoint appends chat completions when missing`() {
        val client = ChatStreamClient(okhttp3.OkHttpClient())
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            client.endpointUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            client.endpointUrl("https://api.deepseek.com/"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            client.endpointUrl("https://api.deepseek.com/v1/chat/completions"),
        )
    }

    // ── P211：流式用量 ────────────────────────────────────────

    @Test
    fun `usage frame is folded into the synthesized done`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n" +
                        "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":7}}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        val rec = Recorder()
        client.open(request(), rec.callback).start()

        assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        val done = rec.eventsSnapshot().filterIsInstance<SseEvent.Done>().single()
        assertEquals("hi", done.reply)
        assertEquals(30, done.promptTokens)
        assertEquals(7, done.completionTokens)
    }

    @Test
    fun `request asks for usage in the stream`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody("data: [DONE]\n\n"),
        )
        val rec = Recorder()
        client.open(request(), rec.callback).start()
        rec.done.await(5, TimeUnit.SECONDS)

        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(
            "OpenAI 兼容流默认不回 usage，必须带 stream_options",
            body.getJSONObject("stream_options").getBoolean("include_usage"),
        )
    }

    // ── P217：日志脱敏 ────────────────────────────────────────

    private fun captureLogs(block: () -> Unit): String {
        val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
        ChatStreamLog.sink = { line -> lines += line }
        try {
            block()
        } finally {
            ChatStreamLog.sink = { android.util.Log.d("AI-Chat", it) }
        }
        return lines.joinToString("\n")
    }

    @Test
    fun `logs never contain the user message or the reply text`() {
        val rec = Recorder()
        val logs = captureLogs {
            server.enqueue(enqueueSample("normal.sse"))
            client.open(request(), rec.callback).start()
            assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        }

        val reply = rec.textSnapshot()
        assertTrue("应收到回复", reply.isNotEmpty())
        assertFalse("日志里不能出现用户消息：$logs", logs.contains("在吗"))
        assertFalse("日志里不能出现模型回复：$logs", logs.contains(reply))
        assertTrue("日志至少要记录流已开始：$logs", logs.contains("first data line received"))
    }

    @Test
    fun `error logs do not include the upstream body`() {
        val rec = Recorder()
        val logs = captureLogs {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"error":{"message":"用户刚才说的秘密"}}"""),
            )
            client.open(request(), rec.callback).start()
            assertTrue(rec.done.await(5, TimeUnit.SECONDS))
        }

        assertTrue("应回调服务端错误", rec.errorsSnapshot().any { it is ChatError.Server })
        assertFalse("日志不能带上游错误正文：$logs", logs.contains("秘密"))
        assertTrue("错误日志至少要记长度：$logs", logs.contains("HTTP error body"))
    }
}
