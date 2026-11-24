package com.lanweixi.client.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.repository.RoomMessageRepository
import com.lanweixi.core.data.repository.RoomSessionSummaryRepository
import com.lanweixi.core.network.ChatError
import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.feature.chat.StreamHandle
import com.lanweixi.feature.chat.StreamRunner
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P210：上下文压缩的端到端（内存 Room + 假流）。
 *
 * 验的是：阈值、覆盖范围累加、「摘要 + 最近消息」的取法、失败不影响主流程。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionSummarizerTest {

    private lateinit var db: LanWeixiDatabase
    private lateinit var messages: RoomMessageRepository
    private lateinit var summaries: RoomSessionSummaryRepository

    private var now = 1_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LanWeixiDatabase::class.java,
        ).allowMainThreadQueries().build()
        messages = RoomMessageRepository(db.messageDao())
        summaries = RoomSessionSummaryRepository(db.summaryDao(), clock = { ++now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(count: Int, sessionId: String = "default") {
        repeat(count) { i ->
            db.messageDao().insert(
                MessageEntity(
                    role = if (i % 2 == 0) "user" else "assistant",
                    speaker = if (i % 2 == 0) "" else "__host__",
                    text = "第${i + 1}条消息",
                    status = "sent",
                    createdAt = now + i,
                    sessionId = sessionId,
                ),
            )
        }
    }

    private fun summarizer(
        runner: StreamRunner,
        key: String = "sk-test",
    ) = SessionSummarizer(
        messages = messages,
        summaries = summaries,
        runner = runner,
        configProvider = object : ChatConfigProvider {
            override suspend fun current() = ChatConfig(
                baseUrl = "https://api.deepseek.com",
                apiKey = key,
                persona = "人设",
                model = "deepseek-chat",
            )
        },
        clock = { now },
    )

    private class FakeRunner(
        private val reply: String?,
        private val fail: Boolean = false,
    ) : StreamRunner {
        var lastRequest: ChatStreamRequest? = null
        var callCount = 0

        override fun run(request: ChatStreamRequest, callback: ChatStreamCallback): StreamHandle? {
            lastRequest = request
            callCount++
            if (fail) {
                callback.onError(ChatError.Network(IOException("boom")))
            } else {
                val text = reply ?: ""
                callback.onEvent(SseEvent.Delta(text))
                callback.onEvent(
                    SseEvent.Done(reply = text, latencyMs = null, promptTokens = null, completionTokens = null),
                )
            }
            return object : StreamHandle {
                override fun cancel() = Unit
            }
        }
    }

    @Test
    fun `short conversation does not summarize`() = runBlocking {
        seed(10)
        val runner = FakeRunner("摘要")

        val compressed = summarizer(runner).maybeCompress("default")

        assertFalse(compressed)
        assertEquals(0, runner.callCount)
        assertEquals(0, summaries.count())
    }

    @Test
    fun `long conversation produces a rolling summary`() = runBlocking {
        seed(40)
        val runner = FakeRunner("他明天下午三点去面试，带简历。")
        val summarizer = summarizer(runner)

        assertTrue(summarizer.maybeCompress("default"))

        val row = summaries.latest("default")!!
        assertEquals("他明天下午三点去面试，带简历。", row.content)
        assertEquals(32L, row.untilId)
        assertEquals(32, row.coveredCount)
        // 摘要请求带上了系统提示与对话原文
        val request = runner.lastRequest!!
        assertEquals(2, request.messages.size)
        assertEquals("system", request.messages[0].role)
        assertTrue(request.messages[1].content.contains("【新对话】"))
        assertTrue(request.messages[1].content.contains("第1条消息"))
        assertTrue("最近的 8 条不该进摘要", !request.messages[1].content.contains("第33条消息"))
    }

    @Test
    fun `summaryOf returns the stored text`() = runBlocking {
        seed(40)
        val summarizer = summarizer(FakeRunner("前情提要"))
        summarizer.maybeCompress("default")

        assertEquals("前情提要", summarizer.summaryOf("default"))
        assertNull(summarizer.summaryOf("other-session"))
    }

    @Test
    fun `failed model call leaves no summary`() = runBlocking {
        seed(40)
        val runner = FakeRunner( reply = null, fail = true)

        assertFalse(summarizer(runner).maybeCompress("default"))
        assertEquals(0, summaries.count())
        assertNull(summarizer(runner).summaryOf("default"))
    }

    @Test
    fun `missing api key skips silently`() = runBlocking {
        seed(40)
        val runner = FakeRunner("摘要")

        assertFalse(summarizer(runner, key = "").maybeCompress("default"))
        assertEquals(0, runner.callCount)
    }

    @Test
    fun `second compression folds the previous summary in`() = runBlocking {
        seed(40)
        val first = summarizer(FakeRunner("第一版摘要"))
        first.maybeCompress("default")

        // 再聊 40 条（阈值再次触发），摘要要带上旧摘要
        val more = 40
        repeat(more) { i ->
            db.messageDao().insert(
                MessageEntity(
                    role = "user", speaker = "", text = "新第${i + 1}条",
                    status = "sent", createdAt = now + 100 + i, sessionId = "default",
                ),
            )
        }
        val secondRunner = FakeRunner("第二版摘要")
        val second = summarizer(secondRunner)

        assertTrue(second.maybeCompress("default"))
        val body = secondRunner.lastRequest!!.messages[1].content
        assertTrue("旧摘要要带进新请求", body.contains("【已有摘要】"))
        assertTrue(body.contains("第一版摘要"))
        assertEquals("第二版摘要", summaries.latest("default")!!.content)
        assertTrue(summaries.latest("default")!!.coveredCount > 32)
    }
}
