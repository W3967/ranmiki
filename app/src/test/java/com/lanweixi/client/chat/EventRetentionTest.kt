package com.lanweixi.client.chat

import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.repository.EventRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P217：启动清理用的保留策略（90 天 + 最近 2000 条）。 */
class EventRetentionTest {

    private class FakeEvents : EventRepository {
        var trimBefore: Long? = null
        var keepLatest: Int? = null
        var redacted = false

        override suspend fun record(event: EventEntity) = 0L

        override suspend fun recent(limit: Int) = emptyList<EventEntity>()

        override suspend fun listByTypeBetween(type: String, from: Long, to: Long) =
            emptyList<EventEntity>()

        override suspend fun trim(before: Long, keepLatest: Int) {
            trimBefore = before
            this.keepLatest = keepLatest
        }

        override suspend fun redactLegacyPayloads() {
            redacted = true
        }
    }

    @Test
    fun `prune uses the 90 day window and the 2000 row cap`() = runTest {
        val events = FakeEvents()
        val now = 10_000_000_000L

        EventRetention.prune(events, now = now)

        assertEquals(now - EventRetention.MAX_AGE_MS, events.trimBefore)
        assertEquals(EventRetention.KEEP_LATEST, events.keepLatest)
        assertEquals("90 天", 90L * 24 * 60 * 60 * 1000, EventRetention.MAX_AGE_MS)
    }

    @Test
    fun `prune also redacts legacy user text`() = runTest {
        val events = FakeEvents()

        EventRetention.prune(events, now = 1L)

        assertTrue("旧事件里的用户原文要一起改写", events.redacted)
    }
}
