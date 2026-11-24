package com.lanweixi.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * P183：液态玻璃底。
 *
 * Android 没有原生 backdrop blur（`Modifier.blur` 只模糊自己，不模糊背后），
 * 所以这里做的是**视觉近似**：半透明底 + 顶部高光 + 细亮线 + 微噪点。
 * 质感像磨砂玻璃，但背后内容是透出来的原色，不模糊。
 *
 * @param intensity 玻璃强度 0~1。0 = 几乎全透，1 = 厚玻璃。
 *        设置页的滑块直接控制它。
 */
@Composable
fun MuchaGlass(
    modifier: Modifier = Modifier,
    intensity: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) {
    val i = intensity.coerceIn(0f, 1f)
    // 底色不透明度：越强越厚
    val baseAlpha = 0.16f + i * 0.50f
    // 顶部高光：越强越明显
    val topGlow = 0.05f + i * 0.14f
    val base = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        // 顶部略亮，往下略沉 —— 玻璃的受光感
                        blend(base, Color.White, 0.10f).copy(alpha = baseAlpha),
                        base.copy(alpha = baseAlpha),
                    ),
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = topGlow),
                        Color.Transparent,
                    ),
                    endY = 56f,
                ),
            ),
    ) {
        // 顶部一道细亮线，模拟玻璃边缘的高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f * i),
                            Color.White.copy(alpha = 0.20f * i),
                            Color.White.copy(alpha = 0.06f * i),
                        ),
                    ),
                ),
        )
        content()
    }
}

/** 把 [color] 往 [toward] 混 [t]（0~1）。 */
private fun blend(color: Color, toward: Color, t: Float): Color = Color(
    red = color.red + (toward.red - color.red) * t,
    green = color.green + (toward.green - color.green) * t,
    blue = color.blue + (toward.blue - color.blue) * t,
    alpha = 1f,
)
