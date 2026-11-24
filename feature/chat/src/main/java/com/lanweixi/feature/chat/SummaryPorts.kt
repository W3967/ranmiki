package com.lanweixi.feature.chat

/**
 * P210：上下文压缩的端口。
 *
 * 真正的实现放在 `:app`（要调模型、要读写消息表与摘要表）；
 * 这里只留两个小接口，让 ConversationEngine / GroupChatEngine / ViewModel
 * 都不直接依赖网络层，单测塞假实现就行。
 */
interface SummaryEngine {
    /**
     * 达到阈值就压缩一次。
     *
     * @return true = 真的生成了新摘要（调用方据此决定要不要刷新 UI）
     */
    suspend fun maybeCompress(sessionId: String): Boolean
}

/** 读某个会话当前的摘要；没有就返回 null。 */
fun interface SummaryProvider {
    suspend fun summaryOf(sessionId: String): String?
}
