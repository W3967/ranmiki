package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptDynamic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P204：聊天页的引用状态（引用哪条、发送时带上、失败恢复）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelQuoteTest {

    private class FakeUseCase(private val completed: Boolean = true) : ConversationUseCase {
        var lastQuoteId: Long = -1
        var lastQuotePreview: String = ""

        override suspend fun send(
            userText: String,
            dynamic: PromptDynamic,
            onDelta: ((String) -> Unit)?,
            replyTarget: Int,
            onToolCall: ((name: String, sensitive: Boolean) -> Unit)?,
            overrideConfig: ChatConfig?,
            quoteId: Long,
            quotePreview: String,
        ): ConversationOutcome {
            lastQuoteId = quoteId
            lastQuotePreview = quotePreview
            onDelta?.invoke("好")
            return if (completed) {
                ConversationOutcome(true, "好", null, 900)
            } else {
                ConversationOutcome(false, "", null, 0, note = "配置缺失：未设置 API Key")
            }
        }
    }

    private class FakeHistoryQuery(private val seed: List<HistoryMessage>) : ChatHistoryQueryPort {
        override suspend fun recent(limit: Int) = seed
    }

    private val assistantMessage = ChatMessageUi(
        role = "assistant",
        text = "今天有点累\n想早点睡",
        createdAt = 1L,
        id = 7,
    )

    private fun vm(useCase: FakeUseCase, seed: List<HistoryMessage> = emptyList()) = ChatViewModel(
        useCase = useCase,
        historyQuery = FakeHistoryQuery(seed),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        characterName = "小满",
    )

    @Test
    fun `quoting an assistant message records speaker and one line preview`() = runTest {
        val viewModel = vm(FakeUseCase())

        viewModel.quoteMessage(assistantMessage)

        val quote = viewModel.state.value.quote
        assertEquals(7L, quote?.messageId)
        assertEquals("小满", quote?.speaker)
        assertEquals("今天有点累 想早点睡", quote?.preview)
    }

    @Test
    fun `quoting a user message shows me`() = runTest {
        val viewModel = vm(FakeUseCase())

        viewModel.quoteMessage(ChatMessageUi("user", "我先睡了", 2L, id = 8))

        assertEquals("我", viewModel.state.value.quote?.speaker)
    }

    @Test
    fun `send passes the quote and clears it on success`() = runTest {
        val useCase = FakeUseCase(completed = true)
        val viewModel = vm(useCase)

        viewModel.quoteMessage(assistantMessage)
        viewModel.onDraftChange("那早点睡")
        viewModel.send()

        assertEquals(7L, useCase.lastQuoteId)
        assertEquals("小满：今天有点累 想早点睡", useCase.lastQuotePreview)
        assertNull("发送后引用条要收起", viewModel.state.value.quote)
    }

    @Test
    fun `failed send restores the quote for retry`() = runTest {
        val useCase = FakeUseCase(completed = false)
        val viewModel = vm(useCase)

        viewModel.quoteMessage(assistantMessage)
        viewModel.onDraftChange("那早点睡")
        viewModel.send()

        assertEquals("没发出去要把引用放回来", 7L, viewModel.state.value.quote?.messageId)
    }

    @Test
    fun `clear quote drops it`() = runTest {
        val viewModel = vm(FakeUseCase())
        viewModel.quoteMessage(assistantMessage)
        viewModel.clearQuote()
        assertNull(viewModel.state.value.quote)
    }

    @Test
    fun `locate and clear highlight`() = runTest {
        val viewModel = vm(FakeUseCase())
        viewModel.locateQuote(7)
        assertEquals(7L, viewModel.state.value.highlightId)
        viewModel.clearHighlight()
        assertEquals(0L, viewModel.state.value.highlightId)
    }
}
