package com.lanweixi.domain.docs

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 解析结果：正文 + 格式标签。 */
data class ExtractedDoc(
    val text: String,
    val kind: String,
)

/** 解析失败时抛出，message 直接给用户看。 */
class DocExtractException(message: String) : Exception(message)

/**
 * P135：文档正文提取（纯 JVM，可单测）。
 *
 * 只做「把文件变成纯文本」这一件事：
 * - txt / md：先按 UTF-8 严格解码，失败退回 GB18030（国内老 txt 常见）；
 * - docx：解 zip 后读 `word/document.xml`，抓 `<w:t>` 文本、`<w:p>` 换行；
 * - 其余格式：明确拒绝，不做半吊子解析。
 */
object DocTextExtractor {

    const val KIND_TXT = "txt"
    const val KIND_MD = "md"
    const val KIND_DOCX = "docx"

    /** P216：单篇纯文本上限（正常笔记远小于此）。 */
    const val MAX_TEXT_BYTES = 5 * 1024 * 1024

    /** P216：docx 的 `word/document.xml` 上限（不含图片等其它条目）。 */
    const val MAX_DOCX_XML_BYTES = 10 * 1024 * 1024

    fun supportedExtensions(): List<String> = listOf("txt", "md", "markdown", "docx")

    fun kindOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "txt" -> KIND_TXT
        "md", "markdown" -> KIND_MD
        "docx" -> KIND_DOCX
        else -> null
    }

    fun extract(fileName: String, input: InputStream): ExtractedDoc {
        val ext = fileName.substringAfterLast('.', "")
        val kind = kindOf(fileName)
            ?: throw DocExtractException("暂不支持 ${if (ext.isBlank()) "无扩展名文件" else ".$ext 格式"}。可导入 txt / md / docx。")
        val text = when (kind) {
            KIND_DOCX -> extractDocx(input)
            else -> decodeText(input)
        }.trim()
        if (text.isEmpty()) throw DocExtractException("这个文件里没有可读的文字")
        return ExtractedDoc(text = text, kind = kind)
    }

    /** 先按 UTF-8 严格解码，失败再按 GB18030。P216：读之前先限大小，避免超大 txt 撑爆内存。 */
    internal fun decodeText(input: InputStream): String {
        val bytes = readLimited(input, MAX_TEXT_BYTES, "文本")
        if (bytes.isEmpty()) return ""
        return runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrElse {
            runCatching { String(bytes, charset("GB18030")) }.getOrDefault(String(bytes, Charsets.UTF_8))
        }
    }

    /** P216：边读边计数；超上限直接报错（txt / docx 的 xml 都走这里）。 */
    private fun readLimited(input: InputStream, limitBytes: Int, what: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limitBytes) {
                throw DocExtractException(
                    "$what 超过 ${limitBytes / (1024 * 1024)}MB 上限，无法导入",
                )
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * docx 是 zip 包，正文在 `word/document.xml`。
     *
     * 用 DOM 而不是流式解析：单篇文档的 xml 规模在手机上一次解析完全可接受，
     * 而 DOM 让「段落 = `<w:p>`、文本 = `<w:t>`」这条规则写起来最直白。
     */
    internal fun extractDocx(input: InputStream): String {
        var documentXml: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    documentXml = readLimited(zip, MAX_DOCX_XML_BYTES, "docx 正文")
                    break
                }
                zip.closeEntry()
            }
        }
        val bytes = documentXml
            ?: throw DocExtractException("这个 docx 里找不到正文（可能是旧版 .doc 改名成 .docx）")

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // 文档是用户本地的，关掉外部实体防止 XXE
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val dom = runCatching { factory.newDocumentBuilder().parse(bytes.inputStream()) }
            .getOrElse { throw DocExtractException("docx 正文解析失败：${it.message ?: "未知原因"}") }

        val out = StringBuilder()
        val paragraphs = dom.getElementsByTagNameNS("*", "p")
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as? org.w3c.dom.Element ?: continue
            val runs = paragraph.getElementsByTagNameNS("*", "t")
            val line = StringBuilder()
            for (j in 0 until runs.length) {
                line.append(runs.item(j).textContent.orEmpty())
            }
            if (line.isNotBlank()) {
                out.append(line.toString().trim()).append('\n')
            }
        }
        return out.toString()
    }
}