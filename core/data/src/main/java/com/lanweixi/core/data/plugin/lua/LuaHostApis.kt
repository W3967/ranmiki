package com.lanweixi.core.data.plugin.lua

import com.lanweixi.core.data.repository.DocumentRepository
import com.lanweixi.core.data.repository.NoteRepository
import com.lanweixi.core.data.repository.PromptRepository
import com.lanweixi.core.data.repository.TaskRepository
import com.lanweixi.domain.plugin.PluginPermissions
import kotlinx.coroutines.runBlocking
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/**
 * P140：注入给插件的宿主 API。
 *
 * 只给**语义化**的读写接口，不给 SQL、不给文件、不给网络——
 * 插件在 Lua 里能做的事，就是这里列出来的这些。
 *
 * 每个接口都先查 manifest 授权：没授权的调用直接抛错，脚本里能 `pcall` 兜住。
 *
 * 注意：底层 Repository 是 suspend，而 LuaJ 的调用是同步的，
 * 所以这里用 runBlocking。调用方必须保证自己在 IO 线程上执行脚本。
 */
class LuaHostApis(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val documents: DocumentRepository,
    private val prompts: PromptRepository,
    private val granted: Set<String>,
    /** P170：插件自己的配置值（读 config.json）。 */
    private val configValues: com.lanweixi.domain.plugin.PluginConfigValues =
        com.lanweixi.domain.plugin.PluginConfigValues(),
    /** P188：全局搜索（可选，没注入就不挂 host.search）。 */
    private val globalSearch: com.lanweixi.core.data.search.GlobalSearch? = null,
    private val logger: (String) -> Unit,
) {

    fun installInto(globals: Globals) {
        val host = LuaValue.tableOf()

        // host.log(msg)
        host.set("log", fn1 { msg ->
            val text = msg.tojstring().take(500)
            logger(text)
            com.lanweixi.core.data.plugin.HostLog.i("plugin", text)
            LuaValue.NIL
        })

        host.set("notes", notesApi())
        host.set("tasks", tasksApi())
        host.set("docs", docsApi())
        host.set("prompts", promptsApi())
        host.set("http", httpApi())
        // host.config("key") / host.config("key", 默认值)
        // host.search(query) —— 需要 search:index
        globalSearch?.let { search ->
            host.set("search", fn1 { q ->
                requirePermission(PluginPermissions.SEARCH_INDEX)
                val query = q.tojstring().trim()
                val hits = if (query.isEmpty()) emptyList() else {
                    runBlocking { search.search(query, perKind = 5) }
                }
                LuaSandbox.anyToLua(
                    hits.map {
                        mapOf(
                            "kind" to it.kind,
                            "id" to it.id,
                            "title" to it.title,
                            "snippet" to it.snippet,
                        )
                    },
                )
            })
        }

        // host.logs(n) —— 需要 log:subscribe_host
        host.set("logs", fn1 { n ->
            requirePermission(PluginPermissions.LOG_SUBSCRIBE_HOST)
            val limit = if (n.isnil()) 50 else n.toint()
            val entries = com.lanweixi.core.data.plugin.HostLog.recent(limit)
            LuaSandbox.anyToLua(
                entries.map {
                    mapOf("level" to it.level, "tag" to it.tag, "message" to it.render())
                },
            )
        })

        host.set("config", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val key = args.arg1().tojstring()
                val fallback = if (args.narg() >= 2) args.arg(2).tojstring() else ""
                return LuaValue.valueOf(configValues.get(key, fallback))
            }
        })
        globals.set("host", host)
    }

    private fun notesApi(): LuaValue {
        val table = LuaValue.tableOf()
        table.set("search", fn1 { q ->
            requirePermission(PluginPermissions.CORE_DB_READ_NOTE)
            val rows = runBlocking { notes.search(q.tojstring()).take(20) }
            LuaSandbox.anyToLua(rows.map { mapOf("id" to it.id, "title" to it.title, "content" to it.content.take(400)) })
        })
        table.set("list", fn0 {
            requirePermission(PluginPermissions.CORE_DB_READ_NOTE)
            val rows = runBlocking { notes.listActive().take(20) }
            LuaSandbox.anyToLua(rows.map { mapOf("id" to it.id, "title" to it.title) })
        })
        // P185：写接口。需要 core_db:write:note，且宿主会弹一次确认
        table.set("create", fn2 { titleArg, contentArg ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_NOTE)
            val title = titleArg.tojstring().trim()
            if (title.isEmpty()) return@fn2 LuaValue.FALSE
            val now = System.currentTimeMillis()
            val id = runBlocking {
                runCatching {
                    notes.save(
                        com.lanweixi.core.data.entity.NoteEntity(
                            title = title.take(80),
                            content = contentArg.tojstring(),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }.getOrNull()
            }
            LuaValue.valueOf((id ?: -1L).toDouble())
        })
        table.set("delete", fn1 { id ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_NOTE)
            val target = id.toint()
            if (target <= 0) LuaValue.valueOf(false) else {
                runBlocking { runCatching { notes.deleteById(target.toLong()) }.isSuccess }
                    .let { LuaValue.valueOf(it) }
            }
        })
        return table
    }

    private fun tasksApi(): LuaValue {
        val table = LuaValue.tableOf()
        table.set("list", fn0 {
            requirePermission(PluginPermissions.CORE_DB_READ_TASK)
            val open = runBlocking { tasks.listOpen().take(30) }
            LuaSandbox.anyToLua(
                open.map {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title,
                        "priority" to it.priority,
                        "dueAt" to it.dueAt,
                    )
                },
            )
        })
        table.set("complete", fn1 { id ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_TASK)
            val target = id.toint()
            if (target <= 0) LuaValue.valueOf(false) else {
                runBlocking { runCatching { tasks.setDone(target.toLong(), true) }.isSuccess }
                    .let { LuaValue.valueOf(it) }
            }
        })
        return table
    }

    private fun docsApi(): LuaValue {
        val table = LuaValue.tableOf()
        // P191：host.docs.import(title, content) —— 需要 core_db:write:document_index
        table.set("import", fn2 { titleArg, contentArg ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_DOCUMENT_INDEX)
            val title = titleArg.tojstring().trim()
            val content = contentArg.tojstring()
            if (title.isEmpty() || content.isBlank()) return@fn2 LuaValue.FALSE
            val chunks = com.lanweixi.domain.docs.DocChunker.chunk(content)
            if (chunks.isEmpty()) return@fn2 LuaValue.FALSE
            val id = runBlocking {
                runCatching {
                    documents.import(
                        title = title.take(80),
                        fileName = title.take(80) + ".txt",
                        kind = "txt",
                        chunks = chunks,
                    )
                }.getOrNull()
            }
            LuaValue.valueOf((id ?: -1L).toDouble())
        })
        table.set("delete", fn1 { id ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_DOCUMENT_INDEX)
            val target = id.toint()
            if (target <= 0) LuaValue.valueOf(false) else {
                runBlocking { runCatching { documents.deleteById(target.toLong()) }.isSuccess }
                    .let { LuaValue.valueOf(it) }
            }
        })
        // host.docs.readAll() / host.docs.content(id) —— 需要 document:read_all
        table.set("readAll", fn0 {
            requirePermission(PluginPermissions.DOCUMENT_READ_ALL)
            val docs = runBlocking { documents.listAll() }
            LuaSandbox.anyToLua(
                docs.map {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title,
                        "kind" to it.kind,
                        "charCount" to it.charCount,
                    )
                },
            )
        })
        table.set("content", fn1 { idValue ->
            requirePermission(PluginPermissions.DOCUMENT_READ_ALL)
            val docId = idValue.toint()
            if (docId <= 0) LuaValue.valueOf("") else {
                val chunks = runBlocking { documents.chunksOf(docId.toLong()) }
                LuaValue.valueOf(chunks.joinToString("\n") { it.text }.take(MAX_DOC_CHARS))
            }
        })
        table.set("search", fn1 { q ->
            requirePermission(PluginPermissions.CORE_DB_READ_DOCUMENT_INDEX)
            val query = q.tojstring()
            val docs = runBlocking { documents.search(query).take(10) }
            LuaSandbox.anyToLua(
                docs.map { doc ->
                    mapOf(
                        "id" to doc.id,
                        "title" to doc.title,
                        "snippet" to (runBlocking { documents.firstHit(doc.id, query)?.text }.orEmpty().take(300)),
                    )
                },
            )
        })
        return table
    }

    /**
     * host.http.get(url) / host.http.post(url, body)
     *
     * 需要 network 权限。硬约束：
     * · 10 秒超时
     * · 响应截断 4KB（插件不该把整页 HTML 塞进上下文）
     * · **拒绝内网地址**（P216：按解析后的真实 IP 判断，覆盖十进制 IP / IPv6；不跟随重定向）
     */
    private fun httpApi(): LuaValue {
        val table = LuaValue.tableOf()
        table.set("get", fn1 { urlValue ->
            requirePermission(PluginPermissions.NETWORK)
            LuaValue.valueOf(httpRequest(urlValue.tojstring(), null))
        })
        table.set("post", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                requirePermission(PluginPermissions.NETWORK)
                return LuaValue.valueOf(
                    httpRequest(args.arg1().tojstring(), args.arg(2).tojstring()),
                )
            }
        })
        return table
    }

    /** 同步 HTTP（Lua 调用本身是同步的，调用方须已在 IO 线程）。 */
    private fun httpRequest(rawUrl: String, body: String?): String {
        val url = when (val checked = SsrfGuard.check(rawUrl)) {
            is SsrfGuard.Check.Rejected -> return checked.reason
            is SsrfGuard.Check.Allowed -> checked.url
        }
        return try {
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = if (body == null) "GET" else "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                // P216：外网 302 到内网地址不能自动跟过去
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "LanWeixiPlugin/1.0")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location").orEmpty().take(120)
                conn.disconnect()
                return "不允许跟随重定向（HTTP $code" +
                    (if (location.isBlank()) "" else " → $location") + "）"
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            "[HTTP " + code + "]\n" + text.take(MAX_HTTP_CHARS)
        } catch (e: Exception) {
            "请求失败：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun promptsApi(): LuaValue {
        val table = LuaValue.tableOf()
        // P185：写接口，需要 core_db:write:creation_prompt
        table.set("create", fn2 { titleArg, positiveArg ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_CREATION_PROMPT)
            val title = titleArg.tojstring().trim()
            val positive = positiveArg.tojstring()
            if (title.isEmpty() || positive.isEmpty()) return@fn2 LuaValue.FALSE
            val now = System.currentTimeMillis()
            val id = runBlocking {
                runCatching {
                    prompts.save(
                        com.lanweixi.core.data.entity.PromptEntity(
                            title = title.take(80),
                            positive = positive.take(4000),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }.getOrNull()
            }
            LuaValue.valueOf((id ?: -1L).toDouble())
        })
        table.set("delete", fn1 { id ->
            requirePermission(PluginPermissions.CORE_DB_WRITE_CREATION_PROMPT)
            val target = id.toint()
            if (target <= 0) LuaValue.valueOf(false) else {
                runBlocking { runCatching { prompts.deleteById(target.toLong()) }.isSuccess }
                    .let { LuaValue.valueOf(it) }
            }
        })
        table.set("list", fn1 { category ->
            requirePermission(PluginPermissions.CORE_DB_READ_CREATION_PROMPT)
            val cat = category.tojstring()
            val rows = runBlocking {
                if (cat.isBlank()) prompts.listAll().take(20) else prompts.listByCategory(cat).take(20)
            }
            LuaSandbox.anyToLua(rows.map { mapOf("title" to it.title, "positive" to it.positive.take(300)) })
        })
        return table
    }

    private fun requirePermission(permissionId: String) {
        if (!PluginPermissions.isKnown(permissionId)) {
            error("宿主不认识这个权限：" + permissionId)
        }
        if (permissionId !in granted) {
            error("插件没有获得权限：" + permissionId + "（请让用户在插件页勾选）")
        }
    }

    private fun fn0(body: () -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = body()
    }

    private fun fn1(body: (LuaValue) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = body(args.arg1())
    }

    private fun fn2(body: (LuaValue, LuaValue) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = body(args.arg1(), args.arg(2))
    }

    private companion object {
        /** 响应截断，避免插件把整页内容塞进模型上下文。 */
        const val MAX_HTTP_CHARS = 4_000

        /** 单篇文档拼接后的上限，避免把整本书塞进上下文。 */
        const val MAX_DOC_CHARS = 8_000
    }
}
