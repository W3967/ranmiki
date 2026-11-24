package com.lanweixi.domain.docs

/**
 * P135：把长文切成可检索的块。
 *
 * 为什么不按固定长度硬切：中文文档里句子边界比字数重要，
 * 所以先按段落（空行）分组，再按句子补到接近上限，超了才硬切。
 */
object DocChunker {

    /** 单块目标长度（字符）。 */
    const val DEFAULT_CHUNK_CHARS = 700

    fun chunk(text: String, maxChars: Int = DEFAULT_CHUNK_CHARS): List<String> {
        require(maxChars > 0) { "maxChars 必须大于 0" }
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flush() {
            val value = buffer.toString().trim()
            if (value.isNotEmpty()) chunks += value
            buffer.setLength(0)
        }

        for (paragraph in normalized.split("\n")) {
            val line = paragraph.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            // 单段就超长：先冲掉已有内容，再按句子/硬切分段
            if (line.length > maxChars) {
                flush()
                splitLongLine(line, maxChars).forEach { chunks += it }
                continue
            }
            if (buffer.length + line.length + 1 > maxChars) flush()
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append(line)
        }
        flush()
        return chunks.filter { it.isNotBlank() }
    }

    private fun splitLongLine(line: String, maxChars: Int): List<String> {
        val out = mutableListOf<String>()
        var index = 0
        while (index < line.length) {
            val end = minOf(index + maxChars, line.length)
            out += line.substring(index, end).trim().takeIf { it.isNotEmpty() } ?: ""
            index = end
        }
        return out.filter { it.isNotEmpty() }
    }
}