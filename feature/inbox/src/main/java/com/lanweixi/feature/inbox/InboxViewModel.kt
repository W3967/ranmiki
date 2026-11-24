package com.lanweixi.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.draft.DraftReview
import com.lanweixi.core.data.draft.ReviewOutcome
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.repository.DraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P206：待审核页状态。
 *
 * `message` 是操作结果的一句话（「已存成任务：…」）。
 * 不用 Toast：真机上系统通知 / 输入法弹窗经常把它盖掉，用户以为没生效。
 */
data class InboxUiState(
    val loading: Boolean = true,
    val pending: List<DraftEntity> = emptyList(),
    val reviewed: List<DraftEntity> = emptyList(),
    val showReviewed: Boolean = false,
    /** 有操作在执行（按钮禁用，防连点）。 */
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val countLabel: String get() = if (pending.isEmpty()) "没有待审核" else pending.size.toString() + " 条待审核"
}

/** P207：修改草稿时的表单状态。 */
data class DraftEditUiState(
    val id: Long = 0L,
    val kind: String = DraftKind.NOTE,
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    val duePreset: String = DraftDuePreset.NONE,
    /** 原始截止时间戳：不在快捷档里时保留原值（编辑不该悄悄清掉精确时间）。 */
    val exactDueAt: Long = 0L,
    val priority: Int = 1,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isTask: Boolean get() = kind == DraftKind.TASK
}

/**
 * P206/P207：AI 主导的「待审核」队列。
 *
 * 只做两件事：拉列表（[DraftRepository]）、把审核动作转给 [DraftReview]。
 * 「确认后写进哪张表、标题怎么兜底」这些规则在 core:data 的 DraftReviewer 里，
 * 这里不重复实现，免得两处走偏。
 */
