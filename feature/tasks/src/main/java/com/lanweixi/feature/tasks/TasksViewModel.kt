package com.lanweixi.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.repository.EventRepository
import com.lanweixi.core.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 优先级：0 低 / 1 中 / 2 高。 */
object TaskPriority {
    const val LOW = 0
    const val MID = 1
    const val HIGH = 2

    fun label(value: Int): String = when (value) {
        HIGH -> "高"
        LOW -> "低"
        else -> "中"
    }
}

/**
 * 截止时间快捷键。
 *
 * 刻意不做日历选择器：任务清单里「今天/明天/这周」覆盖了绝大多数情况，
 * 少一层弹窗就少一次犹豫。
 */
object DuePreset {
    const val NONE = "none"
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val IN_3_DAYS = "in3"
    const val IN_7_DAYS = "in7"

    val order = listOf(NONE, TODAY, TOMORROW, IN_3_DAYS, IN_7_DAYS)

    fun label(preset: String): String = when (preset) {
        TODAY -> "今天"
        TOMORROW -> "明天"
        IN_3_DAYS -> "3 天后"
        IN_7_DAYS -> "7 天后"
        else -> "不设"
    }

    /** 换算成当天 23:59:59.999 的时间戳；NONE 返回 0。 */
    fun toMillis(preset: String, now: Long = System.currentTimeMillis()): Long {
        if (preset == NONE) return 0L
        val zone = java.util.TimeZone.getDefault().toZoneId()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = when (preset) {
            TODAY -> 0L
            TOMORROW -> 1L
            IN_3_DAYS -> 3L
            IN_7_DAYS -> 7L
            else -> return 0L
        }
        val target = today.plusDays(days)
        return target.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    }

    /** 反查：给定时间戳落在哪一档（用于编辑页回显）。 */
    fun fromMillis(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return NONE
        return order.firstOrNull { it != NONE && toMillis(it, now) == millis } ?: NONE
    }
}

data class TasksUiState(
    val loading: Boolean = true,
    val open: List<TaskEntity> = emptyList(),
    val done: List<TaskEntity> = emptyList(),
    val showDone: Boolean = false,
    val error: String? = null,
)

data class TaskEditUiState(
    val id: Long = 0L,
    val title: String = "",
    val detail: String = "",
    val priority: Int = TaskPriority.MID,
    val duePreset: String = DuePreset.NONE,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * P134：任务清单。
 *
 * 列表默认只看待办；已完成收在折叠区，避免"做完的事一直占屏幕"。
 */
class TasksViewModel(
    private val tasks: TaskRepository,
    /** P208：完成回传——勾掉一项时写事件，下次对话她能知道。 */
    private val events: EventRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _list = MutableStateFlow(TasksUiState())
    val list: StateFlow<TasksUiState> = _list.asStateFlow()

    private val _edit = MutableStateFlow(TaskEditUiState())
    val edit: StateFlow<TaskEditUiState> = _edit.asStateFlow()

    init {
        refresh()
    }

    fun toggleDoneSection() = _list.update { it.copy(showDone = !it.showDone) }

    fun refresh() {
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { tasks.listOpen() to tasks.listDone() }
                .onSuccess { (open, done) ->
                    _list.update {
                        it.copy(loading = false, open = open, done = done, error = null)
                    }
                }
                .onFailure { e ->
                    _list.update { it.copy(loading = false, error = e.message ?: "读取失败") }
                }
        }
    }

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch {
            runCatching { tasks.setDone(id, done) }
                .onSuccess { if (done) recordDoneEvent(id) }
            refresh()
        }
    }

    /**
     * P208：完成回传。
     *
     * 只写一条 events（type = task_done），不弹任何东西。
     * P217：payload 不再存任务标题——events 表会长期保留，而「最近完成」是
     * 对话侧直接从 tasks 表读的（TaskDigest），这里不需要正文。
     */
    private suspend fun recordDoneEvent(id: Long) {
        val repo = events ?: return
        val task = runCatching { tasks.byId(id) }.getOrNull() ?: return
        runCatching {
            repo.record(
                EventEntity(
                    type = TASK_DONE_EVENT,
                    payload = "chars=${task.title.length}",
                    at = clock(),
                ),
            )
        }
    }

    fun edit(id: Long) {
        _edit.value = TaskEditUiState(id = id, loading = id != 0L)
        if (id == 0L) return
        viewModelScope.launch {
            runCatching { tasks.byId(id) }.onSuccess { row ->
                if (row == null) {
                    _edit.update { it.copy(loading = false, error = "任务不存在") }
                } else {
                    _edit.value = TaskEditUiState(
                        id = row.id,
                        title = row.title,
                        detail = row.detail,
                        priority = row.priority,
                        duePreset = DuePreset.fromMillis(row.dueAt, clock()),
                    )
                }
            }.onFailure { e ->
                _edit.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun onTitleChange(text: String) = _edit.update { it.copy(title = text) }
    fun onDetailChange(text: String) = _edit.update { it.copy(detail = text) }
    fun onPriorityChange(value: Int) = _edit.update { it.copy(priority = value) }
    fun onDuePresetChange(preset: String) = _edit.update { it.copy(duePreset = preset) }

    fun save(onDone: () -> Unit) {
        val s = _edit.value
        if (s.saving) return
        if (s.title.isBlank()) {
            _edit.update { it.copy(error = "给任务起个名字") }
            return
        }
        _edit.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                tasks.save(
                    TaskEntity(
                        id = s.id,
                        title = s.title.trim(),
                        detail = s.detail,
                        dueAt = DuePreset.toMillis(s.duePreset, clock()),
                        priority = s.priority,
                        done = false,
                        doneAt = 0L,
                        noteRef = 0L,
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

    fun delete(onDone: () -> Unit) {
        val id = _edit.value.id
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching { tasks.deleteById(id) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _edit.update { it.copy(error = e.message ?: "删除失败") } }
        }
    }

    /** 交给 UI 判断是否标红。 */
    fun isOverdue(task: TaskEntity): Boolean =
        !task.done && task.dueAt > 0L && task.dueAt < clock()

    companion object {
        /** P208：任务完成事件类型（对话动态段按这个类型读）。 */
        const val TASK_DONE_EVENT = "task_done"
    }
}

class TasksViewModelFactory(
    private val tasks: TaskRepository,
    /** P208：完成回传用（可空：单测/旧装配不传就不写事件）。 */
    private val events: EventRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TasksViewModel(tasks, events) as T
}