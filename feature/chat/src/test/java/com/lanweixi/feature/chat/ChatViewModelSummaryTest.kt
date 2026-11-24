package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptDynamic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P210：单聊把「前情摘要」塞进动态段，并在发送后触发压缩。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSummaryTest {

    private class CapturingUseCase : ConversationUseCase {
        var lastDynamic: PromptDynamic? = null

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
            lastDynamic = dynamic
            return ConversationOutcome(true, "好", null, 900)
        }
    }

    private class FakeHistory : ChatHistoryQueryPort {
        override suspend fun recent(limit: Int) = emptyList<HistoryMessage>()
    }

    private class CountingEngine : SummaryEngine {
        var calls = 0
        var lastSessionId: String? = null

        override suspend fun maybeCompress(sessionId: String): Boolean {
            calls++
            lastSessionId = sessionId
            return true
        }
    }

    private fun vm(
        useCase: ConversationUseCase,
        provider: SummaryProvider? = null,
        engine: SummaryEngine? = null,
    ) = ChatViewModel(
        useCase = useCase,
        historyQuery = FakeHistory(),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        characterName = "小满",
        summaryProvider = provider,
        summaryEngine = engine,
    )

    @Test
    fun `summary goes into the dynamic plot summary`() = runTest {
        val useCase = CapturingUseCase()
        val viewModel = vm(useCase, provider = SummaryProvider { "他明天下午三点面试" })

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertEquals("他明天下午三点面试", useCase.lastDynamic?.plotSummary)
    }

    @Test
    fun `no provider means no plot summary`() = runTest {
        val useCase = CapturingUseCase()
        val viewModel = vm(useCase)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertEquals(null, useCase.lastDynamic?.plotSummary)
    }

    @Test
    fun `compression runs after a successful send`() = runTest {
        val engine = CountingEngine()
        val viewModel = vm(CapturingUseCase(), engine = engine)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertEquals(1, engine.calls)
        assertEquals("default", engine.lastSessionId)
    }

    @Test
    fun `providers are optional and never crash the send`() = runTest {
        val useCase = CapturingUseCase()
        val boom = SummaryProvider { error("摘要读挂了") }
        val viewModel = vm(useCase, provider = boom, engine = null)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        // 读摘要失败不该影响这次发送
        assertEquals(null, useCase.lastDynamic?.plotSummary)
        assertTrue(useCase.lastDynamic != null)
    }
}
