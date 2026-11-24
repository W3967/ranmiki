package com.lanweixi.client.chat

import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.feature.chat.ChatHistoryPort
import com.lanweixi.feature.chat.HistoryMessage

/**
 * P06c：ChatHistoryPort 的 Room 实现（放在 :app，避免 :core:data ↔ :feature:chat 成环）。
 *
 * - 默认 sessionId = "default"
 * - 成功消息 status = "sent"、speaker = ""
 * - createdAt 用当前毫秒
 * - 历史按 createdAt 升序取最近 limit 条
 */
class RoomChatHistoryPort(
    private val messages: MessageRepository,
    private val session: com.lanweixi.feature.chat.SessionPort,
    private val defaultSessionId: String = DEFAULT_SESSION_ID,
) : ChatHistoryPort {

    companion object {
        const val DEFAULT_SESSION_ID = "default"
        const val STATUS_SENT = "sent"
    }

    override suspend fun append(
        role: String,
        content: String,
        sessionId: String,
        status: String,
        quoteId: Long,
        quotePreview: String,
    ) {
        val target = sessionId.ifBlank { defaultSessionId }
        messages.append(
            MessageEntity(
                role = role,
                speaker = "",
                text = content,
                status = status.ifBlank { STATUS_SENT },
                createdAt = System.currentTimeMillis(),
                sessionId = target,
                quoteId = quoteId,
                quotePreview = quotePreview,
            )
        )
    }

    /** P105：跨会话取最近消息（盲评用）。 */
    override suspend fun recentAll(limit: Int): List<HistoryMessage> =
        messages.recentAll(limit.coerceAtLeast(1))
            .map {
                HistoryMessage(
                    role = it.role,
                    content = it.text,
                    createdAt = it.createdAt,
                    id = it.id,
                    quotePreview = it.quotePreview,
                    quoteId = it.quoteId,
                )
            }

    override suspend fun recent(limit: Int): List<HistoryMessage> {
        val target = session.current()
        val rows = messages.history(target)
            .sortedBy { it.createdAt }
            .takeLast(limit.coerceAtLeast(0))
        return rows.map {
            HistoryMessage(
                role = it.role,
                content = it.text,
                createdAt = it.createdAt,
                id = it.id,
                quotePreview = it.quotePreview,
                quoteId = it.quoteId,
            )
        }
    }
}
