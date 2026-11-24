package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.dao.ReviewSnapshot
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P136：复盘的汇总范围与 CRUD 验收（内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewDaoTest {

    private lateinit var db: LanWeixiDatabase
    private val weekStart = 1_000L
    private val weekEnd = 2_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `counts only include the given window`() = runBlocking {
        val dao = db.reviewDao()
        // 窗口内
        db.noteDao().insert(NoteEntity(title = "本周笔记", content = "", createdAt = 1_200L, updatedAt = 1_200L))
        // 窗口外
        db.noteDao().insert(NoteEntity(title = "上周笔记", content = "", createdAt = 500L, updatedAt = 500L))

        assertEquals(1, dao.notesCreatedCount(weekStart, weekEnd))
        assertEquals(listOf("本周笔记"), dao.notesCreated(weekStart, weekEnd).map { it.title })
    }

    @Test
    fun `tasks created and done are counted separately`() = runBlocking {
        val dao = db.reviewDao()
        // 本周建、本周做完
        val a = db.taskDao().insert(TaskEntity(title = "A", createdAt = 1_100L, updatedAt = 1_100L))
        db.taskDao().setDone(a, true, 1_500L, 1_500L)
        // 上周建、本周做完（应只算在 done 里）
        val b = db.taskDao().insert(TaskEntity(title = "B", createdAt = 600L, updatedAt = 600L))
        db.taskDao().setDone(b, true, 1_400L, 1_400L)
        // 本周建、还没做
        db.taskDao().insert(TaskEntity(title = "C", createdAt = 1_300L, updatedAt = 1_300L))

        assertEquals(2, dao.tasksCreatedCount(weekStart, weekEnd))
        assertEquals(listOf("A", "C"), dao.tasksCreated(weekStart, weekEnd).map { it.title })
        assertEquals(listOf("B", "A"), dao.tasksDone(weekStart, weekEnd).map { it.title })
    }

    @Test
    fun `prompts and documents are windowed`() = runBlocking {
        val dao = db.reviewDao()
        db.promptDao().insert(
            PromptEntity(title = "本周期提示词", positive = "p", createdAt = 1_200L, updatedAt = 1_200L),
        )
        db.promptDao().insert(
            PromptEntity(title = "老提示词", positive = "p", createdAt = 100L, updatedAt = 100L),
        )
        db.documentDao().insert(
            DocumentEntity(
                title = "本周文档", fileName = "a.txt", kind = "txt",
                charCount = 1, chunkCount = 1, importedAt = 1_200L, updatedAt = 1_200L,
            ),
        )

        assertEquals(1, dao.promptsAddedCount(weekStart, weekEnd))
        assertEquals(listOf("本周期提示词"), dao.promptsAdded(weekStart, weekEnd).map { it.title })
        assertEquals(1, dao.documentsAddedCount(weekStart, weekEnd))
        assertEquals(listOf("本周文档"), dao.documentsAdded(weekStart, weekEnd).map { it.title })
    }

    @Test
    fun `review crud`() = runBlocking {
        val dao = db.reviewDao()
        val id = dao.insert(
            ReviewEntity(
                periodType = "week",
                periodStart = weekStart,
                periodEnd = weekEnd,
                summary = "这周把文档导入做完了",
                createdAt = 9_000L,
                updatedAt = 9_000L,
            ),
        )
        assertEquals(listOf(id), dao.listAll().map { it.id })

        dao.update(dao.byId(id)!!.copy(summary = "改了一版", updatedAt = 9_900L))
        assertEquals("改了一版", dao.byId(id)!!.summary)

        dao.deleteById(id)
        assertNull(dao.byId(id))
        assertTrue(dao.listAll().isEmpty())
    }

    /** P208：AI 草稿的提议 / 采纳 / 驳回要按周期统计。 */
    @Test
    fun `drafts are counted by status and window`() = runBlocking {
        val drafts = db.draftDao()
        val approved = drafts.insert(
            DraftEntity(kind = DraftKind.TASK, title = "采纳的", createdAt = 1_200L),
        )
        drafts.markReviewed(approved, DraftStatus.APPROVED, targetId = 7L, now = 1_300L)
        val rejected = drafts.insert(
            DraftEntity(kind = DraftKind.NOTE, title = "驳回的", createdAt = 1_250L),
        )
        drafts.markReviewed(rejected, DraftStatus.REJECTED, targetId = 0L, now = 1_350L)
        drafts.insert(DraftEntity(kind = DraftKind.NOTE, title = "还没审", createdAt = 1_400L))
        drafts.insert(DraftEntity(kind = DraftKind.NOTE, title = "上周的", createdAt = 500L))

        val dao = db.reviewDao()
        assertEquals(3, dao.draftsProposedCount(weekStart, weekEnd))
        assertEquals(1, dao.draftsApprovedCount(weekStart, weekEnd))
        assertEquals(1, dao.draftsRejectedCount(weekStart, weekEnd))
    }
}