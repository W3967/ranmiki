package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.GroupSession

/**
 * P198：会话列表里的一行——单聊和群聊混排。
 *
 * QQ 式：一个列表里既有和兰未晞的单聊，也有各个群。
 */
data class ConversationItem(
    /** 单聊 = sessionId，群聊 = group id。 */
    val id: String,
    val isGroup: Boolean,
    val title: String,
    /** 单聊 = 首条用户消息（话题标签），群聊 = 最后一条消息或成员说明。 */
    val subtitle: String,
    /** 最后活动时间；0 表示还没有消息。 */
    val lastAt: Long,
    val avatarUri: String? = null,
)

/** 某个会话/群的最后一条消息。 */
data class LastMessage(val text: String, val at: Long)

data class SessionsUiState(
    val items: List<ConversationItem> = emptyList(),
    val loading: Boolean = true,
    val banner: String? = null,
) {
    val empty: Boolean get() = !loading && items.isEmpty()
}

/**
 * 会话列表的数据来源。
 *
 * 生产实现放 `:app`（SessionPort + AgentStore + MessageRepository 拼起来），
 * 单测直接给 fake——不碰 Room，也不碰 DataStore。
 */
interface SessionsSource {
    /** 单聊会话（含 default；会包含群 id，由 ViewModel 过滤）。 */
    suspend fun listChats(): List<SessionSummary>

    suspend fun listGroups(): List<GroupSession>

    /** 该会话/群最后一条消息；没有返回 null。 */
    suspend fun lastMessage(sessionId: String): LastMessage?

    /** 新建单聊会话并切过去，返回新 id。 */
    suspend fun createChat(): String

    /** 删除单聊会话（连同消息）。 */
    suspend fun deleteChat(sessionId: String)

    /** 删除群（群定义 + 该群消息）。 */
    suspend fun deleteGroup(groupId: String)
}
