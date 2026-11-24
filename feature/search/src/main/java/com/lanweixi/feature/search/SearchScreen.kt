package com.lanweixi.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.search.SearchHit
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar

/**
 * P199：全局搜索页。
 *
 * 结果按类型分组（笔记 / 任务 / 文档 / 提示词 / 复盘），点一条跳到对应页面。
 * 之前 `GlobalSearch` 只有插件能用，App 里没入口——这张卡就是补入口。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpen: (SearchHit) -> Unit,
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
        MuchaTopBar(title = "搜索", onBack = onBack)

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("搜笔记 / 任务 / 文档 / 提示词 / 复盘") },
            shape = RoundedCornerShape(10.dp),
        )

        when {
            state.error != null -> CenterHint(state.error, error = true)

            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            !state.searched -> CenterHint("输入关键词开始搜", error = false)

            state.empty -> CenterHint("没搜到「${state.query}」", error = false)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SECTIONS.forEach { (kind, label) ->
                    val rows = state.hits.filter { it.kind == kind }
                    if (rows.isNotEmpty()) {
                        item(key = "header_$kind") {
                            Text(
                                text = "$label · ${rows.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            )
                        }
                        items(rows, key = { kind + "_" + it.id }) { hit ->
                            HitRow(hit = hit, onClick = { onOpen(hit) })
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private val SECTIONS = listOf(
    "note" to "笔记",
    "task" to "任务",
    "doc" to "文档",
    "prompt" to "提示词",
    "review" to "复盘",
)

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    MuchaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
    ) {
        Text(
            text = hit.title.ifBlank { "无标题" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hit.snippet.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = hit.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenterHint(text: String?, error: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
