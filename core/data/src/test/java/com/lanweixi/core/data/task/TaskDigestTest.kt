package com.lanweixi.core.data.task

import com.lanweixi.core.data.entity.TaskEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P208：对话动态段的任务摘要。 */
class TaskDigestTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 24, 10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(daysFromToday: Long, hour: Int = 15): Long =
        LocalDateTime.of(2026, 11, 24, hour, 0).plusDays(daysFromToday)
            .atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        title: String,
        dueAt: Long = 0L,
        done: Boolean = false,
        doneAt: Long = 0L,
    ) = TaskEntity(
        id = id,
        title = title,
        dueAt = dueAt,
        done = done,
        doneAt = doneAt,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `lists tasks due today`() {
        val digest = TaskDigest.build(
            open = listOf(task(1, "交周报", dueAt = at(0)), task(2, "开会", dueAt = at(0, 18))),
            done = emptyList(),
            now = now,
        )

        assertEquals("今天要做：交周报、开会。", digest)
    }

    @Test
    fun `ignores tasks due other days`() {
        val digest = TaskDigest.build(
            open = listOf(task(1, "昨天的事", dueAt = at(-1)), task(2, "明天的", dueAt = at(1))),
            done = emptyList(),
            now = now,
        )

        assertNull(digest)
    }

    @Test
    fun `lists recently completed and joins with semicolon`() {
        val digest = TaskDigest.build(
            open = listOf(task(1, "交周报", dueAt = at(0))),
            done = listOf(task(2, "买牛奶", done = true, doneAt = now)),
            now = now,
        )

        assertEquals("今天要做：交周报；最近完成：买牛奶。", digest)
    }

    @Test
    fun `ignores completions older than three days`() {
        val fourDaysAgo = now - 4L * 24 * 3600_000L
        val digest = TaskDigest.build(
            open = emptyList(),
            done = listOf(task(1, "很久以前", done = true, doneAt = fourDaysAgo)),
            now = now,
        )

        assertNull(digest)
    }

    @Test
    fun `caps at three titles per group`() {
        val open = (1..5).map { task(it.toLong(), "任务$it", dueAt = at(0)) }
        val done = (6..9).map { task(it.toLong(), "完成$it", done = true, doneAt = now) }

        val digest = TaskDigest.build(open = open, done = done, now = now)!!

        assertTrue("最多三条今天到期", digest.contains("今天要做：任务1、任务2、任务3"))
        assertTrue(!digest.contains("任务4"))
        assertTrue("最多三条最近完成", digest.contains("最近完成：完成6、完成7、完成8"))
        assertTrue(!digest.contains("完成9"))
    }

    @Test
    fun `clips long titles`() {
        val digest = TaskDigest.build(
            open = listOf(task(1, "这是一条非常非常长的任务标题需要被截断处理掉", dueAt = at(0))),
            done = emptyList(),
            now = now,
        )!!

        assertTrue(digest.contains("…"))
        assertEquals("今天要做：" + "这是一条非常非常长的任务标题需要被截断处理掉".take(18) + "…。", digest)
    }
}
