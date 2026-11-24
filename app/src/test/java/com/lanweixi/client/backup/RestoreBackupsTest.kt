package com.lanweixi.client.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P217：备份导入留底的清理策略（最多 3 份 + 7 天）。 */
class RestoreBackupsTest {

    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("restore-backup").toFile()

    @Test
    fun `save writes the backup and keeps at most three files`() {
        val dir = tempDir()
        try {
            repeat(5) { i -> RestoreBackups.save(dir, "json-$i", now = 1_000L + i) }

            val files = dir.listFiles().orEmpty().sortedBy { it.name }
            assertEquals("最多留 3 份", 3, files.size)
            // 最新 3 份（1002/1003/1004）留下，最旧的删掉
            assertTrue(files.all { it.name >= "restore-backup-1002.json" })
            assertEquals("json-4", File(dir, "restore-backup-1004.json").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `prune deletes files older than the retention window`() {
        val dir = tempDir()
        try {
            val old = RestoreBackups.save(dir, "old", now = 1_000L)
            old.setLastModified(System.currentTimeMillis() - RestoreBackups.MAX_AGE_MS - 1_000)

            val kept = RestoreBackups.prune(dir)

            assertFalse("超过 7 天的留底要删掉", old.exists())
            assertTrue(kept.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `prune keeps fresh files`() {
        val dir = tempDir()
        try {
            val fresh = RestoreBackups.save(dir, "fresh", now = System.currentTimeMillis())

            val kept = RestoreBackups.prune(dir)

            assertTrue(fresh.exists())
            assertEquals(listOf(fresh.name), kept.map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `unrelated files are ignored`() {
        val dir = tempDir()
        try {
            val other = File(dir, "avatar_debug.log").apply { writeText("x") }
            RestoreBackups.prune(dir)

            assertTrue("不能碰非留底文件", other.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
