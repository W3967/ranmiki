package com.lanweixi.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanweixi.domain.plugin.RemotePlugin

/**
 * P166：远程插件源对话框。
 *
 * 输入索引地址 → 拉取 → 列表 → 点安装。装完仍走本地授权，索引不授予任何权限。
 */
@Composable
fun RemoteSourceDialog(
    sourceUrl: String,
    onSourceUrlChange: (String) -> Unit,
    onFetch: () -> Unit,
    onInstall: (RemotePlugin) -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    error: String? = null,
    plugins: List<RemotePlugin> = emptyList(),
    sourceName: String = "",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sourceName.isBlank()) "远程插件源" else sourceName) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = onSourceUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("索引地址") },
                    placeholder = { Text("https://example.com/plugins.json") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onFetch, enabled = !loading) {
                        Text(if (loading) "拉取中…" else "拉取列表")
                    }
                }

                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                }

                plugins.forEach { plugin ->
                    Column {
                        Text(
                            plugin.name + "  v" + plugin.version,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (plugin.description.isNotBlank()) {
                            Text(
                                plugin.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (plugin.sizeText().isNotBlank()) {
                                Text(
                                    plugin.sizeText(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!plugin.checksumValid()) {
                                // P216：没有校验值的条目不给装（索引可能被改过）
                                Text(
                                    "源未提供 sha256，不能安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            TextButton(
                                onClick = { onInstall(plugin) },
                                enabled = plugin.checksumValid(),
                            ) {
                                Text("安装", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (!loading && plugins.isEmpty() && error == null) {
                    Text(
                        "填入索引地址后拉取。索引格式：{\"plugins\":[{\"id\",\"name\",\"version\",\"downloadUrl\"}]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
