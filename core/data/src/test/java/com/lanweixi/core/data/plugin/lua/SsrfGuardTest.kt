package com.lanweixi.core.data.plugin.lua

import java.net.InetAddress
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P216：`host.http` 的 SSRF 防护。
 *
 * 这里不真的联网：要么在解析阶段就被拒，要么用注入的 resolver。
 */
class SsrfGuardTest {

    private fun rejected(rawUrl: String): SsrfGuard.Check.Rejected {
        val check = SsrfGuard.check(rawUrl)
        assertTrue("应拒绝 $rawUrl，实际：$check", check is SsrfGuard.Check.Rejected)
        return check as SsrfGuard.Check.Rejected
    }

    @Test
    fun `loopback is rejected`() {
        assertTrue(rejected("http://127.0.0.1/").reason.contains("内网"))
        assertTrue(rejected("http://127.1.2.3:8080/x").reason.contains("内网"))
    }

    @Test
    fun `decimal ip bypass is rejected`() {
        // 2130706433 = 127.0.0.1：老实现只查 "127." 前缀，这条能过去
        assertTrue(rejected("http://2130706433/").reason.contains("内网"))
        // 2852039166 = 169.254.169.254（云元数据）
        assertTrue(rejected("http://2852039166/latest/meta-data").reason.contains("内网"))
    }

    @Test
    fun `ipv6 loopback and mapped private are rejected`() {
        assertTrue(rejected("http://[::1]/").reason.contains("内网"))
        assertTrue(rejected("http://[::ffff:10.0.0.1]/").reason.contains("内网"))
        assertTrue(rejected("http://[fc00::1]/").reason.contains("内网"))
        assertTrue(rejected("http://[fe80::1]/").reason.contains("内网"))
    }

    @Test
    fun `private ranges are rejected`() {
        listOf(
            "http://10.0.0.1/",
            "http://172.16.0.1/",
            "http://172.31.255.1/",
            "http://192.168.0.1/",
            "http://169.254.169.254/latest/meta-data",
            "http://100.64.0.1/",
            "http://0.0.0.0/",
        ).forEach { assertTrue("应拒绝 $it", SsrfGuard.check(it) is SsrfGuard.Check.Rejected) }
    }

    @Test
    fun `localhost names are rejected without dns`() {
        assertTrue(rejected("http://localhost:8080/x").reason.contains("内网"))
        assertTrue(rejected("http://api.localhost/x").reason.contains("内网"))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertTrue(rejected("file:///etc/passwd").reason.contains("http"))
        assertTrue(rejected("ftp://example.com/x").reason.contains("http"))
    }

    @Test
    fun `credentials in url do not bypass the host check`() {
        assertTrue(rejected("http://user:pass@127.0.0.1/").reason.contains("内网"))
    }

    @Test
    fun `public address passes`() {
        val public = InetAddress.getByName("93.184.216.34")
        val check = SsrfGuard.check("https://example.com/", resolver = { listOf(public) })
        assertTrue("公网地址应放行，实际：$check", check is SsrfGuard.Check.Allowed)
    }

    @Test
    fun `one private address in a dns answer is enough to reject`() {
        // 解析结果里只要有一个内网 IP 就拒（防 DNS 轮询混入）
        val check = SsrfGuard.check(
            "https://example.com/",
            resolver = {
                listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("192.168.1.10"))
            },
        )
        assertTrue(check is SsrfGuard.Check.Rejected)
    }

    @Test
    fun `dns failure is rejected with a clear reason`() {
        val check = SsrfGuard.check("https://no-such-host.invalid/", resolver = { error("no dns") })
        assertTrue(check is SsrfGuard.Check.Rejected)
        assertTrue((check as SsrfGuard.Check.Rejected).reason.contains("解析失败"))
    }

    @Test
    fun `public ipv6 address is allowed`() {
        val allowed = SsrfGuard.isBlockedAddress(InetAddress.getByName("2001:4860:4860::8888"))
        assertTrue("公网 IPv6 不该被拦", !allowed)
        assertTrue("公网 IPv4 不该被拦", !SsrfGuard.isBlockedAddress(InetAddress.getByName("8.8.8.8")))
    }
}
