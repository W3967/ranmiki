package com.lanweixi.core.data.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.repository.RoomDocumentRepository
import com.lanweixi.core.data.repository.RoomDraftRepository
import com.lanweixi.core.data.repository.RoomNoteRepository
import com.lanweixi.core.data.repository.RoomPromptRepository
import com.lanweixi.core.data.repository.RoomTaskRepository
import com.lanweixi.domain.tools.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P138：宿主内置工具真的能从库里取到数据。P206：写入工具只落草稿。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostToolsTest {

    private lateinit var db: LanWeixiDatabase
    private lateinit var tools: HostTools

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tools = HostTools(
            notes = RoomNoteRepository(db.noteDao()),
            tasks = RoomTaskRepository(db.taskDao()),
            documents = RoomDocumentRepository(db.documentDao()),
            prompts = RoomPromptRepository(db.promptDao()),
            drafts = RoomDraftRepository(db.draftDao()),
            sessionId = { "default" },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun executor(name: String) = tools.executors().first { it.spec.name == name }

    @Test
    fun `exposes four tools`() {
        val names = tools.executors().map { it.spec.name }.toSet()
        assertTrue(names.containsAll(listOf(
            "host_search_notes", "host_list_tasks", "host_search_documents", "host_list_prompts",
        )))
    }

    @Test
    fun `exposes write tools too`() {
        val names = tools.executors().map { it.spec.name }.toSet()
        assertTrue(names.containsAll(listOf("host_propose_note", "host_propose_task")))
    }

    @Test
    fun `propose note writes draft only`() = runBlocking {
        val result = executor("host_propose_note").execute(
            ToolCall(
                "c1",
                "host_propose_note",
                "{\"title\":\"面试要点\",\"content\":\"带简历\\n提前到\",\"tags\":\"工作\"}",
            ),
        )

        assertTrue(!result.isError)
        assertTrue(result.content.contains("待审核"))
        val draft = db.draftDao().listPending().single()
        assertEquals(DraftKind.NOTE, draft.kind)
        assertEquals("面试要点", draft.title)
        assertEquals("工作", draft.tags)
        assertEquals("default", draft.sourceSessionId)
        // 关键：正式笔记表必须还是空的
        assertTrue(db.noteDao().listActive().isEmpty())
    }

    @Test
    fun `propose task normalizes seconds and priority`() = runBlocking {
        val result = executor("host_propose_task").execute(
            ToolCall(
                "c1",
                "host_propose_task",
                // 模型常见的「秒级时间戳」，要归一化成毫秒
                "{\"title\":\"交报告\",\"detail\":\"增长\",\"dueAt\":1800000000,\"priority\":2}",
            ),
        )

        assertTrue(!result.isError)
        val draft = db.draftDao().listPending().single()
        assertEquals(DraftKind.TASK, draft.kind)
        assertEquals(1_800_000_000_000L, draft.dueAt)
        assertEquals(2, draft.priority)
        assertTrue(db.taskDao().listOpen().isEmpty())
    }

    @Test
    fun `propose task accepts human due`() = runBlocking {
        val now = System.currentTimeMillis()
        val result = executor("host_propose_task").execute(
            ToolCall(
                "c1",
                "host_propose_task",
                "{\"title\":\"面试\",\"detail\":\"带简历\",\"due\":\"tomorrow 15:00\"}",
            ),
        )

        assertTrue(!result.isError)
        val draft = db.draftDao().listPending().single()
        assertTrue("应该解析出未来时间戳", draft.dueAt > now)
        assertTrue("不该超过两天", draft.dueAt < now + 2 * 24 * 3600_000L)
        assertTrue(result.content.contains("截止"))
    }

    @Test
    fun `propose note rejects empty content`() = runBlocking {
        val result = executor("host_propose_note").execute(
            ToolCall("c1", "host_propose_note", "{\"title\":\"\",\"content\":\"\"}"),
        )

        assertTrue(result.isError)
        assertTrue(db.draftDao().listPending().isEmpty())
    }

    @Test
    fun `propose task requires title`() = runBlocking {
        val result = executor("host_propose_task").execute(
            ToolCall("c1", "host_propose_task", "{\"detail\":\"只有详情\"}"),
        )

        assertTrue(result.isError)
        assertTrue(db.draftDao().listPending().isEmpty())
    }

    @Test
    fun `search notes returns titles`() = runBlocking {
        db.noteDao().insert(
            com.lanweixi.core.data.entity.NoteEntity(
                title = "Kotlin 协程", content = "挂起函数", createdAt = 1, updatedAt = 1,
            ),
        )
        val result = executor("host_search_notes").execute(
            ToolCall("c1", "host_search_notes", "{\"query\":\"协程\"}"),
        )
        assertTrue(result.content.contains("Kotlin 协程"))
        assertTrue(!result.isError)
    }

    @Test
    fun `search notes with blank query lists recent`() = runBlocking {
        db.noteDao().insert(
            com.lanweixi.core.data.entity.NoteEntity(
                title = "随便一篇", content = "", createdAt = 1, updatedAt = 1,
            ),
        )
        val result = executor("host_search_notes").execute(
            ToolCall("c1", "host_search_notes", "{}"),
        )
        assertTrue(result.content.contains("随便一篇"))
    }

    @Test
    fun `list tasks separates open and done`() = runBlocking {
        val open = db.taskDao().insert(
            com.lanweixi.core.data.entity.TaskEntity(title = "写周报", createdAt = 1, updatedAt = 1),
        )
        val done = db.taskDao().insert(
            com.lanweixi.core.data.entity.TaskEntity(title = "买牛奶", createdAt = 1, updatedAt = 1),
        )
        db.taskDao().setDone(done, true, 2, 2)

        val result = executor("host_list_tasks").execute(
            ToolCall("c1", "host_list_tasks", "{\"includeDone\":true}"),
        )
        assertTrue(result.content.contains("写周报"))
        assertTrue(result.content.contains("买牛奶"))
        assertTrue(result.content.contains("未完成"))
        assertTrue(result.content.contains("已完成"))
        assertTrue(open > 0)
    }

    @Test
    fun `search documents returns hits`() = runBlocking {
        val id = db.documentDao().insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "设计稿", fileName = "d.md", kind = "md",
                charCount = 10, chunkCount = 1, importedAt = 1, updatedAt = 1,
            ),
        )
        db.documentDao().insertChunks(
            listOf(
                com.lanweixi.core.data.entity.DocChunkEntity(
                    docId = id, seq = 0, text = "赛博少女站在雨夜", charCount = 8,
                ),
            ),
        )
        val result = executor("host_search_documents").execute(
            ToolCall("c1", "host_search_documents", "{\"query\":\"雨夜\"}"),
        )
        assertTrue(result.content.contains("设计稿"))
        assertTrue(result.content.contains("雨夜"))
    }

    @Test
    fun `list prompts filters by category`() = runBlocking {
        db.promptDao().insert(
            com.lanweixi.core.data.entity.PromptEntity(
                title = "赛博少女", category = "image", positive = "neon", createdAt = 1, updatedAt = 1,
            ),
        )
        db.promptDao().insert(
            com.lanweixi.core.data.entity.PromptEntity(
                title = "口播开头", category = "video", positive = "钩子", createdAt = 1, updatedAt = 1,
            ),
        )
        val result = executor("host_list_prompts").execute(
            ToolCall("c1", "host_list_prompts", "{\"category\":\"image\"}"),
        )
        assertTrue(result.content.contains("赛博少女"))
        assertTrue(!result.content.contains("口播开头"))
    }

    @Test
    fun `empty library returns friendly text`() = runBlocking {
        val notes = executor("host_search_notes").execute(ToolCall("c1", "host_search_notes", "{}"))
        assertTrue(notes.content.contains("没有"))

        val tasks = executor("host_list_tasks").execute(ToolCall("c2", "host_list_tasks", "{}"))
        assertTrue(tasks.content.contains("没有"))
    }
}