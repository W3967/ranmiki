package com.lanweixi.core.data.tools

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * P206：把「人话截止时间」换算成毫秒时间戳。
 *
 * 为什么不让模型自己算：真机实测说「明天下午三点」，模型给出的毫秒值是**三天后**。
 * 日期算术对模型不可靠，宿主手里有真实时钟，换算是确定性的。
 *
 * 接受的写法：
 * - `today` / `明天` / `tomorrow`（+ 可选时刻，如 `tomorrow 15:00`）
 * - `2026-11-25` / `2026/11/25`（默认当天 23:59:59.999）
 * - `2026-11-25 15:00`
 * - 纯数字（秒或毫秒时间戳，兼容模型直接给时间戳）
 *
 * 解析不出来返回 0（= 不设截止），不抛异常。
 */
internal object DueTimeParser {

    private val DATE = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.US)
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-M-d H:mm", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("H:mm", Locale.US)
    private val END_OF_DAY: LocalTime = LocalTime.of(23, 59, 59, 999_000_000)

    fun parse(raw: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val text = raw.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return 0L
        text.toLongOrNull()?.let { return normalizeEpochMillis(it) }

        // 先试完整的「日期 时间」，再退回「只有日期」
        runCatching { LocalDateTime.parse(text.replace('/', '-'), DATE_TIME) }
            .getOrNull()
            ?.let { return it.atZone(zone).toInstant().toEpochMilli() }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val (dayText, timeText) = splitDayAndTime(text)
        val date = when (dayText) {
            "today", "今天" -> today
            "tomorrow", "明天" -> today.plusDays(1)
            else -> parseDate(dayText) ?: return 0L
        }
        val time = timeText?.let(::parseTime) ?: END_OF_DAY
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** 把「日期 [时间]」拆开；没有空格时整段都当日期。 */
    private fun splitDayAndTime(text: String): Pair<String, String?> {
        val normalized = text.replace('/', '-').trim()
        val space = normalized.indexOf(' ')
        return if (space < 0) {
            normalized to null
        } else {
            normalized.substring(0, space) to normalized.substring(space + 1).trim()
        }
    }

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw, DATE) }.getOrNull()

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw, TIME) }.getOrNull()
            ?: runCatching { LocalTime.parse(raw) }.getOrNull()
}

/**
 * 秒 / 毫秒时间戳归一化。
 *
 * 1e9 ~ 4e9 只可能是秒（2001–2096），乘 1000；其余按毫秒处理。
 */
internal fun normalizeEpochMillis(raw: Long): Long {
    if (raw <= 0L) return 0L
    return if (raw in 1_000_000_000L..4_000_000_000L) raw * 1000L else raw
}
