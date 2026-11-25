package com.lanweixi.feature.chat

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanweixi.domain.prompt.AgentProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P197：多 Agent 群聊页。
 *
 * 结构对标 QQ 群聊：
 *  - 顶栏：返回 + 群名 + 成员数（右侧「成员」查看名单）
 *  - 消息区：自己的在右（主题色气泡），别人的在左（带头像 + 昵称 + 各自气泡色）
 *  - 输入栏：左侧「@」选人，默认不 @（兰未晞接），@ 谁这条就只发给谁
 *
 * 纯 Compose：不引用 Hilt / Room / DAO / 引擎。
 */
@Composable
fun GroupChatScreen(
    state: GroupChatUiState,
    userAvatarUri: String? = null,
    onBack: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onCancel: () -> Unit = {},
    /** P203：多选 @——点一下选中，再点取消。 */
    onToggleTarget: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearTargets: () -> Unit = {},
    /** P205：长按气泡引用这条消息。 */
    onQuote: (GroupMessage) -> Unit = {},
    onClearQuote: () -> Unit = {},
    /** P205：点引用卡片定位原消息。 */
    onLocateQuote: (Long) -> Unit = {},
    onClearHighlight: () -> Unit = {},
    /** P218：成员面板里可加入的工具人全集。 */
    allAgents: List<AgentProfile> = emptyList(),
    /** P218：把工具人加进群 / 移出群。 */
    onAddMember: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMembers by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.partialText != null) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) runCatching { listState.scrollToItem(itemCount - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        GroupTopBar(
            title = state.title,
            subtitle = memberSummary(state.members),
            onBack = onBack,
            onOpenMembers = { showMembers = true },
        )

        state.banner?.let { banner ->
            BannerStrip(banner)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = state.messages,
                key = { _, message ->
                    message.id.takeIf { it > 0 } ?: "${message.speaker}-${message.createdAt}"
                },
            ) { index, message ->
                val previous = state.messages.getOrNull(index - 1)
                if (previous == null || message.createdAt - previous.createdAt > TIME_GAP_MS) {
                    TimeDivider(message.createdAt)
                }
                GroupMessageRow(
                    message = message,
                    member = state.members.firstOrNull { it.id == message.speaker },
                    userAvatarUri = userAvatarUri,
                    hostName = state.members.firstOrNull { it.isHost }?.name ?: "兰未晞",
                    highlighted = state.highlightId > 0 && state.highlightId == message.id,
                    onQuote = { onQuote(message) },
                    onLocate = onLocateQuote,
                )
            }
            if (state.messages.isEmpty() && state.partialText == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 群聊页不压遮罩，背景是整张插画——提示语必须有底色才看得清
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = "这个群还没人说话。\n不 @ 就是兰未晞接，@ 谁就是谁回（可多选）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            state.partialText?.let { partial ->
                item {
                    GroupMessageRow(
                        message = GroupMessage(
                            speaker = state.partialSpeakerId ?: GroupSpeaker.HOST,
                            text = partial.ifBlank { "…" },
                            createdAt = System.currentTimeMillis(),
                        ),
                        member = state.members.firstOrNull { it.id == state.partialSpeakerId },
                        userAvatarUri = userAvatarUri,
                        hostName = state.members.firstOrNull { it.isHost }?.name ?: "兰未晞",
                    )
                }
            }
        }

        // P205：点引用卡片后滚到原消息，高亮一会儿再撤掉
        LaunchedEffect(state.highlightId) {
            val id = state.highlightId
            if (id > 0) {
                val index = state.messages.indexOfFirst { it.id == id }
                if (index >= 0) runCatching { listState.animateScrollToItem(index) }
                kotlinx.coroutines.delay(1600)
                onClearHighlight()
            }
        }

        // P205：输入框上方的引用条
        state.quote?.let { quote ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "↩ ${quote.speaker}：${quote.preview}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onClearQuote() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // P203：@ 了人时显示发给谁（多人显示人数）；点 × 清空回到兰未晞
        if (state.targetIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.targets.size > 1) {
                                "@${state.targetLabel}（${state.targets.size} 人，依次回复）"
                            } else {
                                "@${state.targetLabel}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onClearTargets() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        GroupInputBar(
            draft = state.draft,
            sending = state.sending,
            hint = when {
                state.targetIds.isEmpty() -> "说点什么…"
                state.targets.size == 1 -> "对 ${state.targetLabel} 说…"
                else -> "对 ${state.targetLabel} 说…（${state.targets.size} 人）"
            },
            pickerOpen = pickerOpen,
            onPickerOpenChange = { pickerOpen = it },
            members = state.members,
            selected = state.targetIds,
            onToggleTarget = onToggleTarget,
            onSelectAll = onSelectAll,
            onClearTargets = onClearTargets,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onCancel = onCancel,
        )
    }

    if (showMembers) {
        MembersDialog(
            members = state.members,
            allAgents = allAgents,
            onAddMember = onAddMember,
            onRemoveMember = onRemoveMember,
            onDismiss = { showMembers = false },
        )
    }
}

