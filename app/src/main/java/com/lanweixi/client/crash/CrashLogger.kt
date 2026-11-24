package com.lanweixi.client.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P212：崩溃兜底。
 *
 * 不接第三方 SDK、不上传。未捕获异常先落一份文件再交给系统默认处理
 * （该崩还是崩，但现场留下了）；下次启动在设置页能看到份数并导出。
 * 只保留最近 [MAX_FILES] 份，避免无限增长。
 */
object CrashLogger {

    private const val DIR = "crash"
    private const val MAX_FILES = 5

    /** 在 Application 里装一次；重复调用会链在已有 handler 后面。 */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val text = buildString {
            appendLine("time: " + Date())
            appendLine("thread: " + thread.name)
            appendLine("app: " + appVersion(context))
            appendLine("device: " + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE)
            appendLine()
            append(throwable.stackTraceToString())
        }
        runCatching { File(dir, "crash-$stamp.txt").writeText(text) }
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { runCatching { it.delete() } }
    }

    fun list(context: Context): List<File> {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }

    fun count(context: Context): Int = list(context).size

    /** 把最近几份拼成一段纯文本，给「导出诊断日志」用。 */
    fun exportText(context: Context): String {
        val files = list(context)
        if (files.isEmpty()) return "（没有崩溃记录）\n"
        return buildString {
            appendLine("# 崩溃日志（最近 ${files.size} 份）")
            appendLine("导出时间：" + Date())
            appendLine()
            files.forEach { file ->
                appendLine("## " + file.name)
                appendLine(runCatching { file.readText() }.getOrDefault("（读取失败）"))
                appendLine()
            }
        }
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName + " (" + info.longVersionCode + ")"
    }.getOrDefault("unknown")
}
