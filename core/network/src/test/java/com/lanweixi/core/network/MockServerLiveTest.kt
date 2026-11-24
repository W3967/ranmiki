package com.lanweixi.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * T03 端到端验收：连真实的 node mock_server.js，跑七场景并打印首字延迟。
 *
 * 运行方式：
 *   node DSH输入件/samples/mock_server.js 8790
 *   ./gradlew :core:network:testDebugUnitTest -Dmock.baseUrl=http://127.0.0.1:8790
 *
 * 未传 mock.baseUrl 时整类跳过（Assume），不影响 CI 里的纯单测。
 */
class MockServerLiveTest {

    private data class Result(
        val scenario: String,
        val firstDeltaMs: Long,
        val chars: Int,
        val done: Boolean,
        val error: String?,
    )

    @Test
    fun `seven scenarios against node mock server`() {
        val base = System.getProperty("mock.baseUrl").orEmpty()
        assumeTrue("mock.baseUrl not set, live test skipped", base.isNotBlank())

        val client = ChatStreamClient(okhttp3.OkHttpClient())
        val results = mutableListOf<Result>()

        for (scenario in listOf("normal", "hiccup", "oversize", "abort", "mid_error", "upstream", "error")) {
            val rec = Recorder()
            val request = ChatStreamRequest(
                baseUrl = "$base/api/chat/stream?scenario=$scenario",
                apiKey = "sk-live",
                messages = listOf(ChatMessage("user", "在吗")),
            )
            client.open(request, rec.callback).start()
            rec.done.await(10, TimeUnit.SECONDS)
            results += Result(
                scenario = scenario,
                firstDeltaMs = rec.firstDeltaMs,
                chars = rec.textLength(),
                done = rec.hasDone,
                error = rec.errorText,
            )
        }

        println("=== T03 live smoke (mock_server.js) ===")
        println(String.format("%-12s %12s %8s %8s  %s", "scenario", "firstDelta", "chars", "done", "error"))
        for (r in results) {
            println(
                String.format(
                    "%-12s %10dms %8d %8s  %s",
                    r.scenario, r.firstDeltaMs, r.chars, r.done, r.error ?: "-"
                )
            )
        }

        // 七场景的硬性断言
        val byName = results.associateBy { it.scenario }
        assertTrue("normal first delta too slow: ${byName["normal"]!!.firstDeltaMs}ms",
            byName["normal"]!!.firstDeltaMs in 0..1000)
        assertTrue("normal must complete", byName["normal"]!!.done)
        assertTrue("hiccup must keep text", byName["hiccup"]!!.chars > 0)
        assertTrue("oversize must be whole", byName["oversize"]!!.chars >= 3000)
        assertTrue("abort must not report done", !byName["abort"]!!.done)
        assertTrue("mid_error must surface error", byName["mid_error"]!!.error != null)
        assertTrue("upstream must parse text", byName["upstream"]!!.chars > 0)
        assertEquals("error scenario should map to 401 detail", true, byName["error"]!!.error != null)
    }

    private class Recorder {
        @Volatile var firstDeltaMs = -1L
        @Volatile var hasDone = false
        @Volatile var errorText: String? = null
        private var chars = 0
        private val lock = Any()
        private val startedAt = System.nanoTime()
        val done = CountDownLatch(1)

        val callback = object : ChatStreamCallback {
            override fun onEvent(event: SseEvent) {
                synchronized(lock) {
                    if (event is SseEvent.Delta) {
                        if (firstDeltaMs < 0) firstDeltaMs = (System.nanoTime() - startedAt) / 1_000_000
                        chars += event.text.length
                    }
                    if (event is SseEvent.Done) hasDone = true
                }
            }

            override fun onError(error: ChatError) {
                errorText = error.message
                done.countDown()
            }

            override fun onClosed() {
                done.countDown()
            }
        }

        fun textLength(): Int = synchronized(lock) { chars }
    }
}