class InboxViewModel(
    private val drafts: DraftRepository,
    private val review: DraftReview,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private val _edit = MutableStateFlow(DraftEditUiState())
    val edit: StateFlow<DraftEditUiState> = _edit.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun toggleReviewed() {
        _state.update { it.copy(showReviewed = !it.showReviewed) }
        viewModelScope.launch { reload() }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** 确认一条：写进笔记 / 任务。 */
    fun approve(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val title = _state.value.pending.firstOrNull { it.id == id }?.title.orEmpty()
            val outcome = runCatching { review.approve(id) }
                .getOrElse { ReviewOutcome.Failed(id, it.message ?: "确认失败") }
            reload()
            _state.update { it.copy(busy = false, message = describe(outcome, title)) }
        }
    }

    /** 驳回一条：只改状态，不写任何正式数据。 */
    fun reject(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = runCatching { review.reject(id) }
                .getOrElse { ReviewOutcome.Failed(id, it.message ?: "驳回失败") }
            reload()
            _state.update { it.copy(busy = false, message = describe(outcome, "")) }
        }
    }

    /** 全部确认。单条失败不中断（失败数会写在 message 里）。 */
    fun approveAll() {
        if (_state.value.busy || _state.value.pending.isEmpty()) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcomes = runCatching { review.approveAll() }.getOrDefault(emptyList())
            val saved = outcomes.count { it is ReviewOutcome.NoteSaved || it is ReviewOutcome.TaskSaved }
            val failed = outcomes.count { it is ReviewOutcome.Failed }
            reload()
            val text = if (outcomes.isEmpty()) {
                "没有可确认的草稿"
            } else {
                "确认了 " + saved + " 条" + if (failed > 0) "，失败 " + failed + " 条" else ""
            }
            _state.update { it.copy(busy = false, message = text) }
        }
    }

    /** P207：撤销一次确认——删掉已写入的记录，草稿回到待审核。 */
    fun undo(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = runCatching { review.undo(id) }
                .getOrElse { ReviewOutcome.Failed(id, it.message ?: "撤销失败") }
            reload()
            _state.update { it.copy(busy = false, message = describe(outcome, "")) }
        }
    }

    // ── P207：修改 ───────────────────────────────────────

    /** 打开修改页：载入草稿内容。 */
    fun openEditor(id: Long) {
        _edit.value = DraftEditUiState(id = id, loading = true)
        viewModelScope.launch {
            runCatching { drafts.byId(id) }.onSuccess { draft ->
                if (draft == null) {
                    _edit.update { it.copy(loading = false, error = "这条草稿已经不存在了") }
                    return@onSuccess
                }
                _edit.value = DraftEditUiState(
                    id = draft.id,
                    kind = draft.kind,
                    title = draft.title,
                    body = draft.body,
                    tags = draft.tags,
                    // 精确时间（不在快捷档里）单独标出来，保存时保留原值
                    duePreset = run {
                        val preset = DraftDuePreset.fromMillis(draft.dueAt, clock())
                        if (draft.dueAt > 0L && preset == DraftDuePreset.NONE) DraftDuePreset.EXACT else preset
                    },
                    exactDueAt = draft.dueAt,
                    priority = draft.priority,
                )
            }.onFailure { e ->
                _edit.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun onTitleChange(text: String) = _edit.update { it.copy(title = text) }
    fun onBodyChange(text: String) = _edit.update { it.copy(body = text) }
    fun onTagsChange(text: String) = _edit.update { it.copy(tags = text) }
    fun onPriorityChange(value: Int) = _edit.update { it.copy(priority = value.coerceIn(0, 2)) }

    fun onDuePresetChange(preset: String) = _edit.update {
        it.copy(
            duePreset = preset,
            // 选「不设」才清掉精确时间；切到快捷档时由 toMillis 决定
            exactDueAt = if (preset == DraftDuePreset.NONE) 0L else it.exactDueAt,
        )
    }

    /** 保存修改并确认：先落草稿内容，再走确认（正式记录与草稿一致）。 */
    fun saveEdit(onDone: () -> Unit) {
        val form = _edit.value
        if (form.saving) return
        if (form.title.isBlank() && form.body.isBlank()) {
            _edit.update { it.copy(error = "标题和正文至少写一个") }
            return
        }
        _edit.update { it.copy(saving = true, error = null) }
        val dueAt = resolveDue(form)
        viewModelScope.launch {
            val outcome = runCatching {
                review.approveEdited(
                    id = form.id,
                    title = form.title,
                    body = form.body,
                    tags = form.tags,
                    dueAt = dueAt,
                    priority = form.priority,
                )
            }.getOrElse { ReviewOutcome.Failed(form.id, it.message ?: "保存失败") }
            reload()
            _state.update { it.copy(message = describe(outcome, form.title)) }
            _edit.update { it.copy(saving = false) }
            if (outcome !is ReviewOutcome.Failed) onDone()
        }
    }

    /** 截止时间：快捷档换算；「原时间」保留精确值；不设才清零。 */
    private fun resolveDue(form: DraftEditUiState): Long = when (form.duePreset) {
        DraftDuePreset.NONE -> 0L
        DraftDuePreset.EXACT -> form.exactDueAt
        else -> DraftDuePreset.toMillis(form.duePreset, clock())
    }

    private suspend fun reload() {
        runCatching {
            val pending = drafts.listPending()
            val reviewed = if (_state.value.showReviewed) drafts.listReviewed() else emptyList()
            pending to reviewed
        }.onSuccess { (pending, reviewed) ->
            _state.update {
                it.copy(loading = false, pending = pending, reviewed = reviewed, error = null)
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.message ?: "读取失败") }
        }
    }

    private fun describe(outcome: ReviewOutcome, title: String): String = when (outcome) {
        is ReviewOutcome.NoteSaved -> "已存成笔记" + suffix(title)
        is ReviewOutcome.TaskSaved -> "已存成任务" + suffix(title)
        is ReviewOutcome.Rejected -> "已驳回"
        is ReviewOutcome.Undone -> "已撤销，草稿回到待审核"
        is ReviewOutcome.Failed -> "没处理成：" + outcome.message
    }

    private fun suffix(title: String): String = if (title.isBlank()) "" else "：「" + title + "」"
}

class InboxViewModelFactory(
    private val drafts: DraftRepository,
    private val review: DraftReview,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InboxViewModel(drafts, review) as T
}
