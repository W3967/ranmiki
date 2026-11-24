package com.lanweixi.core.data.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P210：会话摘要导出（Markdown）。
 *
 * 用户在设置里点「导出会话摘要」拿到这个文件——上下文压缩后原文删不掉，
 * 但摘要对用户是可读的，存一份在手边比存在数据库里踏实。
 */
object SummariesMarkdown {

    data class Row(
        val sessionId: String,
        /** 人话标题（兰未晞 / 群名）；空则用会话 id。 */
        val title: String,
        val content: String,
        val coveredCount: Int,
        val updatedAt: Long,
    )

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun render(rows: List<Row>, exportedAt: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append("# 会话摘要\n\n")
        sb.append("> 共 ").append(rows.size).append(" 段 · 导出时间 ")
            .append(timeFormat.format(Date(exportedAt))).append("\n")
        sb.append("> 摘要由她自动生成，用于长对话的上下文压缩；原文仍保存在应用数据库里。\n\n")

        if (rows.isEmpty()) {
            sb.append("（还没有生成过摘要）\n")
            return sb.toString()
        }

        rows.forEach { row ->
            sb.append("## ").append(row.title.ifBlank { row.sessionId }).append("\n\n")
            sb.append("- 覆盖 ").append(row.coveredCount).append(" 条消息")
            if (row.updatedAt > 0L) {
                sb.append(" · 更新于 ").append(timeFormat.format(Date(row.updatedAt)))
            }
            sb.append("\n- 会话 id：`").append(row.sessionId).append("`\n\n")
            sb.append(row.content.trim()).append("\n\n---\n\n")
        }
        return sb.toString()
    }
}
