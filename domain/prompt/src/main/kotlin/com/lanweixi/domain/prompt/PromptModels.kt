package com.lanweixi.domain.prompt

/** P06a：提示词模块自有的消息类型，不依赖 :core:network。 */
data class PromptMessage(val role: String, val content: String)

data class PromptHistoryItem(val role: String, val content: String)

/**
 * 动态段：全部由调用方注入可空字符串，模块内不反向依赖 vitals / memory 具体类。
 * 空值（null 或空白）表示整段省略。
 */
data class PromptDynamic(
    val timeLine: String? = null,
    val recentEvent: String? = null,
    val plotSummary: String? = null,
)

data class PromptRequest(
    val persona: String,
    val dynamic: PromptDynamic = PromptDynamic(),
    val history: List<PromptHistoryItem> = emptyList(),
    val userText: String,
    val memoryBlock: String? = null,
    val maxTokens: Int = PromptBudgetGuard.MAX_TOKENS_DEFAULT,
    val maxHistory: Int = 8,
)

/** 预算快照：稳定段 / 动态段 / 单轮总量（估算 token）。 */
data class PromptBudget(
    val stable: Int,
    val dynamic: Int,
    val total: Int,
)

enum class RejectReason {
    STABLE_BUDGET,
    DYNAMIC_BUDGET,
    TOTAL_BUDGET,
    MAX_TOKENS_RANGE,
}

sealed interface PromptBuildResult {
    data class Built(
        val messages: List<PromptMessage>,
        val maxTokens: Int,
        val budget: PromptBudget,
    ) : PromptBuildResult

    data class Rejected(
        val reason: RejectReason,
        val actual: Int,
        val limit: Int,
        val budget: PromptBudget,
    ) : PromptBuildResult
}

object PromptBudgetGuard {
    const val STABLE_LIMIT = 1200
    const val DYNAMIC_LIMIT = 300
    const val TOTAL_LIMIT = 3000
    const val MAX_TOKENS_DEFAULT = 900
    const val MAX_TOKENS_MIN = 800
    const val MAX_TOKENS_MAX = 1000
    const val MAX_HISTORY = 8
    const val PLOT_SUMMARY_MAX_CHARS = 300
}

/**
 * 保守 token 估算：CJK 按字、拉丁按词、标点按 1。
 * 只用于预算守卫，不追求与真实分词器一致；宁可高估。
 */
object TokenEstimator {
    fun estimate(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        var tokens = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                isCjk(c) -> {
                    tokens += 1
                    i += 1
                }
                c.isLetterOrDigit() -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '\'' || text[j] == '-')) j++
                    tokens += 1
                    i = j
                }
                c.isWhitespace() -> i += 1
                else -> {
                    tokens += 1
                    i += 1
                }
            }
        }
        return tokens
    }

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF ||
            code in 0x3040..0x30FF || // 假名
            code in 0xAC00..0xD7AF // 韩文
    }
}
