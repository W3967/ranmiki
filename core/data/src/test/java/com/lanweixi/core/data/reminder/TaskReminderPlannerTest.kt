package com.lanweixi.core.data.reminder

import com.lanweixi.core.data.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P199：任务提醒判定。 */
class TaskReminderPlannerTest {

    private val now = 1_000_000_000L

    private fun task(
        id: Long,
        title: String = "任务$id",
        dueAt: Long = 0L,
        done: Boolean = false,
    ) = TaskEntity(
        id = id,
        title = title,
        dueAt = dueAt,
        done = done,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `upcoming task within an hour is reminded`() {
        val soon = task(1, dueAt = now + 30 * 60_000)
        val result = TaskReminderPlanner.plan(listOf(soon), emptySet(), now)

        assertEquals(1, result.size)
        assertEquals(false, result.single().overdue)
        assertEquals("任务1", result.single().title)
    }

    @Test
    fun `task later than an hour is silent`() {
        val later = task(1, dueAt = now + 2 * 60 * 60_000)
        assertTrue(TaskReminderPlanner.plan(listOf(later), emptySet(), now).isEmpty())
    }

    @Test
    fun `recently overdue task is reminded as overdue`() {
        val late = task(1, dueAt = now - 10 * 60_000)
        val result = TaskReminderPlanner.plan(listOf(late), emptySet(), now)

        assertEquals(1, result.size)
        assertTrue(result.single().overdue)
    }

    @Test
    fun `task overdue for more than a day is not brought back`() {
        val ancient = task(1, dueAt = now - 3 * 24 * 60 * 60_000L)
        assertTrue(TaskReminderPlanner.plan(listOf(ancient), emptySet(), now).isEmpty())
    }

    @Test
    fun `done task is never reminded`() {
        val done = task(1, dueAt = now + 10 * 60_000, done = true)
        assertTrue(TaskReminderPlanner.plan(listOf(done), emptySet(), now).isEmpty())
    }

    @Test
    fun `task without due date is skipped`() {
        assertTrue(TaskReminderPlanner.plan(listOf(task(1)), emptySet(), now).isEmpty())
    }

    @Test
    fun `already notified task is not repeated`() {
        val soon = task(1, dueAt = now + 10 * 60_000)
        assertTrue(TaskReminderPlanner.plan(listOf(soon), setOf(TaskReminderPlanner.keyOf(1, soon.dueAt)), now).isEmpty())
    }

    @Test
    fun `multiple reminders are sorted by due time`() {
        val late = task(1, title = "晚", dueAt = now + 50 * 60_000)
        val early = task(2, title = "早", dueAt = now + 5 * 60_000)
        val overdue = task(3, title = "过", dueAt = now - 5 * 60_000)

        val result = TaskReminderPlanner.plan(listOf(late, early, overdue), emptySet(), now)

        assertEquals(listOf("过", "早", "晚"), result.map { it.title })
    }

    @Test
    fun `blank title falls back to placeholder`() {
        val blank = task(1, title = "  ", dueAt = now + 60_000)
        assertEquals("无标题任务", TaskReminderPlanner.plan(listOf(blank), emptySet(), now).single().title)
    }

    @Test
    fun `keys of deleted tasks are pruned`() {
        val alive = TaskReminderPlanner.keyOf(1, 1000L)
        val rescheduledOld = TaskReminderPlanner.keyOf(1, 500L)
        val deleted = TaskReminderPlanner.keyOf(9, 1000L)
        val garbage = "not-a-key"

        val kept = TaskReminderPlanner.pruneKeys(
            notified = setOf(alive, rescheduledOld, deleted, garbage),
            openTaskIds = setOf(1L),
        )

        assertTrue(alive in kept)
        assertTrue("改期的旧键留着无害", rescheduledOld in kept)
        assertTrue("删掉的任务应该清掉", deleted !in kept)
        assertTrue("解析不了的键也清掉", garbage !in kept)
    }
}
