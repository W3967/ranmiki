package com.lanweixi.feature.chat

import com.lanweixi.core.network.ChatError
import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P197：群聊发送规则（@ 谁调谁的 API、落库带 speaker、上下文带名字）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatEngineTest {

    private class FakePort(
        private val seed: List<GroupMessage> = emptyList(),
    ) : GroupChatPort {
        val rows = mutableListOf<Triple<String, String, String>>()
        var lastUserQuoteId: Long? = null
        var lastUserQuotePreview: String? = null

        override suspend fun history(groupId: String, limit: Int): List<GroupMessage> {
            val stored = rows.filter { it.first == groupId }
                .map { GroupMessage(it.second, it.third, 0L) }
            return (seed + stored).takeLast(limit)
        }

        override suspend fun append(
            groupId: String,
            speaker: String,
            text: String,
            quoteId: Long,
            quotePreview: String,
        ) {
            rows += Triple(groupId, speaker, text)
            if (speaker == GroupSpeaker.USER) {
                lastUserQuoteId = quoteId
                lastUserQuotePreview = quotePreview
            }
        }

        fun assistantRows() = rows.filter { it.second != GroupSpeaker.USER }
    }

    private val hostConfig = ChatConfig(
        baseUrl = "https://host.example/v1",
        apiKey = "sk-host",
        persona = "你是兰未晞，脾气有点傲。",
        characterName = "兰未晞",
        model = "host-model",
    )

    private val agent = AgentProfile(
        id = "agent_1",
        name = "修图师",
        provider = "deepseek",
        apiKey = "sk-agent",
        baseUrl = "https://agent.example/v1",
        model = "agent-model",
        workPrompt = "你负责修图和出图建议，给参数。",
    )

    private val agent2 = AgentProfile(
        id = "agent_2",
        name = "文案",
        provider = "deepseek",
        apiKey = "sk-writer",
        baseUrl = "https://writer.example/v1",
        model = "writer-model",
        workPrompt = "你负责写文案。",
    )

    private fun engine(
        runner: StreamRunner,
        port: FakePort = FakePort(),
        apiKey: String = "sk-host",
    ): Pair<GroupChatEngine, FakePort> = Pair(
        GroupChatEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            port = port,
            configProvider = FixedConfigProvider(hostConfig.copy(apiKey = apiKey)),
            dispatcher = UnconfinedTestDispatcher(),
        ),
        port,
    )

    private fun okRunner(captured: MutableList<ChatStreamRequest>, reply: String = "好") =
        StreamRunner { request, cb: ChatStreamCallback ->
            captured += request
            cb.onEvent(SseEvent.Delta(reply))
            cb.onEvent(SseEvent.Done(reply, null, null, null))
            null
        }

    @Test
    fun `no mention goes to host with global config`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, port) = engine(okRunner(captured, "嗯，我在。"))

        val result = engine.send(
            groupId = "group_1",
            agents = listOf(agent),
            userText = "在吗",
            targetAgentId = null,
        )

        assertTrue(result.completed)
        assertEquals(GroupSpeaker.HOST, result.speakerId)
        assertEquals("兰未晞", result.speakerName)
        assertEquals("https://host.example/v1", captured.single().baseUrl)
        assertEquals("host-model", captured.single().model)
        val system = captured.single().messages.first().content
        assertTrue("主持人带人设", system.contains("脾气有点傲"))
        assertTrue("主持人要知道这是群聊", system.contains("【群聊】"))

        // 先落用户、再落兰未晞的回复
        assertEquals(
            listOf(GroupSpeaker.USER to "在吗", GroupSpeaker.HOST to "嗯，我在。"),
            port.rows.map { it.second to it.third },
        )
    }

    @Test
    fun `mention routes to that agent and uses its own api config`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, port) = engine(okRunner(captured, "给我三组参数。"))

        val result = engine.send(
            groupId = "group_1",
            agents = listOf(agent),
            userText = "帮我出个图",
            targetAgentId = agent.id,
        )

        assertTrue(result.completed)
        assertEquals(agent.id, result.speakerId)
        assertEquals("修图师", result.speakerName)
        assertEquals("https://agent.example/v1", captured.single().baseUrl)
        assertEquals("sk-agent", captured.single().apiKey)
        assertEquals("agent-model", captured.single().model)

        val system = captured.single().messages.first().content
        assertTrue("工具人只拿工作说明", system.contains("你负责修图和出图建议"))
        assertFalse("工具人不发人设段", system.contains("脾气有点傲"))

        // 被 @ 时正文前加 @名字，比只靠 system 说明更稳
        assertTrue(captured.single().messages.last().content.contains("@修图师"))

        assertEquals(agent.id, port.assistantRows().single().second)
    }

    @Test
    fun `agent without api key fails loudly and does not call network`() = runTest {
        var rounds = 0
        val runner = StreamRunner { _, _: ChatStreamCallback ->
            rounds++
            null
        }
        val emptyAgent = agent.copy(apiKey = "")
        val (engine, port) = engine(runner)

        val result = engine.send("group_1", listOf(emptyAgent), "帮我修图", emptyAgent.id)

        assertFalse(result.completed)
        assertTrue("要说清楚是没配 Key", result.error!!.contains("API Key"))
        assertEquals("网络不该被调用", 0, rounds)
        assertTrue("用户的话仍然留档", port.rows.any { it.second == GroupSpeaker.USER })

        assertEquals(emptyList<Triple<String, String, String>>(), port.assistantRows())
    }

    @Test
    fun `others speeches enter context with name prefix`() = runTest {
        val port = FakePort()
        port.append("group_1", GroupSpeaker.USER, "先定个方向")
        port.append("group_1", agent.id, "我建议夜景")
        port.append("group_1", GroupSpeaker.HOST, "行，就夜景。")

        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, _) = engine(okRunner(captured, "那就夜景"), port)

        engine.send("group_1", listOf(agent), "开始吧", null)

        val messages = captured.single().messages
        assertTrue(
            "别人说的话要带名字",
            messages.any { it.role == "user" && it.content == "[修图师] 我建议夜景" },
        )
        assertTrue(
            "自己的历史是 assistant",
            messages.any { it.role == "assistant" && it.content == "行，就夜景。" },
        )
    }

    @Test
    fun `stream failure reports reason and keeps only user row`() = runTest {
        val runner = StreamRunner { _, cb: ChatStreamCallback ->
            cb.onError(ChatError.InvalidKey(401, "bad key"))
            null
        }
        val (engine, port) = engine(runner)

        val result = engine.send("group_1", listOf(agent), "在吗", null)

        assertFalse(result.completed)
        assertTrue(result.error!!.contains("API Key"))
        assertEquals(listOf(GroupSpeaker.USER), port.rows.map { it.second })
    }

    @Test
    fun `host api key missing tells user where to fix it`() = runTest {
        val (engine, port) = engine(okRunner(mutableListOf()), apiKey = "")

        val result = engine.send("group_1", listOf(agent), "在吗", null)

        assertFalse(result.completed)
        assertTrue(result.error!!.contains("兰未晞还没配 API Key"))
        assertTrue(port.assistantRows().isEmpty())
    }

    @Test
    fun `deleted member fails instead of silently ignoring mention`() = runTest {
        var rounds = 0
        val runner = StreamRunner { _, _: ChatStreamCallback ->
            rounds++
            null
        }
        val (engine, _) = engine(runner)

        val result = engine.send("group_1", emptyList(), "帮我修图", "agent_gone")

        assertFalse(result.completed)
        assertTrue(result.error!!.contains("不在群里"))
        assertEquals(0, rounds)
    }

    @Test
    fun `illegal key characters are reported actionably`() = runTest {
        // 手机输入法把 "sk-test" 猜成「上课－test」，OkHttp 在 Authorization 上直接抛
        val runner = StreamRunner { _, _: ChatStreamCallback ->
            throw IllegalArgumentException("Unexpected char 0x4e0a at 7 in Authorization value")
        }
        val (engine, _) = engine(runner)

        val result = engine.send("group_1", listOf(agent), "在吗", null)

        assertFalse(result.completed)
        assertTrue("要告诉用户去改 Key，不要只说异常", result.error!!.contains("非法字符"))
    }

    // ── P203：一次 @ 多人 ──

    @Test
    fun `mentioning two agents sends the user message once and both replies`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, port) = engine(okRunner(captured, "好"))

        val results = engine.sendToMany(
            groupId = "group_1",
            agents = listOf(agent, agent2),
            userText = "都来看",
            targetAgentIds = listOf(agent.id, agent2.id),
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.completed })
        assertEquals("用户消息只落一次", 1, port.rows.count { it.second == GroupSpeaker.USER })
        assertEquals(
            "落库顺序：用户 → 修图师 → 文案",
            listOf(GroupSpeaker.USER, agent.id, agent2.id),
            port.rows.map { it.second },
        )
        assertEquals(2, captured.size)
        assertEquals("https://agent.example/v1", captured[0].baseUrl)
        assertEquals("https://writer.example/v1", captured[1].baseUrl)
        assertTrue(
            "第二个人要看到第一个人的回复",
            captured[1].messages.any { it.content.contains("[修图师] 好") },
        )
    }

    @Test
    fun `a failing member does not stop the rest`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            captured += request
            if (captured.size == 1) {
                cb.onError(ChatError.InvalidKey(401, "bad key"))
            } else {
                cb.onEvent(SseEvent.Done("好的", null, null, null))
            }
            null
        }
        val (engine, port) = engine(runner)

        val results = engine.sendToMany(
            groupId = "group_1",
            agents = listOf(agent, agent2),
            userText = "都来看",
            targetAgentIds = listOf(agent.id, agent2.id),
        )

        assertEquals(2, results.size)
        assertFalse("第一个失败", results[0].completed)
        assertTrue("失败原因可读", results[0].error!!.contains("API Key 无效"))
        assertTrue("第二个照常回", results[1].completed)
        assertEquals(
            "只有成功的回复落库",
            listOf(GroupSpeaker.USER, agent2.id),
            port.rows.map { it.second },
        )
    }

    // ── P216：本次用户消息不能进两次提示词 ───────────────

    @Test
    fun `current user message appears once in the prompt`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, _) = engine(okRunner(captured, "好"))

        engine.send("group_1", listOf(agent), "这次只说一遍", null)

        val userMessages = captured.single().messages.filter { it.role == "user" }
        assertEquals("用户消息在提示词里只能出现一次，实际：$userMessages", 1, userMessages.size)
        assertEquals("这次只说一遍", userMessages.single().content)
    }

    @Test
    fun `current user message appears once for each mentioned agent`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, _) = engine(okRunner(captured, "好"))

        engine.sendToMany(
            groupId = "group_1",
            agents = listOf(agent, agent2),
            userText = "都来看",
            targetAgentIds = listOf(agent.id, agent2.id),
        )

        assertEquals(2, captured.size)
        captured.forEachIndexed { index, request ->
            // 别人的发言也会以 user 角色 replay（带 [名字] 前缀），所以不能数 user 条数，
            // 只能数「本次这句话」出现了几次。
            val mentions = request.messages.filter {
                it.role == "user" && it.content.contains("都来看")
            }
            assertEquals("第 ${index + 1} 个目标里本次消息只能出现一次：$mentions", 1, mentions.size)
            assertTrue("被 @ 的人要带 @ 前缀", mentions.single().content.startsWith("@"))
        }
    }

    @Test
    fun `earlier user history is still replayed`() = runTest {
        val port = FakePort()
        port.append("group_1", GroupSpeaker.USER, "上一句", 0L, "")
        port.append("group_1", GroupSpeaker.HOST, "嗯", 0L, "")
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, _) = engine(okRunner(captured, "好"), port)

        engine.send("group_1", listOf(agent), "这一句", null)

        val userContents = captured.single().messages.filter { it.role == "user" }.map { it.content }
        assertEquals("历史用户消息 + 本次用户消息", 2, userContents.size)
        assertEquals("上一句", userContents[0])
        assertEquals("这一句", userContents[1])
    }

    // ── P205：引用 ──

    @Test
    fun `quote is persisted and shown to the model`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, port) = engine(okRunner(captured, "好"))

        engine.sendToMany(
            groupId = "group_1",
            agents = listOf(agent),
            userText = "这是什么意思",
            targetAgentIds = listOf(agent.id),
            quoteId = 42,
            quotePreview = "兰未晞：今天有点累",
        )

        assertEquals(42L, port.lastUserQuoteId)
        assertEquals("兰未晞：今天有点累", port.lastUserQuotePreview)
        // 落库正文保持干净
        assertTrue(port.rows.any { it.second == GroupSpeaker.USER && it.third == "这是什么意思" })
        val userMessage = captured.single().messages.last { it.role == "user" }
        assertTrue(
            "模型要看到引用，实际：${userMessage.content}",
            userMessage.content.contains("（引用：兰未晞：今天有点累）"),
        )
    }

    @Test
    fun `history quotes are replayed into the prompt`() = runTest {
        val port = FakePort(
            seed = listOf(
                GroupMessage(
                    speaker = GroupSpeaker.USER,
                    text = "早上好",
                    createdAt = 1L,
                    id = 1,
                    quotePreview = "兰未晞：昨晚聊到很晚",
                ),
            ),
        )
        val captured = mutableListOf<ChatStreamRequest>()
        val (engine, _) = engine(okRunner(captured, "早"), port)

        engine.sendToMany("group_1", listOf(agent), "继续", listOf(agent.id))

        val userContents = captured.single().messages
            .filter { it.role == "user" }
            .map { it.content }
        assertTrue(
            "历史里的引用也要还原给模型，实际：$userContents",
            userContents.any { it.contains("早上好") && it.contains("（引用：兰未晞：昨晚聊到很晚）") },
        )
    }

    // ── P210：群聊的「前情摘要」 ─────────────────────────

    private fun engineWithSummary(
        runner: StreamRunner,
        summary: String,
        compressor: SummaryEngine? = null,
        port: FakePort = FakePort(),
    ) = GroupChatEngine(
        stream = runner,
        promptBuilder = PromptBuilder(),
        port = port,
        configProvider = FixedConfigProvider(hostConfig),
        dispatcher = UnconfinedTestDispatcher(),
        summaryProvider = SummaryProvider { summary },
        summaryEngine = compressor,
    )

    @Test
    fun `host request carries the plot summary`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val engine = engineWithSummary(okRunner(captured), "前面定好了周六去看展")

        engine.send("group_1", listOf(agent), "在吗", null)

        val system = captured.single().messages.first().content
        assertTrue("兰未晞要拿到前情摘要，实际：$system", system.contains("前面定好了周六去看展"))
    }

    @Test
    fun `agent request carries the summary in system`() = runTest {
        val captured = mutableListOf<ChatStreamRequest>()
        val engine = engineWithSummary(okRunner(captured), "前面定好了周六去看展")

        engine.send("group_1", listOf(agent), "修一下", agent.id)

        val system = captured.single().messages.first().content
        assertTrue("工具人的 system 段要带摘要，实际：$system", system.contains("【前情摘要】"))
        assertTrue(system.contains("周六去看展"))
    }

    @Test
    fun `compression triggers after a group send`() = runTest {
        var calls = 0
        var lastSession: String? = null
        val compressor = object : SummaryEngine {
            override suspend fun maybeCompress(sessionId: String): Boolean {
                calls++
                lastSession = sessionId
                return true
            }
        }
        val engine = engineWithSummary(okRunner(mutableListOf()), "摘要", compressor)

        engine.send("group_1", listOf(agent), "hi", null)

        assertEquals(1, calls)
        assertEquals("group_1", lastSession)
    }
}
