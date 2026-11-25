package com.lanweixi.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.GroupSession
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * P198：会话列表主页（QQ 式：单聊 + 群聊混排）。
 *
 * 这是底部「聊天」Tab 的第一屏。点单聊进 [ChatScreen]，点群进 [GroupChatScreen]。
 * 右上角「+」：发起新聊天 / 新建群聊 / 管理群聊。
 *
 * 纯 Compose：不碰 Room / DataStore / 引擎。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionsScreen(
    state: SessionsUiState,
    characterName: String,
    agents: List<AgentProfile> = emptyList(),
    onOpen: (ConversationItem) -> Unit = {},
    onNewChat: () -> Unit = {},
    onDelete: (ConversationItem) -> Unit = {},
    onCreateGroup: (GroupSession) -> Unit = {},
    onOpenGroups: () -> Unit = {},
    /** P218：右上角 + 里直接新建工具人（不跳设置页）。 */
    onNewAgent: () -> Unit = {},
    /** P202：还没配 API Key 时在列表顶部提示，不用点进会话才发现。 */
    missingApiKey: Boolean = false,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ConversationItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        MuchaTopBar(
            title = "聊天",
            subtitle = if (state.items.isEmpty()) null else "${state.items.size} 个会话",
            actions = {
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            // 日光金主题下 primary 也是金色，压在金色插画上等于隐形——加个底
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("发起新聊天") },
                            onClick = {
                                menuOpen = false
                                onNewChat()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("新建群聊") },
                            onClick = {
                                menuOpen = false
                                creatingGroup = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("新建工具人") },
                            onClick = {
                                menuOpen = false
                                onNewAgent()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("管理群聊") },
                            onClick = {
                                menuOpen = false
                                onOpenGroups()
                            },
                        )
                    }
                }
            },
        )

        if (missingApiKey) {
            // P202：跳过引导后的兜底提示——直接给一个能点的出口
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "还没配 API Key，现在发消息不会有回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("去设置", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        state.banner?.let { banner ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        when {
            state.empty -> EmptySessions()
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(state.items, key = { it.id + if (it.isGroup) "#g" else "#c" }) { item ->
                    ConversationRow(
                        item = item,
                        onClick = { onOpen(item) },
                        onLongClick = { pendingDelete = item },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (creatingGroup) {
        CreateGroupDialog(
            agents = agents,
            characterName = characterName,
            onDismiss = { creatingGroup = false },
            onCreate = {
                creatingGroup = false
                onCreateGroup(it)
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (item.isGroup) "删除群聊" else "删除会话") },
            text = {
                Text(
                    if (item.isGroup) {
                        "「${item.title}」会被删掉，聊天记录也一起清掉，恢复不了。"
                    } else {
                        "这个会话的聊天记录会全部清掉，恢复不了。兰未晞的人设和记忆不受影响。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(
            name = item.title,
            imageUri = item.avatarUri,
            size = 50.dp,
            tint = if (item.isGroup) Color(0xFFE07A9B) else null,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = listTime(item.lastAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptySessions() {
    // 会话列表页有遮罩，这里只补一句引导
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有对话",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点右上角 + 开始聊天，或者建个群把工具人拉进来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 列表头像：群聊用群名首字 + 品牌渐变，单聊用角色头像。 */
// ───────────────────────────── 建群 ─────────────────────────────

@Composable
private fun CreateGroupDialog(
    agents: List<AgentProfile>,
    characterName: String,
    onDismiss: () -> Unit,
    onCreate: (GroupSession) -> Unit,
) {
    var title by remember { mutableStateOf("讨论组") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建群聊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$characterName 会是群主，不用选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        "还没有工具人。先去「设置 → 工具人」建一个，群里才有别人。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                agents.forEach { agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (agent.id in selected) {
                                    selected - agent.id
                                } else {
                                    selected + agent.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (agent.id in selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(4.dp),
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(agent.name, style = MaterialTheme.typography.bodyMedium)
                        if (!agent.configured) {
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "未配 Key",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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

// ───────────────────────────── 时间 ─────────────────────────────

private val HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val MMDD = DateTimeFormatter.ofPattern("MM-dd", Locale.getDefault())
private val YYMMDD = DateTimeFormatter.ofPattern("yy-MM-dd", Locale.getDefault())

/** 列表时间：今天显示时刻，昨天显示「昨天」，更早显示日期。 */
internal fun listTime(at: Long, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): String {
    if (at <= 0L) return ""
    val date = Instant.ofEpochMilli(at).atZone(now.zone)
    return when {
        date.toLocalDate() == now.toLocalDate() -> date.format(HHMM)
        date.toLocalDate() == now.toLocalDate().minusDays(1) -> "昨天"
        date.year == now.year -> date.format(MMDD)
        else -> date.format(YYMMDD)
    }
}
