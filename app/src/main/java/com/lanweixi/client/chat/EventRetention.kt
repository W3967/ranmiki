package com.lanweixi.client.chat

import com.lanweixi.core.data.repository.EventRepository

/**
 * P217：events 表保留策略。
 *
 * `events` 是纯追加的审计/用量表：usage（用量统计）会读，`conversation` / `task_done`
 * 目前没有读取方。不清理会一直涨。启动时清一次：
 * - 超过 [MAX_AGE_MS]（90 天）的删掉；
 * - 再只留最近 [KEEP_LATEST] 条（防有人一天写几万条）。
 *
 * 用量统计只看「今天 / 本周」，90 天足够。
 */
object EventRetention {

    const val MAX_AGE_MS: Long = 90L * 24 * 60 * 60 * 1000

    const val KEEP_LATEST: Int = 2_000

    /** 清理失败不影响启动（调用方 runCatching）。 */
    suspend fun prune(events: EventRepository, now: Long = System.currentTimeMillis()) {
        events.trim(before = now - MAX_AGE_MS, keepLatest = KEEP_LATEST)
        // 旧版本 events 里存过用户原文，顺手改写掉（幂等）
        events.redactLegacyPayloads()
    }
}
