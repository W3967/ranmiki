package com.lanweixi.feature.prompts

import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.repository.PromptRepository
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

/** P133：创作提示词 ViewModel 单测（纯 JVM，Fake 仓库）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakePrompts : PromptRepository {
        val rows = mutableListOf<PromptEntity>()
        var nextId = 1L

        private fun sorted() = rows.sortedWith(
            compareByDescending<PromptEntity> { it.pinned }.thenByDescending { it.updatedAt },
        )

        override suspend fun listAll() = sorted()
        override suspend fun listByCategory(category: String) = sorted().filter { it.category == category }
        override suspend fun byId(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun search(query: String) = sorted().filter {
            it.title.contains(query) || it.positive.contains(query) ||
                it.negative.contains(query) || it.memo.contains(query)
        }
        override suspend fun save(prompt: PromptEntity): Long {
            if (prompt.id == 0L) {
                val id = nextId++
                rows += prompt.copy(id = id, updatedAt = id * 100L)
                return id
            }
            val i = rows.indexOfFirst { it.id == prompt.id }
            if (i >= 0) rows[i] = prompt.copy(updatedAt = prompt.id * 100L)
            return prompt.id
        }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(pinned = pinned)
        }
        override suspend fun count() = rows.size
    }

    private fun vm(repo: FakePrompts) = PromptsViewModel(repo)

    @Test
    fun `init loads all prompts`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 1, title = "A", positive = "p", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        assertEquals(listOf("A"), vm.list.value.items.map { it.title })
        assertFalse(vm.list.value.loading)
    }

    @Test
    fun `filter by category`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 1, title = "IP", category = "ip", positive = "p", createdAt = 1, updatedAt = 200)
            rows += PromptEntity(id = 2, title = "图", category = "image", positive = "p", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.selectCategory(PromptCategory.IMAGE)
        assertEquals(listOf("图"), vm.list.value.items.map { it.title })
        vm.selectCategory(PromptCategory.ALL)
        assertEquals(2, vm.list.value.items.size)
    }

    @Test
    fun `search hits positive and memo`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 1, title = "雨夜", positive = "少女站在雨里", createdAt = 1, updatedAt = 200)
            rows += PromptEntity(id = 2, title = "分镜", positive = "特写眼睛", memo = "参考王家卫", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.onQueryChange("雨里")
        assertEquals(listOf("雨夜"), vm.list.value.items.map { it.title })
        vm.onQueryChange("王家卫")
        assertEquals(listOf("分镜"), vm.list.value.items.map { it.title })
        vm.onQueryChange("")
        assertEquals(2, vm.list.value.items.size)
    }

    @Test
    fun `blank prompt is rejected`() = runTest {
        val repo = FakePrompts()
        val vm = vm(repo)
        vm.edit(0L)
        var returned = false
        vm.save { returned = true }
        assertFalse(returned)
        assertTrue(repo.rows.isEmpty())
        assertEquals("标题和正向提示词至少写一个", vm.edit.value.error)
    }

    @Test
    fun `creating keeps title and positive`() = runTest {
        val repo = FakePrompts()
        val vm = vm(repo)
        vm.edit(0L)
        vm.onTitleChange("赛博少女")
        vm.onPositiveChange("雨夜，霓虹，reflection")
        vm.onCategoryChange(PromptCategory.IMAGE)
        vm.save { }

        assertEquals(1, repo.rows.size)
        val row = repo.rows[0]
        assertEquals("赛博少女", row.title)
        assertEquals("雨夜，霓虹，reflection", row.positive)
        assertEquals(PromptCategory.IMAGE, row.category)
    }

    @Test
    fun `editing keeps id`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 9, title = "旧", positive = "旧正向", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(9L)
        assertEquals("旧", vm.edit.value.title)
        vm.onTitleChange("新")
        vm.save { }
        assertEquals("新", repo.rows[0].title)
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun `copy full joins positive negative and params`() = runTest {
        val repo = FakePrompts()
        val vm = vm(repo)
        vm.edit(0L)
        vm.onPositiveChange("正向")
        vm.onNegativeChange("模糊")
        vm.onParamsChange("1024x1024")
        assertEquals("正向", vm.copyPositive())
        assertEquals("正向\n\n负向：模糊\n\n参数：1024x1024", vm.copyFull())
    }

    @Test
    fun `toggle pinned from list reorders`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 1, title = "老", positive = "p", createdAt = 1, updatedAt = 500)
            rows += PromptEntity(id = 2, title = "新", positive = "p", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        assertEquals(listOf("老", "新"), vm.list.value.items.map { it.title })
        vm.togglePinnedFromList(2, true)
        assertEquals(listOf("新", "老"), vm.list.value.items.map { it.title })
    }

    @Test
    fun `delete removes prompt`() = runTest {
        val repo = FakePrompts().apply {
            rows += PromptEntity(id = 4, title = "删我", positive = "p", createdAt = 1, updatedAt = 100)
        }
        val vm = vm(repo)
        vm.edit(4L)
        vm.delete { }
        assertTrue(repo.rows.isEmpty())
        assertNull(repo.byId(4))
    }
}