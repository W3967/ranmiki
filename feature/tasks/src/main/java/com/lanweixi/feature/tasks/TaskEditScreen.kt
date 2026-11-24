package com.lanweixi.feature.tasks

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** P134：任务编辑页。 */
@Composable
fun TaskEditScreen(
    state: TaskEditUiState,
    onTitleChange: (String) -> Unit,
    onDetailChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onDuePresetChange: (String) -> Unit,
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
                    title = if (state.id == 0L) "新建任务" else "编辑任务",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = onSave, enabled = !state.saving) {
                                            Text(if (state.saving) "保存中…" else "保存", color = MaterialTheme.colorScheme.primary)
                                        }
                    },
                )

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("要做什么") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value = state.detail,
                    onValueChange = onDetailChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    placeholder = { Text("备注（可选）") },
                    shape = RoundedCornerShape(10.dp),
                )

                Text(
                    "优先级",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TaskPriority.HIGH, TaskPriority.MID, TaskPriority.LOW).forEach { value ->
                        FilterChip(
                            selected = state.priority == value,
                            onClick = { onPriorityChange(value) },
                            label = { Text(TaskPriority.label(value)) },
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "截止",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuePreset.order.take(3).forEach { preset ->
                        FilterChip(
                            selected = state.duePreset == preset,
                            onClick = { onDuePresetChange(preset) },
                            label = { Text(DuePreset.label(preset)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuePreset.order.drop(3).forEach { preset ->
                        FilterChip(
                            selected = state.duePreset == preset,
                            onClick = { onDuePresetChange(preset) },
                            label = { Text(DuePreset.label(preset)) },
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (state.id != 0L) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除任务", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个任务？") },
            text = { Text("删除后无法恢复。") },
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