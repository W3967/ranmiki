package com.lanweixi.core.data.draft

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.repository.DraftRepository
import com.lanweixi.core.data.repository.RoomDraftRepository
import com.lanweixi.core.data.repository.RoomNoteRepository
import com.lanweixi.core.data.repository.RoomTaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P216：草稿审核的事务边界（真 Room + 真事务）。
 *
 * 纯 JVM 的 [DraftReviewerTest] 只验规则；这里专门验「中途失败会不会留孤儿数据」：
 * 插了笔记但标草稿那一步失败时，笔记必须跟着回滚，草稿仍是 pending。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftReviewTransactionTest {

    private lateinit var db: LanWeixiDatabase

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

    private fun reviewer(drafts: DraftRepository = RoomDraftRepository(db.draftDao())) = DraftReviewer(
        drafts = drafts,
        notes = RoomNoteRepository(db.noteDao()),
        tasks = RoomTaskRepository(db.taskDao()),
        transaction = { block -> db.withTransaction { block() } },
    )

    /** 写正式记录成功，但「标草稿已确认」失败——模拟进程/数据库中断。 */
    private class FailingApproveDrafts(private val delegate: DraftRepository) : DraftRepository by delegate {
        override suspend fun markApproved(id: Long, targetId: Long) {
            throw IllegalStateException("磁盘写入失败")
        }
    }

    private fun seedNoteDraft(title: String = "孤儿笔记"): Long = runBlocking {
        db.draftDao().insert(
            DraftEntity(kind = DraftKind.NOTE, title = title, body = "正文", createdAt = 1, status = DraftStatus.PENDING),
        )
    }

    @Test
    fun `note insert is rolled back when marking the draft fails`() = runBlocking {
        val draftId = seedNoteDraft()
        val failing = FailingApproveDrafts(RoomDraftRepository(db.draftDao()))

        val outcome = reviewer(failing).approve(draftId)

        assertTrue("写库失败要返回可展示的失败结果", outcome is ReviewOutcome.Failed)
        assertEquals("不能留下孤儿笔记", 0, db.noteDao().listActive().size)
        assertEquals("草稿必须还是待审核", DraftStatus.PENDING, db.draftDao().byId(draftId)!!.status)
    }

    @Test
    fun `task insert is rolled back when marking the draft fails`() = runBlocking {
        val draftId = db.draftDao().insert(
            DraftEntity(kind = DraftKind.TASK, title = "孤儿任务", body = "详情", createdAt = 1),
        )
        val failing = FailingApproveDrafts(RoomDraftRepository(db.draftDao()))

        val outcome = reviewer(failing).approve(draftId)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertEquals(0, db.taskDao().listOpen().size)
        assertEquals(DraftStatus.PENDING, db.draftDao().byId(draftId)!!.status)
    }

    @Test
    fun `edited content is rolled back together with the approval`() = runBlocking {
        val draftId = seedNoteDraft(title = "旧标题")
        val failing = FailingApproveDrafts(RoomDraftRepository(db.draftDao()))

        val outcome = reviewer(failing).approveEdited(
            id = draftId,
            title = "新标题",
            body = "新正文",
            tags = "改了",
            dueAt = 0L,
            priority = 1,
        )

        assertTrue(outcome is ReviewOutcome.Failed)
        val row = db.draftDao().byId(draftId)!!
        assertEquals("改内容与确认必须同一事务：失败时标题回到旧值", "旧标题", row.title)
        assertEquals(DraftStatus.PENDING, row.status)
        assertEquals(0, db.noteDao().listActive().size)
    }

    @Test
    fun `undo rolls back when deleting the target fails`() = runBlocking {
        val draftId = seedNoteDraft(title = "已确认")
        assertTrue(reviewer().approve(draftId) is ReviewOutcome.NoteSaved)

        val failing = object : DraftRepository by RoomDraftRepository(db.draftDao()) {
            override suspend fun markPending(id: Long) {
                throw IllegalStateException("数据库被占用")
            }
        }

        val outcome = reviewer(failing).undo(draftId)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertEquals("撤销失败时正式记录必须还在", 1, db.noteDao().listActive().size)
        assertEquals(DraftStatus.APPROVED, db.draftDao().byId(draftId)!!.status)
    }

    @Test
    fun `happy path still writes both rows in one transaction`() = runBlocking {
        val draftId = seedNoteDraft(title = "正常确认")

        val outcome = reviewer().approve(draftId)

        assertTrue(outcome is ReviewOutcome.NoteSaved)
        val note = db.noteDao().listActive().single()
        assertEquals("正常确认", note.title)
        val row = db.draftDao().byId(draftId)!!
        assertEquals(DraftStatus.APPROVED, row.status)
        assertEquals(note.id, row.targetId)
    }
}
