package com.lanweixi.core.design.mucha

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lanweixi.core.design.R

/**
 * P178：主题背景。
 *
 * 星夜 / 日光金各一张插画；纯净模式不铺图，但**仍要垫一层不透明底色**。
 *
 * 纯净模式如果直接 `content()`，外层 `Scaffold(containerColor = Transparent)`
 * 就没有任何不透明层，底下是 Activity 的窗口——滚动时会出现闪烁和残影。
 * 所以这里统一用 Box + 不透明背景兜住。
 */
@Composable
fun MuchaBackground(
    theme: MuchaThemeMode,
    modifier: Modifier = Modifier,
    /**
     * 当前页面的遮罩覆盖值。null = 不遮罩。
     */
    veil: Float? = null,
    content: @Composable () -> Unit,
) {
    val res = when (theme) {
        MuchaThemeMode.StarryNight -> R.drawable.bg_starry
        MuchaThemeMode.DaylightGold -> R.drawable.bg_gold
        MuchaThemeMode.PureMaterial3 -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val effectiveVeil = veil ?: 0f
            if (effectiveVeil > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveVeil)),
                )
            }
        } else {
            // 纯净模式：垫不透明底，避免透出窗口导致闪烁
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        content()
    }
}
