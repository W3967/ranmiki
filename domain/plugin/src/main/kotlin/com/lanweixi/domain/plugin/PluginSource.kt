package com.lanweixi.domain.plugin

/**
 * P166：远程插件源。
 *
 * 一个源就是一个 JSON 索引（DSH 风格插件仓库的极简版）：
 * 宿主拉取索引 → 展示列表 → 用户点安装 → 下载 zip → 走本地安装流程。
 *
 * **索引不授予任何权限**：插件装进来后仍然要用户逐项授权，
 * 这一点和本地装 zip 完全一致。
 */
data class PluginSourceIndex(
    val name: String = "",
    val description: String = "",
    val plugins: List<RemotePlugin> = emptyList(),
)

/** 索引里的一条插件。 */
data class RemotePlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    /** 插件 zip 的下载地址。 */
    val downloadUrl: String,
    /** 字节数，用于展示体积；0 表示未知。 */
    val sizeBytes: Long = 0,
    /**
     * **P216：必填**。sha256 十六进制串；索引不提供或格式不对就不允许安装。
     *
     * 以前是「可选」——索引方（或者能改索引的人）可以省掉校验值，
     * 下载下来的 zip 与展示的内容不一定一致。
     */
    val sha256: String = "",
    /** 宿主版本下限；空表示不限。 */
    val minHostVersion: String = "",
) {
    /** P216：校验值是否有效（64 位十六进制）。 */
    fun checksumValid(): Boolean = SHA256_HEX.matches(sha256.trim())

    /** 供 UI 展示的体积文案。 */
    fun sizeText(): String = when {
        sizeBytes <= 0 -> ""
        sizeBytes < 1024 -> sizeBytes.toString() + " B"
        sizeBytes < 1024 * 1024 -> (sizeBytes / 1024).toString() + " KB"
        else -> ((sizeBytes / 1024.0 / 1024.0 * 10).toLong() / 10.0).toString() + " MB"
    }

    private companion object {
        val SHA256_HEX = Regex("^[a-fA-F0-9]{64}$")
    }
}

/** 拉取结果。 */
sealed interface SourceFetchResult {
    data class Success(val index: PluginSourceIndex, val sourceUrl: String) : SourceFetchResult
    data class Failure(val message: String, val sourceUrl: String) : SourceFetchResult
}
