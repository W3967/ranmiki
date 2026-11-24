package com.lanweixi.domain.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P167：版本比较。 */
class PluginVersionTest {

    @Test
    fun `patch bump is newer`() {
        assertTrue(PluginVersion.isNewer("1.0.1", "1.0.0"))
        assertFalse(PluginVersion.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `multi digit segments compare numerically not lexically`() {
        // 字符串比较会认为 "10" < "9"，这里必须按数字
        assertTrue(PluginVersion.isNewer("1.2.10", "1.2.9"))
        assertTrue(PluginVersion.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertEquals(0, PluginVersion.compare("2.0", "2.0.0"))
        assertEquals(0, PluginVersion.compare("2", "2.0.0"))
        assertTrue(PluginVersion.isNewer("2.0.1", "2.0"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertEquals(0, PluginVersion.compare("v1.2.3", "1.2.3"))
        assertTrue(PluginVersion.isNewer("v1.3.0", "1.2.9"))
    }

    @Test
    fun `prerelease is older than release`() {
        assertTrue(PluginVersion.isNewer("1.0.0", "1.0.0-beta"))
        assertFalse(PluginVersion.isNewer("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(PluginVersion.isNewer("1.0.0", "1.0.0"))
        assertEquals(0, PluginVersion.compare("1.0.0", "1.0.0"))
    }

    @Test
    fun `blank strings are treated as zero`() {
        assertEquals(0, PluginVersion.compare("", "0.0.0"))
        assertTrue(PluginVersion.isNewer("1.0.0", ""))
    }
}
