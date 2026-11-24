package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.AgentProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P203：群聊多选 @。
 *
 * 只测 ViewModel 的编排：选谁、按什么顺序调、没配 Key 的怎么处理——
 * 真实引擎的落库与上下文在 GroupChatEngineTest。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatViewModelTest {

    private class FakePort : GroupChatPort {
        val rows = mutableListOf<Triple<String, String, String>>()

        override suspend fun history(groupId: String, limit: Int): List<GroupMessage> =
            rows.filter { it.first == groupId }
                .takeLast(limit)
                .map { GroupMessage(it.second, it.third, 0L) }

        override suspend fun append(
            groupId: String,
            speaker: String,
            text: String,
            quoteId: Long,
            quotePreview: String,
        ) {
            rows += Triple(groupId, speaker, text)
        }
    }

    private class FakeUseCase(
        private val results: List<GroupSendResult> = emptyList(),
    ) : GroupChatUseCase {
        val calls = mutableListOf<List<String>>()
        var lastQuoteId: Long = -1
        var lastQuotePreview: String = ""

        override suspend fun send(
            groupId: String,
            agents: List<AgentProfile>,
            userText: String,
            targetAgentId: String?,
            onDelta: (String) -> Unit,
        ): GroupSendResult = sendToMany(
            groupId = groupId,
            agents = agents,
            userText = userText,
            targetAgentIds = listOf(targetAgentId ?: GroupSpeaker.HOST),
            onDelta = { _, partial -> onDelta(partial) },
        ).first()

        override suspend fun sendToMany(
            groupId: String,
            agents: List<AgentProfile>,
            userText: String,
            targetAgentIds: List<String>,
            onDelta: (String, String) -> Unit,
            quoteId: Long,
            quotePreview: String,
        ): List<GroupSendResult> {
            calls += targetAgentIds
            lastQuoteId = quoteId
            lastQuotePreview = quotePreview
            return targetAgentIds.map { id ->
                results.firstOrNull { it.speakerId == id }
                    ?: GroupSendResult(true, id, id, "好")
            }
        }
    }

    private val agentReady = AgentProfile(id = "a1", name = "修图师", apiKey = "sk-1")
    private val agentReady2 = AgentProfile(id = "a3", name = "文案", apiKey = "sk-3")
    private val agentNoKey = AgentProfile(id = "a2", name = "旧工具人", apiKey = "")

    private fun vm(
        useCase: FakeUseCase,
        scope: CoroutineScope,
        members: List<AgentProfile> = listOf(agentReady, agentReady2, agentNoKey),
    ) = GroupChatViewModel(
        groupId = "g1",
        groupTitle = "测试群",
        hostName = "兰未晞",
        hostAvatarUri = null,
        membersProvider = { members },
        port = FakePort(),
        useCase = useCase,
        scope = scope,
    )

    @Test
    fun `no mention sends to the host only`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.onDraftChange("在吗")
        viewModel.send()

        assertEquals(listOf(listOf(GroupSpeaker.HOST)), useCase.calls)
    }

    @Test
    fun `two mentions are sent in member order regardless of click order`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        // 先点第二个再点第一个：发送顺序仍按成员列表（修图师在前）
        viewModel.toggleTarget(agentReady2.id)
        viewModel.toggleTarget(agentReady.id)
        viewModel.onDraftChange("帮我看看")
        viewModel.send()

        assertEquals(listOf(listOf(agentReady.id, agentReady2.id)), useCase.calls)
    }

    @Test
    fun `select all skips members without a key and says so`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.selectAllTargets()

        assertEquals(setOf(GroupSpeaker.HOST, agentReady.id, agentReady2.id), viewModel.state.value.targetIds)
        val banner = viewModel.state.value.banner
        assertTrue("要说清楚谁没选上", banner is ChatBanner.Notice && banner.text.contains("旧工具人"))
        assertTrue(banner is ChatBanner.Notice && banner.text.contains("没配 API Key"))
    }

    @Test
    fun `selecting an unconfigured member is refused`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.toggleTarget(agentNoKey.id)

        assertTrue(agentNoKey.id !in viewModel.state.value.targetIds)
        val banner = viewModel.state.value.banner
        assertTrue(banner is ChatBanner.Notice && banner.text.contains("没配 API Key"))
    }

    @Test
    fun `toggling twice removes the mention`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.toggleTarget(agentReady.id)
        assertEquals(setOf(agentReady.id), viewModel.state.value.targetIds)
        viewModel.toggleTarget(agentReady.id)
        assertTrue(viewModel.state.value.targetIds.isEmpty())
    }

    @Test
    fun `clear targets falls back to the host`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.toggleTarget(agentReady.id)
        viewModel.clearTargets()

        assertTrue(viewModel.state.value.targetIds.isEmpty())
        assertEquals("兰未晞", viewModel.state.value.targetLabel)
    }

    @Test
    fun `partial failure is reported without losing the rest`() = runTest {
        val useCase = FakeUseCase(
            results = listOf(
                GroupSendResult(true, GroupSpeaker.HOST, "兰未晞", "我在。"),
                GroupSendResult(false, agentReady.id, "修图师", "", "修图师的 API Key 无效或没填"),
            ),
        )
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.toggleTarget(GroupSpeaker.HOST)
        viewModel.toggleTarget(agentReady.id)
        viewModel.onDraftChange("都在吗")
        viewModel.send()

        assertEquals(listOf(listOf(GroupSpeaker.HOST, agentReady.id)), useCase.calls)
        val banner = viewModel.state.value.banner
        assertTrue("失败原因要显示出来", banner is ChatBanner.Error)
        assertTrue((banner as ChatBanner.Error).text.contains("API Key 无效"))
    }

    @Test
    fun `empty targets after members change sends to host`() = runTest {
        val useCase = FakeUseCase()
        // agentReady 不在 membersProvider 里：选中的 id 会在 refreshMembers 时被清掉
        val viewModel = vm(
            useCase,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            members = listOf(agentNoKey),
        )

        viewModel.toggleTarget(agentReady.id) // 拒绝（不在成员里）
        viewModel.onDraftChange("hi")
        viewModel.send()

        assertEquals(listOf(listOf(GroupSpeaker.HOST)), useCase.calls)
    }

    // ── P205：引用 ──

    @Test
    fun `quoting a tool member mentions them`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        viewModel.toggleTarget(agentReady2.id) // 先 @ 了别人

        viewModel.quoteMessage(GroupMessage(agentReady.id, "给我三组参数", 1L, id = 11))

        assertEquals(setOf(agentReady.id), viewModel.state.value.targetIds)
        assertEquals("修图师", viewModel.state.value.quote?.speaker)
        assertEquals(11L, viewModel.state.value.quote?.messageId)
    }

    @Test
    fun `quoting the host clears mentions`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        viewModel.toggleTarget(agentReady.id)

        viewModel.quoteMessage(GroupMessage(GroupSpeaker.HOST, "都来报个到", 2L, id = 12))

        assertTrue(viewModel.state.value.targetIds.isEmpty())
        assertEquals("兰未晞", viewModel.state.value.quote?.speaker)
    }

    @Test
    fun `quoting myself clears mentions and shows me`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        viewModel.toggleTarget(agentReady.id)

        viewModel.quoteMessage(GroupMessage(GroupSpeaker.USER, "我说的", 3L, id = 13))

        assertTrue(viewModel.state.value.targetIds.isEmpty())
        assertEquals("我", viewModel.state.value.quote?.speaker)
    }

    @Test
    fun `send passes the quote and clears it`() = runTest {
        val useCase = FakeUseCase()
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        viewModel.quoteMessage(GroupMessage(GroupSpeaker.HOST, "都来报个到", 2L, id = 12))
        viewModel.onDraftChange("什么意思")
        viewModel.send()

        assertEquals(12L, useCase.lastQuoteId)
        assertEquals("兰未晞：都来报个到", useCase.lastQuotePreview)
        assertTrue("发送后引用条要收起", viewModel.state.value.quote == null)
    }

    @Test
    fun `failed send restores the quote`() = runTest {
        val useCase = FakeUseCase(
            results = listOf(GroupSendResult(false, GroupSpeaker.HOST, "兰未晞", "", "失败")),
        )
        val viewModel = vm(useCase, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        viewModel.quoteMessage(GroupMessage(GroupSpeaker.HOST, "都来报个到", 2L, id = 12))
        viewModel.onDraftChange("什么意思")
        viewModel.send()

        assertEquals(12L, viewModel.state.value.quote?.messageId)
    }
}
