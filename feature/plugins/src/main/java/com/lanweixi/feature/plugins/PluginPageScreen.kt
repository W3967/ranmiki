package com.lanweixi.feature.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaCard
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.domain.plugin.PluginUiNode

/**
 * P165：插件界面渲染器。
 *
 * 插件在 Lua 里描述一棵节点树，这里把它渲染成原生 Compose。
 * 所有节点都用宿主主题，插件无法定制样式——这是保证视觉统一的代价，
 * 也是它跑不了任意代码的收益。
 *
 * @param onAction 按钮点击回调，参数是插件里声明的 action 函数名
 */
@Composable
fun PluginPageScreen(
    title: String,
    nodes: List<PluginUiNode>,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    error: String? = null,
    busy: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MuchaTopBar(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            nodes.forEach { node ->
                PluginNodeView(node = node, busy = busy, onAction = onAction)
            }
            if (nodes.isEmpty() && error == null) {
                Text(
                    "这个页面没有内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PluginNodeView(
    node: PluginUiNode,
    busy: Boolean,
    onAction: (String) -> Unit,
) {
    when (node) {
        is PluginUiNode.Title -> Text(
            node.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        is PluginUiNode.Text -> Text(
            node.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        is PluginUiNode.Notice -> Text(
            node.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        is PluginUiNode.Card -> MuchaCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                node.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (node.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    node.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is PluginUiNode.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            node.items.forEach { item ->
                Row {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " " + item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        is PluginUiNode.Button -> Button(
            onClick = { onAction(node.action) },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(node.text.ifBlank { "执行" })
        }

        PluginUiNode.Divider -> HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
