package com.lanweixi.client.chat

import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.repository.EventRepository
import com.lanweixi.core.data.usage.UsageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P211：把每次对话的用量写进 events。 */
class SessionUsageRecorderTest {

    private class FakeEvents : EventRepository {
        val rows = mutableListOf<EventEntity>()

        override suspend fun record(event: EventEntity): Long {
            rows += event
            return rows.size.toLong()
        }

        override suspend fun recent(limit: Int) = rows.takeLast(limit).reversed()

        override suspend fun listByTypeBetween(type: String, from: Long, to: Long) =
            rows.filter { it.type == type && it.at in from..to }

        // P217：EventRepository 新增保留策略；这里不需要，占位即可
        override suspend fun trim(before: Long, keepLatest: Int) = Unit
        override suspend fun redactLegacyPayloads() = Unit
    }

    private fun recorder(events: FakeEvents) = SessionUsageRecorder(events, clock = { 9_000L })

    @Test
    fun `writes one usage event`() = runBlocking {
        val events = FakeEvents()

        recorder(events).record("default", 1200, 300)

        val row = events.rows.single()
        assertEquals(UsageDigest.EVENT_TYPE, row.type)
        assertEquals("1200,300", row.payload)
        assertEquals(9_000L, row.at)
    }

    @Test
    fun `missing tokens are written as zero`() = runBlocking {
        val events = FakeEvents()

        recorder(events).record("default", 500, null)

        assertEquals("500,0", events.rows.single().payload)
    }

    @Test
    fun `all-zero usage is skipped`() = runBlocking {
        val events = FakeEvents()

        recorder(events).record("default", null, null)
        recorder(events).record("default", 0, 0)

        assertTrue(events.rows.isEmpty())
    }

    @Test
    fun `stored events sum back to the recorded usage`() = runBlocking {
        val events = FakeEvents()
        val recorder = recorder(events)

        recorder.record("s1", 100, 20)
        recorder.record("s1", 300, 40)

        val totals = UsageDigest.sumOf(events.rows)
        assertEquals(460L, totals.total)
    }
}
