package com.lanweixi.core.data.summary

/**
 * P210：上下文压缩的判定与切分（纯逻辑，可单测）。
 *
 * 规则：
 * - 未压缩的新消息超过 [THRESHOLD] 条才压；
 * - 最近 [KEEP_TAIL] 条永远不压（原样进上下文，保证接得上）；
 * - 一次最多压 [MAX_COMPRESS_BATCH] 条，剩下的下次再压（`untilId` 严格递增，
 *   不会出现「中间一段永远进不了摘要」）；
 * - 摘要与「最近 8 条」一起喂模型，所以摘要是**滚动**的：
 *   每次都把「旧摘要 + 新一段」重新压一遍。
 */
object SummaryPlanner {

    /** 未压缩消息超过这么多条才触发。 */
    const val THRESHOLD = 30

    /** 最近这么多条原样保留。 */
    const val KEEP_TAIL = 8

    /** 单次最多压缩多少条（控制摘要请求的输入长度）。 */
    const val MAX_COMPRESS_BATCH = 40

    /** 摘要正文上限（字符），超出截断。 */
    const val MAX_SUMMARY_CHARS = 240

    /** 一条待压缩的消息。 */
    data class Candidate(val id: Long, val role: String, val text: String)

    data class Plan(
        /** 这次要压进摘要的消息（按时间正序）。 */
        val toCompress: List<Candidate>,
        /** 压缩到哪条消息（含）；下一轮从这里之后开始。 */
        val untilId: Long,
        /** 更新后的覆盖总条数。 */
        val coveredCount: Int,
    )

    /**
     * @param history 该会话的全部消息（按时间正序，id 递增）
     * @param lastUntilId 已有摘要覆盖到的消息 id（0 = 还没有摘要）
     * @param lastCoveredCount 已有摘要覆盖的总条数
     */
    fun plan(
        history: List<Candidate>,
        lastUntilId: Long,
        lastCoveredCount: Int,
    ): Plan? {
        val fresh = history.filter { it.id > lastUntilId }
        if (fresh.size <= THRESHOLD) return null

        // 保留最近 KEEP_TAIL 条，剩下的按批压缩
        val head = fresh.dropLast(KEEP_TAIL).take(MAX_COMPRESS_BATCH)
        if (head.isEmpty()) return null

        return Plan(
            toCompress = head,
            untilId = head.last().id,
            coveredCount = lastCoveredCount + head.size,
        )
    }
}
