package com.lanweixi.core.network

import org.json.JSONObject

/**
 * T03：SSE 载荷解析。纯函数，不碰网络，便于单测逐场景固定。
 *
 * 支持两种上游：
 * 1) 本项目契约：`{"type":"start|delta|done|error", ...}`；
 * 2) OpenAI 兼容：`{"choices":[{"delta":{"content":"..."},"finish_reason":null}]}` + `[DONE]`。
 */
object SseParser {

    fun parsePayload(payload: String): SseEvent? {
        val text = payload.trim()
        if (text.isEmpty()) return null
        if (text == "[DONE]") return SseEvent.EndOfStream

        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }

        when (json.optString("type")) {
            "start" -> return SseEvent.Start(
                model = json.optString("model").takeIf { it.isNotEmpty() },
                memories = json.optInt("memories", 0),
                vitals = json.optBoolean("vitals", false),
            )
            "delta" -> return SseEvent.Delta(json.optString("text"))
            "done" -> return SseEvent.Done(
                reply = json.optString("reply"),
                latencyMs = json.optLong("latencyMs", -1).takeIf { it >= 0 },
                promptTokens = json.optJSONObject("usage")?.optInt("prompt_tokens"),
                completionTokens = json.optJSONObject("usage")?.optInt("completion_tokens"),
                finishReason = json.optString("finishReason").takeIf { it.isNotEmpty() },
            )
            "error" -> {
                // 契约格式是「error 为字符串」；Anthropic 是嵌套对象 {type, message}
                val plain = json.optString("error")
                val message = if (plain.isNotEmpty()) {
                    plain
                } else {
                    json.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { "未知错误" }
                }
                return SseEvent.Error(message)
            }
        }

        // P209：Anthropic 的流事件（type 是 message_start / content_block_delta / …）
        parseAnthropic(json)?.let { return it }

        // P211：OpenAI 兼容流的用量帧（stream_options.include_usage）
        // 形状：{"choices":[], "usage":{"prompt_tokens":..,"completion_tokens":..}}
        json.optJSONObject("usage")?.let { usage ->
            val prompt = usage.optInt("prompt_tokens", -1).takeIf { it >= 0 }
            val completion = usage.optInt("completion_tokens", -1).takeIf { it >= 0 }
            if (prompt != null || completion != null) return SseEvent.Usage(prompt, completion)
        }

        // OpenAI 兼容分支
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val choice = choices.optJSONObject(0) ?: return null

        // P138：tool_calls 优先——同一帧里可能既有 content 又有工具调用
        val toolFragments = parseToolCallFragments(choice.optJSONObject("delta"))
        if (toolFragments.isNotEmpty()) return SseEvent.ToolCallsDelta(toolFragments)

        val delta = choice.optJSONObject("delta")
        val content = delta?.optString("content").orEmpty()
        if (content.isNotEmpty()) return SseEvent.Delta(content)

        // 结束标记：工具调用的判断交给上层（累积到的 tool_calls 分片非空即代表要调工具）
        if (!choice.isNull("finish_reason") && choice.optString("finish_reason").isNotEmpty()) {
            return SseEvent.EndOfStream
        }
        return null
    }

    /** 把一个 SSE 事件块（不含空行分隔）拆成事件；空块返回 null。 */
    fun parseBlock(block: String): SseEvent? {
        val buffer = StringBuilder()
        var hasData = false
        for (line in block.lineSequence()) {
            if (line.startsWith("data:")) {
                if (hasData) buffer.append('\n')
                buffer.append(line.removePrefix("data:").trimStart())
                hasData = true
            } else if (line.startsWith("event:") || line.startsWith("id:") || line.startsWith(":")) {
                // 保留但不消费；本项目只依赖 data
            }
        }
        if (!hasData) return null
        return parsePayload(buffer.toString())
    }

    /**
     * 一个传输块内可能有多行 `data:`（平台实现差异）：
     * 1) 标准 SSE —— 多行 data 属于同一事件，用 \n 连接；
     * 2) DeepSeek/OpenAI 兼容流 —— 每行 data 就是一条独立事件。
     *
     * 先按标准解析整块；失败则逐行解析，两种形态都能吃下。
     */
    fun parseEvents(block: String): List<SseEvent> {
        val dataLines = block.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trimStart() }
            .toList()
        if (dataLines.isEmpty()) return emptyList()
        if (dataLines.size == 1) return listOfNotNull(parsePayload(dataLines[0]))

        // 多行：先逐行（OpenAI 兼容流的常见形态），全部可解析即采用；
        // 否则回退为「多行 data 属于同一事件」的标准 SSE 语义。
        // 注意：org.json 会忽略尾随内容，不能直接拿整块试解析。
        val perLine = dataLines.map { parsePayload(it) }
        if (perLine.all { it != null }) return perLine.filterNotNull()

        val joined = parsePayload(dataLines.joinToString("\n"))
        return if (joined != null) listOf(joined) else perLine.filterNotNull()
    }
}

