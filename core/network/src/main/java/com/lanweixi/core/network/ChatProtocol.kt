package com.lanweixi.core.network

/**
 * P209：上游 API 协议。
 *
 * 现在只区分两种：OpenAI 兼容（`/chat/completions` + `Authorization: Bearer`）
 * 和 Anthropic（`/v1/messages` + `x-api-key`）。
 *
 * 协议**不单独存配置**：按 BaseURL 推断。用户选 Anthropic 预设时自动带
 * `api.anthropic.com`，自定义代理域名里通常也带 `anthropic`，够用且不用加迁移。
 */
enum class ChatProtocol { OPENAI, ANTHROPIC }

/** P209：Anthropic 要求带版本头，值固定。 */
const val ANTHROPIC_VERSION = "2023-06-01"

/** P209：按 BaseURL 判断走哪套协议。 */
fun protocolOf(baseUrl: String): ChatProtocol =
    if (baseUrl.contains("anthropic", ignoreCase = true)) ChatProtocol.ANTHROPIC else ChatProtocol.OPENAI

/**
 * P209：Anthropic 的 `/v1/messages` 地址拼接。
 *
 * 用户可能填 `https://api.anthropic.com`、`.../v1` 或完整 `.../v1/messages`，
 * 三种都要落到同一个地址上。
 */
fun messagesUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "https://api.anthropic.com/v1/messages"
    return when {
        trimmed.endsWith("/v1/messages") -> trimmed
        trimmed.endsWith("/messages") -> trimmed
        trimmed.endsWith("/v1") -> "$trimmed/messages"
        else -> "$trimmed/v1/messages"
    }
}
