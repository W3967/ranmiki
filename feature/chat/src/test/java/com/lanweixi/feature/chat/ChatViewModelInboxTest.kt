package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptDynamic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** P207：待审核入口与「从草稿来源跳回原消息」。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelInboxTest {

    private class FakeUseCase : ConversationUseCase {
        override suspend fun send(
            userText: String,
            dynamic: PromptDynamic,
            onDelta: ((String) -> Unit)?,
            replyTarget: Int,
            onToolCall: ((name: String, sensitive: Boolean) -> Unit)?,
            overrideConfig: ChatConfig?,
            quoteId: Long,
            quotePreview: String,
        ): ConversationOutcome = ConversationOutcome(true, "好", null, 900)
    }

    private class FakeHistoryQuery(private val seed: List<HistoryMessage>) : ChatHistoryQueryPort {
        override suspend fun recent(limit: Int) = seed
    }

    private val seed = listOf(
        HistoryMessage(
            role = "user",
            content = "remember this: interview tomorrow 3pm, bring resume and portfolio",
            createdAt = 1,
            id = 11,
        ),
        HistoryMessage(role = "assistant", content = "记好了", createdAt = 2, id = 12),
        HistoryMessage(role = "user", content = "你好", createdAt = 3, id = 13),
    )

    private fun vm(
        highlightQuery: String? = null,
        pending: (suspend () -> Int)? = null,
    ) = ChatViewModel(
        useCase = FakeUseCase(),
        historyQuery = FakeHistoryQuery(seed),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        characterName = "小满",
        pendingDrafts = pending,
        highlightQuery = highlightQuery,
    )

    @Test
    fun `loads pending draft count`() = runTest {
        val viewModel = vm(pending = { 3 })

        assertEquals(3, viewModel.state.value.pendingDrafts)
    }

    @Test
    fun `no provider leaves count zero`() = runTest {
        val viewModel = vm()

        assertEquals(0, viewModel.state.value.pendingDrafts)
    }

    @Test
    fun `failing provider falls back to zero`() = runTest {
        val viewModel = vm(pending = { error("boom") })

        assertEquals(0, viewModel.state.value.pendingDrafts)
    }

    @Test
    fun `highlight query finds the source user message`() = runTest {
        val viewModel = vm(highlightQuery = "remember this: interview tomorrow 3pm")

        assertEquals(11L, viewModel.state.value.highlightId)
    }

    @Test
    fun `highlight matches on prefix when source is truncated`() = runTest {
        // 草稿里存的是节选，历史里是完整原文
        val viewModel = vm(highlightQuery = "remember this: interview tomorrow 3pm, bring resume")

        assertEquals(11L, viewModel.state.value.highlightId)
    }

    @Test
    fun `unmatched highlight query leaves nothing`() = runTest {
        val viewModel = vm(highlightQuery = "完全没说过的话")

        assertEquals(0L, viewModel.state.value.highlightId)
    }
}
