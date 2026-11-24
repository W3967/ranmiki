package com.lanweixi.core.data.export

import com.lanweixi.core.data.entity.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P199：把笔记导出成**给人看的** Markdown。
 *
 * 备份是 JSON——完整、能恢复，但打开一片乱。这里是阅读用的：
 * 一篇一个 `##`，带标签、更新时间，正文原样保留。
 *
 * 不做转义：正文里本来就可能写着 Markdown（用户自己写的），原样输出才对。
 */
object NotesMarkdown {

    fun export(notes: List<NoteEntity>, now: Long = System.currentTimeMillis()): String {
        val sorted = notes.sortedByDescending { it.updatedAt }
        return buildString {
            appendLine("# 兰未晞 · 笔记导出")
            appendLine()
            appendLine("> 导出时间：${formatTime(now)} · 共 ${sorted.size} 篇")
            appendLine()
            if (sorted.isEmpty()) {
                appendLine("（没有笔记）")
                return@buildString
            }
            sorted.forEachIndexed { index, note ->
                appendLine("---")
                appendLine()
                appendLine("## ${index + 1}. ${note.title.ifBlank { "无标题" }}")
                appendLine()
                val meta = buildList {
                    if (note.tags.isNotBlank()) add("标签：${note.tags}")
                    if (note.archived) add("已归档")
                    add("更新：${formatTime(note.updatedAt)}")
                }
                appendLine(meta.joinToString(separator = " · ", prefix = "_", postfix = "_"))
                appendLine()
                val body = note.content.trim()
                appendLine(if (body.isEmpty()) "（无正文）" else body)
                appendLine()
            }
        }
    }

    private fun formatTime(at: Long): String =
        if (at <= 0L) {
            "—"
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(at))
        }
}
