package com.lanweixi.feature.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.repository.PromptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 提示词分类（产品概念，存字符串）。 */
object PromptCategory {
    const val ALL = "all"
    const val IP = "ip"
    const val VIDEO = "video"
    const val IMAGE = "image"
    const val OTHER = "other"

    val order = listOf(ALL, IP, VIDEO, IMAGE, OTHER)

    fun label(id: String): String = when (id) {
        IP -> "IP 创作"
        VIDEO -> "短视频"
        IMAGE -> "绘图"
        OTHER -> "其他"
        else -> "全部"
    }
}

data class PromptsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: String = PromptCategory.ALL,
    val items: List<PromptEntity> = emptyList(),
    val error: String? = null,
)

data class PromptEditUiState(
    val id: Long = 0L,
    val title: String = "",
    val category: String = PromptCategory.IP,
    val positive: String = "",
    val negative: String = "",
    val params: String = "",
    val memo: String = "",
    val pinned: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val copied: String? = null,
)

/**
 * P133：创作提示词库。
 *
 * 只依赖 [PromptRepository]；复制动作在 UI 层落到剪贴板，这里只产出要复制的文本。
 */
class PromptsViewModel(
    private val prompts: PromptRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(PromptsUiState())
    val list: StateFlow<PromptsUiState> = _list.asStateFlow()

    private val _edit = MutableStateFlow(PromptEditUiState())
    val edit: StateFlow<PromptEditUiState> = _edit.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(text: String) {
        _list.update { it.copy(query = text) }
        refresh()
    }

    fun selectCategory(category: String) {
        _list.update { it.copy(category = category) }
        refresh()
    }

    fun refresh() {
        val snapshot = _list.value
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val rows = when {
                    snapshot.query.isNotBlank() -> prompts.search(snapshot.query)
                    snapshot.category == PromptCategory.ALL -> prompts.listAll()
                    else -> prompts.listByCategory(snapshot.category)
                }
                _list.update { it.copy(loading = false, items = rows, error = null) }
            }.onFailure { e ->
                _list.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun edit(id: Long) {
        _edit.value = PromptEditUiState(id = id, loading = id != 0L)
        if (id == 0L) return
        viewModelScope.launch {
            runCatching { prompts.byId(id) }.onSuccess { row ->
                if (row == null) {
                    _edit.update { it.copy(loading = false, error = "提示词不存在") }
                } else {
                    _edit.value = PromptEditUiState(
                        id = row.id,
                        title = row.title,
                        category = row.category.ifBlank { PromptCategory.IP },
                        positive = row.positive,
                        negative = row.negative,
                        params = row.params,
                        memo = row.memo,
                        pinned = row.pinned,
                    )
                }
            }.onFailure { e ->
                _edit.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun onTitleChange(text: String) = _edit.update { it.copy(title = text) }
    fun onCategoryChange(category: String) = _edit.update { it.copy(category = category) }
    fun onPositiveChange(text: String) = _edit.update { it.copy(positive = text) }
    fun onNegativeChange(text: String) = _edit.update { it.copy(negative = text) }
    fun onParamsChange(text: String) = _edit.update { it.copy(params = text) }
    fun onMemoChange(text: String) = _edit.update { it.copy(memo = text) }

    /** 保存；标题与正向都空则拒绝。 */
    fun save(onDone: () -> Unit) {
        val s = _edit.value
        if (s.saving) return
        if (s.title.isBlank() && s.positive.isBlank()) {
            _edit.update { it.copy(error = "标题和正向提示词至少写一个") }
            return
        }
        _edit.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                prompts.save(
                    PromptEntity(
                        id = s.id,
                        title = s.title.trim().ifBlank { "未命名提示词" },
                        category = s.category,
                        positive = s.positive,
                        negative = s.negative,
                        params = s.params,
                        memo = s.memo,
                        pinned = s.pinned,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )
            }.onSuccess {
                _edit.update { it.copy(saving = false) }
                refresh()
                onDone()
            }.onFailure { e ->
                _edit.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun togglePinned(onDone: () -> Unit) {
        val s = _edit.value
        if (s.id == 0L) {
            _edit.update { it.copy(pinned = !it.pinned) }
            return
        }
        val next = !s.pinned
        _edit.update { it.copy(pinned = next) }
        viewModelScope.launch {
            runCatching { prompts.setPinned(s.id, next) }
            refresh()
            onDone()
        }
    }

    fun togglePinnedFromList(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            runCatching { prompts.setPinned(id, pinned) }
            refresh()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _edit.value.id
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching { prompts.deleteById(id) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _edit.update { it.copy(error = e.message ?: "删除失败") } }
        }
    }

    /** 只复制正向（最高频）。 */
    fun copyPositive(): String = _edit.value.positive

    /** 复制完整版：正向 + 负向 + 参数，直接能粘到别人家的输入框。 */
    fun copyFull(): String {
        val s = _edit.value
        val parts = mutableListOf<String>()
        if (s.positive.isNotBlank()) parts += s.positive.trim()
        if (s.negative.isNotBlank()) parts += "负向：" + s.negative.trim()
        if (s.params.isNotBlank()) parts += "参数：" + s.params.trim()
        return parts.joinToString("\n\n")
    }

    /** 列表页直接复制某条的正向。 */
    fun copyOf(row: PromptEntity): String = row.positive

    fun notifyCopied(message: String) = _edit.update { it.copy(copied = message) }
    fun clearCopied() = _edit.update { it.copy(copied = null) }
}

class PromptsViewModelFactory(
    private val prompts: PromptRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PromptsViewModel(prompts) as T
}