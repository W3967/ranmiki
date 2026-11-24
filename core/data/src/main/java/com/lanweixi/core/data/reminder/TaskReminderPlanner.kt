package com.lanweixi.core.data.reminder

import com.lanweixi.core.data.entity.TaskEntity

/**
 * P199：一条该发的任务提醒。
 *
 * [overdue] 为 true 表示**已经过了截止时间**（才过了不久），false 表示「快到了」。
 */
data class TaskReminder(
    val taskId: Long,
    val title: String,
    val dueAt: Long,
    val overdue: Boolean,
)

/**
 * P199：任务提醒的判定逻辑。
 *
 * 纯函数，不碰通知和数据库——时间窗口和去重规则都在这里，方便单测。
 *
 * 两条铁律：
 *  - 同一任务只提醒一次（[alreadyNotified] 由调用方持久化）；
 *  - 过期太久的不补提醒——手机放了三天再打开，不该弹一屏旧任务。
 */
object TaskReminderPlanner {

    /** 提前多久提醒「快到了」。 */
    const val UPCOMING_WINDOW_MS = 60 * 60 * 1000L

    /** 过期多久以内还提醒；更早的算过期太久，不补。 */
    const val OVERDUE_WINDOW_MS = 24 * 60 * 60 * 1000L

    fun plan(
        tasks: List<TaskEntity>,
        alreadyNotified: Set<String>,
        now: Long,
    ): List<TaskReminder> = tasks
        .mapNotNull { task ->
            // 做完了、没设截止、提醒过的：都不是这次要发的
            if (task.done || task.dueAt <= 0L) return@mapNotNull null
            // 去重键带 dueAt：任务改期后算一次新的提醒，不会被旧记录挡住
            if (keyOf(task.id, task.dueAt) in alreadyNotified) return@mapNotNull null
            val overdue = task.dueAt <= now
            val withinWindow = if (overdue) {
                now - task.dueAt <= OVERDUE_WINDOW_MS
            } else {
                task.dueAt - now <= UPCOMING_WINDOW_MS
            }
            if (!withinWindow) return@mapNotNull null
            TaskReminder(
                taskId = task.id,
                title = task.title.ifBlank { "无标题任务" },
                dueAt = task.dueAt,
                overdue = overdue,
            )
        }
        .sortedBy { it.dueAt }

    /** 去重键：同一任务 + 同一截止时间只提醒一次。 */
    fun keyOf(taskId: Long, dueAt: Long): String = "$taskId@$dueAt"

    /**
     * P199：任务删了以后，它留下的提醒键也该清掉。
     *
     * 只删「任务已经不在待办里」的键；改期的旧键保留也无害（新键不同，不会挡住提醒）。
     */
    fun pruneKeys(notified: Set<String>, openTaskIds: Set<Long>): Set<String> =
        notified.filter { key ->
            key.substringBefore('@').toLongOrNull()?.let { it in openTaskIds } == true
        }.toSet()
}
