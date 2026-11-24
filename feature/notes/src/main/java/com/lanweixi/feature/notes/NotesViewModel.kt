package com.lanweixi.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 笔记列表页状态。 */
data class NotesUiState(
    val loading: Boolean = true,
    val query: String = "",
    val notes: List<NoteEntity> = emptyList(),
    val archived: List<NoteEntity> = emptyList(),
    val showArchived: Boolean = false,
    val error: String? = null,
    /** P199：当前按标签筛选；null = 不筛。 */
    val selectedTag: String? = null,
    /** P199：全部用过的标签（去重排序），列表顶部的筛选条用。 */
    val allTags: List<String> = emptyList(),
)

/** P199：标签解析。中英文逗号、分号都算分隔符，顺手去掉空白。 */
internal fun parseTags(raw: String): List<String> =
    raw.split(',', '，', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** 编辑页状态。`id == 0L` 表示新建。 */
data class NoteEditUiState(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val tags: String = "",
    val archived: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * P132：工作台笔记的列表与编辑。
 *
 * 只依赖 [NoteRepository]；时间戳由仓库补，这里不管。
 */
class NotesViewModel(
    private val notes: NoteRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(NotesUiState())
    val list: StateFlow<NotesUiState> = _list.asStateFlow()

    private val _edit = MutableStateFlow(NoteEditUiState())
    val edit: StateFlow<NoteEditUiState> = _edit.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(text: String) {
        _list.update { it.copy(query = text) }
        refresh()
    }

    fun toggleArchived() {
        _list.update { it.copy(showArchived = !it.showArchived) }
        refresh()
    }

    /**
     * P199：点同一个标签取消筛选。
     *
     * 标签筛选和搜索是**叠加**的：先按关键词查，再按标签过一遍。
     */
    fun onTagSelect(tag: String?) {
        _list.update { it.copy(selectedTag = if (it.selectedTag == tag) null else tag) }
        refresh()
    }

    fun refresh() {
        val snapshot = _list.value
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                // 全量 active 用来算标签池；搜索时再走一次 SQL
                val all = notes.listActive()
                val active = if (snapshot.query.isBlank()) all else notes.search(snapshot.query)
                val archived = notes.listArchived()
                val tags = all.flatMap { parseTags(it.tags) }.distinct().sorted()
                // 标签被改没了就把筛选也清掉，不然会卡在一个空列表上
                val selected = snapshot.selectedTag?.takeIf { it in tags }
                val filtered = if (selected == null) {
                    active
                } else {
                    active.filter { selected in parseTags(it.tags) }
                }
                _list.update {
                    it.copy(
                        loading = false,
                        notes = filtered,
                        archived = archived,
                        allTags = tags,
                        selectedTag = selected,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _list.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    /** 打开编辑页：`id == 0` 新建，否则载入。 */
    fun edit(id: Long) {
        _edit.value = NoteEditUiState(id = id, loading = id != 0L)
        if (id == 0L) return
        viewModelScope.launch {
            runCatching { notes.byId(id) }.onSuccess { note ->
                if (note == null) {
                    _edit.update { it.copy(loading = false, error = "笔记不存在") }
                } else {
                    _edit.value = NoteEditUiState(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        tags = note.tags,
                        archived = note.archived,
                    )
                }
            }.onFailure { e ->
                _edit.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun onTitleChange(text: String) = _edit.update { it.copy(title = text) }
    fun onContentChange(text: String) = _edit.update { it.copy(content = text) }
    fun onTagsChange(text: String) = _edit.update { it.copy(tags = text) }

    /** 保存；标题与正文都空则拒绝（避免产生空笔记）。 */
    fun save(onDone: () -> Unit) {
        val s = _edit.value
        if (s.saving) return
        if (s.title.isBlank() && s.content.isBlank()) {
            _edit.update { it.copy(error = "标题和正文至少写一个") }
            return
        }
        _edit.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                notes.save(
                    NoteEntity(
                        id = s.id,
                        title = s.title.trim().ifBlank { firstLine(s.content) },
                        content = s.content,
                        tags = s.tags.trim(),
                        archived = s.archived,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )
            }.onSuccess {
                _edit.update { it.copy(saving = false, saved = true) }
                refresh()
                onDone()
            }.onFailure { e ->
                _edit.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _edit.value.id
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching { notes.deleteById(id) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _edit.update { it.copy(error = e.message ?: "删除失败") } }
        }
    }

    fun archive(onDone: () -> Unit) {
        val id = _edit.value.id
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching { notes.setArchived(id, true) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _edit.update { it.copy(error = e.message ?: "归档失败") } }
        }
    }

    fun unarchive(id: Long) {
        viewModelScope.launch {
            runCatching { notes.setArchived(id, false) }
            refresh()
        }
    }

    private fun firstLine(text: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.take(40).orEmpty().ifBlank { "无标题" }
}

class NotesViewModelFactory(
    private val notes: NoteRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(notes) as T
}