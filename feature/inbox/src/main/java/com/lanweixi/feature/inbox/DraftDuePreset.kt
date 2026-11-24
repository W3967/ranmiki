package com.lanweixi.feature.inbox

import java.time.Instant
import java.time.ZoneId

/**
 * P207：草稿编辑页的截止时间快捷档。
 *
 * 与 `feature:tasks` 的 `DuePreset` 口径一致（当天 23:59:59.999），
 * 但不跨模块引用——feature 之间不互相依赖。
 */
object DraftDuePreset {
    const val NONE = "none"
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val IN_3_DAYS = "in3"
    const val IN_7_DAYS = "in7"

    /** P207：原截止时间不在快捷档里（模型给过精确时间），保留原值。 */
    const val EXACT = "exact"

    val order = listOf(NONE, TODAY, TOMORROW, IN_3_DAYS, IN_7_DAYS)

    fun label(preset: String): String = when (preset) {
        TODAY -> "今天"
        TOMORROW -> "明天"
        IN_3_DAYS -> "3 天后"
        IN_7_DAYS -> "7 天后"
        else -> "不设"
    }

    /** 换算成当天 23:59:59.999 的时间戳；NONE 返回 0。 */
    fun toMillis(preset: String, now: Long = System.currentTimeMillis()): Long {
        if (preset == NONE) return 0L
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = when (preset) {
            TODAY -> 0L
            TOMORROW -> 1L
            IN_3_DAYS -> 3L
            IN_7_DAYS -> 7L
            else -> return 0L
        }
        return today.plusDays(days).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    }

    /** 反查：给定时间戳落在哪一档（编辑页回显用）。 */
    fun fromMillis(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return NONE
        return order.firstOrNull { it != NONE && toMillis(it, now) == millis } ?: NONE
    }
}
