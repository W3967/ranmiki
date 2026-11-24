package com.lanweixi.feature.chat

import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.ChatStreamClient
import com.lanweixi.core.network.ChatStreamRequest
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.domain.prompt.PromptDynamic

/** P37：会话摘要（id + 消息数）。 */
data class SessionSummary(
    val id: String,
    val messageCount: Int,
    /** P142：首条用户消息，当话题标签用。 */
    val preview: String = "",
    /** P142：最后一条消息的时间。 */
    val lastAt: Long = 0L,
)

/** P37：会话端口（当前会话 + 列表 + 切换/删除）。 */
interface SessionPort {
    suspend fun current(): String
    suspend fun switch(id: String)
    suspend fun list(): List<SessionSummary>
    suspend fun delete(id: String)
    /** 生成新会话 id 并切换过去。 */
    suspend fun createNew(): String
}

/** P25：事件写入端口（events 表落库用）。 */
interface EventPort {
    suspend fun append(type: String, payload: String, at: Long)
}

/** 历史消息 DTO（端口层，不依赖 Room 实体）。 */
data class HistoryMessage(
    val role: String,
    val content: String,
    val createdAt: Long,
    /** P204：消息 id（点引用定位用；fake 实现可为 0）。 */
    val id: Long = 0,
    /** P204：引用快照（空 = 无引用）。 */
    val quotePreview: String = "",
    /** P204：被引用消息 id（0 = 无引用）。 */
    val quoteId: Long = 0,
)

/**
 * P07a：只读历史端口。
 * ViewModel 只注入这个接口，编译层面拿不到 append()。
 */
interface ChatHistoryQueryPort {
    suspend fun recent(limit: Int): List<HistoryMessage>

    /** P105：跨会话的最近消息（盲评用）；默认退化为当前会话。 */
    suspend fun recentAll(limit: Int): List<HistoryMessage> = recent(limit)
}

/**
 * 完整历史端口：写入只允许 ConversationEngine 使用。
 * 生产实现 `RoomChatHistoryPort` 在 :app。
 *
 * 当前实现只支持单会话 `default`；多会话单列后续卡片，不扩 schema。
 */
interface ChatHistoryPort : ChatHistoryQueryPort {
    suspend fun append(
        role: String,
        content: String,
        sessionId: String,
        status: String,
        /** P204：引用快照（带名字），仅 user 消息使用。 */
        quoteId: Long = 0,
        quotePreview: String = "",
    )
}

/** 底层流的取消句柄。 */
interface StreamHandle {
    fun cancel()
}

/** 流式请求端口：生产用 ChatStreamClient，测试用 fake 同步驱动。 */
fun interface StreamRunner {
    fun run(request: ChatStreamRequest, callback: ChatStreamCallback): StreamHandle?
}

class ChatStreamClientRunner(private val client: ChatStreamClient) : StreamRunner {
    override fun run(request: ChatStreamRequest, callback: ChatStreamCallback): StreamHandle {
        val handle = client.open(request, callback)
        handle.start()
        return object : StreamHandle {
            override fun cancel() = handle.cancel()
        }
    }
}

/**
 * P07a：对话用例接口（仅测试 seam）。
 * 生产类型仍是 ConversationEngine；ViewModel 依赖这个接口以便 JVM 单测注入 fake。
 */
interface ConversationUseCase {
    suspend fun send(
        userText: String,
        dynamic: PromptDynamic = PromptDynamic(),
        onDelta: ((String) -> Unit)? = null,
        /** P67：本次允许连发的条数（0 表示不限制）。 */
        replyTarget: Int = 0,
        /**
         * P141：模型每次要调工具时回调（工具名 + 是否敏感）。
         *
         * 给 UI 用来显示「她查了什么」——工具调用不该是黑盒。
         */
        onToolCall: ((name: String, sensitive: Boolean) -> Unit)? = null,
        /**
         * P196：群聊用。传了就按这份配置调模型，不再读全局设置。
         * null = 走 ChatConfigProvider（单聊路径）。
         */
        overrideConfig: com.lanweixi.domain.prompt.ChatConfig? = null,
        /** P204：引用某条消息时带上（快照已含「名字：节选」，0/空 = 不引用）。 */
        quoteId: Long = 0,
        quotePreview: String = "",
    ): ConversationOutcome
}

/**
 * 主动消息调度端口（只定义，不接 WorkManager）。
 * 真正的调度归 M4；这里提供空实现与手动实现供测试/装配使用。
 */
interface EngineScheduler {
    fun scheduleIdle(action: () -> Unit)
}

object EmptyEngineScheduler : EngineScheduler {
    override fun scheduleIdle(action: () -> Unit) = Unit
}

class ManualEngineScheduler : EngineScheduler {
    private val queue = ArrayDeque<() -> Unit>()
    override fun scheduleIdle(action: () -> Unit) {
        queue.addLast(action)
    }

    fun runPending() {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }

    fun pendingCount(): Int = queue.size
}
