package com.lanweixi.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.design.components.MuchaTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P206：待审核页。
 *
 * 她整理出来的笔记 / 任务先落在这里，用户点「确认」才进工作台。
 * 驳回的也留一行（透明的记录），不做隐式删除。
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onApproveAll: () -> Unit,
    /** P207：改内容再确认。 */
    onEdit: (Long) -> Unit = {},
    /** P207：撤销一次确认。 */
    onUndo: (Long) -> Unit = {},
    /** P207：跳到触发这条草稿的原会话。 */
    onOpenSource: (DraftEntity) -> Unit = {},
    onToggleReviewed: () -> Unit,
    onClearMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MuchaTopBar(
            title = "待审核",
            onBack = onBack,
            actions = {
                if (state.pending.isNotEmpty()) {
                    TextButton(onClick = onApproveAll, enabled = !state.busy) {
                        Text(
                            "全部确认",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )

        Text(
            text = "她整理出来的笔记和任务，先在这里等你看一眼；确认之后才进工作台。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        state.message?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { onClearMessage() },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.pending.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "还没有待审核",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "在单聊里跟她说「记一下：…」，她会先放进这里",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(state.pending, key = { "pending-${it.id}" }) { draft ->
                    DraftCard(
                        draft = draft,
                        busy = state.busy,
                        onApprove = { onApprove(draft.id) },
                        onReject = { onReject(draft.id) },
                        onEdit = { onEdit(draft.id) },
                        onOpenSource = { onOpenSource(draft) },
                    )
                }

                item {
                    TextButton(
                        onClick = onToggleReviewed,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.showReviewed) "收起已处理" else "查看已处理",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.showReviewed) {
                    if (state.reviewed.isEmpty()) {
                        item {
                            Text(
                                "还没有处理过的草稿",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(state.reviewed, key = { "reviewed-${it.id}" }) { draft ->
                        ReviewedRow(draft = draft, busy = state.busy, onUndo = { onUndo(draft.id) })
                    }
                }
            }
        }
    }
}

/** 一条待审核草稿。 */
@Composable
private fun DraftCard(
    draft: DraftEntity,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(draft.kind)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(draft.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.sourceSessionId.isNotBlank() || draft.sourceText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    // P207：点来源跳回那条对话，看看她当时是根据什么记的
                    Text(
                        "来自聊天 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenSource),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                draft.title.ifBlank { "无标题" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (draft.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    draft.body.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (draft.kind == DraftKind.TASK) {
                Spacer(Modifier.height(4.dp))
                Text(
                    taskMeta(draft),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (draft.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "#" + draft.tags.trim().replace(',', ' ').replace('，', ' ').trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit, enabled = !busy) {
                    Text(
                        "修改",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onReject, enabled = !busy) {
                    Text(
                        "驳回",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onApprove, enabled = !busy) {
                    Text("确认", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 已处理的一行（只读 + P207 可撤销）。 */
@Composable
private fun ReviewedRow(draft: DraftEntity, busy: Boolean, onUndo: () -> Unit) {
    val approved = draft.status == DraftStatus.APPROVED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (approved) "已确认" else "已驳回",
            style = MaterialTheme.typography.labelSmall,
            color = if (approved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(48.dp),
        )
        Text(
            draft.title.ifBlank { "无标题" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (draft.reviewedAt > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(
                formatTime(draft.reviewedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (approved) {
            Spacer(Modifier.width(4.dp))
            Text(
                "撤销",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onUndo)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    val label = if (kind == DraftKind.TASK) "任务" else "笔记"
    val color = if (kind == DraftKind.TASK) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun taskMeta(draft: DraftEntity): String {
    val due = if (draft.dueAt > 0L) {
        "截止 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(draft.dueAt))
    } else {
        "不设截止"
    }
    val priority = when (draft.priority) {
        2 -> "高"
        0 -> "低"
        else -> "中"
    }
    return due + " · 优先级 " + priority
}

private fun formatTime(at: Long): String =
    if (at <= 0L) "" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
