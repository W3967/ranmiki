package com.lanweixi.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanweixi.core.data.dao.ReviewSnapshot
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.repository.ReviewRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 复盘周期。 */
object ReviewPeriod {
    const val WEEK = "week"
    const val MONTH = "month"

    fun label(type: String): String = if (type == MONTH) "本月" else "本周"

    fun range(type: String, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val (start, endExclusive) = if (type == MONTH) {
            val first = today.withDayOfMonth(1)
            first to first.plusMonths(1)
        } else {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusWeeks(1)
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to
            endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("M月d日")

internal fun formatRange(start: Long, end: Long): String {
    val zone = ZoneId.systemDefault()
    val s = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val e = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
    return s.format(dateFormat) + " – " + e.format(dateFormat)
}

data class ReviewListUiState(
    val loading: Boolean = true,
    val reviews: List<ReviewEntity> = emptyList(),
    val error: String? = null,
    /** P199：最近 4 周的产出趋势（含本周）。 */
    val trend: List<WeekPoint> = emptyList(),
    /** P208：本周 AI 提议 / 采纳的草稿数。 */
    val aiProposed: Int = 0,
    val aiApproved: Int = 0,
)

data class ReviewEditUiState(
    val id: Long = 0L,
    val periodType: String = ReviewPeriod.WEEK,
    val periodStart: Long = 0L,
    val periodEnd: Long = 0L,
    val summary: String = "",
    val snapshot: ReviewSnapshot? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * P136：复盘。
 *
 * 打开时按周期现算汇总，不存快照——后来补录的内容也应该出现在复盘里。
 */
class ReviewViewModel(
    private val reviews: ReviewRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _list = MutableStateFlow(ReviewListUiState())
    val list: StateFlow<ReviewListUiState> = _list.asStateFlow()

    private val _edit = MutableStateFlow(ReviewEditUiState())
    val edit: StateFlow<ReviewEditUiState> = _edit.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val rows = reviews.listAll()
                // P199：趋势图要最近 4 周，每周边界跟单周复盘一致
                val trend = buildWeeklyTrend(clock()) { from, to -> reviews.snapshot(from, to) }
                // P208：本周 AI 草稿的采纳情况
                val (weekStart, weekEnd) = ReviewPeriod.range(ReviewPeriod.WEEK, clock())
                val week = reviews.snapshot(weekStart, weekEnd)
                Triple(rows, trend, week)
            }
                .onSuccess { (rows, trend, week) ->
                    _list.update {
                        it.copy(
                            loading = false,
                            reviews = rows,
                            trend = trend,
                            aiProposed = week.draftsProposed,
                            aiApproved = week.draftsApproved,
                        )
                    }
                }
                .onFailure { e ->
                    _list.update { it.copy(loading = false, error = e.message ?: "读取失败") }
                }
        }
    }

    /** 新建：默认本周。 */
    fun newReview(periodType: String = ReviewPeriod.WEEK) {
        val (start, end) = ReviewPeriod.range(periodType, clock())
        _edit.value = ReviewEditUiState(
            periodType = periodType,
            periodStart = start,
            periodEnd = end,
            loading = true,
        )
        loadSnapshot(start, end)
    }

    fun open(id: Long) {
        _edit.value = ReviewEditUiState(id = id, loading = true)
        viewModelScope.launch {
            runCatching { reviews.byId(id) }.onSuccess { row ->
                if (row == null) {
                    _edit.update { it.copy(loading = false, error = "复盘不存在") }
                    return@onSuccess
                }
                _edit.value = ReviewEditUiState(
                    id = row.id,
                    periodType = row.periodType,
                    periodStart = row.periodStart,
                    periodEnd = row.periodEnd,
                    summary = row.summary,
                    loading = true,
                )
                loadSnapshot(row.periodStart, row.periodEnd)
            }.onFailure { e ->
                _edit.update { it.copy(loading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    /** 切换周期（周 ↔ 月），同时重新算汇总。 */
    fun switchPeriod(type: String) {
        val (start, end) = ReviewPeriod.range(type, clock())
        _edit.update {
            it.copy(periodType = type, periodStart = start, periodEnd = end, loading = true)
        }
        loadSnapshot(start, end)
    }

    private fun loadSnapshot(start: Long, end: Long) {
        viewModelScope.launch {
            runCatching { reviews.snapshot(start, end) }
                .onSuccess { snap -> _edit.update { it.copy(loading = false, snapshot = snap) } }
                .onFailure { e -> _edit.update { it.copy(loading = false, error = e.message ?: "汇总失败") } }
        }
    }

    fun onSummaryChange(text: String) = _edit.update { it.copy(summary = text) }

    fun save(onDone: () -> Unit) {
        val s = _edit.value
        if (s.saving) return
        _edit.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                reviews.save(
                    ReviewEntity(
                        id = s.id,
                        periodType = s.periodType,
                        periodStart = s.periodStart,
                        periodEnd = s.periodEnd,
                        summary = s.summary,
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

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { reviews.deleteById(id) }
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _edit.update { it.copy(error = e.message ?: "删除失败") } }
        }
    }
}

class ReviewViewModelFactory(
    private val reviews: ReviewRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewViewModel(reviews) as T
}