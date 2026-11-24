package com.lanweixi.feature.chat

import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.core.network.ToolCallFragment
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.PromptBuilder
import com.lanweixi.domain.tools.ToolCall
import com.lanweixi.domain.tools.ToolExecutor
import com.lanweixi.domain.tools.ToolResult
import com.lanweixi.domain.tools.ToolSource
import com.lanweixi.domain.tools.ToolSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P138：工具调用累积与多轮循环。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineToolTest {

    private class FakeHistory : ChatHistoryPort {
        val rows = mutableListOf<Pair<String, String>>()

        /** P215：记录落库会话，用来验证「会话在 send 开始时就定死」。 */
        val sessions = mutableListOf<String>()

        override suspend fun recent(limit: Int) = emptyList<HistoryMessage>()
        override suspend fun append(
            role: String,
            content: String,
            sessionId: String,
            status: String,
            quoteId: Long,
            quotePreview: String,
        ) {
            rows += role to content
            sessions += sessionId
        }
    }

    /** P215：可切换的假会话端口。 */
    private class FakeSession(var currentId: String = "A") : SessionPort {
        override suspend fun current(): String = currentId
        override suspend fun switch(id: String) {
            currentId = id
        }

        override suspend fun list(): List<SessionSummary> = emptyList()
        override suspend fun delete(id: String) = Unit
        override suspend fun createNew(): String = "new"
    }

    private fun engine(
        runner: StreamRunner,
        executors: Map<String, ToolExecutor> = emptyMap(),
        confirm: suspend (ToolSpec, ToolCall) -> Boolean = { _, _ -> true },
        tools: List<ToolSpec> = executors.values.map { it.spec },
        history: FakeHistory = FakeHistory(),
        session: SessionPort? = null,
    ) = Triple(
        ConversationEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            history = history,
            configProvider = FixedConfigProvider(
                ChatConfig(baseUrl = "http://127.0.0.1/mock", apiKey = "sk-test", persona = "你是助手"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
            sessionProvider = session,
            tools = { tools },
            executors = { executors },
            confirmSensitive = confirm,
        ),
        history,
        tools,
    )

    private fun echoTool(name: String = "host_echo", sensitive: Boolean = false) =
        object : ToolExecutor {
            var lastCall: ToolCall? = null
            override val spec = ToolSpec(
                name = name,
                description = "回声",
                source = ToolSource.HOST,
                sensitive = sensitive,
            )

            override suspend fun execute(call: ToolCall): ToolResult {
                lastCall = call
                return ToolResult(call.id, call.name, "结果是：" + call.arguments)
            }
        }

    @Test
    fun `tool round is executed and fed back`() = runTest {
        var round = 0
        val tool = echoTool()
        val runner = StreamRunner { _, cb: ChatStreamCallback ->
            round++
            if (round == 1) {
                cb.onToolCalls(
                    listOf(ToolCallFragment(0, "call_1", "host_echo", "{\"q\":")),
                )
                cb.onToolCalls(listOf(ToolCallFragment(0, null, null, "\"笔记\"}")))
                cb.onClosed()
            } else {
                cb.onEvent(SseEvent.Delta("查到了"))
                cb.onEvent(SseEvent.Done("查到了", null, null, null))
            }
            null
        }
        val (engine, history, _) = engine(runner, mapOf("host_echo" to tool))

        val outcome = engine.send("帮我查", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertTrue(outcome.completed)
        assertEquals("查到了", outcome.reply)
        assertEquals(1, outcome.toolRounds)
        assertEquals("{\"q\":\"笔记\"}", tool.lastCall?.arguments)
        assertEquals(listOf("user" to "帮我查", "assistant" to "查到了"), history.rows)
    }

    @Test
    fun `sensitive tool can be refused`() = runTest {
        var round = 0
        val tool = echoTool(name = "host_danger", sensitive = true)
        val runner = StreamRunner { _, cb: ChatStreamCallback ->
            round++
            if (round == 1) {
                cb.onToolCalls(listOf(ToolCallFragment(0, "c1", "host_danger", "{}")))
                cb.onClosed()
            } else {
                cb.onEvent(SseEvent.Done("好的，那我不动", null, null, null))
            }
            null
        }
        val (engine, _, _) = engine(runner, mapOf("host_danger" to tool), confirm = { _, _ -> false })

        val outcome = engine.send("删点东西", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertTrue(outcome.completed)
        assertEquals("好的，那我不动", outcome.reply)
        assertEquals("敏感工具被拒后不应执行", null, tool.lastCall)
    }

    @Test
    fun `unknown tool returns error text instead of crashing`() = runTest {
        var round = 0
        var secondRequest: ChatStreamRequest? = null
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            round++
            if (round == 1) {
                cb.onToolCalls(listOf(ToolCallFragment(0, "c1", "not_exist", "{}")))
                cb.onClosed()
            } else {
                secondRequest = request
                cb.onEvent(SseEvent.Done("它办不到", null, null, null))
            }
            null
        }
        val (engine, _, _) = engine(runner, mapOf("host_echo" to echoTool()))

        val outcome = engine.send("调个不存在的", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertTrue(outcome.completed)
        val toolMsg = secondRequest?.messages?.lastOrNull { it.role == "tool" }
        assertTrue("错误文本应被包装后回填", toolMsg!!.content.contains("工具不存在：not_exist"))
        assertEquals("c1", toolMsg!!.toolCallId)
    }

    @Test
    fun `tool rounds are capped`() = runTest {
        var round = 0
        val tool = echoTool()
        // 每次都返回工具调用：引擎应在 3 轮后停下
        val runner = StreamRunner { _, cb: ChatStreamCallback ->
            round++
            cb.onToolCalls(listOf(ToolCallFragment(0, "c$round", "host_echo", "{}")))
            cb.onClosed()
            null
        }
        val (engine, history, _) = engine(runner, mapOf("host_echo" to tool))

        val outcome = engine.send("死循环", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertTrue(outcome.completed)
        assertTrue("轮数应被限制住", outcome.toolRounds <= 3)
        assertEquals("tool rounds exceeded", outcome.note)
        // P215：超限这条也必须落库——以前直接 return，用户消息和回复一起消失
        assertEquals("user" to "死循环", history.rows.first())
        assertEquals("assistant", history.rows.last().first)
        assertTrue("回复不能丢", history.rows.last().second.isNotBlank())
    }

    // ── P215：落库会话必须在 send 开始时定死 ─────────────

    @Test
    fun `messages go to the session captured at send start`() = runTest {
        val session = FakeSession("A")
        val history = FakeHistory()
        // 模拟「发送过程中别的页面把 current 切到了 B」
        val runner = StreamRunner { _, cb: ChatStreamCallback ->
            session.currentId = "B"
            cb.onEvent(SseEvent.Done("好", null, null, null))
            null
        }
        val (engine, _, _) = engine(runner, history = history, session = session)

        engine.send("在吗", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertEquals(listOf("A", "A"), history.sessions)
    }

    @Test
    fun `no tools means single clean round`() = runTest {
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            // 没有可用工具时不应带 tools
            assertTrue(request.tools.isEmpty())
            cb.onEvent(SseEvent.Done("直接回答", null, null, null))
            null
        }
        val (engine, _, _) = engine(runner)

        val outcome = engine.send("闲聊", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)
        assertTrue(outcome.completed)
        assertEquals(0, outcome.toolRounds)
    }

    @Test
    fun `accumulator merges fragments by index`() {
        val acc = ToolCallAccumulator()
        acc.addAll(listOf(ToolCallFragment(0, "a", "n1", "{\"x\":")))
        acc.addAll(listOf(ToolCallFragment(1, "b", "n2", "{}")))
        acc.addAll(listOf(ToolCallFragment(0, null, null, "1}")))

        val built = acc.build()
        assertEquals(2, built.size)
        assertEquals("a", built[0].id)
        assertEquals("n1", built[0].name)
        assertEquals("{\"x\":1}", built[0].arguments)
        assertEquals("b", built[1].id)
    }

    @Test
    fun `accumulator skips nameless calls`() {
        val acc = ToolCallAccumulator()
        acc.addAll(listOf(ToolCallFragment(0, "a", null, "{}")))
        assertTrue(acc.build().isEmpty())
    }

    @Test
    fun `reports tool call and wraps result`() = runTest {
        var round = 0
        var secondRequest: com.lanweixi.core.network.ChatStreamRequest? = null
        val reported = mutableListOf<Pair<String, Boolean>>()
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            round++
            if (round == 1) {
                cb.onToolCalls(listOf(ToolCallFragment(0, "c1", "host_echo", "{}")))
                cb.onClosed()
            } else {
                secondRequest = request
                cb.onEvent(SseEvent.Done("好了", null, null, null))
            }
            null
        }
        val tool = echoTool()
        val engine = ConversationEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            history = FakeHistory(),
            configProvider = FixedConfigProvider(
                ChatConfig(baseUrl = "http://127.0.0.1/mock", apiKey = "sk-test", persona = "你是助手"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
            tools = { listOf(tool.spec) },
            executors = { mapOf("host_echo" to tool) },
        )

        engine.send(
            "查一下",
            com.lanweixi.domain.prompt.PromptDynamic(),
            null,
            0,
            onToolCall = { name, sensitive -> reported += name to sensitive },
        )

        assertEquals(listOf("host_echo" to false), reported)

        val toolMsg = secondRequest?.messages?.lastOrNull { it.role == "tool" }
        assertTrue("结果要有边界标记", toolMsg!!.content.contains("[工具 host_echo 的返回结果"))
        assertTrue("要有防编造提示", toolMsg.content.contains("不要推测") || toolMsg.content.contains("不要编"))
    }

    @Test
    fun `forces a tool round for data questions`() = runTest {
        var round = 0
        val toolChoices = mutableListOf<String>()
        val tool = echoTool()
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            round++
            toolChoices += request.toolChoice
            if (round == 1) {
                // 第一次故意不调工具（模拟「靠上下文编数字」）
                cb.onEvent(SseEvent.Delta("有 26 条"))
                cb.onEvent(SseEvent.Done("有 26 条", null, null, null))
            } else if (round == 2) {
                cb.onToolCalls(listOf(ToolCallFragment(0, "c1", "host_echo", "{}")))
                cb.onClosed()
            } else {
                cb.onEvent(SseEvent.Done("其实是 0 条", null, null, null))
            }
            null
        }
        val engine = ConversationEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            history = FakeHistory(),
            configProvider = FixedConfigProvider(
                ChatConfig(baseUrl = "http://127.0.0.1/mock", apiKey = "sk-test", persona = "你是助手"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
            tools = { listOf(tool.spec) },
            executors = { mapOf("host_echo" to tool) },
        )

        val outcome = engine.send("我有多少条笔记", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertEquals("数据类问题第一轮就该 required", "required", toolChoices.first())
        assertTrue("应该补了一轮工具", outcome.toolRounds >= 1)
        assertEquals("其实是 0 条", outcome.reply)
    }

    @Test
    fun `casual chat does not force tools`() = runTest {
        var round = 0
        val toolChoices = mutableListOf<String>()
        val runner = StreamRunner { request, cb: ChatStreamCallback ->
            round++
            toolChoices += request.toolChoice
            cb.onEvent(SseEvent.Done("嗯", null, null, null))
            null
        }
        val engine = ConversationEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            history = FakeHistory(),
            configProvider = FixedConfigProvider(
                ChatConfig(baseUrl = "http://127.0.0.1/mock", apiKey = "sk-test", persona = "你是助手"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
            tools = { listOf(echoTool().spec) },
            executors = { mapOf("host_echo" to echoTool()) },
        )

        val outcome = engine.send("今天有点累", com.lanweixi.domain.prompt.PromptDynamic(), null, 0)

        assertEquals("auto", toolChoices.first())
        assertEquals(0, outcome.toolRounds)
        assertEquals(1, round)
    }
}
