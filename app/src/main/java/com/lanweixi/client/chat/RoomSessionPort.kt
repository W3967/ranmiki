package com.lanweixi.client.chat

import com.lanweixi.core.data.dao.KvDao
import com.lanweixi.core.data.dao.MessageDao
import com.lanweixi.core.data.entity.KvEntity
import com.lanweixi.feature.chat.SessionPort
import com.lanweixi.feature.chat.SessionSummary

/** P37：会话端口的生产实现（kv 表存当前会话，messages 表聚合会话列表）。 */
class RoomSessionPort(
    private val kv: KvDao,
    private val messages: MessageDao,
) : SessionPort {

    override suspend fun current(): String =
        kv.get(KEY_ACTIVE)?.takeIf { it.isNotBlank() } ?: DEFAULT_SESSION

    override suspend fun switch(id: String) {
        kv.put(KvEntity(key = KEY_ACTIVE, value = id))
    }

    override suspend fun list(): List<SessionSummary> =
        messages.sessionDigest().map {
            SessionSummary(
                id = it.sessionId,
                messageCount = it.messageCount,
                preview = it.preview.orEmpty(),
                lastAt = it.lastAt,
            )
        }

    override suspend fun delete(id: String) {
        messages.deleteBySession(id)
        if (current() == id) switch(DEFAULT_SESSION)
    }

    override suspend fun createNew(): String {
        val id = "s${System.currentTimeMillis()}"
        switch(id)
        return id
    }

    companion object {
        const val DEFAULT_SESSION = "default"
        private const val KEY_ACTIVE = "active_session"
    }
}
