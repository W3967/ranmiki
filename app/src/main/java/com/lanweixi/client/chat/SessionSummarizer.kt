package com.lanweixi.client.chat

import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.core.data.repository.SessionSummaryRepository
import com.lanweixi.core.data.summary.SummaryPlanner
import com.lanweixi.core.network.ChatError
import com.lanweixi.core.network.ChatMessage
import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.feature.chat.StreamHandle
import com.lanweixi.feature.chat.StreamRunner
import com.lanweixi.feature.chat.SummaryEngine
import com.lanweixi.feature.chat.SummaryProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * P210：会话摘要——上下文压缩的实现。
 *
 * 会话超过阈值时，把「旧摘要 + 还没压过的那一段」重新压成一段短摘要，覆盖保存。
 * 单聊与群聊共用：单聊的 sessionId 是会话 id，群聊的是群 id（messages 表本来就一表存）。
 *
 * 失败策略：任何一步失败都返回 false / null，不抛异常、不影响这轮对话；
 * 下次发送再试。摘要是「锦上添花」，不能让它把聊天搞挂。
 */
class SessionSummarizer(
    private val messages: MessageRepository,
    private val summaries: SessionSummaryRepository,
    private val runner: StreamRunner,
    private val configProvider: ChatConfigProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SummaryEngine, SummaryProvider {

    override suspend fun summaryOf(sessionId: String): String? =
        runCatching { summaries.latest(sessionId)?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    override suspend fun maybeCompress(sessionId: String): Boolean {
        val last = runCatching { summaries.latest(sessionId) }.getOrNull()
        val rows = runCatching { messages.history(sessionId) }.getOrNull() ?: return false
        if (rows.isEmpty()) return false

        val plan = SummaryPlanner.plan(
            history = rows.map { SummaryPlanner.Candidate(it.id, it.role, it.text) },
            lastUntilId = last?.untilId ?: 0L,
            lastCoveredCount = last?.coveredCount ?: 0,
        ) ?: return false

        val config = runCatching { configProvider.current() }.getOrNull() ?: return false
        if (config.apiKey.isBlank()) return false

        val summary = requestSummary(config, last?.content, plan.toCompress) ?: return false
        runCatching {
            summaries.save(
                sessionId = sessionId,
                untilId = plan.untilId,
                content = summary.take(SummaryPlanner.MAX_SUMMARY_CHARS),
                coveredCount = plan.coveredCount,
            )
        }.getOrElse { return false }
        return true
    }

    /** 让模型把「旧摘要 + 新一段」压成一段。 */
    private suspend fun requestSummary(
        config: ChatConfig,
        previous: String?,
        batch: List<SummaryPlanner.Candidate>,
    ): String? {
        val dialogue = batch.joinToString("\n") { row ->
            val role = when (row.role) {
                "assistant" -> "她"
                "tool" -> "工具结果"
                else -> "用户"
            }
            "$role：${row.text.replace('\n', ' ').take(MAX_LINE_CHARS)}"
        }
        val userContent = buildString {
            val prev = previous?.trim().orEmpty()
            if (prev.isNotEmpty()) append("【已有摘要】\n").append(prev).append("\n\n")
            append("【新对话】\n").append(dialogue)
        }
        val request = ChatStreamRequest(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            messages = listOf(
                ChatMessage("system", SUMMARY_SYSTEM),
                ChatMessage("user", userContent),
            ),
            maxTokens = 400,
            temperature = 0.3,
        )
        return collectText(request)
    }

    /** 复用流式客户端，只关心拼出来的整段文本。 */
    private suspend fun collectText(request: ChatStreamRequest): String? =
        suspendCancellableCoroutine { cont ->
            val buffer = StringBuilder()
            var settled = false

            fun settle(value: String?) {
                if (settled) return
                settled = true
                if (cont.isActive) cont.resume(value)
            }

            // P215：同步抛异常（比如 baseUrl 非法让 Request.Builder().url() 抛）也必须 settle，
            // 否则 suspendCancellableCoroutine 永远不返回——群聊里 await 它，整轮发送会卡在「正在输入」。
            val handle: StreamHandle? = try {
                runner.run(
                    request,
                    object : ChatStreamCallback {
                        override fun onEvent(event: SseEvent) {
                            when (event) {
                                is SseEvent.Delta -> buffer.append(event.text)
                                is SseEvent.Done -> settle(
                                    event.reply.ifEmpty { buffer.toString() }.trim().ifEmpty { null },
                                )
                                else -> Unit
                            }
                        }

                        override fun onError(error: ChatError) = settle(null)

                        override fun onClosed() =
                            settle(buffer.toString().trim().ifEmpty { null })
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                settle(null)
                null
            }
            cont.invokeOnCancellation { runCatching { handle?.cancel() } }
        }

    private companion object {
        const val MAX_LINE_CHARS = 160

        val SUMMARY_SYSTEM = """
            你是对话摘要器。把用户给的聊天内容压缩成一段不超过 200 字的中文摘要。
            必须保留：人物与称呼、达成的约定、待办与时间、重要事实（数字、地址、偏好）、情绪转折。
            不要评价，不要复述原文，不要加标题或列表，只输出摘要正文。
        """.trimIndent()
    }
}
