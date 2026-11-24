package com.lanweixi.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.plugin.HostLog
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar

/**
 * P189：宿主日志查看。
 *
 * 只读内存里最近 300 条，进程重启就清空。
 * 排查插件问题时比连 logcat 方便。
 */
@Composable
fun HostLogsScreen(onBack: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    val entries = remember(version) { HostLog.recent(200) }

    Column(modifier = Modifier.fillMaxSize()) {
        MuchaTopBar(
            title = "宿主日志",
            onBack = onBack,
            actions = {
                TextButton(onClick = {
                    HostLog.clear()
                    version++
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { version++ }) {
                    Text("刷新", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries) { entry ->
                    MuchaCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 10.dp,
                    ) {
                        Text(
                            entry.render(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (entry.level) {
                                "E" -> MaterialTheme.colorScheme.error
                                "W" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
