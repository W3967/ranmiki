package com.lanweixi.feature.review

import com.lanweixi.core.data.dao.ReviewSnapshot
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.TaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P199：最近 4 周趋势（周切分 + 统计）。 */
class ReviewTrendTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millis(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun snapshot(from: Long, to: Long, tasksDone: Int, notes: Int) = ReviewSnapshot(
        periodStart = from,
        periodEnd = to,
        notes = List(notes) { index ->
            NoteEntity(
                id = index + 1L,
                title = "笔记$index",
                content = "",
                createdAt = from,
                updatedAt = from,
            )
        },
        tasksCreated = emptyList(),
        tasksDone = List(tasksDone) { index ->
            TaskEntity(
                id = 100L + index,
                title = "任务$index",
                done = true,
                doneAt = from,
                createdAt = from,
                updatedAt = from,
            )
        },
        prompts = emptyList(),
        documents = emptyList(),
    )

    @Test
    fun `trend has four weeks ending with this week`() = runTest {
        val now = millis(2026, 11, 24, 15)
        val calls = mutableListOf<Long>()

        val trend = buildWeeklyTrend(now) { from, to ->
            calls += from
            snapshot(from, to, tasksDone = 1, notes = 1)
        }

        assertEquals(4, trend.size)
        assertEquals("本周", trend.last().label)
        assertEquals(4, calls.size)
        // 本周从周一零点开始
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(monday.atStartOfDay(zone).toInstant().toEpochMilli(), trend.last().start)
        // 一周一个点，依次往前推
        assertEquals(monday.minusWeeks(1).format(DateTimeFormatter.ofPattern("M/d")), trend[2].label)
        assertEquals(monday.minusWeeks(3).format(DateTimeFormatter.ofPattern("M/d")), trend[0].label)
    }

    @Test
    fun `point counts come from the snapshot`() = runTest {
        val now = millis(2026, 11, 24, 15)
        var weekIndex = 0

        val trend = buildWeeklyTrend(now) { from, to ->
            weekIndex++
            // 越靠近现在产出越多
            snapshot(from, to, tasksDone = weekIndex, notes = weekIndex * 2)
        }

        assertEquals(listOf(1, 2, 3, 4), trend.map { it.tasksDone })
        assertEquals(listOf(2, 4, 6, 8), trend.map { it.notes })
    }

    @Test
    fun `zero weeks yields an empty trend`() = runTest {
        val trend = buildWeeklyTrend(millis(2026, 11, 24), weeks = 0) { from, to ->
            snapshot(from, to, tasksDone = 0, notes = 0)
        }
        assertTrue(trend.isEmpty())
    }

    @Test
    fun `week window ends on sunday end of day`() = runTest {
        val now = millis(2026, 11, 24, 15)
        val trend = buildWeeklyTrend(now) { from, to -> snapshot(from, to, 0, 0) }

        val last = trend.last()
        val endDate = Instant.ofEpochMilli(last.end).atZone(zone).toLocalDate()
        val startDate = Instant.ofEpochMilli(last.start).atZone(zone).toLocalDate()
        assertEquals(DayOfWeek.MONDAY, startDate.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, endDate.dayOfWeek)
        assertEquals(LocalDate.from(startDate).plusDays(6), endDate)
    }
}
