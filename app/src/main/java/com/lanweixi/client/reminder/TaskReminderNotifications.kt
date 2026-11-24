package com.lanweixi.client.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lanweixi.client.MainActivity
import com.lanweixi.client.R
import com.lanweixi.core.data.reminder.TaskReminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P199：任务提醒的通知发件箱。
 *
 * 单独一个渠道（用户可以在系统里单独关掉任务提醒）；
 * 点通知会打开 App 并直接进任务页（[EXTRA_ROUTE] 由 MainActivity 消费）。
 */
object TaskReminderNotifications {

    const val CHANNEL_ID = "task_reminder"

    /** 通知带过来的跳转意图；目前只有「tasks」。 */
    const val EXTRA_ROUTE = "open_route"
    const val ROUTE_TASKS = "tasks"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "任务到点或快到期时提醒"
            },
        )
    }

    /** Android 13+ 要运行时权限；更早的版本看用户有没有在系统里关掉通知。 */
    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /** 真发出去了返回 true；没权限 / 抛异常返回 false（调用方据此决定要不要记为已提醒）。 */
    fun notify(
        context: Context,
        reminder: TaskReminder,
        /** P208：用当前角色名，别在通知里写死「兰未晞」。 */
        characterName: String = "兰未晞",
    ): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)

        val whenText = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(reminder.dueAt))
        val title = if (reminder.overdue) "$characterName 说这个过期了" else "$characterName 提醒你"
        val text = if (reminder.overdue) {
            "${reminder.title} · 截止 $whenText"
        } else {
            "${reminder.title} · $whenText"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, ROUTE_TASKS)
        }
        val pending = PendingIntent.getActivity(
            context,
            reminder.taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(reminder.taskId.toInt(), notification)
            true
        }.getOrDefault(false)
    }
}
