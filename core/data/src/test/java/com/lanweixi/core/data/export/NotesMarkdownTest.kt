package com.lanweixi.core.data.export

import com.lanweixi.core.data.entity.NoteEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P199：Markdown 导出格式。 */
class NotesMarkdownTest {

    private fun note(
        id: Long,
        title: String,
        content: String = "",
        tags: String = "",
        archived: Boolean = false,
        updatedAt: Long,
    ) = NoteEntity(
        id = id,
        title = title,
        content = content,
        tags = tags,
        archived = archived,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `empty list still produces a readable file`() {
        val md = NotesMarkdown.export(emptyList(), now = millis(2026, 11, 24, 15, 30))

        assertTrue(md.contains("# 兰未晞 · 笔记导出"))
        assertTrue(md.contains("共 0 篇"))
        assertTrue(md.contains("（没有笔记）"))
    }

    @Test
    fun `notes carry title tags and update time`() {
        val md = NotesMarkdown.export(
            listOf(
                note(1, "周报", "本周做了三件事", tags = "工作,复盘", updatedAt = millis(2026, 11, 24, 9, 5)),
            ),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertTrue(md.contains("## 1. 周报"))
        assertTrue(md.contains("标签：工作,复盘"))
        assertTrue(md.contains("更新：2026-11-24 09:05"))
        assertTrue(md.contains("本周做了三件事"))
        assertTrue(md.contains("共 1 篇"))
    }

    @Test
    fun `newest note comes first`() {
        val md = NotesMarkdown.export(
            listOf(
                note(1, "旧的", updatedAt = millis(2026, 11, 20, 10, 0)),
                note(2, "新的", updatedAt = millis(2026, 11, 24, 10, 0)),
            ),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertTrue(md.indexOf("## 1. 新的") < md.indexOf("## 2. 旧的"))
    }

    @Test
    fun `blank title and content have placeholders`() {
        val md = NotesMarkdown.export(
            listOf(note(1, "", content = "  ", updatedAt = millis(2026, 11, 24, 10, 0))),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertTrue(md.contains("## 1. 无标题"))
        assertTrue(md.contains("（无正文）"))
    }

    @Test
    fun `archived notes are marked`() {
        val md = NotesMarkdown.export(
            listOf(note(1, "归档的", archived = true, updatedAt = millis(2026, 11, 24, 10, 0))),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertTrue(md.contains("已归档"))
    }

    @Test
    fun `body markdown is left untouched`() {
        val body = "## 小标题\n\n- 要点一\n- 要点二\n\n```kotlin\nval x = 1\n```"
        val md = NotesMarkdown.export(
            listOf(note(1, "带格式", content = body, updatedAt = millis(2026, 11, 24, 10, 0))),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertTrue("正文里的 Markdown 不该被转义", md.contains(body))
        assertFalse(md.contains("\\#\\#"))
    }

    @Test
    fun `separator between notes`() {
        val md = NotesMarkdown.export(
            listOf(
                note(1, "A", updatedAt = millis(2026, 11, 24, 10, 0)),
                note(2, "B", updatedAt = millis(2026, 11, 23, 10, 0)),
            ),
            now = millis(2026, 11, 24, 15, 30),
        )

        assertEquals(2, "---".toRegex().findAll(md).count())
    }
}
