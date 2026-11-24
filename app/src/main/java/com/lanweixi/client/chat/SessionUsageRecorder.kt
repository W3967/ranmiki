package com.lanweixi.client.chat

import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.repository.EventRepository
import com.lanweixi.core.data.usage.UsageDigest
import com.lanweixi.feature.chat.UsageRecorder

/**
 * P211：把每次对话的用量写进 events 表。
 *
 * payload 就是 "prompt,completion"，设置页按时间窗取出来加总。
 * 用量统计是锦上添花，写失败不该影响对话——调用方已经 runCatching 包着。
 */
class SessionUsageRecorder(
    private val events: EventRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UsageRecorder {

    override suspend fun record(sessionId: String, promptTokens: Int?, completionTokens: Int?) {
        val prompt = promptTokens ?: 0
        val completion = completionTokens ?: 0
        if (prompt == 0 && completion == 0) return
        events.record(
            EventEntity(
                type = UsageDigest.EVENT_TYPE,
                payload = "$prompt,$completion",
                at = clock(),
            ),
        )
    }
}
