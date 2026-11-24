package com.lanweixi.domain.prompt

/**
 * P195：群聊会话。
 *
 * 兰未晞固定在每个群里（主持人，不可退），其他都是工具人。
 * 消息表已有 `sessionId` 与 `speaker` 字段——
 * `sessionId` 存群 id，`speaker` 存发言的 agent id（空串 = 用户）。
 */
data class GroupSession(
    val id: String,
    val title: String,
    /** 成员 agent id 列表。兰未晞不在这个列表里——她是隐含的主持人。 */
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
