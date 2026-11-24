package com.lanweixi.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * P201：OpenAI 兼容接口的 `/chat/completions` 地址拼接。
 *
 * 设置页的 BaseURL 常只填到域名，这里统一补路径；
 * 流式客户端与连通性探针共用同一份规则，避免两处拼出不同地址。
 */
fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "https://api.deepseek.com/chat/completions"
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

/**
 * P201：配置引导里的连通性测试结果。
 *
 * 每一类都对应一句用户能照做的话，不要在 UI 层再翻译一遍。
 */
sealed interface ProbeResult {
    /** 服务端正常回了 200。 */
    data class Ok(val detail: String) : ProbeResult

    /** 401/403：Key 无效或没权限。 */
    data class InvalidKey(val detail: String) : ProbeResult

    /** 429：限流或余额/配额问题（部分服务商把余额不足也归到 429）。 */
    data class Busy(val detail: String) : ProbeResult

    /** 402：明确的余额不足。 */
    data class NoBalance(val detail: String) : ProbeResult

    /** 404：BaseURL 少了 /v1 之类，或者模型名不对。 */
    data class WrongEndpoint(val detail: String) : ProbeResult

    /** 5xx 或其他 4xx。 */
    data class ServerError(val code: Int, val detail: String) : ProbeResult

    /** 没连上：超时 / DNS / 证书。 */
    data class Network(val detail: String) : ProbeResult

    /** Key 里有中文或全角字符（输入法猜词造成），本地就拦掉，不发请求。 */
    data object InvalidKeyChars : ProbeResult
}

/**
 * P201：真发一次请求验证服务商配置。
 *
 * - 非流式、`max_tokens=1`，只验证「这个 Key + 这个地址 + 这个模型」能不能通；
 * - 自己的超时比对话短（默认 15s），配置页等太久用户会以为卡死；
 * - 不写日志、不打 Key。
 */
class ConnectivityProbe @Inject constructor(
    private val client: OkHttpClient,
) {

    suspend fun probe(
        baseUrl: String,
        apiKey: String,
        model: String,
        timeoutMs: Long = 15_000,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        // 百度/vivo 输入法会把 sk-test 猜成「上课－test」（'-' 变全角）。
        // 这种 Key 发出去 OkHttp 会抛 IllegalArgumentException，不如本地说清楚。
        if (key.any { it.code !in 33..126 }) {
            return@withContext ProbeResult.InvalidKeyChars
        }

        val protocol = protocolOf(baseUrl)
        val json = JSONObject()
            .put("model", model.trim().ifBlank { defaultModelFor(protocol) })
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", "ping")),
            )
            .put("max_tokens", 1)
            .put("stream", false)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val builder = if (protocol == ChatProtocol.ANTHROPIC) {
            Request.Builder()
                .url(messagesUrl(baseUrl))
                .header("x-api-key", key)
                .header("anthropic-version", ANTHROPIC_VERSION)
        } else {
            Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .header("Authorization", "Bearer $key")
        }
        val request = builder
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val oneShot = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        try {
            oneShot.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ProbeResult.Ok("HTTP " + response.code)
                } else {
                    val raw = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    mapHttpError(response.code, raw)
                }
            }
        } catch (e: IOException) {
            ProbeResult.Network(humanNetworkError(e))
        }
    }

    /** P209：模型名留空时给个该协议的默认值。 */
    private fun defaultModelFor(protocol: ChatProtocol): String = when (protocol) {
        ChatProtocol.ANTHROPIC -> "claude-3-5-sonnet-latest"
        ChatProtocol.OPENAI -> "deepseek-chat"
    }

    private fun mapHttpError(code: Int, raw: String): ProbeResult {
        val detail = errorDetail(raw)
        return when (code) {
            401, 403 -> ProbeResult.InvalidKey(detail)
            402 -> ProbeResult.NoBalance(detail)
            429 -> ProbeResult.Busy(detail)
            404 -> ProbeResult.WrongEndpoint(detail)
            else -> ProbeResult.ServerError(code, detail)
        }
    }

    /** 从 DeepSeek/OpenAI 风格的错误体里取 message；取不到就用原文前 300 字。 */
    private fun errorDetail(raw: String): String {
        if (raw.isBlank()) return ""
        val fromJson = runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return (fromJson ?: raw).take(300)
    }

    private fun humanNetworkError(e: IOException): String {
        val text = e.message.orEmpty()
        return when {
            e is java.net.SocketTimeoutException || text.contains("timeout", ignoreCase = true) ->
                "连接超时，检查网络或 BaseURL 是否正确"

            e is java.net.UnknownHostException ->
                "域名解析失败，BaseURL 可能填错了"

            text.contains("SSL", ignoreCase = true) || text.contains("certificate", ignoreCase = true) ->
                "证书校验失败，检查网络代理或被劫持"

            text.isBlank() -> e.javaClass.simpleName
            else -> text
        }
    }
}
