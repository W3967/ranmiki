package com.lanweixi.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * P201：连通性测试的七类结果，本地用 MockWebServer 覆盖。
 *
 * 只验证「分类正确、错误详情能读出来、非法 Key 不发请求」——
 * 真实服务商的官方错误体放到真机联调时再对。
 */
class ConnectivityProbeTest {

    private lateinit var server: MockWebServer
    private lateinit var probe: ConnectivityProbe

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        probe = ConnectivityProbe(OkHttpClient())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun baseUrl(): String = server.url("/").toString()

    @Test
    fun `ok when server returns 200`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"chatcmpl-1"}"""))
        val result = probe.probe(baseUrl(), "sk-test", "deepseek-chat")
        assertTrue("200 应该判成功，实际 $result", result is ProbeResult.Ok)
    }

    @Test
    fun `invalid key on 401 and message is surfaced`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key provided"}}"""),
        )
        val result = probe.probe(baseUrl(), "sk-test", "deepseek-chat")
        assertTrue("401 应该判 Key 无效，实际 $result", result is ProbeResult.InvalidKey)
        assertTrue(
            "要把上游的原话带出来",
            (result as ProbeResult.InvalidKey).detail.contains("Invalid API key"),
        )
    }

    @Test
    fun `busy on 429`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limited"}}"""))
        assertTrue(probe.probe(baseUrl(), "sk-test", "m") is ProbeResult.Busy)
    }

    @Test
    fun `no balance on 402`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient Balance"}}"""))
        val result = probe.probe(baseUrl(), "sk-test", "m")
        assertTrue("402 应该判余额不足，实际 $result", result is ProbeResult.NoBalance)
    }

    @Test
    fun `wrong endpoint on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertTrue(probe.probe(baseUrl(), "sk-test", "m") is ProbeResult.WrongEndpoint)
    }

    @Test
    fun `server error on 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = probe.probe(baseUrl(), "sk-test", "m")
        assertTrue(result is ProbeResult.ServerError)
        assertEquals(500, (result as ProbeResult.ServerError).code)
    }

    @Test
    fun `chinese key is rejected locally without a request`() = runBlocking {
        // 输入法把 sk-test 猜成「上课－test」：全角字符混进 Key
        val result = probe.probe(baseUrl(), "sk－test", "m")
        assertEquals(ProbeResult.InvalidKeyChars, result)
        assertEquals("非法 Key 不得发请求", 0, server.requestCount)
    }

    @Test
    fun `network failure is translated to a human sentence`() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        val result = probe.probe(url, "sk-test", "m")
        assertTrue("连不上应该判网络错误，实际 $result", result is ProbeResult.Network)
        assertTrue((result as ProbeResult.Network).detail.isNotBlank())
    }

    @Test
    fun `timeout is reported as timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = probe.probe(baseUrl(), "sk-test", "m", timeoutMs = 1_000)
        assertTrue("超时应该判网络错误，实际 $result", result is ProbeResult.Network)
        assertTrue(
            "超时文案要让人知道去检查网络或地址，实际 ${(result as ProbeResult.Network).detail}",
            result.detail.contains("超时"),
        )
    }

    @Test
    fun `endpoint url is shared with the streaming client`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            chatCompletionsUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            chatCompletionsUrl("https://api.deepseek.com/"),
        )
        assertEquals(
            "https://api.moonshot.cn/v1/chat/completions",
            chatCompletionsUrl("https://api.moonshot.cn/v1"),
        )
        assertEquals(
            "https://x.example/v1/chat/completions",
            chatCompletionsUrl("https://x.example/v1/chat/completions"),
        )
    }

    // ── P209：Anthropic ───────────────────────────────────

    @Test
    fun `anthropic base url probes the messages endpoint with key header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"msg_1"}"""))
        val anthropicBase = server.url("/anthropic").toString().trimEnd('/')

        val result = probe.probe(anthropicBase, "sk-ant-test", "claude-3-5-sonnet-latest")

        assertTrue("200 应该判成功，实际 $result", result is ProbeResult.Ok)
        val recorded = server.takeRequest()
        assertEquals("/anthropic/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertTrue("Anthropic 不用 Bearer 头", recorded.getHeader("Authorization") == null)
    }

    @Test
    fun `anthropic nested error message is surfaced`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}",
            ),
        )
        val anthropicBase = server.url("/anthropic").toString().trimEnd('/')

        val result = probe.probe(anthropicBase, "sk-ant-test", "claude-3-5-sonnet-latest")

        assertTrue("401 应判 Key 无效，实际 $result", result is ProbeResult.InvalidKey)
        assertTrue(
            "要把上游原话带出来",
            (result as ProbeResult.InvalidKey).detail.contains("invalid x-api-key"),
        )
    }
}
