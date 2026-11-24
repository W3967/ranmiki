package com.lanweixi.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.AgentProviders

/**
 * P194：工具人管理。
 *
 * 跟兰未晞不同——他们只有「工作说明」，没有性格。
 */
@Composable
fun AgentsScreen(
    agents: List<AgentProfile>,
    onBack: () -> Unit,
    onSave: (AgentProfile) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<AgentProfile?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MuchaTopBar(title = "工具人", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "他们是干活的，不是角色。各配各的 API，群里 @ 谁就是谁接。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Button(
                onClick = {
                    editing = AgentProfile(
                        id = "agent_" + System.currentTimeMillis(),
                        name = "新工具人",
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新建工具人")
            }

            Spacer(Modifier.height(12.dp))

            if (agents.isEmpty()) {
                Text(
                    "还没有工具人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            agents.forEach { agent ->
                AgentRow(agent = agent, onClick = { editing = agent })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editing?.let { draft ->
        AgentEditDialog(
            initial = draft,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = if (agents.any { it.id == draft.id }) {
                { onDelete(draft.id); editing = null }
            } else null,
        )
    }
}

@Composable
private fun AgentRow(agent: AgentProfile, onClick: () -> Unit) {
    MuchaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 14.dp,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(parseColor(agent.color), CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (agent.configured) {
                        "${AgentProviders.byId(agent.provider).label} · ${agent.model}"
                    } else {
                        "还没配 API Key，点它不会回复"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (agent.configured) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun AgentEditDialog(
    initial: AgentProfile,
    onDismiss: () -> Unit,
    onSave: (AgentProfile) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var provider by remember { mutableStateOf(initial.provider) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var workPrompt by remember { mutableStateOf(initial.workPrompt) }
    var color by remember { mutableStateOf(initial.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑工具人") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("服务商", style = MaterialTheme.typography.labelMedium)
                // P209：加了 Claude 之后预设变成 7 个，按 3 个一行排，别再写死两行
                AgentProviders.ALL.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            ProviderChip(p.label, provider == p.id) {
                                provider = p.id
                                if (p.baseUrl.isNotBlank()) baseUrl = p.baseUrl
                                if (p.defaultModel.isNotBlank()) model = p.defaultModel
                            }
                        }
                    }
                }
                AgentProviders.byId(provider).keyHint.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "去 $it 申请 Key",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 输入法会把 "sk" 猜成「上课」、把 "-" 换成全角，Key 一旦混进中文，
                // OkHttp 会直接抛 "Unexpected char ... in Authorization value"，现象是「点了没反应」。
                // 这里提前拦下来，别让用户去猜。
                val keyHasBadChars = apiKey.trim().any { it.code !in 33..126 }
                if (keyHasBadChars) {
                    Text(
                        "Key 里有中文或全角字符（多半是输入法猜词），这样发出去一定会报错，请用粘贴重新输入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (apiKey.isBlank()) {
                    Text(
                        "不填 Key 它就不会回复——建了也只能在列表里看着。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = workPrompt,
                    onValueChange = { workPrompt = it },
                    label = { Text("工作说明") },
                    supportingText = { Text("它负责什么、怎么输出。这里不写性格。") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("气泡颜色", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentProviders.COLORS.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(parseColor(c), CircleShape)
                                .border(
                                    width = if (color == c) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { "未命名" },
                            provider = provider,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            workPrompt = workPrompt,
                            color = color,
                        ),
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() &&
                    apiKey.trim().none { it.code !in 33..126 },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun ProviderChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrElse { Color(0xFF7C8CF6) }
