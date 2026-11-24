package com.lanweixi.client

import android.app.Application
import com.lanweixi.client.reminder.TaskReminderScheduler
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt 应用入口：SingletonComponent 由它持有。
 * OkHttpClient 与 LanWeixiDatabase 因此各只有一份实例。
 *
 * 陪伴引擎（生命状态 / 社交动态 / 梦境 / 项目 / 记忆审计 / 前台心跳）已整体移除，
 * 这里不再注册任何后台周期任务——只保留 P199 的任务截止提醒。
 */
@HiltAndroidApp
class LanWeiXiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // P212：崩溃兜底（只落本地文件，不上传）
        runCatching { com.lanweixi.client.crash.CrashLogger.install(this) }
        // P199：任务截止提醒（WorkManager 周期任务，一小时一次；没权限时 Worker 自己会直接返回）
        android.util.Log.d("AI-Chat", "app: scheduling task reminders")
        runCatching { TaskReminderScheduler.schedule(this) }
            .onFailure { android.util.Log.e("AI-Chat", "app: schedule failed", it) }
    }
}
