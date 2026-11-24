package com.lanweixi.feature.docs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.entity.DocChunkEntity
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.repository.DocumentRepository
import com.lanweixi.domain.docs.DocChunker
import com.lanweixi.domain.docs.DocTextExtractor
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val docs: List<DocumentEntity> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
    /** 一次性提示：导入结果。 */
    val notice: String? = null,
)

data class DocDetailUiState(
    val doc: DocumentEntity? = null,
    val chunks: List<DocChunkEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * P135：文档库。
 *
 * 导入链路：调用方给一个「打开 InputStream 的方法」+ 文件名，
 * 解析与切块都在这里完成，UI 只负责拿到 SAF 的 Uri。
 */
class DocsViewModel(
    private val docs: DocumentRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(DocsUiState())
    val list: StateFlow<DocsUiState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(DocDetailUiState())
    val detail: StateFlow<DocDetailUiState> = _detail.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(text: String) {
        _list.update { it.copy(query = text) }
        refresh()
    }

    fun consumeNotice() = _list.update { it.copy(notice = null) }

    fun refresh() {
        val snapshot = _list.value
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (snapshot.query.isBlank()) docs.listAll() else docs.search(snapshot.query)
            }.onSuccess { rows ->
                _list.update { it.copy(loading = false, docs = rows) }
            }.onFailure { e ->
                _list.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    /**
     * 导入一个文件。
     *
     * @param fileName 用于判断格式、也用于回退标题
     * @param open 每次调用返回一个新的 InputStream（SAF 的 Uri 可以重复打开）
     */
    fun import(fileName: String, open: () -> InputStream) {
        if (_list.value.importing) return
        _list.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val extracted = open().use { DocTextExtractor.extract(fileName, it) }
                val chunks = DocChunker.chunk(extracted.text)
                if (chunks.isEmpty()) error("切块后没有内容")
                val id = docs.import(
                    title = fileName.substringBeforeLast('.').ifBlank { fileName },
                    fileName = fileName,
                    kind = extracted.kind,
                    chunks = chunks,
                )
                id to chunks.size
            }.onSuccess { (id, count) ->
                _list.update {
                    it.copy(
                        importing = false,
                        notice = "已导入，切出 $count 块",
                    )
                }
                refresh()
                detail(id)
            }.onFailure { e ->
                _list.update {
                    it.copy(importing = false, error = e.message ?: "导入失败")
                }
            }
        }
    }

    fun detail(id: Long) {
        _detail.value = DocDetailUiState(loading = true)
        viewModelScope.launch {
            runCatching { docs.byId(id) to docs.chunksOf(id) }
                .onSuccess { (doc, chunks) ->
                    _detail.value = DocDetailUiState(
                        doc = doc,
                        chunks = chunks,
                        loading = false,
                        error = if (doc == null) "文档不存在" else null,
                    )
                }
                .onFailure { e ->
                    _detail.value = DocDetailUiState(loading = false, error = e.message ?: "读取失败")
                }
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { docs.deleteById(id) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e ->
                    _detail.update { it.copy(error = e.message ?: "删除失败") }
                }
        }
    }
}

class DocsViewModelFactory(
    private val docs: DocumentRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DocsViewModel(docs) as T
}