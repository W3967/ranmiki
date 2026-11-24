package com.lanweixi.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.entity.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P132：工作台笔记列表。
 *
 * 顶部搜索 + 列表；右上角「归档」切换归档视图；右下角「新建」。
 */
@Composable
fun NotesScreen(
    state: NotesUiState,
    onQueryChange: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onUnarchive: (Long) -> Unit,
    onBack: () -> Unit,
    /** P199：按标签筛选；传回同一个标签表示取消。 */
    onTagSelect: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val showing = if (state.showArchived) state.archived else state.notes
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        MuchaTopBar(
            title = if (state.showArchived) "笔记 · 归档" else "笔记",
            onBack = onBack,
            actions = {
                TextButton(onClick = onToggleArchived) {
                    Text(
                        if (state.showArchived) "返回" else "归档",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        if (!state.showArchived) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                singleLine = true,
                placeholder = { Text("搜索标题 / 正文 / 标签") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(10.dp),
            )
            if (state.allTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.allTags,
                    selected = state.selectedTag,
                    onSelect = onTagSelect,
                )
            }
        }

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            state.error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            showing.isEmpty() -> EmptyHint(
                archived = state.showArchived,
                searching = state.query.isNotBlank(),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(showing, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        archived = state.showArchived,
                        onClick = { onOpen(note.id) },
                        onUnarchive = { onUnarchive(note.id) },
                    )
                }
            }
        }

    }

    if (!state.showArchived) {
        androidx.compose.material3.FloatingActionButton(
            onClick = onCreate,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Text("＋", style = MaterialTheme.typography.headlineSmall)
        }
    }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tags) { tag ->
            val active = tag == selected
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                onClick = { onSelect(tag) },
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    archived: Boolean,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
) {
    MuchaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 14.dp,
        contentPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { "无标题" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        note.content.replace('\n', ' ').trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(modifier = Modifier.padding(top = 5.dp)) {
                    Text(
                        formatTime(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (note.tags.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            note.tags,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (archived) {
                TextButton(onClick = onUnarchive) { Text("恢复") }
            }
        }
    }
}

@Composable
private fun EmptyHint(archived: Boolean, searching: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    archived -> "归档里还是空的"
                    searching -> "没搜到"
                    else -> "还没有笔记"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    archived -> "归档的笔记会放在这里，随时可以恢复"
                    searching -> "换个关键词试试"
                    else -> "右下角「＋」写第一篇"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

internal fun formatTime(millis: Long): String =
    if (millis <= 0L) "" else timeFormat.format(Date(millis))