package com.lanweixi.feature.chat

/**
 * P211：token 用量记录。
 *
 * 每次对话成功、上游返回了 usage 就记一笔（events 表，type = "usage"）；
 * 设置页拿它汇总「今天 / 本周用掉多少」。实现放在 `:app`（要写库）。
 */
fun interface UsageRecorder {
    suspend fun record(sessionId: String, promptTokens: Int?, completionTokens: Int?)
}
