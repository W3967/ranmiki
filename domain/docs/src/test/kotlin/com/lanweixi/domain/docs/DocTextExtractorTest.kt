package com.lanweixi.domain.docs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P135：文档提取与切块的单测（纯 JVM）。 */
class DocTextExtractorTest {

    private fun docxBytes(bodyXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                (
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                        "<w:body>$bodyXml</w:body></w:document>"
                    ).toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `kind is derived from extension`() {
        assertEquals(DocTextExtractor.KIND_TXT, DocTextExtractor.kindOf("a.txt"))
        assertEquals(DocTextExtractor.KIND_MD, DocTextExtractor.kindOf("b.MD"))
        assertEquals(DocTextExtractor.KIND_DOCX, DocTextExtractor.kindOf("c.docx"))
        assertNull(DocTextExtractor.kindOf("d.pdf"))
    }

    @Test
    fun `txt is decoded as utf8`() {
        val doc = DocTextExtractor.extract("n.txt", ByteArrayInputStream("你好，世界".toByteArray()))
        assertEquals("你好，世界", doc.text)
        assertEquals(DocTextExtractor.KIND_TXT, doc.kind)
    }

    @Test
    fun `gb18030 txt falls back correctly`() {
        val gb = "第二行中文".toByteArray(charset("GB18030"))
        val doc = DocTextExtractor.extract("gb.txt", ByteArrayInputStream(gb))
        assertEquals("第二行中文", doc.text)
    }

    @Test
    fun `docx paragraphs become lines`() {
        val xml = java.lang.StringBuilder()
            .append("<w:p><w:r><w:t>第一段</w:t></w:r></w:p>")
            .append("<w:p><w:r><w:t>第二段</w:t></w:r><w:r><w:t>续写</w:t></w:r></w:p>")
            .append("<w:p></w:p>")
            .append("<w:p><w:r><w:t>第三段</w:t></w:r></w:p>")
            .toString()
        val doc = DocTextExtractor.extract("d.docx", ByteArrayInputStream(docxBytes(xml)))
        assertEquals(DocTextExtractor.KIND_DOCX, doc.kind)
        assertEquals("第一段\n第二段续写\n第三段", doc.text)
    }

    @Test
    fun `docx without document xml is rejected`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write("<x/>".toByteArray())
            zip.closeEntry()
        }
        val result = runCatching {
            DocTextExtractor.extract("bad.docx", ByteArrayInputStream(out.toByteArray()))
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DocExtractException)
    }

    @Test
    fun `pdf is rejected with a clear message`() {
        val result = runCatching {
            DocTextExtractor.extract("x.pdf", ByteArrayInputStream(ByteArray(0)))
        }
        assertTrue(result.isFailure)
        val message = (result.exceptionOrNull() as DocExtractException).message.orEmpty()
        assertTrue("提示里应说明支持哪些格式", message.contains("txt"))
    }

    @Test
    fun `empty content is rejected`() {
        val result = runCatching {
            DocTextExtractor.extract("e.txt", ByteArrayInputStream("   \n  ".toByteArray()))
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DocExtractException)
    }

    // ── P216：超大文件不能读进内存 ─────────────────────

    @Test
    fun `oversized txt is rejected instead of being read into memory`() {
        val bytes = ByteArray(DocTextExtractor.MAX_TEXT_BYTES + 1) { 'a'.code.toByte() }

        val result = runCatching {
            DocTextExtractor.extract("big.txt", ByteArrayInputStream(bytes))
        }

        assertTrue(result.isFailure)
        val message = (result.exceptionOrNull() as DocExtractException).message.orEmpty()
        assertTrue("提示要说清楚是超上限，实际：$message", message.contains("上限"))
    }

    @Test
    fun `oversized docx document xml is rejected`() {
        val body = "<w:p><w:r><w:t>" + "A".repeat(DocTextExtractor.MAX_DOCX_XML_BYTES + 1024) + "</w:t></w:r></w:p>"

        val result = runCatching {
            DocTextExtractor.extract("big.docx", ByteArrayInputStream(docxBytes(body)))
        }

        assertTrue(result.isFailure)
        val message = (result.exceptionOrNull() as DocExtractException).message.orEmpty()
        assertTrue("提示要说清楚是超上限，实际：$message", message.contains("上限"))
    }
}