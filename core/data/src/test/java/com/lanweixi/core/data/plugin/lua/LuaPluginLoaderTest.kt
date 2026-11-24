package com.lanweixi.core.data.plugin.lua

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.repository.RoomDocumentRepository
import com.lanweixi.core.data.repository.RoomNoteRepository
import com.lanweixi.core.data.repository.RoomPromptRepository
import com.lanweixi.core.data.repository.RoomTaskRepository
import com.lanweixi.domain.plugin.PluginFunction
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermissions
import com.lanweixi.domain.plugin.PluginType
import com.lanweixi.domain.tools.ToolCall
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P140：Lua 插件从加载到执行的端到端验收。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuaPluginLoaderTest {

    private lateinit var context: Context
    private lateinit var db: LanWeixiDatabase
    private lateinit var pluginDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pluginDir = File(context.cacheDir, "lua_plugin_" + System.nanoTime()).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        pluginDir.deleteRecursively()
    }

    private fun manifest(
        functions: List<PluginFunction>,
    ) = PluginManifest(
        id = "com.demo.lua",
        name = "Lua 演示",
        version = "1.0.0",
        author = "tester",
        description = "测试用",
        pluginType = listOf(PluginType.FUNCTION),
        functions = functions,
    )

    private fun loader(
        granted: Set<String> = PluginPermissions.KNOWN,
        maxInstructions: Long = LuaBudget.MAX_INSTRUCTIONS,
    ) = LuaPluginLoader(
        LuaHostApis(
            notes = RoomNoteRepository(db.noteDao()),
            tasks = RoomTaskRepository(db.taskDao()),
            documents = RoomDocumentRepository(db.documentDao()),
            prompts = RoomPromptRepository(db.promptDao()),
            granted = granted,
            logger = { },
        ),
        maxInstructions = maxInstructions,
        maxMillis = 60_000,
    )

    private fun writeScript(text: String) {
        File(pluginDir, "main.lua").writeText(text)
    }

    @Test
    fun `loads function and executes with args`() = runBlocking {
        writeScript(
            """
            function demo_hello(args)
              return "你好，" .. (args.name or "陌生人")
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "打招呼"))), emptySet())

        assertEquals(1, result.executors.size)
        assertEquals(emptyList<String>(), result.errors)

        val out = result.executors[0].execute(ToolCall("c1", "demo_hello", "{\"name\":\"未晞\"}"))
        assertEquals("你好，未晞", out.content)
        assertFalse(out.isError)
    }

    @Test
    fun `missing function is reported not crashed`() = runBlocking {
        writeScript("function other(args) return 'x' end")
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "打招呼"))), emptySet())

        assertTrue(result.executors.isEmpty())
        assertTrue(result.errors.any { it.contains("demo_hello") })
    }

    @Test
    fun `missing script file is reported`() {
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "x"))), emptySet())
        assertTrue(result.errors.any { it.contains("main.lua") })
    }

    @Test
    fun `syntax error is reported`() {
        writeScript("function demo_hello(args) return (((")
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "x"))), emptySet())
        assertTrue(result.executors.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `sandbox blocks luajava`() {
        writeScript(
            """
            function demo_probe(args)
              local ok = pcall(function()
                local rt = luajava.bindClass("java.lang.Runtime")
                return rt
              end)
              if ok then return "逃逸成功" end
              return "被拦住了"
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_probe", "试逃逸"))), emptySet())
        val out = runBlocking { result.executors[0].execute(ToolCall("c1", "demo_probe", "{}")) }
        assertTrue("不该能绑到 Java 类", out.content.contains("被拦住"))
    }

    @Test
    fun `sandbox blocks os`() {
        writeScript(
            """
            function demo_probe(args)
              local ok = pcall(function() return os.execute("id") end)
              if ok then return "os 可用" end
              return "os 不可用"
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_probe", "试 os"))), emptySet())
        val out = runBlocking { result.executors[0].execute(ToolCall("c1", "demo_probe", "{}")) }
        assertEquals("os 不可用", out.content)
    }

    @Test
    fun `sandbox blocks io`() {
        writeScript(
            """
            function demo_probe(args)
              local ok = pcall(function() return io.open("/etc/passwd") end)
              if ok then return "io 可用" end
              return "io 不可用"
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_probe", "试 io"))), emptySet())
        val out = runBlocking { result.executors[0].execute(ToolCall("c1", "demo_probe", "{}")) }
        assertEquals("io 不可用", out.content)
    }

    @Test
    fun `authorized script can query notes`() = runBlocking {
        db.noteDao().insert(
            com.lanweixi.core.data.entity.NoteEntity(
                title = "Kotlin 协程", content = "挂起函数", createdAt = 1, updatedAt = 1,
            ),
        )
        writeScript(
            """
            function demo_search(args)
              local rows = host.notes.search(args.q or "")
              if #rows == 0 then return "没找到" end
              return "找到 " .. #rows .. " 条：" .. rows[1].title
            end
            """.trimIndent(),
        )
        val result = loader(setOf(PluginPermissions.CORE_DB_READ_NOTE))
            .load(pluginDir, manifest(listOf(PluginFunction("demo_search", "搜笔记"))), setOf(PluginPermissions.CORE_DB_READ_NOTE))
        val out = result.executors[0].execute(ToolCall("c1", "demo_search", "{\"q\":\"协程\"}"))
        assertFalse(out.isError)
        assertTrue(out.content.contains("Kotlin 协程"))
    }

    @Test
    fun `unauthorized script gets a clear error`() = runBlocking {
        writeScript(
            """
            function demo_search(args)
              local ok = pcall(function() return host.notes.search("x") end)
              if ok then return "不该成功" end
              return "没有权限"
            end
            """.trimIndent(),
        )
        val result = loader(emptySet())
            .load(pluginDir, manifest(listOf(PluginFunction("demo_search", "搜笔记"))), emptySet())
        val out = result.executors[0].execute(ToolCall("c1", "demo_search", "{}"))
        assertEquals("没有权限", out.content)
    }

    @Test
    fun `script error becomes tool error not crash`() = runBlocking {
        writeScript(
            """
            function demo_boom(args)
              error("故意炸")
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_boom", "炸"))), emptySet())
        val out = result.executors[0].execute(ToolCall("c1", "demo_boom", "{}"))
        assertTrue(out.isError)
        assertTrue(out.content.contains("插件脚本出错"))
    }

    @Test
    fun `output is truncated`() = runBlocking {
        writeScript(
            """
            function demo_long(args)
              return string.rep("啊", 5000)
            end
            """.trimIndent(),
        )
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_long", "长"))), emptySet())
        val out = result.executors[0].execute(ToolCall("c1", "demo_long", "{}"))
        assertTrue("输出应被截断", out.content.length <= 2000)
    }

    @Test
    fun `sensitive flag is carried to spec`() {
        writeScript("function demo_x(args) return 'ok' end")
        val result = loader().load(
            pluginDir,
            manifest(listOf(PluginFunction("demo_x", "敏感", sensitive = true))),
            emptySet(),
        )
        assertTrue(result.executors[0].spec.sensitive)
    }

    // ── P217：sensitive 由宿主按能力强制 ───────────────────

    @Test
    fun `plugin with write permission is forced sensitive`() {
        writeScript("function demo_x(args) return 'ok' end")
        val m = manifest(listOf(PluginFunction("demo_x", "悄悄写", sensitive = false))).copy(
            permissions = listOf(
                com.lanweixi.domain.plugin.PluginPermission(PluginPermissions.CORE_DB_WRITE_NOTE, "写笔记"),
            ),
        )

        val result = loader().load(pluginDir, m, emptySet())

        assertTrue("写权限插件的工具必须强制确认", result.executors[0].spec.sensitive)
    }

    @Test
    fun `read only plugin keeps declared flag`() {
        writeScript("function demo_x(args) return 'ok' end")
        val m = manifest(listOf(PluginFunction("demo_x", "只读", sensitive = false))).copy(
            permissions = listOf(
                com.lanweixi.domain.plugin.PluginPermission(PluginPermissions.CORE_DB_READ_NOTE, "读笔记"),
            ),
        )

        val result = loader().load(pluginDir, m, emptySet())

        assertFalse("只读插件不该被强制确认", result.executors[0].spec.sensitive)
    }

    // ───────────── P216：资源限制 ─────────────

    @Test
    fun `infinite loop in a tool function becomes a tool error`() = runBlocking {
        writeScript("function demo_loop(args) while true do end end")
        val result = loader(maxInstructions = 20_000)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_loop", "死循环"))), emptySet())
        assertEquals(1, result.executors.size)

        val out = result.executors[0].execute(ToolCall("c1", "demo_loop", "{}"))

        assertTrue("死循环要变成工具错误，不能占死线程", out.isError)
        assertTrue("错误要说清楚是超时，实际：${out.content}", out.content.contains("超时"))
    }

    @Test
    fun `infinite loop while loading the script is reported not hung`() {
        writeScript("while true do end")
        val result = loader(maxInstructions = 20_000)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "x"))), emptySet())

        assertTrue(result.executors.isEmpty())
        assertTrue("加载期死循环也要有可读错误", result.errors.any { it.contains("超时") })
    }

    @Test
    fun `oversized main lua is rejected before compiling`() {
        writeScript("-- " + "x".repeat(600_000))
        val result = loader().load(pluginDir, manifest(listOf(PluginFunction("demo_hello", "x"))), emptySet())

        assertTrue(result.executors.isEmpty())
        assertTrue("超大脚本要在读之前拒绝", result.errors.any { it.contains("太大") })
    }

    // ───────────── P163：network 权限门面 ─────────────

    @Test
    fun `http without permission is refused`() = runBlocking {
        writeScript(
            """
            function demo_net(args)
              local ok = pcall(function() return host.http.get("https://example.com") end)
              if ok then return "不该成功" end
              return "没有网络权限"
            end
            """.trimIndent(),
        )
        // granted 里没有 network
        val result = loader(emptySet())
            .load(pluginDir, manifest(listOf(PluginFunction("demo_net", "联网"))), emptySet())
        val out = result.executors[0].execute(ToolCall("c1", "demo_net", "{}"))
        assertEquals("没有网络权限", out.content)
    }

    @Test
    fun `http blocks private addresses`() = runBlocking {
        writeScript(
            """
            function demo_net(args)
              return host.http.get("http://192.168.1.1/admin")
            end
            """.trimIndent(),
        )
        val granted = setOf(PluginPermissions.NETWORK)
        val result = loader(granted)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_net", "联网"))), granted)
        val out = result.executors[0].execute(ToolCall("c1", "demo_net", "{}"))
        assertTrue("内网地址应被拒", out.content.contains("不允许访问内网"))
    }

    @Test
    fun `http blocks localhost`() = runBlocking {
        writeScript(
            """
            function demo_net(args)
              return host.http.get("http://localhost:8080/x")
            end
            """.trimIndent(),
        )
        val granted = setOf(PluginPermissions.NETWORK)
        val result = loader(granted)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_net", "联网"))), granted)
        val out = result.executors[0].execute(ToolCall("c1", "demo_net", "{}"))
        assertTrue("localhost 应被拒", out.content.contains("不允许访问内网"))
    }

    @Test
    fun `http rejects non http scheme`() = runBlocking {
        writeScript(
            """
            function demo_net(args)
              return host.http.get("file:///etc/passwd")
            end
            """.trimIndent(),
        )
        val granted = setOf(PluginPermissions.NETWORK)
        val result = loader(granted)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_net", "联网"))), granted)
        val out = result.executors[0].execute(ToolCall("c1", "demo_net", "{}"))
        assertTrue("file 协议应被拒", out.content.contains("只支持 http/https"))
    }

    // ───────────── P164：document:read_all 门面 ─────────────

    @Test
    fun `doc readAll requires permission`() = runBlocking {
        writeScript(
            """
            function demo_doc(args)
              local ok = pcall(function() return host.docs.readAll() end)
              if ok then return "不该成功" end
              return "没有文档权限"
            end
            """.trimIndent(),
        )
        val result = loader(emptySet())
            .load(pluginDir, manifest(listOf(PluginFunction("demo_doc", "读文档"))), emptySet())
        val out = result.executors[0].execute(ToolCall("c1", "demo_doc", "{}"))
        assertEquals("没有文档权限", out.content)
    }

    @Test
    fun `authorized doc readAll lists documents`() = runBlocking {
        db.documentDao().insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "协程笔记", fileName = "a.txt", kind = "txt",
                charCount = 10, chunkCount = 1, importedAt = 1, updatedAt = 1,
            ),
        )
        writeScript(
            """
            function demo_doc(args)
              local docs = host.docs.readAll()
              if #docs == 0 then return "没有文档" end
              return "共 " .. #docs .. " 篇：" .. docs[1].title
            end
            """.trimIndent(),
        )
        val granted = setOf(PluginPermissions.DOCUMENT_READ_ALL)
        val result = loader(granted)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_doc", "读文档"))), granted)
        val out = result.executors[0].execute(ToolCall("c1", "demo_doc", "{}"))
        assertTrue(out.content.contains("协程笔记"))
    }

    @Test
    fun `doc content returns joined chunks`() = runBlocking {
        val docId = db.documentDao().insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "长文", fileName = "b.txt", kind = "txt",
                charCount = 20, chunkCount = 2, importedAt = 1, updatedAt = 1,
            ),
        )
        db.documentDao().insertChunks(
            listOf(
                com.lanweixi.core.data.entity.DocChunkEntity(
                    docId = docId, seq = 0, text = "第一段", charCount = 3,
                ),
                com.lanweixi.core.data.entity.DocChunkEntity(
                    docId = docId, seq = 1, text = "第二段", charCount = 3,
                ),
            ),
        )
        writeScript(
            """
            function demo_doc(args)
              return host.docs.content(args.id)
            end
            """.trimIndent(),
        )
        val granted = setOf(PluginPermissions.DOCUMENT_READ_ALL)
        val result = loader(granted)
            .load(pluginDir, manifest(listOf(PluginFunction("demo_doc", "读文档"))), granted)
        val out = result.executors[0].execute(ToolCall("c1", "demo_doc", "{\"id\":$docId}"))
        assertTrue(out.content.contains("第一段"))
        assertTrue(out.content.contains("第二段"))
    }
}
