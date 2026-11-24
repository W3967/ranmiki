package com.lanweixi.feature.chat

/**
 * P143：识别「在问工作台数据」的问题。
 *
 * 这类问题必须落到工具上——实测过一次：库里 0 篇笔记，模型靠上下文答「26 条」。
 * 识别到但模型没调工具时，引擎会强制补一轮。
 *
 * **刻意保守**：只认「数量词 + 工作台名词」同时出现，
 * 误判的代价是每轮多一次 API 往返，宁漏不误。
 */
object DataQuestionDetector {

    /** 数量 / 询问词。 */
    private val quantifiers = listOf(
        "几个", "多少", "几条", "几项", "几篇", "哪些", "哪几",
        "列出", "列表", "统计", "总数", "一共", "有几", "还有",
        "how many", "how much", "list ", "show me", "what are",
    )

    /** 工作台里的实体。 */
    private val entities = listOf(
        "任务", "待办", "笔记", "文档", "提示词", "复盘",
        "task", "note", "doc", "prompt", "todo",
    )

    /** 明确的「去查」意图，单独命中即可。 */
    private val explicitQueries = listOf(
        "查一下", "查查", "搜一下", "搜搜", "找一下", "找找",
        "帮我查", "帮我搜", "帮我找", "look up", "search",
    )

    fun looksLikeDataQuestion(text: String): Boolean {
        val t = text.lowercase()
        if (t.isBlank()) return false
        if (explicitQueries.any { it in t }) return true
        val hasQuantifier = quantifiers.any { it in t }
        val hasEntity = entities.any { it in t }
        return hasQuantifier && hasEntity
    }
}