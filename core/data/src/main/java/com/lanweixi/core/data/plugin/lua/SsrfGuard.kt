package com.lanweixi.core.data.plugin.lua

import java.net.InetAddress
import java.net.URL

/**
 * P216：`host.http` 的 SSRF 防护。
 *
 * 原来的实现只按 host 字符串前缀拦（`127.` / `192.168.` / `10.` ...），三类绕过：
 * - **十进制 IP**：`http://2130706433/` 就是 127.0.0.1；
 * - **IPv6**：`http://[::1]/`、`http://[::ffff:10.0.0.1]/` 完全不在前缀规则里；
 * - **重定向**：外网地址 302 到 `http://169.254.169.254/`，自动跟随就跳进内网。
 *
 * 现在把 host 解析成真实 [InetAddress] 再判断，连接侧禁止自动跟随重定向。
 * 残余风险：检查与连接是两次 DNS 解析（DNS rebinding），Android 的
 * `HttpURLConnection` 不暴露对端 IP，做不到彻底消除；插件本来也有 network 授权。
 */
object SsrfGuard {

    sealed interface Check {
        data class Allowed(val url: URL) : Check

        data class Rejected(val reason: String) : Check
    }

    /**
     * 校验 URL。
     *
     * @param resolver 测试注入用；默认走系统 DNS。
     */
    fun check(
        rawUrl: String,
        resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): Check {
        val url = runCatching { URL(rawUrl) }.getOrNull()
            ?: return Check.Rejected("无效地址：" + rawUrl.take(120))
        if (url.protocol !in listOf("http", "https")) {
            return Check.Rejected("只支持 http/https")
        }
        val host = url.host.orEmpty().trim().removeSurrounding("[", "]")
        if (host.isEmpty()) {
            return Check.Rejected("无效地址：" + rawUrl.take(120))
        }
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", ignoreCase = true)) {
            return Check.Rejected("不允许访问内网地址：$host")
        }
        // 十进制/八进制 IP、IPv6 字面量都会在这里变成真实地址
        val addresses = runCatching { resolver(host) }.getOrNull()
            ?: return Check.Rejected("域名解析失败：$host")
        if (addresses.isEmpty()) {
            return Check.Rejected("域名解析失败：$host")
        }
        if (addresses.any { isBlockedAddress(it) }) {
            return Check.Rejected("不允许访问内网地址：$host")
        }
        return Check.Allowed(url)
    }

    /** 回环 / 私网 / 链路本地 / 组播 / 保留段一律拦。 */
    fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 16) {
            // ::ffff:a.b.c.d —— Java 有时给 Inet6Address，按 IPv4 再判一次
            ipv4Mapped(bytes)?.let { return isBlockedIpv4(it) }
            // fc00::/7（IPv6 唯一本地地址，相当于内网）
            val first = bytes[0].toInt() and 0xFF
            return (first and 0xFE) == 0xFC
        }
        return isBlockedIpv4(bytes)
    }

    private fun ipv4Mapped(bytes: ByteArray): ByteArray? {
        for (i in 0 until 10) {
            if (bytes[i].toInt() != 0) return null
        }
        if ((bytes[10].toInt() and 0xFF) != 0xFF || (bytes[11].toInt() and 0xFF) != 0xFF) return null
        return bytes.copyOfRange(12, 16)
    }

    private fun isBlockedIpv4(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return true
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        return when {
            a == 0 -> true                    // 0.0.0.0/8
            a == 10 -> true                   // 10/8
            a == 127 -> true                  // 回环
            a == 169 && b == 254 -> true      // 链路本地（云元数据 169.254.169.254）
            a == 172 && b in 16..31 -> true   // 172.16/12
            a == 192 && b == 168 -> true      // 192.168/16
            a == 100 && b in 64..127 -> true  // 运营商级 NAT 100.64/10
            a >= 224 -> true                  // 组播 + 保留
            else -> false
        }
    }
}
