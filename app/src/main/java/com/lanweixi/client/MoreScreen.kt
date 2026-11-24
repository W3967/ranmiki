package com.lanweixi.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaListItem
import com.lanweixi.core.design.components.MuchaMoreIcon
import com.lanweixi.core.design.components.MuchaTopBar

/**
 * P154：「更多」页——底部 Tab 收纳不下的一级入口。
 *
 * 提示词、文档、复盘、插件、设置都在这里，主 Tab 只留聊天/笔记/任务。
 * P206：待审核（AI 草稿）排在最前，数量直接做成角标——不显眼就等于没有。
 */
@Composable
fun MoreScreen(
    /** P199：全局搜索入口。 */
    onOpenSearch: () -> Unit = {},
    /** P206：待审核队列。 */
    onOpenInbox: () -> Unit = {},
    /** P206：待审核条数（0 不显示角标）。 */
    pendingDraftCount: Int = 0,
    onOpenPrompts: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MuchaTopBar(title = "更多")
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoreEntry(
                title = "待审核",
                subtitle = "她整理的笔记和任务，确认后才进工作台",
                icon = MuchaMoreIcon.REVIEW,
                onClick = onOpenInbox,
                badge = pendingDraftCount.takeIf { it > 0 }?.toString(),
            )
            MoreEntry("搜索", "笔记 / 任务 / 文档 / 提示词，一处搜", MuchaMoreIcon.SEARCH, onOpenSearch)
            MoreEntry("提示词", "IP / 短视频 / 绘图", MuchaMoreIcon.PROMPT, onOpenPrompts)
            MoreEntry("文档", "导入 txt / md / docx", MuchaMoreIcon.DOC, onOpenDocs)
            MoreEntry("复盘", "看看这段时间做了什么", MuchaMoreIcon.REVIEW, onOpenReview)
            MoreEntry("插件", "扩展能力，装上就能用", MuchaMoreIcon.PLUGIN, onOpenPlugins)
            Spacer(Modifier.height(4.dp))
            MoreEntry("设置", "API Key / 模型 / 主题", MuchaMoreIcon.SETTINGS, onOpenSettings)
            Spacer(Modifier.height(24.dp))
            Text(
                "兰未晞 · 私人工作台",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun MoreEntry(
    title: String,
    subtitle: String,
    icon: MuchaMoreIcon,
    onClick: () -> Unit,
    badge: String? = null,
) {
    MuchaListItem(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
