package com.lanweixi.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.mucha.CornerOrnament

/**
 * P171：分组卡片。
 *
 * 圆角卡片 + 四角角饰。角饰用新的 [CornerOrnament]（Modifier 形式），
 * 纯净模式下它自己不渲染，卡片自动回到普通圆角。
 */
@Composable
fun MuchaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 14.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(cornerRadius))
    if (onClick != null) m = m.clickable { onClick() }
    Box(modifier = m) {
        CornerOrnament(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
