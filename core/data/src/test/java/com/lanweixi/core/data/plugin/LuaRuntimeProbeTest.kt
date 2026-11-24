package com.lanweixi.core.data.plugin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * P140 前置：验证 LuaJ 能不能在 Android 运行时（Robolectric 模拟）上跑起来。
 *
 * LuaJ 的 JsePlatform 依赖 java.beans / javax.script 这类 Java SE 才有的东西，
 * Android 上不一定全有——这条测试就是用来探雷的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuaRuntimeProbeTest {

    @Test
    fun `can evaluate arithmetic`() {
        val globals = JsePlatform.standardGlobals()
        val chunk = globals.load("return 1 + 1", "probe")
        assertEquals("2", chunk.call().tojstring())
    }

    @Test
    fun `can call host function from lua`() {
        val globals = JsePlatform.standardGlobals()
        // 模拟宿主注入一个函数
        globals.set(
            "host_query",
            object : org.luaj.vm2.lib.VarArgFunction() {
                override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                    val q = args.arg1().tojstring()
                    return LuaValue.valueOf("你查了：" + q)
                }
            },
        )
        val result = globals.load("return host_query('笔记')", "probe").call()
        assertEquals("你查了：笔记", result.tojstring())
    }

    @Test
    fun `can work with tables as json-ish data`() {
        val globals = JsePlatform.standardGlobals()
        val script = """
            local t = { title = "W日特", priority = 1 }
            return t.title .. "/" .. tostring(t.priority)
        """.trimIndent()
        assertEquals("W日特/1", globals.load(script, "probe").call().tojstring())
    }

    @Test
    fun `android context is reachable but lua itself needs no android apis`() {
        // 只需要能让 Robolectric 初始化；真正跑脚本时不碰 Android API
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.packageName.isNotEmpty())

        val globals = JsePlatform.standardGlobals()
        val ok = globals.load("return type(string) == 'table'", "probe").call()
        assertEquals(true, ok.toboolean())
    }
}