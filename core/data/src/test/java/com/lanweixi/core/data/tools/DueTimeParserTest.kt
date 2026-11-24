package com.lanweixi.core.data.tools

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P206：截止时间解析。
 *
 * 真机踩过的坑：模型把「明天下午三点」算成三天后，所以换算必须由宿主做，
 * 这几条就是钉住换算口径的。
 */
class DueTimeParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 24, 19, 4, 36).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0, ms: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min, s, ms * 1_000_000).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `tomorrow with explicit hour`() {
        assertEquals(at(2026, 11, 25, 15, 0), DueTimeParser.parse("tomorrow 15:00", now, zone))
        assertEquals(at(2026, 11, 25, 15, 0), DueTimeParser.parse("明天 15:00", now, zone))
    }

    @Test
    fun `tomorrow alone means end of day`() {
        assertEquals(at(2026, 11, 25, 23, 59, 59, 999), DueTimeParser.parse("tomorrow", now, zone))
        assertEquals(at(2026, 11, 25, 23, 59, 59, 999), DueTimeParser.parse("明天", now, zone))
    }

    @Test
    fun `today ends at end of day`() {
        assertEquals(at(2026, 11, 24, 23, 59, 59, 999), DueTimeParser.parse("today", now, zone))
    }

    @Test
    fun `absolute date without time ends at end of day`() {
        assertEquals(at(2026, 12, 1, 23, 59, 59, 999), DueTimeParser.parse("2026-12-01", now, zone))
        assertEquals(at(2026, 12, 1, 23, 59, 59, 999), DueTimeParser.parse("2026/12/01", now, zone))
    }

    @Test
    fun `absolute date with time`() {
        assertEquals(at(2026, 12, 1, 8, 30), DueTimeParser.parse("2026-12-01 08:30", now, zone))
    }

    @Test
    fun `epoch seconds are normalized to millis`() {
        assertEquals(1_800_000_000_000L, DueTimeParser.parse("1800000000", now, zone))
    }

    @Test
    fun `garbage and blank mean no deadline`() {
        assertEquals(0L, DueTimeParser.parse("", now, zone))
        assertEquals(0L, DueTimeParser.parse("   ", now, zone))
        assertEquals(0L, DueTimeParser.parse("下个月某个时候", now, zone))
    }

    @Test
    fun `normalize handles millis and seconds`() {
        assertEquals(0L, normalizeEpochMillis(0L))
        assertEquals(1_800_000_000_000L, normalizeEpochMillis(1_800_000_000L))
        assertEquals(1_800_000_000_000L, normalizeEpochMillis(1_800_000_000_000L))
    }
}
