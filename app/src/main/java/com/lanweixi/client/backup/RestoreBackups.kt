package com.lanweixi.client.backup

import java.io.File

/**
 * P190/P217：备份导入前的自动留底。
 *
 * 导入会整体替换数据，所以每次导入前先把当前状态写一份
 * `filesDir/restore-backup-<时间戳>.json`，误操作时有东西可恢复。
 *
 * P217：原来只限制「最多 3 份」，文件本身是明文 JSON（含聊天记录），
 * 一直躺在私有目录里。现在再加 7 天保留期——超期的直接删，
 * 用户真要恢复也不会等一周以上。
 *
 * 说明：文件在应用私有目录（其他应用不可读），所以不额外加密；
 * 真正的边界是「别长期堆积」。
 */
object RestoreBackups {

    const val KEEP = 3

    const val MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 写一份留底并清理旧文件。返回新写出的文件。 */
    fun save(dir: File, json: String, now: Long = System.currentTimeMillis()): File {
        val file = File(dir, "restore-backup-$now.json")
        file.writeText(json)
        prune(dir, now)
        return file
    }

    /**
     * 清理：按文件名（时间戳）倒序最多留 [KEEP] 份；超过 [MAX_AGE_MS] 的一律删。
     *
     * @return 最终保留的文件
     */
    fun prune(dir: File, now: Long = System.currentTimeMillis()): List<File> {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()
        val kept = files.take(KEEP)
        files.forEach { file ->
            val expired = now - file.lastModified() > MAX_AGE_MS
            if (expired || file !in kept) file.delete()
        }
        return kept.filter { now - it.lastModified() <= MAX_AGE_MS }
    }

    private const val PREFIX = "restore-backup-"
}
