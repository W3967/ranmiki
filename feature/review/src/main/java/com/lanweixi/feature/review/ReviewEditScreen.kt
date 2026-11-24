package com.lanweixi.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.dao.ReviewSnapshot

/** P136：复盘编辑（自动汇总 + 一段总结）。 */
@Composable
fun ReviewEditScreen(
    state: ReviewEditUiState,
    onPeriodChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
                MuchaTopBar(
                    title = if (state.id == 0L) "写复盘" else "看复盘",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = onSave, enabled = !state.saving) {
                                            Text(if (state.saving) "保存中…" else "保存", color = MaterialTheme.colorScheme.primary)
                                        }
                    },
                )
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReviewPeriod.WEEK, ReviewPeriod.MONTH).forEach { type ->
                    FilterChip(
                        selected = state.periodType == type,
                        onClick = { onPeriodChange(type) },
                        label = { Text(ReviewPeriod.label(type)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatRange(state.periodStart, state.periodEnd),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                }
            } else {
                state.snapshot?.let { SnapshotBlock(it) }
            }

            Text(
                "总结",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.summary,
                onValueChange = onSummaryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("这段时间做成了什么、卡在哪、下步做什么") },
                shape = RoundedCornerShape(10.dp),
            )

            if (state.error != null) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.id != 0L) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除这条复盘", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条复盘？") },
            text = { Text("只删复盘记录，不动笔记/任务/文档。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SnapshotBlock(snapshot: ReviewSnapshot) {
    MuchaCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        contentPadding = 14.dp,
    ) {
        Column {
            Text(
                "这段时间的产出",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (snapshot.isEmpty) {
                Text(
                    "这个周期没有新增内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            StatLine("笔记", snapshot.notes.size, snapshot.notes.map { it.title })
            StatLine("新建任务", snapshot.tasksCreated.size, snapshot.tasksCreated.map { it.title })
            StatLine("完成任务", snapshot.tasksDone.size, snapshot.tasksDone.map { it.title })
            StatLine("提示词", snapshot.prompts.size, snapshot.prompts.map { it.title })
            StatLine("文档", snapshot.documents.size, snapshot.documents.map { it.title })
    }
}

}
@Composable
private fun StatLine(label: String, count: Int, titles: List<String>) {
    if (count == 0) return
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(
            titles.take(4).joinToString("、") + if (titles.size > 4) "…" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}