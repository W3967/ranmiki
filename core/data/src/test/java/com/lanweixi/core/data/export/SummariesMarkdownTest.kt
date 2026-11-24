package com.lanweixi.core.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

/** P210：会话摘要导出的 Markdown 格式。 */
class SummariesMarkdownTest {

    private val exportedAt = 1_800_000_000_000L

    @Test
    fun `empty export says so`() {
        val md = SummariesMarkdown.render(emptyList(), exportedAt)
        assertTrue(md.contains("# 会话摘要"))
        assertTrue(md.contains("（还没有生成过摘要）"))
    }

    @Test
    fun `renders title covered count and body`() {
        val md = SummariesMarkdown.render(
            listOf(
                SummariesMarkdown.Row(
                    sessionId = "s1795",
                    title = "兰未晞",
                    content = "他明天下午三点去面试，带上简历和作品集。",
                    coveredCount = 32,
                    updatedAt = exportedAt,
                ),
            ),
            exportedAt,
        )

        assertTrue(md.contains("## 兰未晞"))
        assertTrue(md.contains("- 覆盖 32 条消息"))
        assertTrue(md.contains("他明天下午三点去面试"))
        assertTrue(md.contains("会话 id：`s1795`"))
    }

    @Test
    fun `falls back to session id when title is blank`() {
        val md = SummariesMarkdown.render(
            listOf(
                SummariesMarkdown.Row(
                    sessionId = "group-1",
                    title = "",
                    content = "摘要",
                    coveredCount = 5,
                    updatedAt = 0L,
                ),
            ),
            exportedAt,
        )
        assertTrue(md.contains("## group-1"))
    }
}
