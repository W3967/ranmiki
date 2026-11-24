package com.lanweixi.core.data.task

import com.lanweixi.core.data.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId

/**
 * P208：给对话动态段用的任务摘要。
 *
 * 她说「今天要做…」「上次那个做完了」时得有据可依。只挑两类：
 * - 今天到期且没完成的（今天要做）
 * - 最近 [DONE_WINDOW_DAYS] 天完成的（最近完成）
 *
 * 不塞全量清单：动态段预算 300 token，任务一多就没边了。
 */
object TaskDigest {

    private const val DONE_WINDOW_DAYS = 3L
    private const val MAX_PER_GROUP = 3
    private const val MAX_TITLE_CHARS = 18

    fun build(
        open: List<TaskEntity>,
        done: List<TaskEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val dueToday = open
            .filter { !it.done && it.dueAt in dayStart until dayEnd }
            .take(MAX_PER_GROUP)
            .map { clip(it.title) }

        val doneWindow = now - DONE_WINDOW_DAYS * 24 * 3600_000L
        val recentlyDone = done
            .filter { it.doneAt in doneWindow..now }
            .sortedByDescending { it.doneAt }
            .take(MAX_PER_GROUP)
            .map { clip(it.title) }

        if (dueToday.isEmpty() && recentlyDone.isEmpty()) return null

        return buildString {
            if (dueToday.isNotEmpty()) append("今天要做：").append(dueToday.joinToString("、"))
            if (recentlyDone.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append("最近完成：").append(recentlyDone.joinToString("、"))
            }
            append("。")
        }
    }

    private fun clip(title: String): String {
        val text = title.trim().ifBlank { "无标题" }
        return if (text.length > MAX_TITLE_CHARS) text.take(MAX_TITLE_CHARS) + "…" else text
    }
}