private const val TIME_GAP_MS = 5 * 60 * 1000L

private fun memberSummary(members: List<GroupMember>): String {
    val others = members.count { !it.isHost }
    return if (others == 0) "只有你们俩" else "${others + 1} 人 · 兰未晞主持"
}

// ───────────────────────────── 顶栏 ─────────────────────────────

@Composable
private fun GroupTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
) {
    com.lanweixi.core.design.components.MuchaGlass(
        intensity = com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "成员",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenMembers)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ───────────────────────────── 提示条 ─────────────────────────────

@Composable
private fun BannerStrip(banner: ChatBanner) {
    val (text, color) = when (banner) {
        is ChatBanner.Notice -> banner.text to MaterialTheme.colorScheme.primary
        is ChatBanner.Error -> banner.text to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ───────────────────────────── 消息 ─────────────────────────────

@Composable
private fun TimeDivider(at: Long) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = formatTime(at),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageRow(
    message: GroupMessage,
    member: GroupMember?,
    userAvatarUri: String?,
    hostName: String,
    highlighted: Boolean = false,
    onQuote: (() -> Unit)? = null,
    onLocate: ((Long) -> Unit)? = null,
) {
    val isUser = GroupSpeaker.isUser(message.speaker)
    val name = when {
        isUser -> "我"
        member != null -> member.name
        GroupSpeaker.isHost(message.speaker) -> hostName
        else -> "已退群的人"
    }
    val memberColor = parseColor(member?.color)
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        // 群聊页不压遮罩，背景是整张插画。纯半透明气泡会把文字压在花纹上，
        // 这里用 surface 打底、只混一点成员色——既分得清是谁说的，也不牺牲可读性。
        blendOver(MaterialTheme.colorScheme.surface, memberColor, 0.14f).copy(alpha = 0.95f)
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    fun copyToClipboard() {
        clipboard.setText(AnnotatedString(message.text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            ChatAvatar(name = name, imageUri = member?.avatarUri, size = 36.dp, tint = memberColor)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            if (!isUser) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = memberColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
            Box {
                Surface(
                    color = bubbleColor,
                    contentColor = contentColor,
                    shape = bubbleShape,
                    // P205：定位到这条时描一圈主题色
                    border = if (highlighted) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true },
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        // P205：引用卡片
                        if (message.quotePreview.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clickable(enabled = onLocate != null && message.quoteId > 0) {
                                        onLocate?.invoke(message.quoteId)
                                    },
                            ) {
                                Text(
                                    text = "↩ ${message.quotePreview}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            copyToClipboard()
                            menuOpen = false
                        },
                    )
                    if (onQuote != null) {
                        DropdownMenuItem(
                            text = { Text("引用") },
                            onClick = {
                                onQuote()
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            ChatAvatar(name = "我", imageUri = userAvatarUri, size = 36.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ───────────────────────────── 输入栏 ─────────────────────────────

@Composable
private fun GroupInputBar(
    draft: String,
    sending: Boolean,
    hint: String,
    pickerOpen: Boolean,
    onPickerOpenChange: (Boolean) -> Unit,
    members: List<GroupMember>,
    selected: Set<String>,
    onToggleTarget: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearTargets: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    com.lanweixi.core.design.components.MuchaGlass(
        intensity = com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha,
    ) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // @ 选人（P203：多选，点一下勾/取消，不关菜单）
                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected.isEmpty()) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                },
                            )
                            .clickable { onPickerOpenChange(!pickerOpen) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "@",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { onPickerOpenChange(false) }) {
                        DropdownMenuItem(
                            text = { Text("@ 全体成员") },
                            onClick = { onSelectAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("只发给兰未晞") },
                            onClick = { onClearTargets() },
                        )
                        HorizontalDivider()
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            !member.configured -> "${member.name}（未配 Key）"
                                            member.isHost -> "${member.name}（主持）"
                                            else -> member.name
                                        },
                                    )
                                },
                                trailingIcon = {
                                    if (member.id in selected) {
                                        Text(
                                            "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                },
                                onClick = { onToggleTarget(member.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = (com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha + 0.1f)
                            .coerceAtMost(1f),
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    GroupChatField(
                        text = draft,
                        enabled = !sending,
                        hint = hint,
                        onValueChange = onDraftChange,
                    )
                }

                Spacer(Modifier.width(8.dp))
                if (sending) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = (com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha + 0.1f)
                                .coerceAtMost(1f),
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(onClick = onCancel),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "取消",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                } else {
                    val enabled = draft.isNotBlank()
                    Surface(
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(enabled = enabled, onClick = onSend),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "发送",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatField(
    text: String,
    enabled: Boolean,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = onValueChange,
        enabled = enabled,
        maxLines = 4,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
        ),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            if (text.isEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            innerTextField()
        },
    )
}

// ───────────────────────────── 成员 ─────────────────────────────

/**
 * P218：群成员面板。
 *
 * 兰未晞是固定群主（不能移出）；工具人可随时「移出」；
 * 下半部分是还没进群的工具人，点「加入」即可加进来。
 * 加减都走调用方落 AgentStore，然后刷新群成员快照。
 */
@Composable
private fun MembersDialog(
    members: List<GroupMember>,
    allAgents: List<AgentProfile>,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val joinable = allAgents.filter { agent -> members.none { it.id == agent.id } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群成员") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(name = member.name, imageUri = member.avatarUri, size = 30.dp, tint = parseColor(member.color))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = when {
                                    member.isHost -> "主持人 · 用你的主配置"
                                    member.configured -> "工具人 · 单独一套 API"
                                    else -> "还没配 API Key，点 @ 他不会回"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!member.isHost) {
                            TextButton(onClick = { onRemoveMember(member.id) }) {
                                Text("移出", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("添加成员", style = MaterialTheme.typography.labelMedium)
                if (joinable.isEmpty()) {
                    Text(
                        "所有工具人都已在群里",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    joinable.forEach { agent ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChatAvatar(
                                name = agent.name,
                                imageUri = agent.avatarUri,
                                size = 30.dp,
                                tint = parseColor(agent.color),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(agent.name, style = MaterialTheme.typography.bodyMedium)
                                if (!agent.configured) {
                                    Text(
                                        "还没配 API Key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { onAddMember(agent.id) }) {
                                Text("加入", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

// ───────────────────────────── 工具 ─────────────────────────────

/** 把 [tint] 按比例混进 [base]（0 = 全 base，1 = 全 tint）。 */
private fun blendOver(base: Color, tint: Color, t: Float): Color = Color(
    red = base.red + (tint.red - base.red) * t,
    green = base.green + (tint.green - base.green) * t,
    blue = base.blue + (tint.blue - base.blue) * t,
    alpha = 1f,
)

private fun parseColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF7C8CF6)
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF7C8CF6))
}

private fun formatTime(at: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate() == java.time.Instant.ofEpochMilli(now)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
}
