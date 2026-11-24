package com.lanweixi.core.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

/** T03 回调：事件、错误、流关闭三选一地推进。取消后三者都不再触发。 */
interface ChatStreamCallback {
    fun onEvent(event: SseEvent)
    fun onError(error: ChatError)
    fun onClosed()

    /** P138：工具调用分片；默认忽略，只有需要 tools 的调用方才覆盖。 */
    fun onToolCalls(fragments: List<ToolCallFragment>) = Unit
}

/**
 * T03：OkHttp 真流式客户端。
 *
 * - OkHttpClient 由 Hilt（NetworkModule）以 @Singleton 提供，本类只注入、不自建；
 * - 边收边回调，不缓存整段再切片；
 * - 超时分层：连接 10s / 读 20s（首字节与块间隔）/ 整轮 120s；
 * - cancel() 之后不再回调任何内容，已收文本由调用方自行保留；
 * - 完成语义：原生 `done` 事件，或 OpenAI 兼容流的 `[DONE]`/`finish_reason`
 *   在「有累计文本、未取消、未出错」时合成为一条 Done；
 * - 断连或中途 error 不发 Done，调用方据此不得落库。
 */
class ChatStreamClient @Inject constructor(
    private val client: OkHttpClient,
) {

    fun open(request: ChatStreamRequest, callback: ChatStreamCallback): ChatStreamHandle {
        val protocol = protocolOf(request.baseUrl)
        val url = if (protocol == ChatProtocol.ANTHROPIC) {
            messagesUrl(request.baseUrl)
        } else {
            chatCompletionsUrl(request.baseUrl)
        }
        ChatStreamLog.d("POST $url model=${request.model} protocol=$protocol")
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
        if (protocol == ChatProtocol.ANTHROPIC) {
            builder.header("x-api-key", request.apiKey)
            builder.header("anthropic-version", ANTHROPIC_VERSION)
        } else {
            builder.header("Authorization", "Bearer ${request.apiKey}")
        }
        val body = if (protocol == ChatProtocol.ANTHROPIC) {
            buildAnthropicBody(request)
        } else {
            buildBody(request)
        }
        val httpRequest = builder.post(body).build()
        return ChatStreamHandle(client.newCall(httpRequest), callback)
    }

    /** P34：baseUrl 可能只填到域名，统一补 /chat/completions；已带完整路径则不再重复。 */
    internal fun endpointUrl(baseUrl: String): String = chatCompletionsUrl(baseUrl)

    private fun buildBody(request: ChatStreamRequest): RequestBody {
        val messages = JSONArray()
        request.messages.forEach { message ->
            val obj = JSONObject().put("role", message.role)
            // 工具结果消息的 content 才是正文；assistant + tool_calls 时 content 可以为空
            if (message.role == "tool") {
                obj.put("content", message.content)
                message.toolCallId?.let { obj.put("tool_call_id", it) }
            } else if (message.toolCalls.isNotEmpty()) {
                obj.put("content", message.content.ifEmpty { JSONObject.NULL.toString() })
                val calls = JSONArray()
                message.toolCalls.forEach { call ->
                    calls.put(
                        JSONObject()
                            .put("id", call.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", call.name)
                                    .put("arguments", call.arguments),
                            ),
                    )
                }
                obj.put("tool_calls", calls)
            } else {
                obj.put("content", message.content)
            }
            messages.put(obj)
        }

        val json = JSONObject()
            .put("model", request.model)
            .put("messages", messages)
            .put("stream", true)
            .put("max_tokens", request.maxTokens)
            .put("temperature", request.temperature)
            // P211：OpenAI 兼容流默认不回 usage，要显式要；
            // DeepSeek / OpenAI 都认这个字段，最后一帧会带 usage
            .put("stream_options", JSONObject().put("include_usage", true))

        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                tools.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("parameters", tool.parameters.toJsonObject()),
                        ),
                )
            }
            json.put("tools", tools)
            json.put("tool_choice", request.toolChoice)
        }

        return json.toString().toRequestBody("application/json".toMediaType())
    }

    /** 把 JSON Schema 片段转成 JSONObject（org.json 不认 Kotlin Map）。 */
    private fun Map<String, Any?>.toJsonObject(): JSONObject {
        val out = JSONObject()
        forEach { (key, value) -> out.put(key, value.toJsonValue()) }
        return out
    }

    /**
     * P209：Anthropic 的 `/v1/messages` 请求体。
     *
     * 和 OpenAI 的差别：
     * - system 是**独立字段**，不能放在 messages 里；
     * - 工具结果的 shape 是 user 消息里的 `tool_result` 块，不是 `role=tool`；
     * - assistant 的工具调用是 `tool_use` 块（`input` 是对象，不是 JSON 字符串）；
     * - 工具声明用 `input_schema`，`tool_choice` 是 `{type: auto|any}`。
     */
    internal fun buildAnthropicBody(request: ChatStreamRequest): RequestBody {
        val json = JSONObject()
            .put("model", request.model)
            .put("max_tokens", request.maxTokens)
            .put("temperature", request.temperature)
            .put("stream", true)

        val system = request.messages
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }
            .trim()
        if (system.isNotEmpty()) json.put("system", system)

        json.put("messages", anthropicMessages(request.messages))

        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                val schema = tool.parameters.toJsonObject()
                if (!schema.has("type")) schema.put("type", "object")
                tools.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("input_schema", schema),
                )
            }
            json.put("tools", tools)
            // OpenAI 的 required ↔ Anthropic 的 any（都必须调工具）
            val choice = if (request.toolChoice == "required") "any" else "auto"
            json.put("tool_choice", JSONObject().put("type", choice))
        }

        return json.toString().toRequestBody("application/json".toMediaType())
    }

    /**
     * P209：把内部的「OpenAI 形态消息」转成 Anthropic 的 content blocks。
     *
     * - 相邻同角色的消息合并成一条（Anthropic 要求 user/assistant 交替）；
     * - `role = tool` 的消息并入下一条 user 消息的 `tool_result` 块。
     */
    internal fun anthropicMessages(messages: List<ChatMessage>): JSONArray {
        val out = JSONArray()
        var role: String? = null
        var blocks: JSONArray? = null

        fun flush() {
            val r = role
            val b = blocks
            if (r != null && b != null && b.length() > 0) {
                out.put(JSONObject().put("role", r).put("content", b))
            }
            role = null
            blocks = null
        }

        fun begin(next: String) {
            if (role == next && blocks != null) return
            flush()
            role = next
            blocks = JSONArray()
        }

        messages.forEach { message ->
            when (message.role) {
                "system" -> Unit

                "tool" -> {
                    begin("user")
                    blocks!!.put(
                        JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", message.toolCallId.orEmpty())
                            .put("content", message.content),
                    )
                }

                "assistant" -> {
                    begin("assistant")
                    if (message.content.isNotEmpty()) {
                        blocks!!.put(JSONObject().put("type", "text").put("text", message.content))
                    }
                    message.toolCalls.forEach { call ->
                        blocks!!.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", parseToolArguments(call.arguments)),
                        )
                    }
                }

                else -> {
                    begin("user")
                    blocks!!.put(JSONObject().put("type", "text").put("text", message.content))
                }
            }
        }
        flush()
        return out
    }

    /** 工具参数是模型拼的 JSON 文本；解析不了就给空对象，别把整轮对话搞崩。 */
    private fun parseToolArguments(raw: String): JSONObject =
        runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())

    private fun Any?.toJsonValue(): Any = when (this) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val obj = JSONObject()
            this.forEach { (k, v) -> if (k != null) obj.put(k.toString(), v.toJsonValue()) }
            obj
        }
        is List<*> -> {
            val arr = JSONArray()
            this.forEach { arr.put(it.toJsonValue()) }
            arr
        }
        else -> this
    }
}

