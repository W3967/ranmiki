package com.lanweixi.client.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * P199：每小时检查一次到期任务。
 *
 * 只负责「被调度」——判定规则在 [com.lanweixi.core.data.reminder.TaskReminderPlanner]，
 * 执行细节在 [TaskReminderRunner]（设置页手动触发走的是同一份）。
 */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 失败也不该重试：提醒是「有就发、没有就过」，下一小时还会再查
        runCatching { TaskReminderRunner.run(applicationContext) }
        return Result.success()
    }
}
