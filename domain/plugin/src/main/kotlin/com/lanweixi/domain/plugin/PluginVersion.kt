package com.lanweixi.domain.plugin

/**
 * P167：插件版本比较。
 *
 * 只做「谁更新」这一件事，用最朴素的点分数字比较：
 * `1.2.10 > 1.2.9`、`2.0 == 2.0.0`。
 *
 * 非数字段（如 `1.0.0-beta`）按**预发布低于正式版**处理：
 * 同段位置的 `beta` 视作比空串小，所以 `1.0.0-beta < 1.0.0`。
 * 细节不追求完整 semver 语义——插件作者写清晰的三段数字就够用。
 */
object PluginVersion {

    /** a > b 返回正数，a < b 返回负数，相等返回 0。 */
    fun compare(a: String, b: String): Int {
        val pa = segments(a)
        val pb = segments(b)
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val x = pa.getOrNull(i) ?: Part(0, "")
            val y = pb.getOrNull(i) ?: Part(0, "")
            val byNumber = x.number.compareTo(y.number)
            if (byNumber != 0) return byNumber
            // 数字相同时，有预发布后缀的算旧版本
            val bySuffix = when {
                x.suffix == y.suffix -> 0
                x.suffix.isEmpty() -> 1
                y.suffix.isEmpty() -> -1
                else -> x.suffix.compareTo(y.suffix)
            }
            if (bySuffix != 0) return bySuffix
        }
        return 0
    }

    /** [candidate] 是否比 [installed] 新。 */
    fun isNewer(candidate: String, installed: String): Boolean =
        compare(candidate, installed) > 0

    private data class Part(val number: Int, val suffix: String)

    private fun segments(raw: String): List<Part> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(".").map { piece ->
            val digits = piece.takeWhile { it.isDigit() }
            val suffix = piece.dropWhile { it.isDigit() }.removePrefix("-")
            Part(digits.toIntOrNull() ?: 0, suffix)
        }
    }
}
