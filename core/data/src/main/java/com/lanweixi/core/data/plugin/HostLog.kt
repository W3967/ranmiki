package com.lanweixi.core.data.plugin

/**
 * P189：宿主日志环形缓冲。
 *
 * 插件排在沙箱里跑，出问题时用户拿不到任何线索——
 * 所以宿主把关键事件记在内存里（不落盘、不占空间），
 * 授权 `log:subscribe_host` 的插件可以读最近的若干条。
 *
 * 只在内存里，进程结束就没。要长期排查应该接 logcat。
 */
data class HostLogEntry(
    val at: Long,
    val level: String,
    val tag: String,
    val message: String,
) {
    fun render(): String = "$level/$tag: $message"
}

object HostLog {

    private const val MAX_ENTRIES = 300

    private val buffer = ArrayDeque<HostLogEntry>(MAX_ENTRIES)

    @Synchronized
    fun add(level: String, tag: String, message: String) {
        if (buffer.size >= MAX_ENTRIES) {
            buffer.removeFirst()
        }
        buffer.addLast(
            HostLogEntry(
                at = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message.take(500),
            ),
        )
    }

    fun i(tag: String, message: String) = add("I", tag, message)

    fun w(tag: String, message: String) = add("W", tag, message)

    fun e(tag: String, message: String) = add("E", tag, message)

    /** 最近 [limit] 条，按时间正序（旧的在前）。 */
    @Synchronized
    fun recent(limit: Int = 50): List<HostLogEntry> {
        val n = limit.coerceIn(1, MAX_ENTRIES)
        return buffer.toList().takeLast(n)
    }

    @Synchronized
    fun clear() = buffer.clear()
}
