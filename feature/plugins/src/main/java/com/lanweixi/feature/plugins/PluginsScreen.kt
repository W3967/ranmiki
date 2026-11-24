package com.lanweixi.feature.plugins

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.core.data.plugin.InstalledPlugin
import com.lanweixi.domain.plugin.PluginType

/** P137：插件列表。 */
@Composable
fun PluginsScreen(
    state: PluginsUiState,
    onInstallClick: () -> Unit,
    onInstallDemo: () -> Unit = {},
    /** P166：打开远程插件源。 */
    onOpenRemote: () -> Unit = {},
    /** P167：检查已装插件的更新。 */
    onCheckUpdates: () -> Unit = {},
    onOpen: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
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
                    title = "插件",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = onInstallClick) {
                            Text("安装", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )

        if (state.installing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            state.plugins.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有插件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "右上角「安装」选择一个插件 zip",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "插件必须带 plugin.manifest.json 和 main.lua",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onCheckUpdates) {
                        Text("检查更新", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onOpenRemote) {
                        Text("浏览远程源", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onInstallDemo) {
                        Text("载入示例插件看看", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.plugins, key = { it.manifest.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        onClick = { onOpen(plugin.manifest.id) },
                        onToggleEnabled = { onToggleEnabled(plugin.manifest.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val m = plugin.manifest
    MuchaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 12.dp,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "v${m.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(
                            when {
                                m.pluginType.contains(PluginType.UI) && m.pluginType.contains(PluginType.FUNCTION) -> "工具 + UI"
                                m.pluginType.contains(PluginType.UI) -> "UI 插件"
                                else -> "工具插件"
                            },
                        )
                        append(" · ")
                        append(m.id)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    val granted = m.permissions.count { it.permissionId in plugin.grants }
                    Text(
                        if (granted == m.permissions.size) "权限已全部授权" else "权限 $granted/${m.permissions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted == m.permissions.size) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            Switch(checked = plugin.enabled, onCheckedChange = onToggleEnabled)
        }
    }
}