/**
 * 解析 `delta.tool_calls`。
 *
 * 形状：
 * ```json
 * {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"demo_search","arguments":"{\"q\":"}}]}}]}
 * ```
 * `id` / `name` 只在首帧出现，`arguments` 是逐帧拼接的 JSON 片段。
 */
private fun parseToolCallFragments(delta: org.json.JSONObject?): List<ToolCallFragment> {
    val calls = delta?.optJSONArray("tool_calls") ?: return emptyList()
    val out = mutableListOf<ToolCallFragment>()
    for (i in 0 until calls.length()) {
        val item = calls.optJSONObject(i) ?: continue
        val fn = item.optJSONObject("function")
        out += ToolCallFragment(
            index = item.optInt("index", i),
            id = item.optString("id").takeIf { it.isNotEmpty() },
            name = fn?.optString("name")?.takeIf { it.isNotEmpty() },
            arguments = fn?.optString("arguments").orEmpty(),
        )
    }
    return out
}

/**
 * P209：Anthropic 的事件。
 *
 * 对照官方流式格式：
 * - `message_start`：带 model 与 usage.input_tokens；
 * - `content_block_start`：`tool_use` 块带 id / name（文本块什么都不用做）；
 * - `content_block_delta`：`text_delta.text` 增量文本；`input_json_delta.partial_json` 工具参数分片；
 * - `content_block_stop` / `ping`：无操作；
 * - `message_delta`：usage.output_tokens；
 * - `message_stop`：结束。
 */
private fun parseAnthropic(json: org.json.JSONObject): SseEvent? =
    when (json.optString("type")) {
        "message_start" -> {
            val message = json.optJSONObject("message")
            SseEvent.Start(
                model = message?.optString("model")?.takeIf { it.isNotEmpty() },
                memories = 0,
                vitals = false,
                promptTokens = message?.optJSONObject("usage")
                    ?.optInt("input_tokens", 0)
                    ?.takeIf { it > 0 },
            )
        }

        "content_block_start" -> {
            val block = json.optJSONObject("content_block")
            if (block?.optString("type") == "tool_use") {
                SseEvent.ToolCallsDelta(
                    listOf(
                        ToolCallFragment(
                            index = json.optInt("index", 0),
                            id = block.optString("id").takeIf { it.isNotEmpty() },
                            name = block.optString("name").takeIf { it.isNotEmpty() },
                            arguments = "",
                        ),
                    ),
                )
            } else {
                null
            }
        }

        "content_block_delta" -> {
            val delta = json.optJSONObject("delta")
            when (delta?.optString("type")) {
                "text_delta" -> SseEvent.Delta(delta.optString("text"))
                "input_json_delta" -> SseEvent.ToolCallsDelta(
                    listOf(
                        ToolCallFragment(
                            index = json.optInt("index", 0),
                            id = null,
                            name = null,
                            arguments = delta.optString("partial_json"),
                        ),
                    ),
                )

                else -> null
            }
        }

        "content_block_stop", "ping" -> null

        "message_delta" -> {
            val completion = json.optJSONObject("usage")
                ?.optInt("output_tokens", -1)
                ?.takeIf { it >= 0 }
            completion?.let { SseEvent.Usage(promptTokens = null, completionTokens = it) }
        }

        "message_stop" -> SseEvent.EndOfStream

        else -> null
    }
