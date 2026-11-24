package com.lanweixi.client.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * P199：任务提醒的调度。
 *
 * 用 WorkManager 的周期任务（1 小时一次）而不是精确闹钟：
 * 任务提醒本来就不需要秒级精确，还省掉 Android 12+ 的 SCHEDULE_EXACT_ALARM 授权麻烦。
 */
object TaskReminderScheduler {

    private const val UNIQUE_NAME = "task_due_reminder"

    /** 已提醒过的任务 id（SharedPreferences，不占数据库表）。 */
    const val PREFS = "task_reminders"
    const val KEY_NOTIFIED = "notified_ids"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
