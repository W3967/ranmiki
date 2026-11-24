package com.lanweixi.core.data

import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.inject.Singleton

/**
 * T02 收尾：单例作用域断言。
 *
 * 源码级断言，防止后续卡片重新引入「自建数据库」的入口：
 * 1) 数据库提供点必须带 @Singleton；
 * 2) LanWeixiDatabase 不再暴露 create() 工厂；
 * 3) 版本号与 schema 文件一致（v2，加表走 Migration）。
 */
class DataSingletonScopeTest {

    @Test
    fun `database provider is singleton scoped`() {
        val provider = DataModule::class.java.declaredMethods
            .firstOrNull { it.name == "provideDatabase" }
        assertNotNull("DataModule.provideDatabase 必须存在", provider)
        assertNotNull(
            "provideDatabase 必须标注 @Singleton",
            provider!!.getAnnotation(Singleton::class.java),
        )
    }

    @Test
    fun `database has no self built factory`() {
        assertEquals(
            "self-built database factory must be removed",
            0,
            LanWeixiDatabase::class.java.methods.count { it.name == "create" },
        )
    }

    @Test
    fun `database version and name are fixed`() {
        // 加表/加列必须配 Migration 并升版本；文件名是跨版本契约，不允许改
        assertEquals(10, LanWeixiDatabase.VERSION)
        assertEquals("lanweixi.db", LanWeixiDatabase.NAME)
    }
}
