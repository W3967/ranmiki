package com.lanweixi.feature.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanweixi.domain.plugin.ConfigFieldType
import com.lanweixi.domain.plugin.PluginConfigField

/**
 * P170：声明式配置表单。
 *
 * 按 manifest 的 `configSchema` 自动渲染：单行文本、整数、开关、下拉。
 * 插件不用写界面，宿主管渲染与存储。
 */
@Composable
fun PluginConfigDialog(
    title: String,
    fields: List<PluginConfigField>,
    initial: Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(fields) {
        mutableStateMapOf<String, String>().apply { putAll(initial) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (fields.isEmpty()) {
                    Text(
                        "这个插件没有声明配置项。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                fields.forEach { field ->
                    when (field.type) {
                        ConfigFieldType.TEXT -> OutlinedTextField(
                            value = values[field.key] ?: field.defaultValue,
                            onValueChange = { values[field.key] = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(field.title) },
                            supportingText = if (field.description.isBlank()) null else {
                                { Text(field.description) }
                            },
                        )

                        ConfigFieldType.INTEGER -> OutlinedTextField(
                            value = values[field.key] ?: field.defaultValue,
                            onValueChange = { input ->
                                // 只留数字与负号，避免把非数字存进去
                                if (input.isEmpty() || input.matches(Regex("-?\\d*"))) {
                                    values[field.key] = input
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(field.title) },
                        )

                        ConfigFieldType.SWITCH -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.title, style = MaterialTheme.typography.bodyMedium)
                                if (field.description.isNotBlank()) {
                                    Text(
                                        field.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = (values[field.key] ?: field.defaultValue) == "true",
                                onCheckedChange = { values[field.key] = it.toString() },
                            )
                        }

                        ConfigFieldType.CHOICE -> {
                            Text(field.title, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                field.options.forEach { option ->
                                    val active = (values[field.key] ?: field.defaultValue) == option
                                    Surface(
                                        color = if (active) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .clickable { values[field.key] = option }
                                            .padding(horizontal = 2.dp),
                                    ) {
                                        Text(
                                            option,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.toMap()) }) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
