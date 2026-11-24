package com.lanweixi.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P207：修改草稿。
 *
 * 确认前改标题 / 正文 / 标签 / 截止 / 优先级，保存时先落草稿再确认——
 * 这样正式记录和草稿内容一致，撤销之后还能再改再确认。
 */
@Composable
fun DraftEditScreen(
    state: DraftEditUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onDuePresetChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        MuchaTopBar(
            title = if (state.isTask) "修改任务" else "修改笔记",
            onBack = onBack,
            actions = {
                TextButton(onClick = onSave, enabled = !state.saving) {
                    Text(
                        if (state.saving) "保存中…" else "保存并确认",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isTask) {
                    FieldLabel("截止")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = if (state.duePreset == DraftDuePreset.EXACT) {
                            listOf(DraftDuePreset.EXACT) + DraftDuePreset.order
                        } else {
                            DraftDuePreset.order
                        }
                        presets.forEach { preset ->
                            ChoiceChip(
                                label = if (preset == DraftDuePreset.EXACT) {
                                    formatDue(state.exactDueAt)
                                } else {
                                    DraftDuePreset.label(preset)
                                },
                                selected = state.duePreset == preset,
                                onClick = { onDuePresetChange(preset) },
                            )
                        }
                    }

                    FieldLabel("优先级")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "低", 1 to "中", 2 to "高").forEach { (value, label) ->
                            ChoiceChip(
                                label = label,
                                selected = state.priority == value,
                                onClick = { onPriorityChange(value) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.body,
                        onValueChange = onBodyChange,
                        label = { Text("详情") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = onTagsChange,
                        label = { Text("标签（逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.body,
                        onValueChange = onBodyChange,
                        label = { Text("正文") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun formatDue(at: Long): String =
    if (at <= 0L) "不设" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
