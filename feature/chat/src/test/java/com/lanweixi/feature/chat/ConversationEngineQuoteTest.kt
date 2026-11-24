package com.lanweixi.feature.chat

import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P204：消息引用的落库与提示词拼接。
 *
 * 约定：落库正文保持用户原文（UI 干净），引用说明只进提示词。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineQuoteTest {

    private class FakeHistory(
        private val seed: List<HistoryMessage> = emptyList(),
    ) : ChatHistoryPort {
        val rows = mutableListOf<Pair<String, String>>()
        var lastAppendQuoteId: Long? = null
        var lastAppendQuotePreview: String? = null

        override suspend fun recent(limit: Int) = seed

        override suspend fun append(
            role: String,
            content: String,
            sessionId: String,
            status: String,
            quoteId: Long,
            quotePreview: String,
        ) {
            rows += role to content
            if (role == "user") {
                lastAppendQuoteId = quoteId
                lastAppendQuotePreview = quotePreview
            }
        }
    }

    private fun engine(history: FakeHistory, captured: MutableList<ChatStreamRequest>): ConversationEngine =
        ConversationEngine(
            stream = StreamRunner { request, cb: ChatStreamCallback ->
                captured += request
                cb.onEvent(SseEvent.Done("好", null, null, null))
                null
            },
            promptBuilder = PromptBuilder(),
            history = history,
            configProvider = FixedConfigProvider(
                ChatConfig(baseUrl = "http://127.0.0.1/mock", apiKey = "sk-test", persona = "你是助手"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun `quote is persisted as snapshot and shown to the model`() = runTest {
        val history = FakeHistory()
        val captured = mutableListOf<ChatStreamRequest>()
        val engine = engine(history, captured)

        engine.send(
            userText = "那是什么意思",
            dynamic = com.lanweixi.domain.prompt.PromptDynamic(),
            onDelta = null,
            replyTarget = 0,
            quoteId = 42,
            quotePreview = "兰未晞：今天有点累",
        )

        assertEquals(42L, history.lastAppendQuoteId)
        assertEquals("兰未晞：今天有点累", history.lastAppendQuotePreview)
        // 落库正文保持干净（引用只进提示词）
        assertTrue(history.rows.any { it.first == "user" && it.second == "那是什么意思" })

        val userMessage = captured.single().messages.last { it.role == "user" }
        assertTrue(
            "模型要看到引用，实际：${userMessage.content}",
            userMessage.content.contains("（引用：兰未晞：今天有点累）"),
        )
    }

    @Test
    fun `history quotes are replayed into the model prompt`() = runTest {
        val history = FakeHistory(
            seed = listOf(
                HistoryMessage(
                    role = "user",
                    content = "早上好",
                    createdAt = 1L,
                    id = 1,
                    quotePreview = "兰未晞：昨晚聊到很晚",
                ),
                HistoryMessage("assistant", "早", 2L, id = 2),
            ),
        )
        val captured = mutableListOf<ChatStreamRequest>()
        val engine = engine(history, captured)

        engine.send("继续", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        val contents = captured.single().messages.map { it.content }
        assertTrue(
            "历史里的引用也要还原给模型，实际：$contents",
            contents.any { it.contains("早上好") && it.contains("（引用：兰未晞：昨晚聊到很晚）") },
        )
    }

    @Test
    fun `no quote means no quote marker`() = runTest {
        val history = FakeHistory()
        val captured = mutableListOf<ChatStreamRequest>()
        val engine = engine(history, captured)

        engine.send("在吗", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertEquals(0L, history.lastAppendQuoteId)
        assertEquals("", history.lastAppendQuotePreview)
        val userMessage = captured.single().messages.last { it.role == "user" }
        assertFalse("没引用就不要出现引用说明", userMessage.content.contains("引用"))
    }
}
