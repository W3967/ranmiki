package com.lanweixi.core.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T03 收尾：单例作用域断言。
 *
 * 这里做的是源码级断言（注解 + 构造签名），用来防止后续卡片
 * 重新引入「自建 OkHttpClient」或「每次 new 客户端」的写法：
 * 1) OkHttpClient 的提供点必须带 @Singleton；
 * 2) ChatStreamClient 只能通过注入拿到客户端；
 * 3) 不再存在自建客户端的工厂方法。
 */
class SingletonScopeTest {

    @Test
    fun `okhttp client provider is singleton scoped`() {
        val provider = NetworkModule::class.java.declaredMethods
            .firstOrNull { it.name == "provideOkHttpClient" }
        assertNotNull("NetworkModule.provideOkHttpClient 必须存在", provider)
        assertNotNull(
            "provideOkHttpClient 必须标注 @Singleton",
            provider!!.getAnnotation(Singleton::class.java),
        )
    }

    @Test
    fun `chat stream client takes injected okhttp client only`() {
        val ctor = ChatStreamClient::class.java.constructors.single()
        assertEquals(
            "ChatStreamClient 的构造参数必须只有 OkHttpClient",
            1,
            ctor.parameterTypes.size,
        )
        assertEquals(OkHttpClient::class.java, ctor.parameterTypes.single())
        assertTrue(
            "构造器必须带 @Inject，保证由 Hilt 装配",
            ctor.isAnnotationPresent(Inject::class.java),
        )
    }

    @Test
    fun `no self built client factory remains`() {
        assertEquals(
            "self-built OkHttpClient factory must be removed",
            0,
            ChatStreamClient::class.java.methods.count { it.name == "defaultClient" },
        )
    }
}
