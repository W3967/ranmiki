package com.lanweixi.feature.prompts

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

/**
 * P133：创作提示词编辑页。
 *
 * 正向/负向分开放，是因为拼在一起后一行里没法改；参数与备注放在最下面当备忘。
 */
@Composable
fun PromptEditScreen(
    state: PromptEditUiState,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPositiveChange: (String) -> Unit,
    onNegativeChange: (String) -> Unit,
    onParamsChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onCopyPositive: () -> Unit,
    onCopyFull: () -> Unit,
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
                    title = if (state.id == 0L) "新建提示词" else "编辑提示词",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = onSave, enabled = !state.saving) {
                                            Text(if (state.saving) "保存中…" else "保存", color = MaterialTheme.colorScheme.primary)
                                        }
                    },
                )
            }

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
                    placeholder = { Text("标题，比如「赛博少女 · 雨夜」") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = RoundedCornerShape(10.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PromptCategory.IP,
                        PromptCategory.VIDEO,
                        PromptCategory.IMAGE,
                        PromptCategory.OTHER,
                    ).forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategoryChange(category) },
                            label = { Text(PromptCategory.label(category)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = state.positive,
                    onValueChange = onPositiveChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = { Text("正向提示词 / 正文") },
                    shape = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value = state.negative,
                    onValueChange = onNegativeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    placeholder = { Text("负向提示词（可选）") },
                    shape = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value = state.params,
                    onValueChange = onParamsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    placeholder = { Text("参数：模型 / 尺寸 / 采样 / 时长（可选）") },
                    shape = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value = state.memo,
                    onValueChange = onMemoChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    placeholder = { Text("备注：用在哪、效果如何（可选）") },
                    shape = RoundedCornerShape(10.dp),
                )

                if (state.error != null) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.copied != null) {
                    Text(
                        state.copied,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onCopyPositive) { Text("复制正向") }
                    TextButton(onClick = onCopyFull) { Text("复制完整") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onTogglePin) {
                        Text(
                            if (state.pinned) "取消置顶" else "置顶",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.id != 0L) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条提示词？") },
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