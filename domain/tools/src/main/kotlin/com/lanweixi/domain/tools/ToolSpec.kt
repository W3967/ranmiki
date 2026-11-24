package com.lanweixi.domain.tools

/**
 * P138：可被模型调用的工具声明。
 *
 * `parameters` 是 JSON Schema 片段（type/properties/required），
 * 直接塞进请求的 `tools[].function.parameters`。
 */
data class ToolSpec(
    /** 模型看到的名字，必须是 `[a-zA-Z0-9_-]{1,64}`。 */
    val name: String,
    val description: String,
    val parameters: Map<String, Any?> = emptyMap(),
    /** 每次调用都要用户确认。 */
    val sensitive: Boolean = false,
    val source: ToolSource = ToolSource.HOST,
    /** 插件工具的来源插件 id（宿主工具为空）。 */
    val pluginId: String? = null,
    /**
     * 插件清单里写的原始函数名。
     *
     * 模型侧名字必须符合 `^[a-zA-Z0-9_-]{1,64}$`（点号会被清洗成下划线），
     * 但执行时得拿原始名去匹配插件声明，所以两套名字都留着。
     */
    val originalName: String? = null,
)

enum class ToolSource { HOST, PLUGIN }

/** 模型发起的一次调用。`arguments` 是模型拼好的 JSON 文本。 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    /**
     * P207：本轮用户原话。
     *
     * 引擎执行工具时当前消息还没落库，宿主工具用这段文本记草稿来源
     * （「点来源跳回会话」靠它找消息）。插件工具忽略即可。
     */
    val context: String = "",
)

/** 工具执行结果，原样回填给模型。 */
data class ToolResult(
    val callId: String,
    val name: String,
    val content: String,
    val isError: Boolean = false,
)

/** 工具执行器：宿主内置工具与插件工具都走这一个口。 */
interface ToolExecutor {
    val spec: ToolSpec

    /** 执行；失败时返回 `isError = true` 而不是抛异常（模型需要看到错误原因）。 */
    suspend fun execute(call: ToolCall): ToolResult
}

/** 工具名清洗：模型侧只接受字母数字下划线短横线，最长 64。 */
fun sanitizeToolName(raw: String): String {
    val cleaned = raw.map { ch ->
        if (ch.isLetterOrDigit() && ch.code < 128 || ch == '_' || ch == '-') ch else '_'
    }.joinToString("")
    return cleaned.take(64).ifBlank { "tool" }
}