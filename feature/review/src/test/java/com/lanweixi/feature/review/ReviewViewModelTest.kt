package com.lanweixi.feature.review

import com.lanweixi.core.data.dao.ReviewSnapshot
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.repository.ReviewRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P136：复盘 ViewModel 单测（纯 JVM，Fake 仓库）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeReviews : ReviewRepository {
        val rows = mutableListOf<ReviewEntity>()
        var nextId = 1L
        var lastWindow: Pair<Long, Long>? = null
        var snapshot = ReviewSnapshot(0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        override suspend fun listAll() = rows.sortedByDescending { it.periodStart }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun snapshot(periodStart: Long, periodEnd: Long): ReviewSnapshot {
            lastWindow = periodStart to periodEnd
            return snapshot.copy(periodStart = periodStart, periodEnd = periodEnd)
        }
        override suspend fun save(review: ReviewEntity): Long {
            if (review.id == 0L) {
                val id = nextId++
                rows += review.copy(id = id, updatedAt = id * 100L)
                return id
            }
            val i = rows.indexOfFirst { it.id == review.id }
            if (i >= 0) rows[i] = review.copy(updatedAt = review.id * 100L)
            return review.id
        }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    }

    private fun fixedNow(y: Int = 2026, m: Int = 5, d: Int = 20): Long =
        LocalDate.of(y, m, d).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun vm(repo: FakeReviews, now: Long = fixedNow()) = ReviewViewModel(repo) { now }

    @Test
    fun `week range starts on monday`() {
        val (start, end) = ReviewPeriod.range(ReviewPeriod.WEEK, fixedNow(2026, 5, 20))
        val zone = ZoneId.systemDefault()
        val startDate = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDate = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        assertEquals(java.time.DayOfWeek.MONDAY, startDate.dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, endDate.dayOfWeek)
        assertTrue(end > start)
    }

    @Test
    fun `month range covers whole month`() {
        val (start, end) = ReviewPeriod.range(ReviewPeriod.MONTH, fixedNow(2026, 5, 20))
        val zone = ZoneId.systemDefault()
        assertEquals(1, java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate().dayOfMonth)
        assertEquals(
            java.time.Month.MAY,
            java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate().month,
        )
    }

    @Test
    fun `new review defaults to this week and loads snapshot`() = runTest {
        val repo = FakeReviews()
        val vm = vm(repo)
        vm.newReview()
        assertEquals(ReviewPeriod.WEEK, vm.edit.value.periodType)
        assertNotNull(vm.edit.value.snapshot)
        assertNotNull(repo.lastWindow)
    }

    @Test
    fun `switching period recomputes window`() = runTest {
        val repo = FakeReviews()
        val vm = vm(repo)
        vm.newReview(ReviewPeriod.WEEK)
        val weekWindow = repo.lastWindow
        vm.switchPeriod(ReviewPeriod.MONTH)
        assertEquals(ReviewPeriod.MONTH, vm.edit.value.periodType)
        assertTrue("月份窗口应与周不同", repo.lastWindow != weekWindow)
    }

    @Test
    fun `summary is preserved and saved`() = runTest {
        val repo = FakeReviews()
        val vm = vm(repo)
        vm.newReview()
        vm.onSummaryChange("这周把文档导入做完了")
        vm.save { }

        assertEquals(1, repo.rows.size)
        assertEquals("这周把文档导入做完了", repo.rows[0].summary)
        assertEquals(ReviewPeriod.WEEK, repo.rows[0].periodType)
        assertTrue(repo.rows[0].periodStart > 0)
    }

    @Test
    fun `opening existing review keeps id and window`() = runTest {
        val repo = FakeReviews().apply {
            rows += ReviewEntity(
                id = 5, periodType = "month", periodStart = 100, periodEnd = 200,
                summary = "旧总结", createdAt = 1, updatedAt = 1,
            )
        }
        val vm = vm(repo)
        vm.open(5)
        assertEquals(5L, vm.edit.value.id)
        assertEquals("旧总结", vm.edit.value.summary)
        assertEquals(ReviewPeriod.MONTH, vm.edit.value.periodType)
        assertEquals(100L to 200L, repo.lastWindow)
    }

    @Test
    fun `deleting review removes it`() = runTest {
        val repo = FakeReviews().apply {
            rows += ReviewEntity(
                id = 3, periodType = "week", periodStart = 1, periodEnd = 2,
                summary = "删我", createdAt = 1, updatedAt = 1,
            )
        }
        val vm = vm(repo)
        vm.delete(3) { }
        assertTrue(repo.rows.isEmpty())
        assertNull(repo.byId(3))
    }

    @Test
    fun `snapshot aggregate counts each kind`() = runTest {
        val repo = FakeReviews()
        repo.snapshot = ReviewSnapshot(
            periodStart = 0, periodEnd = 0,
            notes = listOf(NoteEntity(id = 1, title = "n1", content = "", createdAt = 1, updatedAt = 1)),
            tasksCreated = listOf(TaskEntity(id = 1, title = "t1", createdAt = 1, updatedAt = 1)),
            tasksDone = listOf(
                TaskEntity(id = 1, title = "t1", done = true, doneAt = 2, createdAt = 1, updatedAt = 1),
                TaskEntity(id = 2, title = "t2", done = true, doneAt = 3, createdAt = 1, updatedAt = 1),
            ),
            prompts = listOf(PromptEntity(id = 1, title = "p1", positive = "x", createdAt = 1, updatedAt = 1)),
            documents = listOf(
                DocumentEntity(
                    id = 1, title = "d1", fileName = "a.txt", kind = "txt",
                    charCount = 1, chunkCount = 1, importedAt = 1, updatedAt = 1,
                ),
            ),
        )
        val vm = vm(repo)
        vm.newReview()
        val snap = vm.edit.value.snapshot!!
        assertEquals(1, snap.notes.size)
        assertEquals(1, snap.tasksCreated.size)
        assertEquals(2, snap.tasksDone.size)
        assertEquals(1, snap.prompts.size)
        assertEquals(1, snap.documents.size)
        assertTrue(!snap.isEmpty)
    }

    @Test
    fun `empty snapshot is reported as empty`() = runTest {
        val repo = FakeReviews()
        val vm = vm(repo)
        vm.newReview()
        assertTrue(vm.edit.value.snapshot!!.isEmpty)
    }
}