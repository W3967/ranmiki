package com.lanweixi.core.network

/**
 * T03：SSE 事件契约（交接说明第 4 节）。
 * 只有 Done 允许调用方落库/标记完成；Abort/中途 error 都不得当作成功。
 *
 * P138：新增 [ToolCallsDelta]，承载 OpenAI 兼容流里的 `delta.tool_calls` 分片。
 */
sealed interface SseEvent {

    data class Start(
        val model: String?,
        val memories: Int,
        val vitals: Boolean,
        /** P209：Anthropic 的 message_start 带输入 token 数。 */
        val promptTokens: Int? = null,
    ) : SseEvent

    data class Delta(val text: String) : SseEvent

    /**
     * P209：单独的用量事件。
     *
     * Anthropic 的输入/输出 token 分散在 message_start 与 message_delta 里，
     * 而它的完成事件只有 `message_stop`；先记用量，合成 Done 时一起带上。
     */
    data class Usage(val promptTokens: Int?, val completionTokens: Int?) : SseEvent

    /**
     * 一次工具调用分片。
     *
     * OpenAI 兼容流会把同一个调用的 `arguments` 拆成很多帧，
     * 这里**照实抛分片**，拼接由上层按 [index] 累积。
     */
    data class ToolCallsDelta(val fragments: List<ToolCallFragment>) : SseEvent

    data class Done(
        val reply: String,
        val latencyMs: Long?,
        val promptTokens: Int?,
        val completionTokens: Int?,
        /** OpenAI 的 finish_reason，`tool_calls` 表示模型想调工具。 */
        val finishReason: String? = null,
    ) : SseEvent

    data class Error(val message: String) : SseEvent

    /** 上游 OpenAI 兼容格式的 `[DONE]` 或 `finish_reason`。 */
    data object EndOfStream : SseEvent
}

/** 单个工具调用分片；`id` / `name` 只在第一帧出现。 */
data class ToolCallFragment(
    val index: Int,
    val id: String?,
    val name: String?,
    val arguments: String,
)

data class ChatMessage(
    val role: String,
    val content: String,
    /** assistant 发起的工具调用（JSON 原文，用于回填历史）。 */
    val toolCalls: List<ToolCallOut> = emptyList(),
    /** role = "tool" 时，这条结果回应的是哪个调用。 */
    val toolCallId: String? = null,
)

/** 历史里的工具调用（与 domain:tools 的 ToolCall 同形，但网络层不依赖 domain）。 */
data class ToolCallOut(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ChatStreamRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val maxTokens: Int = 900,
    val temperature: Double = 0.8,
    /** P138：可调用的工具；空表示本次请求不带 tools。 */
    val tools: List<ToolDeclaration> = emptyList(),
    val toolChoice: String = "auto",
)

/** 工具声明（OpenAI `tools[].function` 的形状）。 */
data class ToolDeclaration(
    val name: String,
    val description: String,
    /** JSON Schema 片段；为空时发一个宽松的 object。 */
    val parameters: Map<String, Any?> = emptyMap(),
)