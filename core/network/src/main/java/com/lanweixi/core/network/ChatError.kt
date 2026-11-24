package com.lanweixi.core.network

/**
 * 方案第 6 节：错误映射成用户能懂的几类，分别对应不同下一步动作。
 */
sealed class ChatError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 网络不可达：提示检查网络后重试。 */
    class Network(cause: Throwable) : ChatError("网络不可达：${cause.message ?: cause.javaClass.simpleName}", cause)

    /** 401/403：密钥无效或余额不足。 */
    class InvalidKey(val status: Int, detail: String) : ChatError("密钥无效或余额不足：$detail")

    /** 429：模型侧繁忙。 */
    class Busy(val status: Int, detail: String) : ChatError("服务繁忙：$detail")

    /** 其它 HTTP 错误。 */
    class Server(val status: Int, detail: String) : ChatError("服务异常（$status）：$detail")

    /** 流中途的 error 事件。 */
    class Upstream(detail: String) : ChatError("对话中断：$detail")

    /** 连接被取消（调用方主动停止，不算失败）。 */
    class Cancelled : ChatError("已取消")
}
