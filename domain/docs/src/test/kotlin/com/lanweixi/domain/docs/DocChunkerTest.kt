package com.lanweixi.domain.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P135：切块规则单测。 */
class DocChunkerTest {

    @Test
    fun `blank text yields no chunks`() {
        assertTrue(DocChunker.chunk("").isEmpty())
        assertTrue(DocChunker.chunk("   \n\n  ").isEmpty())
    }

    @Test
    fun `paragraphs are packed up to the limit`() {
        val text = (1..10).joinToString("\n") { "第${it}段".padEnd(60, '字') }
        val chunks = DocChunker.chunk(text, maxChars = 200)
        assertTrue("应切出多块", chunks.size > 1)
        chunks.forEach { assertTrue("每块不超过上限+1", it.length <= 201) }
        // 块内段落之间仍保留换行，所以比较时两边都去掉
        assertEquals(
            text.replace("\n", "").length,
            chunks.joinToString("") { it }.replace("\n", "").length,
        )
    }

    @Test
    fun `oversized paragraph is hard split`() {
        val text = "长".repeat(1000)
        val chunks = DocChunker.chunk(text, maxChars = 300)
        assertEquals(4, chunks.size)
        chunks.forEach { assertTrue(it.length <= 300) }
        assertEquals(1000, chunks.sumOf { it.length })
    }

    @Test
    fun `blank lines flush the buffer`() {
        val text = "A段\n\nB段"
        val chunks = DocChunker.chunk(text, maxChars = 700)
        assertEquals(listOf("A段", "B段"), chunks)
    }
}