/** 一个进行中的流；cancel() 是幂等的，且取消后不再回调。 */
class ChatStreamHandle internal constructor(
    private val call: Call,
    private val callback: ChatStreamCallback,
) {

    @Volatile
    var isCancelled: Boolean = false
        private set

    fun start() {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isCancelled) return
                callback.onError(ChatError.Network(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (isCancelled) {
                    response.close()
                    return
                }
                response.use { r ->
                    ChatStreamLog.d("HTTP ${r.code} for ${r.request.url}")
                    if (!r.isSuccessful) {
                        val raw = runCatching { r.body?.string() }.getOrNull().orEmpty()
                        // P217：错误体可能回显用户内容，日志只记长度，不记正文
                        ChatStreamLog.d("HTTP error body: ${raw.length} chars")
                        if (isCancelled) return
                        callback.onError(mapHttpError(r.code, raw))
                        return
                    }
                    val source = r.body?.source()
                    if (source == null) {
                        if (!isCancelled) callback.onError(ChatError.Network(IOException("空响应体")))
                        return
                    }
                    readLoop(source)
                }
            }
        })
    }

    private fun readLoop(source: okio.BufferedSource) {
        val block = StringBuilder()
        val received = StringBuilder()
        var completed = false
        // P209：Anthropic 的用量分散在两个事件里，先记下来，合成 Done 时一起带上
        var promptTokens: Int? = null
        var completionTokens: Int? = null

        try {
            while (!isCancelled) {
                val line = source.readUtf8Line() ?: break
                // P217：不打印首帧内容（可能是模型回复或用户内容）；调试只需要知道流开了
                if (line.startsWith("data:") && received.isEmpty()) {
                    ChatStreamLog.d("first data line received (${line.length} chars)")
                }
                if (line.isEmpty()) {
                    if (block.isNotEmpty()) {
                        val text = block.toString()
                        block.setLength(0)
                        for (event in SseParser.parseEvents(text)) {
                            when (event) {
                                is SseEvent.Error -> {
                                    if (!isCancelled) callback.onError(ChatError.Upstream(event.message))
                                    return
                                }

                                is SseEvent.Delta -> {
                                    received.append(event.text)
                                    if (!isCancelled) callback.onEvent(event)
                                }

                                is SseEvent.ToolCallsDelta -> {
                                    if (!isCancelled) callback.onToolCalls(event.fragments)
                                }

                                is SseEvent.Usage -> {
                                    event.promptTokens?.let { promptTokens = it }
                                    event.completionTokens?.let { completionTokens = it }
                                }

                                is SseEvent.EndOfStream -> {
                                    // 契约：有累计文本、未取消、未出错 → 按完成处理
                                    if (!isCancelled && !completed && received.isNotEmpty()) {
                                        completed = true
                                        callback.onEvent(
                                            SseEvent.Done(
                                                reply = received.toString(),
                                                latencyMs = null,
                                                promptTokens = promptTokens,
                                                completionTokens = completionTokens,
                                            )
                                        )
                                    }
                                }

                                is SseEvent.Done -> {
                                    completed = true
                                    if (!isCancelled) callback.onEvent(event)
                                }

                                is SseEvent.Start -> {
                                    event.promptTokens?.let { promptTokens = it }
                                    if (!isCancelled) callback.onEvent(event)
                                }
                            }
                        }
                    }
                } else {
                    block.append(line).append('\n')
                }
            }
        } catch (e: IOException) {
            if (isCancelled) return
            callback.onError(ChatError.Network(e))
            return
        } catch (e: Exception) {
            // P215：回调或解析里冒出的 RuntimeException 不能让流静默断掉。
            // 上层是在 suspendCancellableCoroutine 里等 onError / onClosed，
            // 这里不回调的话，协程会一直挂着（直到用户手动取消 / 离开页面）。
            if (isCancelled) return
            callback.onError(ChatError.Network(e))
            return
        }
        if (!isCancelled) callback.onClosed()
    }

    fun cancel() {
        isCancelled = true
        call.cancel()
    }

    private fun mapHttpError(code: Int, raw: String): ChatError {
        // OpenAI：{"error":{"message":…}}；Anthropic：{"type":"error","error":{"type":…,"message":…}}
        val detail = runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }
                ?: obj.optString("error").takeIf { it.isNotEmpty() }
                ?: raw
        }.getOrDefault(raw)
        return when (code) {
            401, 403 -> ChatError.InvalidKey(code, detail)
            429 -> ChatError.Busy(code, detail)
            else -> ChatError.Server(code, detail)
        }
    }
}

/**
 * P217：流式客户端的日志出口。
 *
 * 生产写 logcat；测试替换 [sink] 收集日志，用来断言日志里没有用户消息 / 模型回复正文。
 * **只允许记录元信息**（URL、状态码、长度）；SSE 行、错误体原文都不要塞进来——
 * 上线后 logcat 可能被其他工具读取，用户内容不该留在那里。
 */
internal object ChatStreamLog {
    @Volatile
    var sink: (String) -> Unit = { android.util.Log.d("AI-Chat", it) }

    fun d(message: String) {
        sink(message)
    }
}
