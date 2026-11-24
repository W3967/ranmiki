package com.lanweixi.feature.chat

/** P07a：聊天首屏的 UI 状态（不直接暴露 ConversationOutcome）。 */
data class ChatMessageUi(
    val role: String,
    val text: String,
    val createdAt: Long,
    /** P204：消息 id（点引用定位用；0 = 未知）。 */
    val id: Long = 0,
    /** P204：被引用消息 id（0 = 无引用）。 */
    val quoteId: Long = 0,
    /** P204：引用快照「名字：节选」。 */
    val quotePreview: String = "",
)

/** P204：输入框正在引用的一条消息。 */
data class ChatQuote(
    val messageId: Long,
    val speaker: String,
    val preview: String,
)

/**
 * 聊天页状态。
 *
 * P199：删掉了三个只写不读的字段——`relationLabel`（关系标签从没算过）、
 * `sessionId` / `sessions`（会话切换移到会话列表页后没人读）。
 * 会话本身仍由 `SessionPort` 管，`ChatViewModel` 进页面时用它切会话。
 */
data class ChatUiState(
    val history: List<ChatMessageUi> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val partialAssistant: String? = null,
    val banner: ChatBanner? = null,
    /** P24：最近一次成功对话的 token usage（上游未返回时为 null）。 */
    val usage: String? = null,
    /** P141：本轮调用过的工具名，按调用顺序。 */
    val toolTrail: List<String> = emptyList(),
    /** P204：输入框上方正在引用的消息（null = 没引用）。 */
    val quote: ChatQuote? = null,
    /** P204：点引用要定位的消息 id（0 = 无）；定位后由 UI 清除高亮。 */
    val highlightId: Long = 0,
    /** P207：她放进待审核的条数（0 = 不显示入口）。 */
    val pendingDrafts: Int = 0,
    /** P211：当前加载的历史条数（点「加载更早」时翻倍）。 */
    val historyLimit: Int = 50,
)

sealed interface ChatBanner {
    /** P202：带动作的提示（如「未配置 API Key」→ 去设置）。 */
    data class Notice(val text: String, val action: BannerAction? = null) : ChatBanner
    data class Error(val text: String, val action: BannerAction? = null) : ChatBanner
}

/**
 * P202：提示条上的一个动作按钮。
 *
 * 只带文案不带回调——回调由 UI 层接（`onOpenSettings`），
 * ViewModel 仍然不碰导航。
 */
data class BannerAction(val label: String)

/**
 * P206：工具名 → 给用户看的动作描述。
 *
 * 直接显示 `host_propose_task` 用户看不懂；写入类也不能说「查了」。
 * 未知工具（插件工具）原样保留，至少能看出是插件在动。
 */
internal fun toolTrailLabel(name: String): String = when (name) {
    "host_search_notes" -> "查了笔记"
    "host_list_tasks" -> "查了任务"
    "host_search_documents" -> "查了文档"
    "host_list_prompts" -> "查了提示词"
    "host_propose_note" -> "记了一条笔记"
    "host_propose_task" -> "记了一条任务"
    else -> name
}
