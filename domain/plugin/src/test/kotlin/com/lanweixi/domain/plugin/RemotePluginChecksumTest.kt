package com.lanweixi.domain.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P216：远程插件索引里的 sha256 必须是有效的 64 位十六进制。 */
class RemotePluginChecksumTest {

    private fun plugin(sha256: String) = RemotePlugin(
        id = "com.demo.plugin",
        name = "演示",
        version = "1.0.0",
        downloadUrl = "https://example.com/p.zip",
        sha256 = sha256,
    )

    @Test
    fun `64 hex chars is valid`() {
        assertTrue(plugin("a".repeat(64)).checksumValid())
        assertTrue(plugin("0123456789abcdef".repeat(4)).checksumValid())
        assertTrue("大写也要接受", plugin("ABCDEF0123456789".repeat(4)).checksumValid())
    }

    @Test
    fun `missing checksum is invalid`() {
        assertFalse(plugin("").checksumValid())
        assertFalse(plugin("   ").checksumValid())
    }

    @Test
    fun `wrong length or non hex is invalid`() {
        assertFalse(plugin("a".repeat(63)).checksumValid())
        assertFalse(plugin("a".repeat(65)).checksumValid())
        assertFalse(plugin("g".repeat(64)).checksumValid())
        assertFalse(plugin("sha256:" + "a".repeat(64)).checksumValid())
    }
}
