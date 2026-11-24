package com.lanweixi.feature.notes

import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P132：笔记 ViewModel 单测（纯 JVM，Fake 仓库）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeNotes : NoteRepository {
        val rows = mutableListOf<NoteEntity>()
        var nextId = 1L

        override suspend fun listActive() = rows.filter { !it.archived }.sortedByDescending { it.updatedAt }
        override suspend fun listArchived() = rows.filter { it.archived }.sortedByDescending { it.updatedAt }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun search(query: String) = listActive().filter {
            it.title.contains(query) || it.content.contains(query) || it.tags.contains(query)
        }
        override suspend fun save(note: NoteEntity): Long {
            if (note.id == 0L) {
                val id = nextId++
                rows += note.copy(id = id, updatedAt = id * 100L)
                return id
            }
            val i = rows.indexOfFirst { it.id == note.id }
            if (i >= 0) rows[i] = note.copy(updatedAt = note.id * 100L)
            return note.id
        }
        override suspend fun delete(note: NoteEntity) { rows.removeAll { it.id == note.id } }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun setArchived(id: Long, archived: Boolean) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(archived = archived)
        }
        override suspend fun countActive() = rows.count { !it.archived }
    }

    private fun vm(repo: FakeNotes) = NotesViewModel(repo)

    @Test
    fun `init loads active notes`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "A", content = "", createdAt = 1, updatedAt = 200)
        }
        val vm = vm(repo)
        assertEquals(listOf("A"), vm.list.value.notes.map { it.title })
        assertEquals(false, vm.list.value.loading)
    }

    @Test
    fun `search filters by query`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "Kotlin", content = "", createdAt = 1, updatedAt = 200)
            rows += NoteEntity(id = 2, title = "做饭", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.onQueryChange("Kotlin")
        assertEquals(listOf("Kotlin"), vm.list.value.notes.map { it.title })
        vm.onQueryChange("")
        assertEquals(2, vm.list.value.notes.size)
    }

    @Test
    fun `creating a note falls back to first content line as title`() = runTest {
        val repo = FakeNotes()
        val vm = vm(repo)
        vm.edit(0L)
        vm.onContentChange("第一行\n第二行")
        var returned = false
        vm.save { returned = true }

        assertTrue(returned)
        assertEquals(1, repo.rows.size)
        assertEquals("第一行", repo.rows[0].title)
        assertEquals("第一行\n第二行", repo.rows[0].content)
    }

    @Test
    fun `blank note is rejected`() = runTest {
        val repo = FakeNotes()
        val vm = vm(repo)
        vm.edit(0L)
        var returned = false
        vm.save { returned = true }
        assertEquals(false, returned)
        assertTrue(repo.rows.isEmpty())
        assertEquals("标题和正文至少写一个", vm.edit.value.error)
    }

    @Test
    fun `editing keeps id and updates fields`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 7, title = "旧", content = "正文", createdAt = 1, updatedAt = 100)
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
    fun `archive moves note out of active list`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 3, title = "归档我", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(3L)
        vm.archive { }
        assertTrue(vm.list.value.notes.isEmpty())
        assertEquals(listOf("归档我"), vm.list.value.archived.map { it.title })
    }

    @Test
    fun `delete removes note`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 4, title = "删我", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(4L)
        vm.delete { }
        assertTrue(repo.rows.isEmpty())
        assertNull(repo.byId(4))
    }

    @Test
    fun `tags are parsed from half and full width separators`() {
        assertEquals(listOf("工作", "学习", "生活"), parseTags("工作,学习，生活"))
        assertEquals(listOf("a", "b"), parseTags(" a ; b "))
        assertEquals(emptyList<String>(), parseTags(""))
        assertEquals(listOf("唯一"), parseTags("唯一"))
    }

    @Test
    fun `tag filter narrows the list and tapping again clears it`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "周报", tags = "工作", content = "", createdAt = 1, updatedAt = 300)
            rows += NoteEntity(id = 2, title = "菜谱", tags = "生活", content = "", createdAt = 1, updatedAt = 200)
            rows += NoteEntity(id = 3, title = "随笔", tags = "工作,随笔", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)

        vm.onTagSelect("工作")
        assertEquals(listOf("周报", "随笔"), vm.list.value.notes.map { it.title })
        assertEquals("工作", vm.list.value.selectedTag)

        vm.onTagSelect("工作")
        assertEquals(3, vm.list.value.notes.size)
        assertNull(vm.list.value.selectedTag)
    }

    @Test
    fun `tag pool is deduped and sorted`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "A", tags = "beta,alpha", content = "", createdAt = 1, updatedAt = 200)
            rows += NoteEntity(id = 2, title = "B", tags = "alpha", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        assertEquals(listOf("alpha", "beta"), vm.list.value.allTags)
    }

    @Test
    fun `tag filter and query stack together`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "Kotlin 笔记", tags = "工作", content = "", createdAt = 1, updatedAt = 300)
            rows += NoteEntity(id = 2, title = "做饭笔记", tags = "生活", content = "", createdAt = 1, updatedAt = 200)
        }
        val vm = vm(repo)
        vm.onQueryChange("笔记")
        vm.onTagSelect("生活")

        assertEquals(listOf("做饭笔记"), vm.list.value.notes.map { it.title })
    }

    @Test
    fun `removing the last note of a tag drops the filter`() = runTest {
        val repo = FakeNotes().apply {
            rows += NoteEntity(id = 1, title = "只有工作", tags = "工作", content = "", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.onTagSelect("工作")
        assertEquals(1, vm.list.value.notes.size)

        // 标签被编辑掉后刷新，筛选不能卡在一个空列表上
        repo.rows[0] = repo.rows[0].copy(tags = "")
        vm.refresh()

        assertNull(vm.list.value.selectedTag)
        assertEquals(1, vm.list.value.notes.size)
    }
}