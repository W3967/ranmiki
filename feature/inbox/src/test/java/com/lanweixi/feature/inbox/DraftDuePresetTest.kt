package com.lanweixi.feature.inbox

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** P207：草稿编辑页的截止快捷档（口径与任务页一致：当天 23:59:59.999）。 */
class DraftDuePresetTest {

    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 11, 24, 10, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private fun endOf(y: Int, m: Int, d: Int): Long =
        LocalDateTime.of(y, m, d, 23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `none means no deadline`() {
        assertEquals(0L, DraftDuePreset.toMillis(DraftDuePreset.NONE, now))
    }

    @Test
    fun `today tomorrow and offsets end at end of day`() {
        assertEquals(endOf(2026, 11, 24), DraftDuePreset.toMillis(DraftDuePreset.TODAY, now))
        assertEquals(endOf(2026, 11, 25), DraftDuePreset.toMillis(DraftDuePreset.TOMORROW, now))
        assertEquals(endOf(2026, 11, 27), DraftDuePreset.toMillis(DraftDuePreset.IN_3_DAYS, now))
        assertEquals(endOf(2026, 12, 1), DraftDuePreset.toMillis(DraftDuePreset.IN_7_DAYS, now))
    }

    @Test
    fun `fromMillis round trips presets`() {
        assertEquals(
            DraftDuePreset.TOMORROW,
            DraftDuePreset.fromMillis(DraftDuePreset.toMillis(DraftDuePreset.TOMORROW, now), now),
        )
    }

    @Test
    fun `fromMillis returns none for exact time`() {
        val exact = LocalDateTime.of(2026, 11, 25, 15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(DraftDuePreset.NONE, DraftDuePreset.fromMillis(exact, now))
    }

    @Test
    fun `labels are human readable`() {
        assertEquals("今天", DraftDuePreset.label(DraftDuePreset.TODAY))
        assertEquals("不设", DraftDuePreset.label(DraftDuePreset.NONE))
        assertEquals(5, DraftDuePreset.order.size)
    }
}
