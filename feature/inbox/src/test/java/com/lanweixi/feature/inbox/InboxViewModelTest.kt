package com.lanweixi.feature.inbox

import com.lanweixi.core.data.draft.DraftReview
import com.lanweixi.core.data.draft.ReviewOutcome
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.repository.DraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P206：待审核页状态机（纯 JVM，假仓储 + 假审核通道）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var drafts: FakeDrafts
    private lateinit var review: FakeReview

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        drafts = FakeDrafts()
        review = FakeReview(drafts)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = InboxViewModel(drafts, review)

    @Test
    fun `init loads pending drafts`() = runTest {
        drafts.seed("A")
        drafts.seed("B")

        val vm = vm()

        assertEquals(listOf("A", "B"), vm.state.value.pending.map { it.title })
        assertFalse(vm.state.value.loading)
        assertEquals("2 条待审核", vm.state.value.countLabel)
    }

    @Test
    fun `approve calls reviewer and reports note`() = runTest {
        val id = drafts.seed("面试要点")
        val vm = vm()

        vm.approve(id)

        assertEquals(1, review.approveCalls)
        assertTrue(vm.state.value.message!!.contains("已存成笔记"))
        assertTrue(vm.state.value.message!!.contains("面试要点"))
        assertTrue(vm.state.value.pending.isEmpty())
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `acknowledged task reports task wording`() = runTest {
        val id = drafts.seed("交报告", kind = DraftKind.TASK)
        val vm = vm()

        vm.approve(id)

        assertTrue(vm.state.value.message!!.contains("已存成任务"))
    }

    @Test
    fun `reject reports rejected and keeps row reviewed`() = runTest {
        val id = drafts.seed("别要了")
        val vm = vm()

        vm.reject(id)

        assertEquals(1, review.rejectCalls)
        assertEquals("已驳回", vm.state.value.message)
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test
    fun `approveAll reports count`() = runTest {
        drafts.seed("A")
        drafts.seed("B")
        val vm = vm()

        vm.approveAll()

        assertEquals(1, review.approveAllCalls)
        assertEquals("确认了 2 条", vm.state.value.message)
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test
    fun `approveAll does nothing when empty`() = runTest {
        val vm = vm()

        vm.approveAll()

        assertEquals(0, review.approveAllCalls)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `failed approval surfaces reason`() = runTest {
        val id = drafts.seed("会失败")
        review.failApprove = true
        val vm = vm()

        vm.approve(id)

        assertTrue(vm.state.value.message!!.contains("没处理成"))
        assertTrue(vm.state.value.message!!.contains("写库炸了"))
        // 失败的草稿仍在待审核里
        assertEquals(listOf(id), vm.state.value.pending.map { it.id })
    }

    @Test
    fun `toggleReviewed loads reviewed rows`() = runTest {
        val id = drafts.seed("已确认的")
        drafts.rows[0] = drafts.rows[0].copy(status = DraftStatus.APPROVED, reviewedAt = 10L)
        val vm = vm()
        assertTrue(vm.state.value.reviewed.isEmpty())

        vm.toggleReviewed()

        assertTrue(vm.state.value.showReviewed)
        assertEquals(listOf(id), vm.state.value.reviewed.map { it.id })
    }

    @Test
    fun `clearMessage resets message`() = runTest {
        val id = drafts.seed("x")
        val vm = vm()
        vm.approve(id)

        vm.clearMessage()

        assertNull(vm.state.value.message)
    }

    // ── P207：修改 / 撤销 ────────────────────────────────

    @Test
    fun `openEditor loads draft into form`() = runTest {
        val id = drafts.seed("面试", kind = DraftKind.TASK)
        drafts.rows[0] = drafts.rows[0].copy(body = "带简历", priority = 2, dueAt = 1_800_000_000_000L)
        val vm = vm()

        vm.openEditor(id)

        val form = vm.edit.value
        assertEquals(id, form.id)
        assertEquals("面试", form.title)
        assertEquals("带简历", form.body)
        assertEquals(2, form.priority)
        assertTrue(form.isTask)
        // 不在快捷档里的精确时间要保留成 EXACT，别悄悄清掉
        assertEquals(DraftDuePreset.EXACT, form.duePreset)
        assertFalse(form.loading)
    }

    @Test
    fun `saveEdit calls reviewer and reports result`() = runTest {
        val id = drafts.seed("旧标题", kind = DraftKind.TASK)
        val vm = vm()
        vm.openEditor(id)
        vm.onTitleChange("新标题")
        vm.onBodyChange("新详情")
        vm.onPriorityChange(2)
        var done = false

        vm.saveEdit { done = true }

        assertEquals(1, review.approveEditedCalls)
        assertTrue(done)
        assertTrue(vm.state.value.message!!.contains("已存成任务"))
        assertTrue(vm.state.value.message!!.contains("新标题"))
        val saved = drafts.rows.single()
        assertEquals("新标题", saved.title)
        assertEquals(DraftStatus.APPROVED, saved.status)
    }

    @Test
    fun `saveEdit rejects empty content`() = runTest {
        val id = drafts.seed("x")
        val vm = vm()
        vm.openEditor(id)
        vm.onTitleChange("   ")
        vm.onBodyChange("  ")
        var done = false

        vm.saveEdit { done = true }

        assertEquals("标题和正文至少写一个", vm.edit.value.error)
        assertFalse(done)
        assertEquals(0, review.approveEditedCalls)
    }

    @Test
    fun `undo reports and returns draft to pending`() = runTest {
        val id = drafts.seed("撤销我")
        drafts.rows[0] = drafts.rows[0].copy(status = DraftStatus.APPROVED, targetId = 9L)
        val vm = vm()
        vm.toggleReviewed()

        vm.undo(id)

        assertEquals(1, review.undoCalls)
        assertEquals("已撤销，草稿回到待审核", vm.state.value.message)
        assertEquals(DraftStatus.PENDING, drafts.rows.single().status)
        assertEquals(listOf(id), vm.state.value.pending.map { it.id })
    }

    @Test
    fun `due preset tomorrow resolves to end of day`() = runTest {
        val id = drafts.seed("明天做", kind = DraftKind.TASK)
        val vm = vm()
        vm.openEditor(id)
        vm.onDuePresetChange(DraftDuePreset.TOMORROW)

        vm.saveEdit { }

        val saved = drafts.rows.single()
        val tomorrowEnd = DraftDuePreset.toMillis(DraftDuePreset.TOMORROW)
        assertEquals(tomorrowEnd, saved.dueAt)
        assertTrue(saved.dueAt > 0L)
    }

    // ── fakes ────────────────────────────────────────────

    private class FakeDrafts : DraftRepository {
        val rows = mutableListOf<DraftEntity>()
        private var nextId = 1L

        fun seed(title: String, kind: String = DraftKind.NOTE): Long {
            val id = nextId++
            rows += DraftEntity(
                id = id,
                kind = kind,
                title = title,
                body = "正文",
                createdAt = id,
            )
            return id
        }

        override suspend fun listPending() = rows.filter { it.status == DraftStatus.PENDING }
        override suspend fun listReviewed(limit: Int) =
            rows.filter { it.status != DraftStatus.PENDING }.take(limit)

        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun propose(draft: DraftEntity): Long {
            val id = nextId++
            rows += draft.copy(id = id)
            return id
        }
        override suspend fun markApproved(id: Long, targetId: Long) = Unit
        override suspend fun markRejected(id: Long) = Unit
        override suspend fun countPending() = rows.count { it.status == DraftStatus.PENDING }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }

        override suspend fun markPending(id: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(status = DraftStatus.PENDING, targetId = 0L)
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
                rows[i] = rows[i].copy(title = title, body = body, tags = tags, dueAt = dueAt, priority = priority)
            }
        }
    }

    private class FakeReview(private val drafts: FakeDrafts) : DraftReview {
        var approveCalls = 0
        var rejectCalls = 0
        var approveAllCalls = 0
        var failApprove = false
        private var nextTargetId = 100L

        override suspend fun approve(id: Long): ReviewOutcome {
            approveCalls++
            if (failApprove) return ReviewOutcome.Failed(id, "写库炸了")
            val i = drafts.rows.indexOfFirst { it.id == id }
            if (i < 0) return ReviewOutcome.Failed(id, "没了")
            val row = drafts.rows[i]
            drafts.rows[i] = row.copy(status = DraftStatus.APPROVED, targetId = nextTargetId)
            return if (row.kind == DraftKind.TASK) {
                ReviewOutcome.TaskSaved(id, nextTargetId++)
            } else {
                ReviewOutcome.NoteSaved(id, nextTargetId++)
            }
        }

        override suspend fun reject(id: Long): ReviewOutcome {
            rejectCalls++
            val i = drafts.rows.indexOfFirst { it.id == id }
            if (i >= 0) drafts.rows[i] = drafts.rows[i].copy(status = DraftStatus.REJECTED)
            return ReviewOutcome.Rejected(id)
        }

        override suspend fun approveAll(): List<ReviewOutcome> {
            approveAllCalls++
            val ids = drafts.listPending().map { it.id }
            return ids.map { approve(it) }
        }

        var approveEditedCalls = 0
        var undoCalls = 0

        override suspend fun approveEdited(
            id: Long,
            title: String,
            body: String,
            tags: String,
            dueAt: Long,
            priority: Int,
        ): ReviewOutcome {
            approveEditedCalls++
            val i = drafts.rows.indexOfFirst { it.id == id }
            if (i < 0) return ReviewOutcome.Failed(id, "没了")
            val kind = drafts.rows[i].kind
            drafts.rows[i] = drafts.rows[i].copy(
                title = title,
                body = body,
                tags = tags,
                dueAt = dueAt,
                priority = priority,
                status = DraftStatus.APPROVED,
                targetId = nextTargetId,
            )
            return if (kind == DraftKind.TASK) {
                ReviewOutcome.TaskSaved(id, nextTargetId++)
            } else {
                ReviewOutcome.NoteSaved(id, nextTargetId++)
            }
        }

        override suspend fun undo(id: Long): ReviewOutcome {
            undoCalls++
            val i = drafts.rows.indexOfFirst { it.id == id }
            if (i < 0) return ReviewOutcome.Failed(id, "没了")
            drafts.rows[i] = drafts.rows[i].copy(status = DraftStatus.PENDING, targetId = 0L)
            return ReviewOutcome.Undone(id)
        }
    }
}
