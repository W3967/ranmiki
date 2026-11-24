package com.lanweixi.client.reminder

import android.content.Context
import com.lanweixi.core.data.reminder.TaskReminderPlanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * P199：Worker 拿数据库的入口。
 *
 * 项目没引 hilt-work（多一个依赖），用 EntryPoint 从 SingletonComponent 里取现成单例就行——
 * 千万别在 Worker 里自己 `Room.databaseBuilder`，那会和进程里那份抢同一个文件。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun database(): com.lanweixi.core.data.LanWeixiDatabase

    /** P208：通知里要用当前角色名。 */
    fun configStore(): com.lanweixi.domain.prompt.ChatConfigStore
}

/**
 * P199：跑一轮任务提醒。
 *
 * 抽成独立对象有两个好处：Worker 只负责「被调度」，设置页也能手动点一次
 * （vivo 这类系统会把 WorkManager 的作业压到一小时后，没有手动入口就没法验证）。
 */
object TaskReminderRunner {

    sealed interface Outcome {
        /** 真的发出去了几条。 */
        data class Sent(val count: Int) : Outcome

        /** 有计划但一条都没发出去——多半是没通知权限。 */
        data class Planned(val planned: Int) : Outcome

        /** 没有需要提醒的任务。 */
        data object Nothing : Outcome
    }

    suspend fun run(context: Context): Outcome {
        val app = context.applicationContext
        if (!TaskReminderNotifications.canNotify(app)) return Outcome.Nothing

        val database = EntryPointAccessors
            .fromApplication(app, ReminderEntryPoint::class.java)
            .database()
        // P208：通知抬头用角色自己的名字（用户可能改过名）
        val characterName = runCatching {
            EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java)
                .configStore().current().characterName
        }.getOrDefault(com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME)

        val prefs = app.getSharedPreferences(TaskReminderScheduler.PREFS, Context.MODE_PRIVATE)
        val tasks = database.taskDao().listOpen()
        // 键是 "任务id@截止时间"：改期后会变成新键，该重新提醒；
        // 任务删了的话，它留下的键顺手清掉，别让 SharedPreferences 一直长
        val notified = TaskReminderPlanner.pruneKeys(
            notified = prefs.getStringSet(TaskReminderScheduler.KEY_NOTIFIED, emptySet()).orEmpty(),
            openTaskIds = tasks.map { it.id }.toSet(),
        )

        val reminders = TaskReminderPlanner.plan(
            tasks = tasks,
            alreadyNotified = notified,
            now = System.currentTimeMillis(),
        )
        if (reminders.isEmpty()) {
            // 清理完还是空，顺手把修剪后的集合写回去
            prefs.edit().putStringSet(TaskReminderScheduler.KEY_NOTIFIED, notified).apply()
            return Outcome.Nothing
        }

        val sent = reminders.filter { TaskReminderNotifications.notify(app, it, characterName) }
        if (sent.isNotEmpty()) {
            prefs.edit()
                .putStringSet(
                    TaskReminderScheduler.KEY_NOTIFIED,
                    (notified + sent.map { TaskReminderPlanner.keyOf(it.taskId, it.dueAt) }).toSet(),
                )
                .apply()
        }
        return if (sent.isEmpty()) Outcome.Planned(reminders.size) else Outcome.Sent(sent.size)
    }
}
