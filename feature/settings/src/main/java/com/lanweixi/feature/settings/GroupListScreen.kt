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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.GroupSession

/**
 * P195：群列表。
 */
@Composable
fun GroupListScreen(
    groups: List<GroupSession>,
    agents: List<AgentProfile>,
    onBack: () -> Unit,
    onCreate: (GroupSession) -> Unit,
    onOpen: (GroupSession) -> Unit,
    onDelete: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GroupSession?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MuchaTopBar(title = "群聊", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "兰未晞在每个群里主持。不 @ 时她接话，@ 谁谁说话。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = { creating = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("建群")
            }
            Spacer(Modifier.height(12.dp))

            if (groups.isEmpty()) {
                Text(
                    "还没有群。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            groups.forEach { g ->
                MuchaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(g) },
                    cornerRadius = 14.dp,
                    contentPadding = 14.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (g.memberIds.isEmpty()) "只有兰未晞" else "兰未晞 + ${g.memberIds.size} 个工具人",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 建错群要能删，否则列表只会越堆越多
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { pendingDelete = g }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (creating) {
        CreateGroupDialog(
            agents = agents,
            onDismiss = { creating = false },
            onCreate = {
                onCreate(it)
                creating = false
            },
        )
    }

    pendingDelete?.let { group ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除群聊") },
            text = { Text("「${group.title}」会被删掉，聊天记录也一起清掉，恢复不了。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(group.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CreateGroupDialog(
    agents: List<AgentProfile>,
    onDismiss: () -> Unit,
    onCreate: (GroupSession) -> Unit,
) {
    var title by remember { mutableStateOf("讨论组") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建群") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("群名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("拉谁进来", style = MaterialTheme.typography.labelMedium)
                if (agents.isEmpty()) {
                    Text(
                        "还没有工具人，先去「工具人」里建一个。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                agents.forEach { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (a.id in selected) selected - a.id else selected + a.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (a.id in selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(4.dp),
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(a.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        GroupSession(
                            id = "group_" + System.currentTimeMillis(),
                            title = title.trim().ifBlank { "讨论组" },
                            memberIds = selected.toList(),
                        ),
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
