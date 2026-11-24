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

/** P211：历史消息「加载更多」与 token 用量记录。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHistoryTest {

    private class FakeHistory : ChatHistoryQueryPort {
        var lastLimit = 0

        override suspend fun recent(limit: Int): List<HistoryMessage> {
            lastLimit = limit
            // 造出「数量正好等于 limit」的历史，让界面认为还有更早的
            return (0 until limit).map { i ->
                HistoryMessage(
                    role = if (i % 2 == 0) "user" else "assistant",
                    content = "第${i}条",
                    createdAt = i.toLong(),
                    id = i.toLong(),
                )
            }
        }
    }

    private class FixedUseCase(
        private val completed: Boolean = true,
        private val prompt: Int? = 100,
        private val completion: Int? = 20,
    ) : ConversationUseCase {
        override suspend fun send(
            userText: String,
            dynamic: PromptDynamic,
            onDelta: ((String) -> Unit)?,
            replyTarget: Int,
            onToolCall: ((name: String, sensitive: Boolean) -> Unit)?,
            overrideConfig: ChatConfig?,
            quoteId: Long,
            quotePreview: String,
        ): ConversationOutcome = if (completed) {
            ConversationOutcome(
                completed = true,
                reply = "好",
                error = null,
                maxTokens = 900,
                promptTokens = prompt,
                completionTokens = completion,
            )
        } else {
            ConversationOutcome(false, "", null, 0, note = "配置缺失")
        }
    }

    private class RecordingUsage : UsageRecorder {
        data class Call(val sessionId: String, val prompt: Int?, val completion: Int?)

        val calls = mutableListOf<Call>()

        override suspend fun record(sessionId: String, promptTokens: Int?, completionTokens: Int?) {
            calls += Call(sessionId, promptTokens, completionTokens)
        }
    }

    /** P215：可切换的假会话端口（全局 current 由它模拟）。 */
    private class FakeSession(var currentId: String = "global") : SessionPort {
        override suspend fun current(): String = currentId
        override suspend fun switch(id: String) {
            currentId = id
        }

        override suspend fun list(): List<SessionSummary> = emptyList()
        override suspend fun delete(id: String) = Unit
        override suspend fun createNew(): String = "new"
    }

    private fun vm(
        history: FakeHistory = FakeHistory(),
        useCase: ConversationUseCase = FixedUseCase(),
        usage: UsageRecorder? = null,
    ) = ChatViewModel(
        useCase = useCase,
        historyQuery = history,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        characterName = "小满",
        usageRecorder = usage,
    )

    @Test
    fun `initial history loads fifty`() = runTest {
        val history = FakeHistory()
        val viewModel = vm(history)

        assertEquals(50, viewModel.state.value.historyLimit)
        assertEquals(50, history.lastLimit)
        assertEquals(50, viewModel.state.value.history.size)
    }

    @Test
    fun `load more doubles the limit`() = runTest {
        val history = FakeHistory()
        val viewModel = vm(history)

        viewModel.loadMoreHistory()

        assertEquals(100, viewModel.state.value.historyLimit)
        assertEquals(100, history.lastLimit)
        assertEquals(100, viewModel.state.value.history.size)
    }

    @Test
    fun `load more stops at the cap`() = runTest {
        val viewModel = vm()

        repeat(20) { viewModel.loadMoreHistory() }

        assertEquals(ChatViewModel.MAX_HISTORY_LOAD, viewModel.state.value.historyLimit)
    }

    @Test
    fun `usage is recorded after a successful send`() = runTest {
        val usage = RecordingUsage()
        val viewModel = vm(usage = usage)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        val call = usage.calls.single()
        assertEquals(100, call.prompt)
        assertEquals(20, call.completion)
        assertEquals("default", call.sessionId)
    }

    @Test
    fun `no usage recorded when upstream returns nothing`() = runTest {
        val usage = RecordingUsage()
        val viewModel = vm(useCase = FixedUseCase(prompt = null, completion = null), usage = usage)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertTrue(usage.calls.isEmpty())
    }

    @Test
    fun `failed send records no usage`() = runTest {
        val usage = RecordingUsage()
        val viewModel = vm(useCase = FixedUseCase(completed = false), usage = usage)

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertTrue(usage.calls.isEmpty())
    }

    // ── P215：页面会话不能被全局 current 带跑 ─────────────

    @Test
    fun `send uses the page session even if another page moved current`() = runTest {
        val session = FakeSession("B")
        val usage = RecordingUsage()
        val viewModel = ChatViewModel(
            useCase = FixedUseCase(),
            historyQuery = FakeHistory(),
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            session = session,
            characterName = "小满",
            initialSessionId = "A",
            usageRecorder = usage,
        )
        // 模拟：别的会话页把全局 current 切到了 B，本页( A )之后才发送
        session.currentId = "B"

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertEquals("A", usage.calls.single().sessionId)
        assertEquals("发送前要把 current 掰回本页", "A", session.currentId)
    }
}
