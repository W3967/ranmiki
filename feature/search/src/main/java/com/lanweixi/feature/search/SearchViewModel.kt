package com.lanweixi.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lanweixi.core.data.search.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P199：全局搜索页状态。
 *
 * [searched] 用来区分「还没搜」和「搜了没结果」——两者都空，但文案完全不同。
 */
data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val searched: Boolean = false,
    val error: String? = null,
) {
    val empty: Boolean get() = searched && !loading && hits.isEmpty()
}

/**
 * P199：全局搜索。
 *
 * 数据来自 [com.lanweixi.core.data.search.GlobalSearch]（笔记 / 任务 / 文档 / 提示词 / 复盘五路合并），
 * 这里只做防抖和状态收口——ViewModel 不碰仓储，单测直接塞 fake 检索函数。
 */
class SearchViewModel(
    private val search: suspend (String) -> List<SearchHit>,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 250L,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        job?.cancel()

        if (text.isBlank()) {
            _state.update { it.copy(hits = emptyList(), searched = false, loading = false, error = null) }
            return
        }

        job = scope.launch {
            // 每敲一个字就查一次会打五路仓储，等手停下来再说
            if (debounceMs > 0) delay(debounceMs)
            _state.update { it.copy(loading = true, error = null) }
            runCatching { search(text.trim()) }
                .onSuccess { hits ->
                    _state.update { it.copy(loading = false, hits = hits, searched = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, searched = true, error = e.message ?: "搜索失败")
                    }
                }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

class SearchViewModelFactory(
    private val search: suspend (String) -> List<SearchHit>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SearchViewModel(
            search = search,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ) as T
}
