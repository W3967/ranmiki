package com.lanweixi.domain.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** 一条等待用户决定的敏感工具调用。 */
data class PendingToolConfirm(
    val callId: String,
    val toolName: String,
    val description: String,
    val arguments: String,
) {
    /** 内部用于唤醒引擎；不参与 UI 展示。 */
    internal var deferred: CompletableDeferred<Boolean>? = null
}

/**
 * P139：敏感工具确认的单一通道。
 *
 * 引擎在 IO 线程挂起等待，UI 在主线程弹窗，用户点完再唤醒。
 * 同时只允许一条待确认——排队等着看几个弹窗比直接拒绝更糟。
 */
class ToolConfirmGateway {

    private val _pending = MutableStateFlow<PendingToolConfirm?>(null)
    val pending: StateFlow<PendingToolConfirm?> = _pending.asStateFlow()

    /**
     * 询问用户。返回 true 才执行。
     *
     * 超时（[TIMEOUT_MS]）或已有别的确认在等 → 一律按拒绝处理：
     * 模型不能靠反复请求把用户「磨」到同意。
     */
    suspend fun ask(spec: ToolSpec, call: ToolCall): Boolean {
        if (_pending.value != null) return false

        val deferred = CompletableDeferred<Boolean>()
        val request = PendingToolConfirm(
            callId = call.id,
            toolName = spec.name,
            description = spec.description,
            arguments = call.arguments,
        ).also { it.deferred = deferred }

        _pending.value = request
        return try {
            val answer = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
            answer ?: false
        } finally {
            _pending.value = null
        }
    }

    /** UI 点击后调用。 */
    fun answer(allow: Boolean) {
        _pending.value?.deferred?.complete(allow)
    }

    /** 取消当前等待（比如用户退出对话）。 */
    fun cancel() {
        _pending.value?.deferred?.complete(false)
    }

    companion object {
        /** 60 秒没人理就按拒绝处理，避免引擎一直挂着。 */
        const val TIMEOUT_MS = 60_000L
    }
}