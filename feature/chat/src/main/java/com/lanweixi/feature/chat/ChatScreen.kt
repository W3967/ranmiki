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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * P50：QQ 风格聊天页。
 *
 * 结构参照 QQ：
 *  - 顶栏：头像 + 昵称 + 在线状态（左上角可拉出抽屉；右侧「···」打开功能菜单）
 *  - 消息区：浅灰底；对方为白色气泡（左），自己为蓝色气泡（右），均带头像
 *  - 输入栏：加号 + 输入框 + 发送按钮，发送中变为「取消」
 *  - 左侧抽屉：头像、昵称、关系阶段、功能入口
 *
 * 约束不变：纯 Compose，不引用 Hilt / Room / DAO / ConversationEngine。
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit = {},
    onResend: (String) -> Unit = {},
    /** P132：工作台笔记。 */
    onOpenNotes: () -> Unit = {},
    /** P133：创作提示词。 */
    onOpenPrompts: () -> Unit = {},
    /** P139：待确认的敏感工具调用。 */
    pendingConfirm: com.lanweixi.domain.tools.PendingToolConfirm? = null,
    onAnswerConfirm: (Boolean) -> Unit = {},
    /** P134：任务清单。 */
    onOpenTasks: () -> Unit = {},
    /** P135：文档库。 */
    onOpenDocs: () -> Unit = {},
    /** P136：复盘。 */
    onOpenReview: () -> Unit = {},
    /** P137：插件。 */
    onOpenPlugins: () -> Unit = {},
    /** P150：慕夏纹样预览（临时）。 */
    onOpenPreview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRetry: () -> Unit = {},
    /** P198：返回会话列表。 */
    onBack: () -> Unit = {},
    /** P204：长按气泡引用这条消息。 */
    onQuote: (ChatMessageUi) -> Unit = {},
    onClearQuote: () -> Unit = {},
    /** P204：点气泡里的引用卡片定位原消息。 */
    onLocateQuote: (Long) -> Unit = {},
    onClearHighlight: () -> Unit = {},
    /** P75：自定义头像（null = 默认渐变头像）。 */
    characterAvatarUri: String? = null,
    userAvatarUri: String? = null,
    /** P91：角色名字（可自定义）。 */
    characterName: String = "兰未晞",
    /** P207：待审核条数（0 = 不显示入口）。 */
    pendingDrafts: Int = 0,
    /** P207：点「等你确认」进待审核页。 */
    onOpenInbox: () -> Unit = {},
    /** P211：加载更早的历史消息。 */
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    // 抽屉开着时，返回键只关抽屉，不退出页面/应用。
    // ModalNavigationDrawer 自带的 BackHandler 受手势开关影响，这里显式兜一层。
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            QqDrawerSheet(
                characterName = characterName,
                characterAvatarUri = characterAvatarUri,
                onOpenNotes = { closeDrawerThen(onOpenNotes) },
                onOpenPrompts = { closeDrawerThen(onOpenPrompts) },
                onOpenTasks = { closeDrawerThen(onOpenTasks) },
                onOpenDocs = { closeDrawerThen(onOpenDocs) },
                onOpenReview = { closeDrawerThen(onOpenReview) },
                onOpenPlugins = { closeDrawerThen(onOpenPlugins) },
            onOpenPreview = { closeDrawerThen(onOpenPreview) },
                onOpenSettings = { closeDrawerThen(onOpenSettings) },
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 聊天页不铺底色，让主题插画原色显示
                .statusBarsPadding()
                .imePadding(),
        ) {
            QqTopBar(
                characterAvatarUri = characterAvatarUri,
                characterName = characterName,
                onBack = onBack,
                onAvatarClick = { scope.launch { drawerState.open() } },
                onMoreClick = { showMoreMenu = true },
                showMoreMenu = showMoreMenu,
                onDismissMore = { showMoreMenu = false },
                onOpenSettings = {
                    showMoreMenu = false
                    onOpenSettings()
                },
            )

            BannerArea(
                state = state,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
            )

            // P207：她有东西等你确认——从聊天页一步到位，不用自己翻「更多」
            if (pendingDrafts > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Surface(
                        // 0.12 的主题色在插画背景上几乎看不见，改成实底 + 描边
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.clickable(onClick = onOpenInbox),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "她有 $pendingDrafts 条等你确认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            val listState = rememberLazyListState()
            val itemCount = state.history.size + if (state.partialAssistant != null) 1 else 0
            // P211：只在「最后一条消息变了」时滚到底。
            // 之前按 itemCount 滚，点「加载更早」把列表变长后会被直接甩回底部。
            val lastMessageId = state.history.lastOrNull()?.id ?: 0L
            LaunchedEffect(lastMessageId, state.partialAssistant != null) {
                if (itemCount > 0) listState.scrollToItem(itemCount - 1)
            }
            // P73：键盘弹出/收起时保持最新消息可见（否则输入框上移会挡住最后一条）
            val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime
                .getBottom(androidx.compose.ui.platform.LocalDensity.current)
            LaunchedEffect(imeBottom) {
                if (imeBottom > 0 && itemCount > 0) {
                    runCatching { listState.animateScrollToItem(itemCount - 1) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // P211：历史还有更早的（当前已加载满）就给个入口
                if (state.history.size >= state.historyLimit) {
                    item(key = "load-more") {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "加载更早的消息",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(
                    items = state.history,
                    key = { it.id.takeIf { id -> id > 0 } ?: "${it.role}-${it.createdAt}" },
                ) { message ->
                    QqMessageRow(
                        role = message.role,
                        text = message.text,
                        sending = state.sending,
                        onResend = onResend,
                        characterAvatarUri = characterAvatarUri,
                        userAvatarUri = userAvatarUri,
                        characterName = characterName,
                        quoteId = message.quoteId,
                        quotePreview = message.quotePreview,
                        highlighted = state.highlightId > 0 && state.highlightId == message.id,
                        onQuote = { onQuote(message) },
                        onLocate = onLocateQuote,
                    )
                }
                state.partialAssistant?.let { partial ->
                    item {
                        QqMessageRow(
                            role = "assistant",
                            text = partial,
                            sending = state.sending,
                            onResend = onResend,
                            characterAvatarUri = characterAvatarUri,
                            userAvatarUri = userAvatarUri,
                            characterName = characterName,
                        )
                    }
                }
            }

            // P204：点引用卡片后滚到原消息，高亮一会儿再撤掉
            LaunchedEffect(state.highlightId) {
                val id = state.highlightId
                if (id > 0) {
                    val index = state.history.indexOfFirst { it.id == id }
                    if (index >= 0) runCatching { listState.animateScrollToItem(index) }
                    kotlinx.coroutines.delay(1600)
                    onClearHighlight()
                }
            }

            // P141：把她刚查过 / 记过的工具显出来——工具调用不该是黑盒
            if (state.toolTrail.isNotEmpty()) {
                Text(
                    text = "她刚刚：" + state.toolTrail.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            // P204：输入框上方的引用条（发送后清掉）
            state.quote?.let { quote ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "↩ ${quote.speaker}：${quote.preview}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 280.dp),
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
            }
            QqInputBar(
                draft = state.draft,
                sending = state.sending,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onCancel = onCancel,
            )
        }
    }

    // P139：模型要动数据时停下来问一句
    if (pendingConfirm != null) {
        ToolConfirmDialog(pendingConfirm) { onAnswerConfirm(it) }
    }
}

// ───────────────────────────── 顶栏 ─────────────────────────────

@Composable
private fun QqTopBar(
    characterAvatarUri: String?,
    characterName: String,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreMenu: Boolean,
    onDismissMore: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    com.lanweixi.core.design.components.MuchaGlass(
        intensity = com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // P198：从会话列表进来，返回键回列表（系统返回键也能用）
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "‹",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // 头像（点击拉出抽屉）
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick),
                ) {
                    ChatAvatar(name = characterName, imageUri = characterAvatarUri, size = 38.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box {
                    IconButton(onClick = onMoreClick) {
                        Text(
                            text = "···",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = onDismissMore) {
                        DropdownMenuItem(text = { Text("返回会话列表") }, onClick = { onDismissMore(); onBack() })
                        DropdownMenuItem(text = { Text("设置") }, onClick = onOpenSettings)
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}


// ───────────────────────────── 抽屉 ─────────────────────────────

@Composable
private fun QqDrawerSheet(
    characterName: String,
    characterAvatarUri: String? = null,
    onOpenNotes: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenDocs: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenPreview: () -> Unit = {},
        onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                ),
            ) {
                Spacer(Modifier.height(56.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape)) {
                        ChatAvatar(name = characterName, imageUri = characterAvatarUri, size = 56.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = characterName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            QqDrawerItem("笔记", "工作台的原子数据", onOpenNotes)
            QqDrawerItem("提示词", "IP / 短视频 / 绘图", onOpenPrompts)
            QqDrawerItem("任务", "今天要推进的事", onOpenTasks)
            QqDrawerItem("文档", "导入 txt / md / docx", onOpenDocs)
            QqDrawerItem("复盘", "看看这段时间做了什么", onOpenReview)
            QqDrawerItem("插件", "扩展能力，装上就能用", onOpenPlugins)
            QqDrawerItem("纹样预览", "慕夏装饰组件（临时）", onOpenPreview)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            QqDrawerItem("设置", "API Key / 模型", onOpenSettings)
        }
    }
}

@Composable
private fun QqDrawerItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ───────────────────────────── 头像 ─────────────────────────────

// ───────────────────────────── 提示条 ─────────────────────────────

@Composable
private fun BannerArea(
    state: ChatUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val banner = state.banner ?: return
    val (text, color) = when (banner) {
        is ChatBanner.Notice -> banner.text to MaterialTheme.colorScheme.primary
        is ChatBanner.Error -> banner.text to MaterialTheme.colorScheme.error
    }
    val action = when (banner) {
        is ChatBanner.Notice -> banner.action
        is ChatBanner.Error -> banner.action
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (action != null) {
                    // P202：配置类问题给「去设置」；普通失败才给「重试」
                    TextButton(onClick = onOpenSettings, enabled = !state.sending) {
                        Text(action.label, style = MaterialTheme.typography.labelMedium)
                    }
                } else if (banner is ChatBanner.Error) {
                    TextButton(onClick = onRetry, enabled = !state.sending) {
                        Text("重试", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    state.usage?.let { usage ->
        Text(
            text = usage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
    }
}

// ───────────────────────────── 消息 ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QqMessageRow(
    role: String,
    text: String,
    sending: Boolean,
    onResend: (String) -> Unit,
    characterAvatarUri: String? = null,
    userAvatarUri: String? = null,
    characterName: String = "兰未晞",
    /** P204：这气泡引用的原消息（0/空 = 没引用）。 */
    quoteId: Long = 0,
    quotePreview: String = "",
    highlighted: Boolean = false,
    onQuote: (() -> Unit)? = null,
    onLocate: ((Long) -> Unit)? = null,
) {
    val isUser = role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    fun copyToClipboard() {
        clipboard.setText(AnnotatedString(text))
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
            Box(modifier = Modifier.size(36.dp).clip(CircleShape)) {
                ChatAvatar(name = characterName, imageUri = characterAvatarUri, size = 36.dp)
            }
            Spacer(Modifier.width(8.dp))
        }

        Box {
            Surface(
                // 气泡取主题的容器色（而不是高对比的 primary）：
                // 星夜是深紫、日光是浅金、纯净是浅紫，三档各有自己的气质。
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = bubbleShape,
                // P204：定位到这条时描一圈主题色，1.6 秒后自己撤掉
                border = if (highlighted) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                shadowElevation = if (isUser) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true },
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    // P204：引用卡片——原消息没了也还能看到快照
                    if (quotePreview.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable(enabled = onLocate != null && quoteId > 0) {
                                    onLocate?.invoke(quoteId)
                                },
                        ) {
                            Text(
                                text = "↩ $quotePreview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = text,
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
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("重发") },
                        enabled = !sending,
                        onClick = {
                            onResend(text)
                            menuOpen = false
                        },
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(36.dp).clip(CircleShape)) {
                ChatAvatar(name = "我", imageUri = userAvatarUri, size = 36.dp)
            }
        }
    }
}

// ───────────────────────────── 输入栏 ─────────────────────────────

@Composable
private fun QqInputBar(
    draft: String,
    sending: Boolean,
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
                // 加号（扩展入口，暂为占位）
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = (com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha + 0.1f).coerceAtMost(1f),
                ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicChatField(
                        text = draft,
                        enabled = !sending,
                        onValueChange = onDraftChange,
                    )
                }

                Spacer(Modifier.width(8.dp))
                if (sending) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = (com.lanweixi.core.design.mucha.LocalMuchaTuning.current.barAlpha + 0.1f).coerceAtMost(1f),
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
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
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
private fun BasicChatField(
    text: String,
    enabled: Boolean,
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
                    text = "说点什么…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            innerTextField()
        },
    )
}

/**
 * P139：敏感工具确认。
 *
 * 模型想动手改东西时停下来问一句——不做静默执行。
 */
@Composable
private fun ToolConfirmDialog(
    pending: com.lanweixi.domain.tools.PendingToolConfirm,
    onAnswer: (Boolean) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text("她想要做一件事") },
        text = {
            Column {
                Text(
                    pending.toolName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (pending.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pending.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pending.arguments.isNotBlank() && pending.arguments != "{}") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pending.arguments,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "这个操作可能会改动你的数据。拒绝的话她不会执行。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) {
                Text("允许", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) {
                Text("拒绝", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
