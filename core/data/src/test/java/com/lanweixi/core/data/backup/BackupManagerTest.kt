package com.lanweixi.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P162：备份导入导出往返。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var db: LanWeixiDatabase
    private lateinit var backup: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seed() = runBlocking {
        db.noteDao().insert(NoteEntity(title = "协程", content = "挂起函数", tags = "kotlin", createdAt = 1, updatedAt = 2))
        db.noteDao().insert(NoteEntity(title = "旧笔记", content = "归档了", archived = true, createdAt = 3, updatedAt = 4))
        db.taskDao().insert(TaskEntity(title = "写周报", priority = 2, createdAt = 5, updatedAt = 6))
        db.promptDao().insert(PromptEntity(title = "IP 角色", category = "ip", positive = "银发", createdAt = 7, updatedAt = 8))
        db.reviewDao().insert(ReviewEntity(periodType = "week", periodStart = 9, periodEnd = 10, summary = "还行", createdAt = 11, updatedAt = 12))
    }

    @Test
    fun `export then import restores everything`() = runBlocking {
        seed()
        val json = backup.exportJson()

        // 清空
        db.noteDao().deleteAll()
        db.taskDao().deleteAll()
        db.promptDao().deleteAll()
        db.documentDao().deleteAll()
        db.reviewDao().deleteAll()
        assertEquals(0, db.noteDao().listAllForBackup().size)

        val stats = backup.importJson(json)

        assertEquals(2, stats.notes)
        assertEquals(1, stats.tasks)
        assertEquals(1, stats.prompts)
        assertEquals(1, stats.reviews)
        assertEquals(5, stats.total)

        assertEquals(2, db.noteDao().listAllForBackup().size)
        assertEquals(1, db.noteDao().listArchived().size)
        assertEquals("协程", db.noteDao().listActive().first().title)
        assertEquals(1, db.taskDao().listAllForBackup().size)
        assertEquals("IP 角色", db.promptDao().listAll().first().title)
    }

    @Test
    fun `exported json is readable and tagged`() = runBlocking {
        seed()
        val json = backup.exportJson()
        val root = JSONObject(json)
        assertEquals(BackupManager.APP_TAG, root.getString("app"))
        assertEquals(BackupManager.FORMAT_VERSION, root.getInt("version"))
        assertTrue(root.has("notes"))
        assertTrue(root.has("tasks"))
        assertTrue(root.has("prompts"))
        assertTrue(root.has("documents"))
        assertTrue(root.has("reviews"))
    }

    @Test
    fun `import refuses foreign json`() = runBlocking {
        val foreign = """{"app":"other-app","version":1}"""
        var failed = false
        try {
            backup.importJson(foreign)
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message!!.contains("不是兰未晞"))
        }
        assertTrue("应拒绝非本应用备份", failed)
    }

    @Test
    fun `import refuses newer format version`() = runBlocking {
        val future = """{"app":"lanweixi-workbench","version":999}"""
        var failed = false
        try {
            backup.importJson(future)
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message!!.contains("升级应用"))
        }
        assertTrue("应拒绝更高版本", failed)
    }

    @Test
    fun `bad json does not touch existing data`() = runBlocking {
        seed()
        val before = db.noteDao().listAllForBackup().size
        // 缺 app 标记 —— 应在删数据之前就抛错
        try {
            backup.importJson("""{"version":1}""")
        } catch (_: Exception) {
        }
        assertEquals("失败时不应清空已有数据", before, db.noteDao().listAllForBackup().size)
    }

    @Test
    fun `empty database exports empty arrays`() = runBlocking {
        val json = backup.exportJson()
        val root = JSONObject(json)
        assertEquals(0, root.getJSONArray("notes").length())
        assertEquals(0, root.getJSONArray("tasks").length())
    }

    // ── P210：会话摘要也要进备份 ─────────────────────────

    @Test
    fun `summaries survive export and import`() = runBlocking {
        seed()
        db.summaryDao().insert(
            com.lanweixi.core.data.entity.SessionSummaryEntity(
                sessionId = "s1",
                untilId = 40,
                content = "他明天面试，带简历。",
                coveredCount = 32,
                createdAt = 13,
                updatedAt = 14,
            ),
        )
        val json = backup.exportJson()
        assertTrue("导出要带 sessionSummaries", JSONObject(json).has("sessionSummaries"))

        // 清空后恢复
        db.summaryDao().deleteAll()
        val stats = backup.importJson(json)

        assertEquals(1, stats.summaries)
        val row = db.summaryDao().latest("s1")!!
        assertEquals("他明天面试，带简历。", row.content)
        assertEquals(40L, row.untilId)
        assertEquals(32, row.coveredCount)
    }

    @Test
    fun `v1 backup without summaries still imports`() = runBlocking {
        // 旧版备份没有 sessionSummaries 段：导入后摘要为空，其他数据照常恢复
        val json = """
            {"app":"lanweixi-workbench","version":1,
             "notes":[{"id":1,"title":"旧笔记","content":"x","tags":"","archived":false,"createdAt":1,"updatedAt":1}]}
        """.trimIndent()

        val stats = backup.importJson(json)

        assertEquals(1, stats.notes)
        assertEquals(0, stats.summaries)
        assertEquals(0, db.summaryDao().count())
    }

    // ── P211：聊天记录也要进备份 ─────────────────────────

    @Test
    fun `chat messages survive export and import`() = runBlocking {
        seed()
        db.messageDao().insert(
            com.lanweixi.core.data.entity.MessageEntity(
                role = "user", text = "在吗", status = "sent", createdAt = 21, sessionId = "default",
            ),
        )
        db.messageDao().insert(
            com.lanweixi.core.data.entity.MessageEntity(
                role = "assistant", speaker = "__host__", text = "在", status = "sent",
                createdAt = 22, sessionId = "group_1", quotePreview = "我：在吗",
            ),
        )

        val json = backup.exportJson()
        assertTrue("导出要带 messages", JSONObject(json).has("messages"))

        db.messageDao().deleteAll()
        assertEquals(0, db.messageDao().count())

        val stats = backup.importJson(json)

        assertEquals(2, stats.messages)
        val rows = db.messageDao().listAllForBackup()
        assertEquals(2, rows.size)
        assertEquals("在吗", rows.first().text)
        assertEquals("default", rows.first().sessionId)
        assertEquals("group_1", rows.last().sessionId)
        assertEquals("__host__", rows.last().speaker)
        assertEquals("我：在吗", rows.last().quotePreview)
    }

    @Test
    fun `v2 backup without messages imports fine`() = runBlocking {
        val json = """
            {"app":"lanweixi-workbench","version":2,
             "sessionSummaries":[{"id":1,"sessionId":"s1","untilId":10,"content":"摘要","coveredCount":8,"createdAt":1,"updatedAt":1}]}
        """.trimIndent()

        val stats = backup.importJson(json)

        assertEquals(1, stats.summaries)
        assertEquals(0, stats.messages)
        assertEquals(0, db.messageDao().count())
    }

    // ── P215：导入旧备份不能清空当前聊天记录 ─────────────

    @Test
    fun `importing an old backup keeps existing chat messages`() = runBlocking {
        // 现状：有聊天记录和摘要
        db.messageDao().insert(
            com.lanweixi.core.data.entity.MessageEntity(
                role = "user", text = "别删我", status = "sent", createdAt = 1, sessionId = "default",
            ),
        )
        db.summaryDao().insert(
            com.lanweixi.core.data.entity.SessionSummaryEntity(
                sessionId = "default", untilId = 5, content = "旧摘要", coveredCount = 4, createdAt = 1, updatedAt = 1,
            ),
        )
        // 导入一份 v1 备份（没有 messages / sessionSummaries 段）
        val oldBackup = """
            {"app":"lanweixi-workbench","version":1,
             "notes":[{"id":1,"title":"旧笔记","content":"x","tags":"","archived":false,"createdAt":1,"updatedAt":1}]}
        """.trimIndent()

        val stats = backup.importJson(oldBackup)

        assertEquals(1, stats.notes)
        assertEquals(0, stats.messages)
        assertEquals("旧备份不带聊天记录，现有的必须留着", 1, db.messageDao().count())
        assertEquals("旧备份不带摘要，现有的必须留着", 1, db.summaryDao().count())
        assertEquals("旧笔记", db.noteDao().listActive().first().title)
    }

    @Test
    fun `importing a backup with messages still replaces them`() = runBlocking {
        db.messageDao().insert(
            com.lanweixi.core.data.entity.MessageEntity(
                role = "user", text = "旧消息", status = "sent", createdAt = 1, sessionId = "default",
            ),
        )
        val backupJson = journalBackup()

        backup.importJson(backupJson)

        val rows = db.messageDao().listAllForBackup()
        assertEquals(1, rows.size)
        assertEquals("新消息", rows.single().text)
    }

    // ── P216：导入不能留下挂在旧文档上的分块 ─────────────

    @Test
    fun `import drops stale doc chunks`() = runBlocking {
        // 现状：文档 id=1，带 2 个分块
        val oldDocId = db.documentDao().insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "旧文档", fileName = "old.txt", kind = "txt",
                charCount = 20, chunkCount = 2, importedAt = 1, updatedAt = 1,
            ),
        )
        db.documentDao().insertChunks(
            listOf(
                com.lanweixi.core.data.entity.DocChunkEntity(
                    docId = oldDocId, seq = 0, text = "旧分块一", charCount = 4,
                ),
                com.lanweixi.core.data.entity.DocChunkEntity(
                    docId = oldDocId, seq = 1, text = "旧分块二", charCount = 4,
                ),
            ),
        )
        // 备份里的新文档恰好也用 id=1（备份恢复的是原 id，最容易串）
        val backupJson = """
            {"app":"lanweixi-workbench","version":3,
             "documents":[{"id":1,"title":"新文档","fileName":"new.txt","kind":"txt",
                           "charCount":5,"chunkCount":0,"importedAt":2,"updatedAt":2}]}
        """.trimIndent()

        backup.importJson(backupJson)

        assertEquals(1, db.documentDao().listAll().size)
        assertEquals("新文档", db.documentDao().listAll().single().title)
        assertTrue(
            "旧分块必须清掉，不能挂到新文档上",
            db.documentDao().chunksOf(1).isEmpty(),
        )
        assertEquals("整个分块表都该清空（备份不带分块）", 0, db.documentDao().chunksOf(oldDocId).size)
    }

    @Test
    fun `importing a backup without documents still drops stale chunks`() = runBlocking {
        val oldDocId = db.documentDao().insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "旧文档", fileName = "old.txt", kind = "txt",
                charCount = 4, chunkCount = 1, importedAt = 1, updatedAt = 1,
            ),
        )
        db.documentDao().insertChunks(
            listOf(com.lanweixi.core.data.entity.DocChunkEntity(docId = oldDocId, seq = 0, text = "旧分块", charCount = 3)),
        )
        val emptyBackup = """{"app":"lanweixi-workbench","version":1}"""

        backup.importJson(emptyBackup)

        assertEquals(0, db.documentDao().listAll().size)
        assertEquals("没有文档时旧分块更不能留", 0, db.documentDao().chunksOf(oldDocId).size)
    }

    /** 造一份带 messages 段的 v3 备份。 */
    private fun journalBackup(): String = """
        {"app":"lanweixi-workbench","version":3,
         "messages":[{"id":9,"role":"user","speaker":"","text":"新消息","status":"sent","createdAt":2,
                      "sessionId":"default","quoteId":0,"quotePreview":""}]}
    """.trimIndent()
}
