package com.lanweixi.feature.search

import com.lanweixi.core.data.search.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P199：全局搜索（防抖、空查询、结果、失败）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private fun CoroutineScope.newVm(
        search: suspend (String) -> List<SearchHit>,
        debounceMs: Long = 0L,
    ) = SearchViewModel(search = search, scope = this, debounceMs = debounceMs)

    @Test
    fun `blank query clears results without searching`() = runTest {
        var calls = 0
        val vm = newVm({ calls++; emptyList() })

        vm.onQueryChange("   ")
        advanceUntilIdle()

        assertEquals(0, calls)
        assertFalse(vm.state.value.searched)
        assertTrue(vm.state.value.hits.isEmpty())
    }

    @Test
    fun `hits land in state`() = runTest {
        val hit = SearchHit("note", 1, "周报", "本周做了…", 100)
        val vm = newVm({ query -> if (query == "周报") listOf(hit) else emptyList() })

        vm.onQueryChange("周报")
        advanceUntilIdle()

        assertEquals(listOf(hit), vm.state.value.hits)
        assertTrue(vm.state.value.searched)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.empty)
    }

    @Test
    fun `empty result is reported as searched but empty`() = runTest {
        val vm = newVm({ emptyList() })

        vm.onQueryChange("不存在")
        advanceUntilIdle()

        assertTrue(vm.state.value.searched)
        assertTrue(vm.state.value.empty)
    }

    @Test
    fun `fast typing only triggers one search`() = runTest {
        var calls = 0
        val vm = newVm({ calls++; emptyList() }, debounceMs = 100)

        vm.onQueryChange("笔")
        vm.onQueryChange("笔记")
        vm.onQueryChange("笔记 标签")
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `failure surfaces an error instead of silent empty`() = runTest {
        val vm = newVm({ error("库挂了") })

        vm.onQueryChange("周报")
        advanceUntilIdle()

        assertEquals("库挂了", vm.state.value.error)
        assertTrue(vm.state.value.hits.isEmpty())
    }

    @Test
    fun `clearing the query resets previous results`() = runTest {
        val vm = newVm({ listOf(SearchHit("note", 1, "周报", "", 1)) })

        vm.onQueryChange("周报")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.hits.size)

        vm.onQueryChange("")
        advanceUntilIdle()

        assertTrue(vm.state.value.hits.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.searched)
    }
}
