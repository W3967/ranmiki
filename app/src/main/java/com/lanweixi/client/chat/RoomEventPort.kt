package com.lanweixi.client.chat

import com.lanweixi.core.data.dao.EventDao
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.feature.chat.EventPort

/** P25：EventPort 的生产实现（events 表写入）。 */
class RoomEventPort(
    private val dao: EventDao,
) : EventPort {

    override suspend fun append(type: String, payload: String, at: Long) {
        dao.insert(EventEntity(type = type, payload = payload, at = at))
    }
}
