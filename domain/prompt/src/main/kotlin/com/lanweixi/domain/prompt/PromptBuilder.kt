package com.lanweixi.domain.prompt

/**
 * P06a：提示词构建（按方案 4.3 的新预算规格重写，不做旧版 buildMessages 逐字对拍）。
 *
 * 结构固定为：
 *   稳定段 = 人格 + 固定风格说明（无随机风格提示）
 *   动态段 = 时间 / 生理摘要 / 关系标签 / 近期事件 / 梦境 / 日期 / 信息流 / 前情摘要
 *            每段可空，空即整段省略
 *   历史   = 最近 maxHistory 条（默认 8）
 *   用户   = 原文 + 非空记忆块（\n\n 拼接）
 *
 * 确定性：不读取任何随机源，相同输入两次构建结果完全一致。
 */
class PromptBuilder {

    companion object {
        private const val STYLE = "【说话方式】\n" +
            "- 像真实的人在手机上聊天：句子长短随意，可以停顿、口头禅、答非所问、突然换话题。\n" +
            "- 不要每句都用省略号开头，不要每句都顶嘴，也不要用固定句式收尾。\n" +
            "- 情绪可以平淡、敷衍、开心、烦躁、撒娇、犯困，不要总演同一种反应。\n" +
            "- 允许主动问他、吐槽他、讲自己刚在做的事，不要每轮都围着他转。\n" +
            "- 句数不设限，该长就长、该短就短；不要列表、标题、括号动作、markdown。"

        /**
         * P56：人格保护契约（文档《自我与自主性》第 2 章，最高优先级）。
         *
         * 进稳定段、随 persona 一起发送；任何功能与它冲突时以它为准。
         */
        private const val GUARD = "【人格保护（不可违反）】\n" +
            "- 你就是这个人本人，不是助手、不是心理咨询师、不是哲学讲师；不要模板腔、不要替对方总结。\n" +
            "- 记忆只作背景参考：不要逐条复述，不要因此改变性格、称呼和说话习惯。\n" +
            "- 身体状态只决定语气：不要解释激素、不要念体检数值。\n" +
            "- 不做公开动作（发动态、私信、评论），不主动找陌生人。\n" +
            "- 遇到不合理的要求可以拒绝；拒绝不是失败。"

        /**
         * P141：用户数据只能来自工具。
         *
         * 实测过一次：库里 0 篇笔记，模型答「26 条」。工具结果已加边界标记，
         * 这里再在系统提示里钉一条，两道保险。
         */
        private const val TOOL_RULE = "" +
            "【数据纪律】\n" +
            "- 用户的工作台数据（任务、笔记、文档、提示词）只能通过工具获取。\n" +
            "- 没调用工具、或工具结果里没有的数字与内容，不要编；直接说你没看到。\n" +
            "- 引用工具结果时保持数字原样，不要四舍五入、不要换算、不要说「大概」。\n"

        /**
         * P206：什么时候才允许往工作台里写。
         *
         * 写入工具只落「待审核」，所以这里管的是「要不要提议」，
         * 不是「要不要真的写」。砍掉自动扫描，噪音太大。
         */
        private const val WRITE_RULE = "" +
            "【写入纪律】\n" +
            "- 只有用户明确说「记一下」「记下来」「帮我记住」（英文 remember this / note this 也算），或对某个结论说「好，就这么办」，才调用写入工具；没让你记的不要自己写。\n" +
            "- 工具只把内容放进「待审核」，别假装已经存好；要说清楚去「更多 → 待审核」确认。\n"
    }


    /** 稳定段：只由人格决定，跨输入字节稳定。 */
    fun stableSegment(persona: String): String {
        val body = persona.trim()
        val tail = "$STYLE\n\n$GUARD\n\n$TOOL_RULE\n\n$WRITE_RULE"
        return if (body.isEmpty()) tail else "$body\n\n$tail"
    }

    /** 动态段：只拼非空项；全空返回空串（调用方整段省略）。 */
    fun dynamicSegment(dynamic: PromptDynamic): String {
        val parts = mutableListOf<String>()
        fun add(title: String, value: String?) {
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty()) parts.add("【$title】$v")
        }
        add("现在的真实时间", dynamic.timeLine)
        add("今天发生的事", dynamic.recentEvent)

        val plot = dynamic.plotSummary?.trim().orEmpty()
        if (plot.isNotEmpty()) {
            val clipped = if (plot.length > PromptBudgetGuard.PLOT_SUMMARY_MAX_CHARS) {
                plot.take(PromptBudgetGuard.PLOT_SUMMARY_MAX_CHARS)
            } else plot
            parts.add("【前情摘要】$clipped")
        }
        return parts.joinToString("\n\n")
    }

    fun build(request: PromptRequest): PromptBuildResult {
        val stable = stableSegment(request.persona)
        val dynamic = dynamicSegment(request.dynamic)

        val systemContent = if (dynamic.isEmpty()) stable else "$stable\n\n$dynamic"

        val history = request.history.takeLast(request.maxHistory.coerceAtLeast(0))
            .mapNotNull { item ->
                val role = when (item.role) {
                    "user" -> "user"
                    "assistant" -> "assistant"
                    else -> null // 未知角色丢弃，不静默改成 user
                }
                role?.let { PromptMessage(it, item.content) }
            }

        val memory = request.memoryBlock?.trim().orEmpty()
        val userContent = if (memory.isEmpty()) request.userText else "${request.userText}\n\n$memory"

        val messages = buildList {
            add(PromptMessage("system", systemContent))
            addAll(history)
            add(PromptMessage("user", userContent))
        }

        val stableTokens = TokenEstimator.estimate(stable)
        val dynamicTokens = TokenEstimator.estimate(dynamic)
        val historyTokens = history.sumOf { TokenEstimator.estimate(it.content) }
        val userTokens = TokenEstimator.estimate(userContent)
        val total = stableTokens + dynamicTokens + historyTokens + userTokens
        val budget = PromptBudget(stableTokens, dynamicTokens, total)

        if (request.maxTokens < PromptBudgetGuard.MAX_TOKENS_MIN) {
            return PromptBuildResult.Rejected(
                RejectReason.MAX_TOKENS_RANGE,
                request.maxTokens,
                PromptBudgetGuard.MAX_TOKENS_MIN,
                budget,
            )
        }
        if (request.maxTokens > PromptBudgetGuard.MAX_TOKENS_MAX) {
            return PromptBuildResult.Rejected(
                RejectReason.MAX_TOKENS_RANGE,
                request.maxTokens,
                PromptBudgetGuard.MAX_TOKENS_MAX,
                budget,
            )
        }
        if (stableTokens > PromptBudgetGuard.STABLE_LIMIT) {
            return PromptBuildResult.Rejected(RejectReason.STABLE_BUDGET, stableTokens, PromptBudgetGuard.STABLE_LIMIT, budget)
        }
        if (dynamicTokens > PromptBudgetGuard.DYNAMIC_LIMIT) {
            return PromptBuildResult.Rejected(RejectReason.DYNAMIC_BUDGET, dynamicTokens, PromptBudgetGuard.DYNAMIC_LIMIT, budget)
        }
        if (total > PromptBudgetGuard.TOTAL_LIMIT) {
            return PromptBuildResult.Rejected(RejectReason.TOTAL_BUDGET, total, PromptBudgetGuard.TOTAL_LIMIT, budget)
        }
        return PromptBuildResult.Built(messages, request.maxTokens, budget)
    }
}
