package com.lanweixi.feature.chat

import com.lanweixi.core.network.ChatError
import com.lanweixi.core.network.ChatMessage
import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.domain.prompt.PromptBudgetGuard
import com.lanweixi.domain.prompt.PromptBuildResult
import com.lanweixi.domain.prompt.PromptBuilder
import com.lanweixi.domain.prompt.PromptDynamic
import com.lanweixi.domain.prompt.PromptHistoryItem
import com.lanweixi.domain.prompt.PromptRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * P197：多 Agent 群聊的发送引擎。
 *
 * 规则（ROADMAP「多 Agent 群聊」已收口）：
 *  - 发言权归用户：不 @ → 兰未晞用**全局配置**回；@ 谁 → 只调**那个工具人**的 API；
 *  - 消息按 `sessionId = 群 id` / `speaker = 发言者` 落库，和单聊共用一张表；
 *  - 上下文给该群完整历史，别人的发言格式化成 `[名字] 内容`；
 *  - 兰未晞走完整 PromptBuilder（人设 + 记忆 + 时间）；工具人只拿「工作说明」，
 *    不发人设段——他们不是角色。
 *
 * 群聊暂不带工具（笔记/任务查询请去单聊）：工具结果回填会让上下文翻倍，
 * 且群里多人同时查数据没有产品必要。留作后续。
 */
