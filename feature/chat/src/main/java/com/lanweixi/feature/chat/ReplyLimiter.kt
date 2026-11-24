package com.lanweixi.feature.chat

/**
 * P67：回复条数硬规则（文档《生命状态 v2》8.2）。
 *
 * 文档要求"回复条数（硬规则，不只是提示词）"：`reply_target` 是这次可以连发的条数，
 * 代码必须兜住，不能只靠模型自觉。
 *
 * 做法：按换行分条；超过 target 只保留前 target 条。
 * - target <= 0：不限制（未取到状态时）
 * - 回复只有一条（没有换行）：原样返回，不做拆分
 */
object ReplyLimiter {

    /** 最多保留多少条（用于日志/测试）。 */
    fun count(reply: String): Int =
        reply.split('\n').count { it.trim().isNotEmpty() }

    fun limit(reply: String, target: Int): String {
        if (target <= 0) return reply
        val parts = reply.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return reply
        if (parts.size <= target) return reply
        return parts.take(target).joinToString("\n")
    }
}
