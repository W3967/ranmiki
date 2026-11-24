package com.lanweixi.core.data.draft

import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.repository.DraftRepository
import com.lanweixi.core.data.repository.NoteRepository
import com.lanweixi.core.data.repository.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P206：审核规则。
 *
 * 纯 JVM + 假仓库：审批逻辑不该依赖 Robolectric，
 * 规则本身（写哪张表、标题兜底、重复确认）才是这层要钉死的。
 */
class DraftReviewerTest {

    private val drafts = FakeDrafts()
    private val notes = FakeNotes()
    private val tasks = FakeTasks()
    private val reviewer = DraftReviewer(drafts, notes, tasks)

    // ── note ─────────────────────────────────────────────

    @Test
    fun `approve note writes note and marks approved`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "面试要点", body = "带简历\n提前半小时")

        val outcome = reviewer.approve(id)

        assertTrue(outcome is ReviewOutcome.NoteSaved)
        val note = notes.rows.single()
        assertEquals("面试要点", note.title)
        assertEquals("带简历\n提前半小时", note.content)
        assertTrue(!note.archived)
        val row = drafts.rows.single()
        assertEquals(DraftStatus.APPROVED, row.status)
        assertEquals(note.id, row.targetId)
        assertTrue(row.reviewedAt > 0L)
    }

    @Test
    fun `approve note keeps tags`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "周报", body = "本周做了三件事", tags = "工作,复盘")

        reviewer.approve(id)

        assertEquals("工作,复盘", notes.rows.single().tags)
    }

    @Test
    fun `blank title falls back to first body line`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "   ", body = "\n  第一条正文\n第二条")

        reviewer.approve(id)

        assertEquals("第一条正文", notes.rows.single().title)
    }

    @Test
    fun `blank title and blank body falls back to placeholder`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "", body = "")

        reviewer.approve(id)

        assertEquals("无标题", notes.rows.single().title)
    }

    // ── task ─────────────────────────────────────────────

    @Test
    fun `approve task writes task with due and priority`() = runBlocking {
        val due = 1_800_000_000_000L
        val id = drafts.seed(
            DraftKind.TASK,
            title = "交报告",
            body = "重点写增长",
            dueAt = due,
            priority = 2,
        )

        val outcome = reviewer.approve(id)

        assertTrue(outcome is ReviewOutcome.TaskSaved)
        val task = tasks.rows.single()
        assertEquals("交报告", task.title)
        assertEquals("重点写增长", task.detail)
        assertEquals(due, task.dueAt)
        assertEquals(2, task.priority)
        assertEquals("approved", drafts.rows.single().status)
    }

    @Test
    fun `task priority is clamped`() = runBlocking {
        val id = drafts.seed(DraftKind.TASK, title = "怪任务", priority = 9)

        reviewer.approve(id)

        assertEquals(2, tasks.rows.single().priority)
    }

    // ── P207：修改后确认 / 撤销 ──────────────────────────

    @Test
    fun `approveEdited writes edited content`() = runBlocking {
        val id = drafts.seed(DraftKind.TASK, title = "旧标题", body = "旧详情", priority = 1)

        val outcome = reviewer.approveEdited(
            id = id,
            title = "新标题",
            body = "新详情",
            tags = "",
            dueAt = 1_800_000_000_000L,
            priority = 2,
        )

        assertTrue(outcome is ReviewOutcome.TaskSaved)
        val task = tasks.rows.single()
        assertEquals("新标题", task.title)
        assertEquals("新详情", task.detail)
        assertEquals(1_800_000_000_000L, task.dueAt)
        assertEquals(2, task.priority)
        // 草稿也跟着更新，撤销后才能再确认成同样的内容
        assertEquals("新标题", drafts.rows.single().title)
    }

    @Test
    fun `approveEdited keeps tags for notes`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "随手记", body = "x")

        reviewer.approveEdited(id, "带标签的", "正文", "工作,重要", 0L, 1)

        assertEquals("带标签的", notes.rows.single().title)
        assertEquals("工作,重要", notes.rows.single().tags)
    }

    @Test
    fun `approveEdited refuses already handled`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "已经确认", body = "x")
        reviewer.approve(id)

        val outcome = reviewer.approveEdited(id, "又改", "y", "", 0L, 1)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertEquals(1, notes.rows.size)
        assertEquals("已经确认", notes.rows.single().title)
    }

    @Test
    fun `undo deletes target and returns draft to pending`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "写错了", body = "x")
        reviewer.approve(id)
        assertEquals(1, notes.rows.size)

        val outcome = reviewer.undo(id)

        assertTrue(outcome is ReviewOutcome.Undone)
        assertTrue("正式记录应删除", notes.rows.isEmpty())
        val row = drafts.rows.single()
        assertEquals(DraftStatus.PENDING, row.status)
        assertEquals(0L, row.targetId)
        assertEquals(0L, row.reviewedAt)
    }

    @Test
    fun `undo task removes task row`() = runBlocking {
        val id = drafts.seed(DraftKind.TASK, title = "别做这个", body = "x")
        reviewer.approve(id)
        assertEquals(1, tasks.rows.size)

        reviewer.undo(id)

        assertTrue(tasks.rows.isEmpty())
        assertEquals(DraftStatus.PENDING, drafts.rows.single().status)
    }

    @Test
    fun `undo only works on approved drafts`() = runBlocking {
        val pendingId = drafts.seed(DraftKind.NOTE, title = "还没确认", body = "x")
        val rejectedId = drafts.seed(DraftKind.NOTE, title = "已驳回", body = "x")
        reviewer.reject(rejectedId)

        assertTrue(reviewer.undo(pendingId) is ReviewOutcome.Failed)
        assertTrue(reviewer.undo(rejectedId) is ReviewOutcome.Failed)
        assertEquals(DraftStatus.PENDING, drafts.rows.first { it.id == pendingId }.status)
        assertEquals(DraftStatus.REJECTED, drafts.rows.first { it.id == rejectedId }.status)
    }

    @Test
    fun `undo then approve again works`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "第一版", body = "x")
        reviewer.approve(id)
        reviewer.undo(id)

        val again = reviewer.approve(id)

        assertTrue(again is ReviewOutcome.NoteSaved)
        assertEquals(1, notes.rows.size)
        assertEquals("第一版", notes.rows.single().title)
    }

    // ── 状态机 ───────────────────────────────────────────

    @Test
    fun `approve twice does not duplicate`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "只该有一次", body = "x")
        reviewer.approve(id)

        val second = reviewer.approve(id)

        assertTrue(second is ReviewOutcome.Failed)
        assertEquals(1, notes.rows.size)
    }

    @Test
    fun `reject writes nothing and marks rejected`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "别要了", body = "x")

        val outcome = reviewer.reject(id)

        assertTrue(outcome is ReviewOutcome.Rejected)
        assertTrue(notes.rows.isEmpty())
        val row = drafts.rows.single()
        assertEquals(DraftStatus.REJECTED, row.status)
        assertEquals(0L, row.targetId)
    }

    @Test
    fun `reject after approve is refused`() = runBlocking {
        val id = drafts.seed(DraftKind.NOTE, title = "已经确认", body = "x")
        reviewer.approve(id)

        val outcome = reviewer.reject(id)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertEquals(DraftStatus.APPROVED, drafts.rows.single().status)
    }

    @Test
    fun `approve missing draft fails`() = runBlocking {
        val outcome = reviewer.approve(999L)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertTrue(notes.rows.isEmpty())
        assertTrue(tasks.rows.isEmpty())
    }

    @Test
    fun `approve unknown kind fails`() = runBlocking {
        val id = drafts.seed("memo", title = "未知类型", body = "x")

        val outcome = reviewer.approve(id)

        assertTrue(outcome is ReviewOutcome.Failed)
        assertEquals(DraftStatus.PENDING, drafts.rows.single().status)
    }

    @Test
    fun `approveAll handles mixed drafts`() = runBlocking {
        drafts.seed(DraftKind.NOTE, title = "笔记一", body = "a")
        drafts.seed(DraftKind.TASK, title = "任务一", body = "b")
        drafts.seed("memo", title = "怪东西", body = "c")

        val outcomes = reviewer.approveAll()

        assertEquals(3, outcomes.size)
        assertEquals(1, notes.rows.size)
        assertEquals(1, tasks.rows.size)
        assertEquals(1, outcomes.count { it is ReviewOutcome.Failed })
        assertEquals(2, drafts.rows.count { it.status == DraftStatus.APPROVED })
        assertEquals(1, drafts.rows.count { it.status == DraftStatus.PENDING })
    }

    // ── 假实现 ───────────────────────────────────────────

    private class FakeDrafts : DraftRepository {
        val rows = mutableListOf<DraftEntity>()
        private var nextId = 1L
        private var clock = 1_000L

        fun seed(
            kind: String,
            title: String,
            body: String = "",
            tags: String = "",
            dueAt: Long = 0L,
            priority: Int = 1,
        ): Long {
            val id = nextId++
            rows += DraftEntity(
                id = id,
                kind = kind,
                title = title,
                body = body,
                tags = tags,
                dueAt = dueAt,
                priority = priority,
                createdAt = clock,
            )
            return id
        }

        override suspend fun listPending() =
            rows.filter { it.status == DraftStatus.PENDING }

        override suspend fun listReviewed(limit: Int) =
            rows.filter { it.status != DraftStatus.PENDING }.take(limit)

        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }

        override suspend fun propose(draft: DraftEntity): Long {
            val id = nextId++
            rows += draft.copy(id = id, status = DraftStatus.PENDING)
            return id
        }

        override suspend fun markApproved(id: Long, targetId: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(status = DraftStatus.APPROVED, targetId = targetId, reviewedAt = ++clock)
        }

        override suspend fun markRejected(id: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(status = DraftStatus.REJECTED, targetId = 0L, reviewedAt = ++clock)
        }

        override suspend fun markPending(id: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(status = DraftStatus.PENDING, targetId = 0L, reviewedAt = 0L)
        }

        override suspend fun updateContent(
            id: Long,
            title: String,
            body: String,
            tags: String,
            dueAt: Long,
            priority: Int,
        ) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) {
                rows[i] = rows[i].copy(
                    title = title, body = body, tags = tags, dueAt = dueAt, priority = priority,
                )
            }
        }

        override suspend fun countPending() = rows.count { it.status == DraftStatus.PENDING }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }
    }

    private class FakeNotes : NoteRepository {
        val rows = mutableListOf<NoteEntity>()
        private var nextId = 1L

        override suspend fun listActive() = rows.filter { !it.archived }
        override suspend fun listArchived() = rows.filter { it.archived }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun search(query: String) = rows.filter { it.title.contains(query) }
        override suspend fun save(note: NoteEntity): Long {
            val id = if (note.id == 0L) nextId++ else note.id
            val row = note.copy(id = id)
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = row else rows += row
            return id
        }
        override suspend fun delete(note: NoteEntity) { rows.removeAll { it.id == note.id } }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
        override suspend fun countActive() = rows.count { !it.archived }
    }

    private class FakeTasks : TaskRepository {
        val rows = mutableListOf<TaskEntity>()
        private var nextId = 1L

        override suspend fun listOpen() = rows.filter { !it.done }
        override suspend fun listDone() = rows.filter { it.done }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun save(task: TaskEntity): Long {
            val id = if (task.id == 0L) nextId++ else task.id
            val row = task.copy(id = id)
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = row else rows += row
            return id
        }
        override suspend fun setDone(id: Long, done: Boolean) = Unit
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun countOpen() = rows.count { !it.done }
    }
}
