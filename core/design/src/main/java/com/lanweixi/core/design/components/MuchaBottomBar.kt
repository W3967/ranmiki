package com.lanweixi.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.R
import com.lanweixi.core.design.mucha.CurlyUnderline
import com.lanweixi.core.design.mucha.LocalMuchaTuning

/**
 * 底部导航的一个入口。图标按 [LocalIconStyle] 在运行时解析。
 */
data class MuchaNavItem(
    val label: String,
    val icon: MuchaNavIcon,
)

/**
 * P173：底部导航。
 *
 * 选中项下方是**卷草下划线**——规范里它只表达「当前在哪」，
 * 所以只用于这里与分段控制器，不得挪去按钮或输入框。
 *
 * 图标不换造型，选中只改 tint 为主题主色。
 */
@Composable
fun MuchaBottomBar(
    items: List<MuchaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    MuchaGlass(
        modifier = modifier,
        intensity = LocalMuchaTuning.current.barAlpha,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                val tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MuchaIcon(
                        iconRes = resolveNavIcon(item.icon),
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Medium else null,
                        color = tint,
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(8.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (active) {
                            CurlyUnderline(modifier = Modifier.width(40.dp))
                        }
                    }
                }
            }
        }
    }
}

