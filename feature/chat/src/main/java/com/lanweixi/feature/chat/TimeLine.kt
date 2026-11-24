package com.lanweixi.feature.chat

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * P74：现实时间（文档《生命状态 v2》第 7 章「现在的真实时间」）。
 *
 * 让角色具备时间观念：
 *  - 日期 + 星期
 *  - 时段（凌晨 / 早上 / 上午 / 中午 / 下午 / 傍晚 / 晚上 / 深夜）
 *  - 时刻
 *  - 距上次对话多久（可选，由调用方传入）
 */
object TimeLine {

    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun period(hour: Int): String = when (hour) {
        in 0..4 -> "凌晨"
        5 -> "清晨"
        in 6..8 -> "早上"
        in 9..10 -> "上午"
        in 11..12 -> "中午"
        in 13..16 -> "下午"
        in 17..18 -> "傍晚"
        in 19..22 -> "晚上"
        else -> "深夜"
    }

    fun now(
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = ZonedDateTime.now(zone).toLocalDateTime(),
    ): String {
        val weekday = weekdays.getOrElse(now.dayOfWeek.value - 1) { "" }
        val p = period(now.hour)
        return "%04d-%02d-%02d %s %s%s".format(
            now.year, now.monthValue, now.dayOfMonth, weekday, p, now.format(fmt),
        )
    }

    /** 距上次对话的粗略描述（给"隔了很久"这种语感）。 */
    fun sinceLast(seconds: Long): String {
        if (seconds <= 0) return ""
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "刚刚才说过话"
            minutes < 60 -> "隔了 $minutes 分钟"
            hours < 24 -> "隔了 $hours 小时"
            else -> "隔了 $days 天"
        }
    }
}
