package com.lanweixi.feature.chat

import androidx.lifecycle.ViewModel
import com.lanweixi.core.network.ChatError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P07a 聊天首屏 ViewModel（P07a.1：生命周期收口）。
 *
 * - 只依赖 ConversationUseCase 与只读的 ChatHistoryQueryPort（编译面拿不到 append）；
 * - Delta 只更新 partialAssistant，不落库；
 * - Done 成功后重新读历史，失败保留草稿并展示 banner；
 * - onCleared() 取消注入的 scope（生产 factory 传入独立 scope，避免泄漏）。
 */
class ChatViewModel(
    private val useCase: ConversationUseCase,
    private val historyQuery: ChatHistoryQueryPort,
    private val scope: CoroutineScope,
    private val session: SessionPort? = null,
    /** P139：敏感工具确认（可为空：为空时引擎那边仍会拒绝）。 */
    private val confirmGateway: com.lanweixi.domain.tools.ToolConfirmGateway? = null,
    /** P91：角色名字（可自定义，用于自我面板等文案）。 */
    private val characterName: String = "兰未晞",
    /** P100：休息预算（每日对话轮次上限）。 */
    /** P198：从会话列表进来时指定要打开的会话 id（null = 沿用当前会话）。 */
    private val initialSessionId: String? = null,
    /** P207：待审核条数（进页面、发送完成后刷新）。 */
    private val pendingDrafts: (suspend () -> Int)? = null,
    /** P207：从草稿来源跳进来时要定位的用户原话。 */
    private val highlightQuery: String? = null,
    /** P208：任务摘要（今天到期 / 最近完成），进动态段的「今天发生的事」。 */
    private val taskContext: (suspend () -> String?)? = null,
    /** P210：读会话摘要，进动态段的「前情摘要」。 */
    private val summaryProvider: SummaryProvider? = null,
    /** P210：会话变长时触发压缩。 */
    private val summaryEngine: SummaryEngine? = null,
    /** P211：token 用量记录（没有就跳过）。 */
    private val usageRecorder: UsageRecorder? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 待用户确认的敏感工具调用；null 表示没有。 */
    val pendingConfirm: StateFlow<com.lanweixi.domain.tools.PendingToolConfirm?> =
        confirmGateway?.pending
            ?: MutableStateFlow<com.lanweixi.domain.tools.PendingToolConfirm?>(null).asStateFlow()

    /** UI 点了允许/拒绝。 */
    fun answerConfirm(allow: Boolean) {
        confirmGateway?.answer(allow)
    }

    private var sendJob: Job? = null

    /**
     * P215：本页绑定的会话 id。
     *
     * 修的是一个会串会话的 bug：会话 A 的页面还在返回栈里时又进了 B（例如待审核页点「来自会话」跳过去），
     * B 的 init 会把全局 `current()` 切到 B；返回 A 时 A 的 ViewModel 不重建，之后在 A 里发消息
     * 读历史/落库/摘要全都按全局 current= B 走——消息写进 B、A 页显示 B 的内容。
     * 所以本页读写一律以这个 id 为准，动手前顺手把 current 掰回自己。
     */
    private val pageSessionId: String = initialSessionId ?: DEFAULT_SESSION_ID

    /** P57：自我面板只在会话开始时注入一次。 */

    /** P33：最近一次发送异常（供 banner 展示定位）。 */
    var lastSendError: Throwable? = null
        private set

    init {
        scope.launch {
            // P198：先切会话再读历史。顺序反了会先读出上一个会话的内容，
            // 而且引擎落库用的也是 sessionProvider.current()——切晚了会写错会话。
            // P215：切的是本页固定的 pageSessionId，不是外面传什么算什么。
            runCatching { session?.switch(pageSessionId) }
            reloadHistory()
            refreshPendingCount()
            highlightFromQuery()
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    fun loadHistory() {
        scope.launch { reloadHistory() }
    }

    /**
     * P211：加载更早的消息。
     *
     * 长会话里 UI 只看得到最近 50 条；点一次翻倍，最多 [MAX_HISTORY_LOAD] 条。
     * 原文一直都在数据库里，这里只是「界面上看不到」的问题。
     */
    fun loadMoreHistory() {
        val current = _state.value.historyLimit
        if (current >= MAX_HISTORY_LOAD) return
        _state.update { it.copy(historyLimit = (current + HISTORY_LIMIT).coerceAtMost(MAX_HISTORY_LOAD)) }
        scope.launch { reloadHistory() }
    }

    /** P207：从待审核页返回时刷一次数量，不然入口上的数字是旧的。 */
    fun refreshPendingDrafts() {
        scope.launch { refreshPendingCount() }
    }

    private suspend fun reloadHistory() {
        // P215：读之前把 current 掰回本页（别的页面可能切走过）
        runCatching { session?.switch(pageSessionId) }
        val limit = _state.value.historyLimit
        val result = runCatching { historyQuery.recent(limit) }
        if (result.isFailure) {
            _state.update { it.copy(banner = ChatBanner.Notice("历史加载失败，稍后重试")) }
            return
        }
        val items = result.getOrDefault(emptyList())
            .filter { it.role == "user" || it.role == "assistant" }
            .map {
                ChatMessageUi(
                    role = it.role,
                    text = it.content,
                    createdAt = it.createdAt,
                    id = it.id,
                    quoteId = it.quoteId,
                    quotePreview = it.quotePreview,
                )
            }
        _state.update { it.copy(history = items) }
    }

    /** P207：待审核条数；没注入 provider 就保持 0（不显示入口）。 */
    private suspend fun refreshPendingCount() {
        val provider = pendingDrafts ?: return
        val count = runCatching { provider() }.getOrDefault(0)
        _state.update { it.copy(pendingDrafts = count) }
    }

    /**
     * P207：从草稿「来自聊天」跳进来时，按触发它的原话找到那条用户消息并高亮。
     *
     * 工具执行时消息还没落库，所以草稿存的是文本不是 id；这里用前 40 字匹配。
     */
    private fun highlightFromQuery() {
        val query = highlightQuery?.trim().orEmpty()
        if (query.isEmpty()) return
        val needle = query.take(HIGHLIGHT_NEEDLE_CHARS)
        val match = _state.value.history.lastOrNull { it.role == "user" && it.text.contains(needle) }
        if (match != null) _state.update { it.copy(highlightId = match.id) }
    }

    private fun formatUsage(prompt: Int?, completion: Int?): String? {
        if (prompt == null && completion == null) return null
        return "tokens: ${prompt ?: 0} → ${completion ?: 0}"
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.sending || snapshot.draft.isBlank()) return
        startSend(snapshot.draft)
    }

    /** P10：重发某条 user 消息（只重发 user 文本，不自动重发 assistant 回复）。 */
    fun resendUserMessage(text: String) {
        if (_state.value.sending || text.isBlank()) return
        _state.update { it.copy(draft = text) }
        startSend(text)
    }

    /** P204：引用某条消息（发送时带进上下文；点 × 取消）。 */
    fun quoteMessage(message: ChatMessageUi) {
        val speaker = if (message.role == "user") "我" else characterName
        val preview = message.text.replace('\n', ' ').replace('\r', ' ').trim().take(60)
        if (preview.isBlank()) return
        _state.update { it.copy(quote = ChatQuote(message.id, speaker, preview)) }
    }

    fun clearQuote() {
        _state.update { it.copy(quote = null) }
    }

    /** P204：点气泡里的引用卡片 → 滚到原消息并高亮。 */
    fun locateQuote(messageId: Long) {
        if (messageId <= 0) return
        _state.update { it.copy(highlightId = messageId) }
    }

    fun clearHighlight() {
        _state.update { it.copy(highlightId = 0) }
    }

    /** 统一发送入口：send() 与 resendUserMessage() 共用同一套状态机。 */
    private fun startSend(userText: String) {
        val quote = _state.value.quote
        _state.update {
            it.copy(
                sending = true,
                banner = null,
                partialAssistant = null,
                toolTrail = emptyList(),
                quote = null,
            )
        }

        val job = scope.launch {
            // P215：本页的会话 id 从页面绑定值取，不再读全局 current；
            // 发之前再掰一次，保证引擎读到的历史就是本页的
            val sid = pageSessionId
            runCatching { session?.switch(sid) }
            // 取消必须原样抛出，不能被兜底成"网络错误"
            val outcome = try {
                useCase.send(
                    userText = userText,
                    // P52：把生命状态注入动态段（文档第 7 章的神经状态块）
                    // P208：顺手带上任务摘要（今天到期 / 最近完成）
                    // P210：长会话的「前情摘要」
                    dynamic = com.lanweixi.domain.prompt.PromptDynamic(
                        // 现实时间（日期/星期/时段）
                        timeLine = com.lanweixi.feature.chat.TimeLine.now(),
                        recentEvent = runCatching { taskContext?.invoke() }.getOrNull(),
                        plotSummary = runCatching { summaryProvider?.summaryOf(sid) }.getOrNull(),
                    ),
                    onDelta = { partial -> _state.update { s -> s.copy(partialAssistant = partial) } },
                    onToolCall = { name, _ ->
                        // P206：显示中文动作，别把 host_propose_task 甩给用户看
                        _state.update { s -> s.copy(toolTrail = s.toolTrail + toolTrailLabel(name)) }
                    },
                    replyTarget = 0,
                    // P204：引用只影响这一条的上下文，落库时存快照
                    quoteId = quote?.messageId ?: 0,
                    quotePreview = quote?.let { "${it.speaker}：${it.preview}" }.orEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // P33：定位「发送无回复」——异常不能被静默吞掉，logcat 可见
                android.util.Log.e("AI-Chat", "sendFailed: ${e.javaClass.name}: ${e.message}", e)
                lastSendError = e
                null
            }

            if (outcome != null && outcome.completed) {
                _state.update {
                    it.copy(
                        sending = false,
                        partialAssistant = null,
                        draft = "",
                        usage = formatUsage(outcome.promptTokens, outcome.completionTokens),
                    )
                }
                loadHistory()
                refreshPendingCount()
                // P210：会话够长了就压一次（后台跑，不阻塞界面）
                scope.launch { runCatching { summaryEngine?.maybeCompress(sid) } }
                // P211：用量记一笔（上游没回 usage 就不记）
                if (outcome.promptTokens != null || outcome.completionTokens != null) {
                    scope.launch {
                        runCatching {
                            usageRecorder?.record(sid, outcome.promptTokens, outcome.completionTokens)
                        }
                    }
                }
                    } else {
                _state.update {
                    it.copy(
                        sending = false,
                        partialAssistant = null,
                        // 没发出去就把引用放回来，方便直接重试
                        quote = it.quote ?: quote,
                        banner = bannerFor(outcome) ?: ChatBanner.Error(
                            "本次回复未完成（${lastSendError?.javaClass?.simpleName ?: "未知"}）"
                        ),
                    )
                }
            }
        }
        sendJob = job
        job.invokeOnCompletion { if (sendJob === job) sendJob = null }
    }


    /** P25：失败后重试最后一条用户消息（只在非发送中可用）。 */
    fun retryLast() {
        if (_state.value.sending) return
        val last = _state.value.history.lastOrNull { it.role == "user" } ?: return
        startSend(last.text)
    }

    /** P09：取消当前发送；底层 SSE 由引擎的 suspendCancellableCoroutine 传导取消。 */
    fun cancelSend() {
        val job = sendJob ?: return
        job.cancel()
        _state.update {
            it.copy(
                sending = false,
                partialAssistant = null,
                banner = ChatBanner.Notice("已取消，本次回复未保存"),
            )
        }
    }

    companion object {
        const val HISTORY_LIMIT = 50

        /** P211：一次最多加载这么多条历史（再早的原文还在库里，但界面不无限翻）。 */
        const val MAX_HISTORY_LOAD = 500

        /** P207：按原话找来源消息时取的长度（太长容易被换行/截断破坏）。 */
        const val HIGHLIGHT_NEEDLE_CHARS = 40

        /** 没有会话端口（单测）时的兜底会话 id，与 RoomSessionPort 的默认一致。 */
        const val DEFAULT_SESSION_ID = "default"
    }
}

/** 失败态映射：配置缺失走 Notice，其余走 Error。 */
private fun bannerFor(outcome: ConversationOutcome?): ChatBanner {
    if (outcome == null) return ChatBanner.Error("本次回复未完成，未写入记录")
    val note = outcome.note.orEmpty()
    // P202：配置问题给一个能点的出口，别让用户自己找设置在哪
    if (note.contains("配置缺失")) {
        return ChatBanner.Notice(
            "未配置 API Key：请先在设置里填写",
            BannerAction("去设置"),
        )
    }
    return when (outcome.error) {
        is ChatError.InvalidKey -> ChatBanner.Error(
            "API Key 无效或未配置，请检查设置",
            BannerAction("去设置"),
        )

        is ChatError.Busy -> ChatBanner.Error("请求过于频繁，请稍后再试")
        is ChatError.Network -> ChatBanner.Error("网络中断或连接被关闭，请重试")
        is ChatError.Upstream -> ChatBanner.Error("上游返回错误，请稍后重试")
        null -> ChatBanner.Error("本次回复未完成，未写入记录")
        else -> ChatBanner.Error("本次回复未完成，未写入记录")
    }
}
