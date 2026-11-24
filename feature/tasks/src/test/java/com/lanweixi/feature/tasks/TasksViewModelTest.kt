package com.lanweixi.feature.tasks

import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.repository.EventRepository
import com.lanweixi.core.data.repository.TaskRepository
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

/** P134：任务 ViewModel 单测（纯 JVM，Fake 仓库）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeTasks : TaskRepository {
        val rows = mutableListOf<TaskEntity>()
        var nextId = 1L

        override suspend fun listOpen() = rows.filter { !it.done }
            .sortedWith(
                compareBy<TaskEntity> { if (it.dueAt == 0L) 1 else 0 }
                    .thenBy { it.dueAt }
                    .thenByDescending { it.priority },
            )

        override suspend fun listDone() = rows.filter { it.done }.sortedByDescending { it.doneAt }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun save(task: TaskEntity): Long {
            if (task.id == 0L) {
                val id = nextId++
                rows += task.copy(id = id, updatedAt = id * 100L)
                return id
            }
            val i = rows.indexOfFirst { it.id == task.id }
            if (i >= 0) rows[i] = task.copy(updatedAt = task.id * 100L)
            return task.id
        }
        override suspend fun setDone(id: Long, done: Boolean) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(done = done, doneAt = if (done) 9_999L else 0L)
        }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun countOpen() = rows.count { !it.done }
    }

    private fun vm(
        repo: FakeTasks,
        now: Long = 1_000_000L,
        events: EventRepository? = null,
    ) = TasksViewModel(repo, events) { now }

    /** P208：完成回传的假事件仓库。 */
    private class FakeEvents : EventRepository {
        val rows = mutableListOf<EventEntity>()

        override suspend fun record(event: EventEntity): Long {
            rows += event
            return rows.size.toLong()
        }

        override suspend fun recent(limit: Int) = rows.takeLast(limit).reversed()

        override suspend fun listByTypeBetween(type: String, from: Long, to: Long) =
            rows.filter { it.type == type && it.at in from..to }

        // P217：EventRepository 新增保留策略；这里不需要，占位即可
        override suspend fun trim(before: Long, keepLatest: Int) = Unit
        override suspend fun redactLegacyPayloads() = Unit
    }

    @Test
    fun `init loads open and done separately`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 1, title = "待办", createdAt = 1, updatedAt = 100)
            rows += TaskEntity(id = 2, title = "做完", done = true, doneAt = 5, createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        assertEquals(listOf("待办"), vm.list.value.open.map { it.title })
        assertEquals(listOf("做完"), vm.list.value.done.map { it.title })
        assertFalse(vm.list.value.loading)
    }

    @Test
    fun `new task requires a title`() = runTest {
        val repo = FakeTasks()
        val vm = vm(repo)
        vm.edit(0L)
        var returned = false
        vm.save { returned = true }
        assertFalse(returned)
        assertTrue(repo.rows.isEmpty())
        assertEquals("给任务起个名字", vm.edit.value.error)
    }

    @Test
    fun `creating task stores priority and due`() = runTest {
        val repo = FakeTasks()
        val vm = vm(repo)
        vm.edit(0L)
        vm.onTitleChange("写交接包")
        vm.onPriorityChange(TaskPriority.HIGH)
        vm.onDuePresetChange(DuePreset.TOMORROW)
        vm.save { }

        assertEquals(1, repo.rows.size)
        val row = repo.rows[0]
        assertEquals("写交接包", row.title)
        assertEquals(TaskPriority.HIGH, row.priority)
        assertTrue("明天应算出未来的时间戳", row.dueAt > 1_000_000L)
    }

    @Test
    fun `marking done moves task between sections`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 3, title = "收尾", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.setDone(3, true)
        assertTrue(vm.list.value.open.isEmpty())
        assertEquals(listOf("收尾"), vm.list.value.done.map { it.title })

        vm.setDone(3, false)
        assertEquals(listOf("收尾"), vm.list.value.open.map { it.title })
        assertTrue(vm.list.value.done.isEmpty())
    }

    @Test
    fun `editing keeps id`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 7, title = "旧", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(7L)
        assertEquals("旧", vm.edit.value.title)
        vm.onTitleChange("新")
        vm.save { }
        assertEquals("新", repo.rows[0].title)
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun `overdue detection ignores done and empty due`() = runTest {
        val vm = vm(FakeTasks(), now = 1_000L)
        assertTrue(vm.isOverdue(TaskEntity(id = 1, title = "x", dueAt = 500, createdAt = 0, updatedAt = 0)))
        assertFalse(vm.isOverdue(TaskEntity(id = 1, title = "x", dueAt = 0, createdAt = 0, updatedAt = 0)))
        assertFalse(
            vm.isOverdue(TaskEntity(id = 1, title = "x", dueAt = 500, done = true, createdAt = 0, updatedAt = 0)),
        )
        assertFalse(vm.isOverdue(TaskEntity(id = 1, title = "x", dueAt = 5_000, createdAt = 0, updatedAt = 0)))
    }

    @Test
    fun `delete removes task`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 4, title = "删我", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(4L)
        vm.delete { }
        assertTrue(repo.rows.isEmpty())
        assertNull(repo.byId(4))
    }

    @Test
    fun `due preset round trips through millis`() {
        val fixedNow = java.time.ZonedDateTime.of(2026, 5, 20, 10, 0, 0, 0, java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        for (preset in listOf(DuePreset.TODAY, DuePreset.TOMORROW, DuePreset.IN_3_DAYS, DuePreset.IN_7_DAYS)) {
            val millis = DuePreset.toMillis(preset, fixedNow)
            assertEquals(preset, DuePreset.fromMillis(millis, fixedNow))
        }
        assertEquals(0L, DuePreset.toMillis(DuePreset.NONE, fixedNow))
        assertEquals(DuePreset.NONE, DuePreset.fromMillis(0L, fixedNow))
    }

    // ── P208：完成回传 ───────────────────────────────────

    @Test
    fun `completing a task records task_done event`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 1, title = "写周报", createdAt = 1, updatedAt = 100)
        }
        val events = FakeEvents()
        val vm = vm(repo, events = events)

        vm.setDone(1, true)

        assertEquals(1, events.rows.size)
        assertEquals("task_done", events.rows.single().type)
        // P217：payload 不再存任务标题（events 长期保留）
        assertEquals("chars=3", events.rows.single().payload)
        assertTrue(
            "事件里不能出现任务原文",
            !events.rows.single().payload.contains("写周报"),
        )
        assertEquals(1_000_000L, events.rows.single().at)
    }

    @Test
    fun `unchecking does not record event`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 1, title = "写周报", done = true, doneAt = 5, createdAt = 1, updatedAt = 100)
        }
        val events = FakeEvents()
        val vm = vm(repo, events = events)

        vm.setDone(1, false)

        assertTrue(events.rows.isEmpty())
    }

    @Test
    fun `no event repository still works`() = runTest {
        val repo = FakeTasks().apply {
            rows += TaskEntity(id = 1, title = "写周报", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)

        vm.setDone(1, true)

        assertTrue(vm.list.value.done.map { it.title }.contains("写周报"))
    }
}