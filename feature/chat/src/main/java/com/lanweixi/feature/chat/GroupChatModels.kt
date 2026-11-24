package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.AgentProfile

/**
 * P197：群聊的发言者标识。
 *
 * 存进消息表的 `speaker` 字段：
 *  - 空串 = 用户（与单聊历史一致，不引入新值）
 *  - [HOST] = 兰未晞（主持人，固定在每个群）
 *  - 其他 = 工具人的 agent id
 */
object GroupSpeaker {

    const val USER = ""
    const val HOST = "__host__"

    fun isUser(id: String): Boolean = id == USER
    fun isHost(id: String): Boolean = id == HOST
}

/** 群聊里的一条消息（端口层 DTO，不依赖 Room 实体）。 */
data class GroupMessage(
    val speaker: String,
    val text: String,
    val createdAt: Long,
    /** P205：消息 id（点引用定位用；0 = 未知）。 */
    val id: Long = 0,
    /** P205：被引用消息 id（0 = 无引用）。 */
    val quoteId: Long = 0,
    /** P205：引用快照「名字：节选」。 */
    val quotePreview: String = "",
)

/**
 * 群聊消息读写端口。
 *
 * 会话隔离靠消息表已有的 `sessionId`（存群 id），发言者靠 `speaker`——
 * 两者都是现成字段，**不需要数据库迁移**。
 */
interface GroupChatPort {
    /** 按时间升序取该群最近 [limit] 条。 */
    suspend fun history(groupId: String, limit: Int): List<GroupMessage>

    /** 追加一条；[speaker] 见 [GroupSpeaker]。P205：可带引用快照。 */
    suspend fun append(
        groupId: String,
        speaker: String,
        text: String,
        quoteId: Long = 0,
        quotePreview: String = "",
    )
}

/** 群成员（UI 用）：兰未晞 + 若干工具人。 */
data class GroupMember(
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    val color: String = "#7C8CF6",
    val isHost: Boolean = false,
    /** 工具人没填 API Key 时点它不会回复，界面上要明确提示。 */
    val configured: Boolean = true,
)

/** 一次群聊发言的结果。 */
data class GroupSendResult(
    val completed: Boolean,
    /** 实际回话的人：agent id 或 [GroupSpeaker.HOST]。 */
    val speakerId: String,
    val speakerName: String,
    val reply: String,
    /** 面向用户的失败原因；成功为 null。 */
    val error: String? = null,
)

/**
 * 群聊发送用例（ViewModel 只依赖这个接口，便于 JVM 单测注入 fake）。
 *
 * 发言权归用户：不 @ → 兰未晞接；@ 谁 → 只调谁的 API。没有自动调度。
 */
interface GroupChatUseCase {
    suspend fun send(
        groupId: String,
        /** 当前群成员快照（发送时现取，改名/新增立即生效）。 */
        agents: List<AgentProfile>,
        userText: String,
        /** @ 的人的 agent id；null = 不 @，由兰未晞接。 */
        targetAgentId: String?,
        onDelta: (String) -> Unit = {},
    ): GroupSendResult

    /**
     * P203/P204：一次 @ 多人。
     *
     * 按 [targetAgentIds] 的顺序逐个调用；用户消息只落一次，
     * 前面人的回复会进后面人的上下文（像群里先后说话）。
     */
    suspend fun sendToMany(
        groupId: String,
        agents: List<AgentProfile>,
        userText: String,
        /** 群成员 id；[GroupSpeaker.HOST] 表示兰未晞。 */
        targetAgentIds: List<String>,
        onDelta: (speakerId: String, partial: String) -> Unit = { _, _ -> },
        /** P205：引用快照（落库存快照、提示词里拼一行）。 */
        quoteId: Long = 0,
        quotePreview: String = "",
    ): List<GroupSendResult>
}

/** 群聊页 UI 状态。 */
data class GroupChatUiState(
    val title: String = "",
    val members: List<GroupMember> = emptyList(),
    val messages: List<GroupMessage> = emptyList(),
    val draft: String = "",
    /** 当前 @ 的成员 id；空 = 不 @，由兰未晞接。 */
    val targetIds: Set<String> = emptySet(),
    val sending: Boolean = false,
    /** 正在流式回话的人（未发言时为 null）。 */
    val partialSpeakerId: String? = null,
    val partialText: String? = null,
    val banner: ChatBanner? = null,
    /** P205：输入框上方正在引用的消息。 */
    val quote: ChatQuote? = null,
    /** P205：点引用要定位的消息 id（0 = 无）。 */
    val highlightId: Long = 0,
) {
    /** 按成员列表顺序取被 @ 的人（顺序稳定，与点击先后无关）。 */
    val targets: List<GroupMember>
        get() = members.filter { it.id in targetIds }

    /** 输入框提示语，让用户一眼知道这条发给谁。 */
    val targetLabel: String
        get() = if (targetIds.isEmpty()) {
            members.firstOrNull { it.isHost }?.name ?: "兰未晞"
        } else {
            targets.joinToString("、") { it.name }.ifBlank { "兰未晞" }
        }
}