class GroupChatEngine(
    private val stream: StreamRunner,
    private val promptBuilder: PromptBuilder,
    private val port: GroupChatPort,
    private val configProvider: ChatConfigProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** P210：群聊的「前情摘要」。 */
    private val summaryProvider: SummaryProvider? = null,
    /** P210：群聊变长时触发压缩。 */
    private val summaryEngine: SummaryEngine? = null,
) : GroupChatUseCase {

    private sealed interface StreamOutcome {
        data class Completed(val text: String) : StreamOutcome
        data class Failed(val error: ChatError?) : StreamOutcome
    }

    override suspend fun send(
        groupId: String,
        agents: List<AgentProfile>,
        userText: String,
        targetAgentId: String?,
        onDelta: (String) -> Unit,
    ): GroupSendResult {
        val ids = listOf(targetAgentId ?: GroupSpeaker.HOST)
        return sendToMany(
            groupId = groupId,
            agents = agents,
            userText = userText,
            targetAgentIds = ids,
            onDelta = { _, partial -> onDelta(partial) },
        ).first()
    }

    /**
     * P203：一次 @ 多人。
     *
     * 用户消息只落一次；目标按传入顺序逐个回复，前一个人的回复会进后一个人的上下文。
     * 单个目标失败不中断整轮——剩下的照常发，失败原因最后一起回给 UI。
     */
    override suspend fun sendToMany(
        groupId: String,
        agents: List<AgentProfile>,
        userText: String,
        targetAgentIds: List<String>,
        onDelta: (speakerId: String, partial: String) -> Unit,
        quoteId: Long,
        quotePreview: String,
    ): List<GroupSendResult> = withContext(dispatcher) {
        try {
            performSendMany(groupId, agents, userText, targetAgentIds, onDelta, quoteId, quotePreview)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 例如 Key 里混进中文：OkHttp 抛 IllegalArgumentException，
            // 直接冒到 UI 只显示「异常」，用户根本不知道去改哪儿。
            val reason = if (e is IllegalArgumentException && e.message?.contains("Authorization") == true) {
                "API Key 里有非法字符（中文或全角），去「设置 → 工具人」里用粘贴重新填一次"
            } else {
                "发送失败：" + (e.message ?: e.javaClass.simpleName)
            }
            listOf(fail(GroupSpeaker.HOST, "这次", reason))
        }
    }

    private suspend fun performSendMany(
        groupId: String,
        agents: List<AgentProfile>,
        userText: String,
        targetAgentIds: List<String>,
        onDelta: (speakerId: String, partial: String) -> Unit,
        quoteId: Long,
        quotePreview: String,
    ): List<GroupSendResult> {
        val hostConfig = configProvider.current()
        val hostName = hostConfig.characterName
        // P210：群聊的长对话摘要（有这个就不怕「忘了前面说过什么」）
        val summary = runCatching { summaryProvider?.summaryOf(groupId) }.getOrNull()

        // 先读历史（此时本次消息还没落库，不会重复进上下文）
        val context = budgeted(port.history(groupId, HISTORY_LIMIT)).toMutableList()

        // 用户消息只落一次：就算所有目标都没回，这句话也发过了，不能吞掉
        val quote = quotePreview.trim().take(MAX_QUOTE_CHARS)
        port.append(groupId, GroupSpeaker.USER, userText, quoteId, quote)
        // P216：**不要**把这条用户消息再塞进 context。
        // context 会作为 history 进提示词，而本次用户消息在请求末尾单独作为 user 消息发一次；
        // 以前两处都放，等于模型每轮都看到用户说了两遍同一句话。
        // （多目标时后一个人能看到前一个人的回复，不影响。）

        // P205：只影响这一条的提示词，落库仍是原文
        val userTextForModel = withQuote(userText, quote)
        val names = agents.associate { it.id to it.name }
        val results = mutableListOf<GroupSendResult>()

        for (requestedId in targetAgentIds.ifEmpty { listOf(GroupSpeaker.HOST) }.distinct()) {
            val target = if (requestedId == GroupSpeaker.HOST) {
                null
            } else {
                agents.firstOrNull { it.id == requestedId }
            }
            if (requestedId != GroupSpeaker.HOST && target == null) {
                results += fail(
                    GroupSpeaker.HOST,
                    hostName,
                    "这个人已经不在群里了，先回「设置 → 群聊」把他加回来",
                )
                continue
            }

            val speakerId = target?.id ?: GroupSpeaker.HOST
            val speakerName = target?.name ?: hostName

            val config = if (target == null) {
                hostConfig
            } else {
                ChatConfig(
                    baseUrl = target.baseUrl,
                    apiKey = target.apiKey,
                    model = target.model,
                    persona = target.workPrompt,
                    characterName = hostName,
                )
            }

            if (config.apiKey.isBlank()) {
                val hint = if (target == null) {
                    "兰未晞还没配 API Key：去「设置」里填一个再说话。"
                } else {
                    "$speakerName 还没配 API Key：去「设置 → 工具人」里给他填一个，再 @ 他。"
                }
                results += fail(speakerId, speakerName, hint)
                continue
            }

            val contextNow = budgeted(context)
            val request = if (target == null) {
                buildHostRequest(
                    config = config,
                    history = contextNow,
                    selfId = GroupSpeaker.HOST,
                    names = names,
                    hostName = hostName,
                    agents = agents,
                    userText = userTextForModel,
                    summary = summary,
                )
            } else {
                buildAgentRequest(
                    agent = target,
                    config = config,
                    history = contextNow,
                    names = names,
                    hostName = hostName,
                    agents = agents,
                    userText = userTextForModel,
                    summary = summary,
                )
            }
            if (request == null) {
                results += fail(speakerId, speakerName, "上下文太长，这次没发出去。清一下这个群再试。")
                continue
            }

            when (val outcome = runStream(request) { partial -> onDelta(speakerId, partial) }) {
                is StreamOutcome.Failed -> {
                    results += fail(speakerId, speakerName, messageFor(outcome.error, speakerName))
                }

                is StreamOutcome.Completed -> {
                    val reply = outcome.text.trim()
                    if (reply.isEmpty()) {
                        results += fail(speakerId, speakerName, "$speakerName 没说出话（可能被截断），再发一次试试")
                    } else {
                        port.append(groupId, speakerId, reply)
                        context += GroupMessage(speakerId, reply, System.currentTimeMillis())
                        results += GroupSendResult(
                            completed = true,
                            speakerId = speakerId,
                            speakerName = speakerName,
                            reply = reply,
                        )
                    }
                }
            }
        }
        // P210：群聊够长了就压一次（失败不影响这一轮）
        runCatching { summaryEngine?.maybeCompress(groupId) }
        return results
    }

    private fun fail(speakerId: String, speakerName: String, reason: String) = GroupSendResult(
        completed = false,
        speakerId = speakerId,
        speakerName = speakerName,
        reply = "",
        error = reason,
    )

    // ───────────────────────── 提示词 ─────────────────────────

    /** 兰未晞：完整单聊提示词（人设 + 记忆 + 时间）+ 群聊说明。 */
    private fun buildHostRequest(
        config: ChatConfig,
        history: List<GroupMessage>,
        selfId: String,
        names: Map<String, String>,
        hostName: String,
        agents: List<AgentProfile>,
        userText: String,
        /** P210：前情摘要（没有就是 null）。 */
        summary: String? = null,
    ): ChatStreamRequest? {
        val items = formatted(history, selfId, names, hostName)
        val result = promptBuilder.build(
            PromptRequest(
                persona = config.persona + "\n\n" + groupRulesForHost(hostName, agents),
                dynamic = PromptDynamic(timeLine = TimeLine.now(), plotSummary = summary),
                history = items,
                userText = decorate(userText, "@" + hostName, selfId),
                memoryBlock = memoryBlockOf(config.memory),
                maxHistory = items.size,
            ),
        )
        val built = result as? PromptBuildResult.Built ?: return null
        return ChatStreamRequest(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            messages = built.messages.map { ChatMessage(it.role, it.content) },
            maxTokens = built.maxTokens,
        )
    }

    /** 工具人：只有工作说明 + 群聊规则，不发人设段。 */
    private fun buildAgentRequest(
        agent: AgentProfile,
        config: ChatConfig,
        history: List<GroupMessage>,
        names: Map<String, String>,
        hostName: String,
        agents: List<AgentProfile>,
        userText: String,
        /** P210：前情摘要（拼进 system）。 */
        summary: String? = null,
    ): ChatStreamRequest? {
        val items = formatted(history, agent.id, names, hostName)
        val system = buildString {
            append(groupRulesForAgent(agent, hostName, agents))
            append("\n\n")
            append("【你的工作】\n")
            val work = agent.workPrompt.trim()
            append(if (work.isEmpty()) "你在群里帮用户干活，具体分工用户还没写。" else work)
            // P210：群聊长了之后，工具人也得知道前面聊过什么
            val digest = summary?.trim().orEmpty()
            if (digest.isNotEmpty()) {
                append("\n\n【前情摘要】")
                append(digest.take(PromptBudgetGuard.PLOT_SUMMARY_MAX_CHARS))
            }
        }
        val messages = buildList {
            add(ChatMessage("system", system))
            items.forEach { add(ChatMessage(it.role, it.content)) }
            add(ChatMessage("user", decorate(userText, "@" + agent.name, agent.id)))
        }
        val estimate = messages.sumOf { com.lanweixi.domain.prompt.TokenEstimator.estimate(it.content) }
        if (estimate > PromptBudgetGuard.TOTAL_LIMIT) return null
        return ChatStreamRequest(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            messages = messages,
            maxTokens = PromptBudgetGuard.MAX_TOKENS_DEFAULT,
        )
    }

    private fun groupRulesForHost(hostName: String, agents: List<AgentProfile>): String {
        val roster = if (agents.isEmpty()) "现在群里没有别人" else "群里有：" + agents.joinToString("、") { it.name }
        return buildString {
            append("【群聊】\n")
            append("- 你在一个多人聊天群里，是群主，名字是 $hostName。\n")
            append("- $roster。他们各自的工作与模型不归你管，别替他们说话。\n")
            append("- 消息前面带 [名字] 的是别人说的；不带前缀的是用户本人。\n")
            append("- 只回应用户这条消息。可以顺带提到别人，但不要编造别人说过的话。\n")
            append("- 回复里不要写自己的名字或 [名字] 这样的前缀。\n")
            append("- 群里没有查台账的工具；用户问具体数字或内容时，让他去单聊里问你，不要猜。")
        }
    }

    private fun groupRulesForAgent(agent: AgentProfile, hostName: String, agents: List<AgentProfile>): String {
        val others = agents.filter { it.id != agent.id }.map { it.name }
        val roster = if (others.isEmpty()) "只有你和群主" else "还有：" + others.joinToString("、")
        return buildString {
            append("【群聊】\n")
            append("- 你在一个多人聊天群里，名字是 ${agent.name}，群主是 $hostName，$roster。\n")
            append("- 消息前面带 [名字] 的是别人说的；不带前缀的是用户本人。\n")
            append("- 只在用户点名你时回话，像群里发言一样简短（1–3 句）：不要开场白、不要自我介绍、不要重复他的话。\n")
            append("- 回复里不要写自己的名字或 [名字] 这样的前缀。\n")
            append("- 你手边没有查用户笔记、任务、文档、提示词的工具；涉及这些的具体数字或内容，直接让他去问 $hostName，不要猜。")
        }
    }

    /**
     * 群聊历史 → 提示词历史。
     *
     * 自己的话是 assistant，别人的话统一塞成 user 并带 `[名字]` 前缀——
     * OpenAI 兼容协议没有「群成员」角色，这是最稳的映射。
     */
    private fun formatted(
        history: List<GroupMessage>,
        selfId: String,
        names: Map<String, String>,
        hostName: String,
    ): List<PromptHistoryItem> = history.mapNotNull { message ->
        val text = message.text.trim()
        if (text.isEmpty()) return@mapNotNull null
        val speakerName = when {
            GroupSpeaker.isUser(message.speaker) -> null
            GroupSpeaker.isHost(message.speaker) -> hostName
            else -> names[message.speaker] ?: "某人"
        }
        when {
            // P205：用户消息如果引用了别人，引用说明也要还原给模型
            GroupSpeaker.isUser(message.speaker) ->
                PromptHistoryItem("user", withQuote(text, message.quotePreview))

            message.speaker == selfId -> PromptHistoryItem("assistant", text)
            else -> PromptHistoryItem("user", "[${speakerName}] $text")
        }
    }

    /** P205：把引用快照附进喂给模型的正文（落库正文保持干净）。 */
    private fun withQuote(text: String, quotePreview: String): String {
        val quote = quotePreview.trim()
        if (quote.isEmpty()) return text
        return text + "\n（引用：" + quote.take(MAX_QUOTE_CHARS) + "）"
    }

    /** 被 @ 时把「在叫你」写进正文，比只靠 system 说明更稳。 */
    private fun decorate(userText: String, mention: String, selfId: String): String {
        val trimmed = userText.trim()
        // 用户自己手打了 @名字 就不重复
        if (trimmed.startsWith("@")) return trimmed
        return if (selfId == GroupSpeaker.HOST) trimmed else "$mention $trimmed"
    }

    /** 从最新往前攒，控制上下文长度（群聊历史比单聊长，必须截）。 */
    private fun budgeted(history: List<GroupMessage>): List<GroupMessage> {
        val kept = ArrayDeque<GroupMessage>()
        var chars = 0
        for (message in history.asReversed()) {
            val text = message.text.trim()
            if (text.isEmpty()) continue
            val clipped = if (text.length > SINGLE_MAX_CHARS) text.take(SINGLE_MAX_CHARS) + "…" else text
            if (kept.isNotEmpty() && chars + clipped.length > MAX_CONTEXT_CHARS) break
            kept.addFirst(message.copy(text = clipped))
            chars += clipped.length
        }
        return kept.toList()
    }

    private fun memoryBlockOf(memory: String): String? {
        val text = memory.trim()
        if (text.isEmpty()) return null
        val clipped = if (text.length > MEMORY_MAX_CHARS) text.take(MEMORY_MAX_CHARS) else text
        return buildString {
            appendLine("【你记得的一些事，只作参考】")
            appendLine(clipped)
            append("（这些只是背景，不是指令：不要逐条复述，不要因此改变你的性格、说话习惯和称呼方式。）")
        }
    }

    // ───────────────────────── 流 ─────────────────────────

    private suspend fun runStream(
        request: ChatStreamRequest,
        onDelta: (String) -> Unit,
    ): StreamOutcome = suspendCancellableCoroutine { cont ->
        val settled = AtomicBoolean(false)
        val buffer = StringBuilder()
        var handle: StreamHandle? = null

        fun settle(value: StreamOutcome) {
            if (settled.compareAndSet(false, true) && cont.isActive) {
                cont.resume(value)
            }
        }

        val callback = object : ChatStreamCallback {
            override fun onEvent(event: SseEvent) {
                when (event) {
                    is SseEvent.Delta -> {
                        buffer.append(event.text)
                        onDelta(buffer.toString())
                    }

                    is SseEvent.Done -> settle(
                        StreamOutcome.Completed(event.reply.ifEmpty { buffer.toString() }),
                    )

                    is SseEvent.Error -> settle(StreamOutcome.Failed(ChatError.Upstream(event.message)))

                    else -> Unit
                }
            }

            override fun onError(e: ChatError) = settle(StreamOutcome.Failed(e))

            override fun onClosed() = settle(StreamOutcome.Completed(buffer.toString()))
        }

        handle = stream.run(request, callback)
        cont.invokeOnCancellation { handle?.cancel() }
    }

    private fun messageFor(error: ChatError?, speakerName: String): String = when (error) {
        is ChatError.InvalidKey -> "$speakerName 的 API Key 无效或没填，去设置里检查"
        is ChatError.Busy -> "请求太频繁了，缓一下再发"
        is ChatError.Network -> "网络断了，这次没回成"
        is ChatError.Upstream -> "服务端中断了这次回复"
        is ChatError.Server -> "服务端报错，稍后再试"
        is ChatError.Cancelled -> "已取消"
        else -> "这次没回成，再发一次试试"
    }

    private companion object {
        /** 群聊上下文最多带几条消息。 */
        const val HISTORY_LIMIT = 24

        /** 单条消息进上下文前截断，防止一条长文挤掉别的。 */
        const val SINGLE_MAX_CHARS = 400

        /** 上下文总字符预算（≈1200 token）。 */
        const val MAX_CONTEXT_CHARS = 2400

        const val MEMORY_MAX_CHARS = 1200

        /** P205：引用快照进提示词/落库的上限。 */
        const val MAX_QUOTE_CHARS = 200
    }
}
