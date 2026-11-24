package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.repository.RoomEventRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P217：events 清理策略（真 Room）。
 *
 * 之前 events 只增不删：usage 每轮写一条，`conversation` / `task_done` 也没人清。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventTrimTest {

    private lateinit var db: LanWeixiDatabase
    private lateinit var events: RoomEventRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        events = RoomEventRepository(db.eventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(count: Int, startAt: Long = 1L) {
        repeat(count) { i ->
            events.record(EventEntity(type = "usage", payload = "1,1", at = startAt + i))
        }
    }

    @Test
    fun `trim removes events older than the cutoff`() = runBlocking {
        events.record(EventEntity(type = "old", payload = "x", at = 100))
        events.record(EventEntity(type = "new", payload = "y", at = 5_000))

        events.trim(before = 1_000, keepLatest = 100)

        assertEquals(1, db.eventDao().count())
        assertEquals("new", db.eventDao().recent(10).single().type)
    }

    @Test
    fun `trim keeps only the latest N rows`() = runBlocking {
        seed(count = 2_500, startAt = 1L)

        events.trim(before = 0, keepLatest = 2_000)

        assertEquals(2_000, db.eventDao().count())
        // 最新那条必须还在（保尾不保头）
        assertEquals(2_500L, db.eventDao().recent(1).single().at)
    }

    @Test
    fun `trim keeps everything when below both limits`() = runBlocking {
        seed(count = 10, startAt = 5_000)

        events.trim(before = 1_000, keepLatest = 2_000)

        assertEquals(10, db.eventDao().count())
    }

    @Test
    fun `trim on empty table is a no-op`() = runBlocking {
        events.trim(before = 1_000, keepLatest = 2_000)

        assertEquals(0, db.eventDao().count())
    }

    // ── P217：历史事件里的用户原文脱敏 ───────────────────

    @Test
    fun `redact replaces user text in legacy events`() = runBlocking {
        events.record(EventEntity(type = "conversation", payload = "今晚吃什么", at = 1))
        events.record(EventEntity(type = "task_done", payload = "写周报", at = 2))
        events.record(EventEntity(type = "usage", payload = "10,2", at = 3))

        events.redactLegacyPayloads()

        val rows = db.eventDao().recent(10).associateBy { it.type }
        assertEquals("chars=5", rows["conversation"]!!.payload)
        assertEquals("chars=3", rows["task_done"]!!.payload)
        assertEquals("usage 的 payload 不动", "10,2", rows["usage"]!!.payload)
    }

    @Test
    fun `redact is idempotent`() = runBlocking {
        events.record(EventEntity(type = "conversation", payload = "hello", at = 1))

        events.redactLegacyPayloads()
        events.redactLegacyPayloads()

        assertEquals("chars=5", db.eventDao().recent(1).single().payload)
    }
}
