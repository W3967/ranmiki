package com.lanweixi.feature.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P217：头像调试日志的大小上限。 */
class AvatarDebugLogTest {

    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("avatar-log").toFile()

    @Test
    fun `appends while under the cap`() {
        val dir = tempDir()
        try {
            appendAvatarDebugLog(dir, "a\n")
            appendAvatarDebugLog(dir, "b\n")

            assertEquals("a\nb\n", File(dir, "avatar_debug.log").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `resets the file when it grows past the cap`() {
        val dir = tempDir()
        try {
            val file = File(dir, "avatar_debug.log")
            file.writeText("x".repeat((AVATAR_LOG_MAX_BYTES + 1).toInt()))

            appendAvatarDebugLog(dir, "new\n")

            assertTrue("超上限要清空重开，实际 ${file.length()} 字节", file.length() < AVATAR_LOG_MAX_BYTES)
            assertEquals("new\n", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `write failure does not throw`() {
        // 目录不存在：runCatching 兜住，不影响界面
        appendAvatarDebugLog(File("/definitely/not/a/dir"), "x\n")
    }
}
