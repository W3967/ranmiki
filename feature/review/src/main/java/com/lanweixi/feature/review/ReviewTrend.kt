package com.lanweixi.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.dao.ReviewSnapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * P199：复盘趋势的一周。
 *
 * 只留两个能一眼看懂的数字：完成的任务、新建的笔记。
 * 文档 / 提示词那几列在复盘详情里已经有，趋势图里塞太多反而看不懂。
 */
data class WeekPoint(
    val label: String,
    val start: Long,
    val end: Long,
    val tasksDone: Int,
    val notes: Int,
)

private val weekLabelFormat = DateTimeFormatter.ofPattern("M/d")

/**
 * P199：最近几周（含本周）的产出趋势。
 *
 * 每周边界按「周一 00:00 → 周日 23:59:59」算，和单周复盘的口径一致。
 * 抽成顶层 suspend 函数是为了能单测——ViewModel 里只负责把仓库塞进来。
 */
internal suspend fun buildWeeklyTrend(
    now: Long,
    weeks: Int = 4,
    snapshotOf: suspend (from: Long, to: Long) -> ReviewSnapshot,
): List<WeekPoint> {
    if (weeks <= 0) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    return (weeks - 1 downTo 0).map { ago ->
        val startDate = monday.minusWeeks(ago.toLong())
        val endDate = startDate.plusWeeks(1).minusDays(1)
        val from = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endDate.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        val snapshot = snapshotOf(from, to)
        WeekPoint(
            label = if (ago == 0) "本周" else startDate.format(weekLabelFormat),
            start = from,
            end = to,
            tasksDone = snapshot.tasksDone.size,
            notes = snapshot.notes.size,
        )
    }
}

/**
 * P199：最近几周的柱状图。
 *
 * 用 Box 撑高度画柱，不上 Canvas——柱子要跟主题色走，组合式反而更好维护。
 */
@Composable
fun ReviewTrendChart(
    trend: List<WeekPoint>,
    /** P208：本周 AI 提议 / 采纳的草稿数（0 不显示这一行）。 */
    aiProposed: Int = 0,
    aiApproved: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (trend.isEmpty()) return
    val maxValue = maxOf(
        trend.maxOf { it.tasksDone },
        trend.maxOf { it.notes },
    ).coerceAtLeast(1)
    val hasData = trend.any { it.tasksDone > 0 || it.notes > 0 }

    // 这里不用 MuchaCard：它内部有个 fillMaxSize 的角饰 Box，
    // 在 Column 里（高度约束有限）会把整张卡片撑满屏，把下面的复盘列表挤掉。
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近 ${trend.size} 周", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                LegendDot(MaterialTheme.colorScheme.primary, "完成任务")
                Spacer(Modifier.width(10.dp))
                LegendDot(MaterialTheme.colorScheme.tertiary, "新建笔记")
            }

            Spacer(Modifier.height(10.dp))

            if (!hasData) {
                Text(
                    "这几周还没有记录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                trend.forEach { point ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Bar(point.tasksDone, maxValue, MaterialTheme.colorScheme.primary)
                            Bar(point.notes, maxValue, MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "本周完成 ${trend.last().tasksDone} 个任务 · 新建 ${trend.last().notes} 篇笔记",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // P208：她提的建议采纳了多少——这是「AI 主导」是否被接受的唯一指标
            if (aiProposed > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "AI 提议 $aiProposed 条 · 采纳 $aiApproved 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Bar(value: Int, max: Int, color: Color) {
    val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
    // 0 也留一条细线，否则整组柱子消失、看不出「这周有位置」
    val height = if (value == 0) 0.02f else fraction.coerceAtLeast(0.06f)
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(height)
            .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
