package com.lanweixi.feature.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.core.design.mucha.OrnateSelectableBox
import androidx.compose.material3.Switch
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

/**
 * P137：插件详情 + 权限授权 + 卸载。
 *
 * 授权默认全不勾；卸载必须二选一（保留沙箱 / 删除沙箱），没有默认值。
 */
@Composable
fun PluginDetailScreen(
    state: PluginDetailUiState,
    onToggleGrant: (String, Boolean) -> Unit,
    onGrantAll: () -> Unit,
    onRevokeAll: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onUninstall: (Boolean) -> Unit,
    onBack: () -> Unit,
    /** P165：打开插件的界面页。 */
    onOpenPage: (String) -> Unit = {},
    /** P170：打开声明式配置表单。 */
    onOpenConfig: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var askUninstall by remember { mutableStateOf(false) }
    val manifest = state.manifest

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                MuchaTopBar(
                    title = manifest?.name ?: "插件",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = { askUninstall = true }, enabled = manifest != null) {
                            Text("卸载", color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (manifest == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        manifest.id + " · v" + manifest.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (manifest.author.isNotBlank()) {
                        Text(
                            "作者：" + manifest.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (manifest.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(manifest.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "启用",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = state.enabled, onCheckedChange = onSetEnabled)
                    }
                }
            }

            if (state.warnings.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "安装提示",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        state.warnings.forEach {
                            Text(
                                "· " + it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 权限
            Text(
                "权限（" + state.grants.size + "/" + manifest.permissions.size + "）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (manifest.permissions.isEmpty()) {
                Text(
                    "这个插件不需要任何权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        manifest.permissions.forEachIndexed { index, permission ->
                            if (index > 0) {
                                Spacer(Modifier.height(6.dp))
                            }
                            // 权限勾选是「条目级选中」，用雕花框 + 星点表达（规范体系二）
                            OrnateSelectableBox(
                                selected = permission.permissionId in state.grants,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = permission.permissionId in state.grants,
                                        onCheckedChange = {
                                            onToggleGrant(permission.permissionId, it)
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            permission.permissionId,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            permission.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onGrantAll) { Text("全部允许") }
                    TextButton(onClick = onRevokeAll) { Text("全部撤销") }
                }
            }

            // 功能
            if (manifest.functions.isNotEmpty()) {
                Text(
                    "提供的能力（" + manifest.functions.size + "）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                manifest.functions.forEach { fn ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    fn.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (fn.sensitive) {
                                    Text(
                                        "每次询问",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (fn.description.isNotBlank()) {
                                Text(
                                    fn.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // P170：声明式配置入口
            if (manifest.configSchema != null) {
                Text(
                    "配置",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onOpenConfig,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("打开配置")
                }
                Spacer(Modifier.height(12.dp))
            }

            // UI 入口
            if (manifest.uiEntries.isNotEmpty()) {
                Text(
                    "界面入口（" + manifest.uiEntries.size + "）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                manifest.uiEntries.forEach { entry ->
                    Button(
                        onClick = { onOpenPage(manifest.id + "/" + entry.entryId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("打开 " + entry.title)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "目录：" + state.dirPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (askUninstall && manifest != null) {
        AlertDialog(
            onDismissRequest = { askUninstall = false },
            title = { Text("卸载 " + manifest.name) },
            text = { Text("插件目录里可能存着它的数据（笔记关联、缓存等）。要不要一起删？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        askUninstall = false
                        onUninstall(true)
                    },
                ) {
                    Text("连数据一起删", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            askUninstall = false
                            onUninstall(false)
                        },
                    ) {
                        Text("只卸载，保留数据")
                    }
                    TextButton(onClick = { askUninstall = false }) { Text("取消") }
                }
            },
        )
    }
}