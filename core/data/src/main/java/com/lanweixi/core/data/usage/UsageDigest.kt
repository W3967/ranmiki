package com.lanweixi.core.data.usage

import com.lanweixi.core.data.entity.EventEntity

/**
 * P211：token 用量汇总。
 *
 * 每次对话成功写一条 `events(type="usage", payload="prompt,completion")`；
 * 这里只做「把一串事件加总」的纯计算，展示层拿去显示「今天 / 本周」。
 */
object UsageDigest {

    /** usage 事件的类型名。 */
    const val EVENT_TYPE = "usage"

    data class Totals(val promptTokens: Long = 0L, val completionTokens: Long = 0L) {
        val total: Long get() = promptTokens + completionTokens
        val isEmpty: Boolean get() = total == 0L
    }

    /** 把 payload 解析成 (prompt, completion)；格式不对就当 0。 */
    fun parsePayload(payload: String): Pair<Int, Int> {
        val parts = payload.split(',', limit = 2)
        val prompt = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val completion = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        return prompt to completion
    }

    fun sumOf(events: List<EventEntity>): Totals {
        var prompt = 0L
        var completion = 0L
        events.forEach { event ->
            if (event.type != EVENT_TYPE) return@forEach
            val (p, c) = parsePayload(event.payload)
            prompt += p
            completion += c
        }
        return Totals(prompt, completion)
    }

    /** 「1.2k」这种给人看的短格式。 */
    fun format(tokens: Long): String = when {
        tokens <= 0L -> "0"
        tokens < 1_000L -> tokens.toString()
        tokens < 1_000_000L -> "%.1fk".format(tokens / 1000.0)
        else -> "%.1fM".format(tokens / 1_000_000.0)
    }
}
