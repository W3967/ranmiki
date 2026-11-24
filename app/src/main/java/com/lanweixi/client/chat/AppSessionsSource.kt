package com.lanweixi.client.chat

import com.lanweixi.core.data.agent.AgentStore
import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.domain.prompt.GroupSession
import com.lanweixi.feature.chat.LastMessage
import com.lanweixi.feature.chat.SessionPort
import com.lanweixi.feature.chat.SessionSummary
import com.lanweixi.feature.chat.SessionsSource

/**
 * P198：会话列表的数据源实现（:app 层拼装）。
 *
 * 单聊会话来自 `SessionPort`（消息表按 sessionId 聚合），群聊来自 `AgentStore`（DataStore JSON）。
 * 两者唯一的耦合点是**消息表共用**：群消息也存 `messages`，`sessionId = 群 id`，
 * 所以单聊列表必须过滤掉群 id，否则同一个群会出现两次。
 */
class AppSessionsSource(
    private val session: SessionPort,
    private val agents: AgentStore,
    private val messages: MessageRepository,
    /** P215：删会话时把摘要一起带走，不然摘要表会一直堆着已删会话的内容。 */
    private val summaries: com.lanweixi.core.data.repository.SessionSummaryRepository,
) : SessionsSource {

    // 空会话（刚建、还没说话）不补进列表：微信 / QQ 也是这样——一句不说就退出，会话不留痕。
    // 补进来反而会留下一个删不完的空坑（删掉了又因为是 current 冒出来）。
    override suspend fun listChats(): List<SessionSummary> = session.list()

    override suspend fun listGroups(): List<GroupSession> = agents.listGroups()

    override suspend fun lastMessage(sessionId: String): LastMessage? {
        if (sessionId.isBlank()) return null
        val rows = messages.history(sessionId)
        val last = rows.maxByOrNull { it.createdAt } ?: return null
        val text = last.text.replace('\n', ' ').trim()
        if (text.isEmpty()) return null
        return LastMessage(text = text, at = last.createdAt)
    }

    override suspend fun createChat(): String = session.createNew()

    override suspend fun deleteChat(sessionId: String) {
        val wasCurrent = runCatching { session.current() }.getOrNull() == sessionId
        session.delete(sessionId)
        runCatching { summaries.deleteBySession(sessionId) }
        // P215：只有删掉的确实是当前会话才切走。
        // 以前无条件 switch(next)，删一条历史会话也会把用户所在的会话切到别的。
        if (!wasCurrent) return
        // 删掉的正好是当前会话时，别把用户晾在一个空会话上：
        // 切到最近还有消息的那条单聊（群 id 不能当 current，否则单聊会写进群里）。
        val groupIds = agents.listGroups().map { it.id }.toSet()
        val next = session.list()
            .filterNot { it.id in groupIds }
            .maxByOrNull { it.lastAt }
        if (next != null) session.switch(next.id)
    }

    override suspend fun deleteGroup(groupId: String) {
        agents.deleteGroup(groupId)
        messages.deleteSession(groupId)
        runCatching { summaries.deleteBySession(groupId) }
    }
}
