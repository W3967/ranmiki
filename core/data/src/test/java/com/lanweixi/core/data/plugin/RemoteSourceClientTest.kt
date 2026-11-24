package com.lanweixi.core.data.plugin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.domain.plugin.RemotePlugin
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P216：远程插件源强制 sha256。
 *
 * 这组用例都不联网：校验值不合格时必须在发起连接之前就拒绝。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteSourceClientTest {

    private lateinit var targetDir: File
    private val client = RemoteSourceClient()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        targetDir = File(context.cacheDir, "remote_test_" + System.nanoTime())
    }

    private fun plugin(sha256: String) = RemotePlugin(
        id = "com.demo.plugin",
        name = "演示",
        version = "1.0.0",
        downloadUrl = "https://example.com/plugin.zip",
        sha256 = sha256,
    )

    @Test
    fun `download refuses a plugin without checksum`() {
        var error: String? = null

        val file = client.download(plugin(""), targetDir) { error = it }

        assertNull(file)
        assertTrue("错误要说明缺校验值，实际：$error", error.orEmpty().contains("sha256"))
        assertTrue("不应该落任何文件", targetDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `download refuses a malformed checksum`() {
        var error: String? = null

        val file = client.download(plugin("not-a-sha256"), targetDir) { error = it }

        assertNull(file)
        assertTrue(error.orEmpty().contains("sha256"))
        assertTrue(targetDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `download refuses an empty target even with valid checksum shape`() {
        // 有效格式的校验值会进入下载流程；这里给不可解析的地址，确认它是按「下载失败」回来，
        // 而不是先落一个空文件。不涉及真实网络。
        var error: String? = null
        val bad = plugin("a".repeat(64)).copy(downloadUrl = "not a url")

        val file = client.download(bad, targetDir) { error = it }

        assertNull(file)
        assertTrue(targetDir.listFiles().isNullOrEmpty())
        assertTrue("要给出失败原因", !error.isNullOrBlank())
    }
}
