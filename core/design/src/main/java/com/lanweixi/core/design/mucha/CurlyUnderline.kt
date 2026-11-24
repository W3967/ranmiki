package com.lanweixi.core.design.mucha

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 体系一：卷草下划线（视图切换选中）—— 规范 §2.1 / §3.1
 *
 * 含义：「当前处在这一页、这一分类」
 * 适用：底部导航 Tab、分段控制器 Segmented、横向可滚动标签栏、内容区视图标签。
 * 四者同款线条，保证「我在哪」是同一种语言。
 *
 * 形态：
 * - 宽约等于文字块，不做通栏；两端向内柔和收束成小卷曲，不做花瓣填充
 * - 与文字基线不少于 4px 间距（调用方用 padding 保证，本组件高 6dp 含线高）
 * - 线高 1.5~2px，纤细单线勾勒，无填充、无渐变着色
 * - 选定纹样：双向对称卷草饰带，两端内卷，中段两段镜像卷浪，浪心极小卷 + 短描线
 * - 落地简化：按文字块宽度横向截取「端部卷曲 + 一段浪峰」，不整段照搬
 *
 * 动效：切换时从旧位置平滑位移到新位置并淡入，320ms EaseOutCubic。
 * 纹样本体静态，不旋转、不呼吸。
 * 降级：纯净模式下不渲染，调用方改用底色高亮（见 MuchaTabRow 的 fallback）。
 *
 * 禁止：不得用于按钮、文字按钮、输入框聚焦、折叠面板展开态。
 */
@Composable
fun CurlyUnderline(
    modifier: Modifier = Modifier,
    color: Color = MuchaTheme.colors.primary,
    /** 1.5~2px，默认 1.75dp 近似，预览页可调 */
    strokeWidth: Dp = 1.75.dp
) {
    if (!MuchaTheme.decorationsEnabled) return
    Canvas(modifier = modifier.height(6.dp)) {
        val w = size.width
        val h = size.height
        val cy = h * 0.55f
        val amp = h * 0.32f
        val strokePx = strokeWidth.toPx()

        val path = Path().apply {
            moveTo(0f, cy)
            // 左端内卷：小卷曲收束
            cubicTo(w * 0.06f, cy - amp, w * 0.10f, cy + amp * 0.6f, w * 0.16f, cy)
            // 中段镜像卷浪：左浪峰
            cubicTo(w * 0.24f, cy - amp, w * 0.30f, cy - amp, w * 0.38f, cy)
            // 浪心极小卷（示意，不堆叠）
            cubicTo(w * 0.42f, cy + amp * 0.45f, w * 0.46f, cy + amp * 0.45f, w * 0.50f, cy)
            // 中段镜像卷浪：右浪峰（与左镜像）
            cubicTo(w * 0.58f, cy - amp, w * 0.62f, cy - amp, w * 0.70f, cy)
            // 右端内卷
            cubicTo(w * 0.78f, cy + amp * 0.6f, w * 0.84f, cy - amp, w * 0.92f, cy)
            cubicTo(w * 0.96f, cy + amp * 0.4f, w * 0.98f, cy, w, cy)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        // 浪心短描线：两点示意，细线不抢戏
        drawLine(
            color = color,
            start = Offset(w * 0.30f, cy - amp * 0.55f),
            end = Offset(w * 0.34f, cy - amp * 0.15f),
            strokeWidth = strokePx * 0.7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.70f, cy - amp * 0.55f),
            end = Offset(w * 0.66f, cy - amp * 0.15f),
            strokeWidth = strokePx * 0.7f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Tab 切换用的指示器：给定目标 x 偏移与宽度，320ms 平滑位移 + 淡入。
 * 调用方在 TabRow / SegmentedControl 里测量选中项的 x / width 后传入。
 * 纯净模式：不画线，调用方用底色高亮（Modifier.background）代替。
 */
@Composable
fun AnimatedCurlyUnderline(
    targetOffsetX: Dp,
    targetWidth: Dp,
    modifier: Modifier = Modifier,
    color: Color = MuchaTheme.colors.primary
) {
    if (!MuchaTheme.decorationsEnabled) return
    val animationsOn = MuchaTheme.animationsEnabled
    val offsetX by animateDpAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(
            durationMillis = if (animationsOn) MuchaMotion.StateChangeMs else 0,
            easing = MuchaEasing
        ),
        label = "curlyOffset"
    )
    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(
            durationMillis = if (animationsOn) MuchaMotion.StateChangeMs else 0,
            easing = MuchaEasing
        ),
        label = "curlyWidth"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = if (animationsOn) MuchaMotion.StateChangeMs else 0,
            easing = MuchaEasing
        ),
        label = "curlyAlpha"
    )
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .offset { IntOffset(with(density) { offsetX.toPx() }.roundToInt(), 0) }
            .width(width)
    ) {
        // alpha 通过 color 透明度实现，避免整层重组
        CurlyUnderline(
            modifier = Modifier.fillMaxWidth(),
            color = color.copy(alpha = alpha)
        )
    }
}
