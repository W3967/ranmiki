package com.lanweixi.client.chat

import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.feature.chat.GroupChatPort
import com.lanweixi.feature.chat.GroupMessage
import com.lanweixi.feature.chat.GroupSpeaker

/**
 * P197：群聊端口在 :app 的 Room 实现。
 *
 * 不新建表：消息表早就有 `sessionId`（存群 id）与 `speaker`（存发言者）。
 * 角色的映射与单聊一致——用户是 `user`，其他人都是 `assistant`；
 * 具体是谁说的看 `speaker`，所以群聊历史不会和单聊串。
 */
class RoomGroupChatPort(
    private val messages: MessageRepository,
) : GroupChatPort {

    override suspend fun history(groupId: String, limit: Int): List<GroupMessage> {
        if (groupId.isBlank() || limit <= 0) return emptyList()
        return messages.history(groupId)
            .sortedBy { it.createdAt }
            .takeLast(limit)
            .map {
                GroupMessage(
                    speaker = it.speaker,
                    text = it.text,
                    createdAt = it.createdAt,
                    id = it.id,
                    quoteId = it.quoteId,
                    quotePreview = it.quotePreview,
                )
            }
    }

    override suspend fun append(
        groupId: String,
        speaker: String,
        text: String,
        quoteId: Long,
        quotePreview: String,
    ) {
        if (groupId.isBlank() || text.isBlank()) return
        messages.append(
            MessageEntity(
                role = if (GroupSpeaker.isUser(speaker)) "user" else "assistant",
                speaker = speaker,
                text = text,
                status = STATUS_SENT,
                createdAt = System.currentTimeMillis(),
                sessionId = groupId,
                quoteId = quoteId,
                quotePreview = quotePreview,
            ),
        )
    }

    private companion object {
        const val STATUS_SENT = "sent"
    }
}
