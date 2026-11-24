package com.lanweixi.core.design.mucha

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 体系二：细雕花框 + 星点（条目选中）—— 规范 §2.2 / §3.2
 *
 * 含义：「这一条被选中了」
 * 适用：列表条目、侧边导航项、单选 Radio、复选 Checkbox、下拉菜单选项、筛选徽章标签。
 * 与 Tab 的「切换视图」性质不同，必须用另一套指示器，避免误读。
 *
 * 形态：
 * - 一圈纤细雕花线框贴合条目轮廓，四角带短小藤蔓卷曲，角内收 8~12px
 * - 圆角 6~8px，线宽 1px，主题主色、透明度略降
 * - 线框内右上角落一个小星点（直径 2~3px）
 * - 选定纹样：细圆角线框，四角各一段藤蔓卷曲 + 数片小叶，中段斜向走空，只装饰四角
 * - 落地简化：角部只保留一段主卷曲 + 两片小叶，删去细碎卷须与花心
 *
 * 动效：选中时描边生长动画，从起点逐步勾勒成完整边框，320ms EaseOutCubic，
 * 同步弹出星点；取消选中时线框反向收束消失，不触发星点。
 * 降级：纯净模式移除线框与星点，改为浅色底填充（调用方处理）。
 *
 * 禁止：不得包裹操作按钮、头像或整块面板；面板级装饰交给四角角饰。
 * 约束：雕花框须避开右侧操作区；多项连排时框间距不少于 4px；
 * 徽章高度不足 24px 时改填充，不强制加框。
 */
@Composable
fun OrnateFrame(
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MuchaTheme.colors.primary,
    cornerRadius: Dp = 8.dp,
    /** 角内收 8~12px，默认 10dp 近似 */
    cornerInset: Dp = 10.dp
) {
    if (!MuchaTheme.decorationsEnabled) return
    // DrawScope 里不能读 CompositionLocal，先取出来
    val sparkleColor = MuchaTheme.colors.sparkle
    val animationsOn = MuchaTheme.animationsEnabled
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animationsOn) MuchaMotion.StateChangeMs else 0,
            easing = MuchaEasing
        ),
        label = "ornateProgress"
    )
    // 星点只在选中时弹出，取消时不触发
    val starScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animationsOn) MuchaMotion.StateChangeMs else 0,
            easing = MuchaEasing
        ),
        label = "ornateStar"
    )

    val measure = remember { PathMeasure() }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (progress <= 0.01f && starScale <= 0.01f) return@Canvas
        val strokePx = 1.dp.toPx()
        val radiusPx = cornerRadius.toPx()
        val insetPx = cornerInset.toPx()

        // 1. 圆角矩形描边，用 PathMeasure 做生长动画
        val rectPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = strokePx / 2, top = strokePx / 2,
                    right = size.width - strokePx / 2, bottom = size.height - strokePx / 2,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }
        measure.setPath(rectPath, false)
        val total = measure.length
        val partial = Path().apply {
            measure.getSegment(0f, total * progress, this, true)
        }
        drawPath(
            path = partial,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // 2. 四角藤蔓卷曲 + 两片小叶（简化版，只画四角）
        if (progress > 0.5f) {
            val cornerAlpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
            val c = color.copy(alpha = 0.85f * cornerAlpha)
            val curl = insetPx * 0.9f
            val leaf = insetPx * 0.55f
            val corners = listOf(
                // 左上：向内卷
                Triple(Offset(insetPx, insetPx), 1f, 1f),
                // 右上
                Triple(Offset(size.width - insetPx, insetPx), -1f, 1f),
                // 左下
                Triple(Offset(insetPx, size.height - insetPx), 1f, -1f),
                // 右下
                Triple(Offset(size.width - insetPx, size.height - insetPx), -1f, -1f)
            )
            corners.forEach { (origin, sx, sy) ->
                val p = Path().apply {
                    moveTo(origin.x, origin.y)
                    cubicTo(
                        origin.x + sx * curl * 0.4f, origin.y,
                        origin.x + sx * curl, origin.y + sy * curl * 0.35f,
                        origin.x + sx * curl, origin.y + sy * curl
                    )
                }
                drawPath(p, c, style = Stroke(width = strokePx, cap = StrokeCap.Round))
                // 两片小叶：短线示意
                drawLine(
                    color = c,
                    start = Offset(origin.x + sx * leaf * 0.4f, origin.y + sy * leaf * 0.2f),
                    end = Offset(origin.x + sx * leaf, origin.y + sy * leaf * 0.7f),
                    strokeWidth = strokePx * 0.8f, cap = StrokeCap.Round
                )
                drawLine(
                    color = c,
                    start = Offset(origin.x + sx * leaf * 0.2f, origin.y + sy * leaf * 0.4f),
                    end = Offset(origin.x + sx * leaf * 0.7f, origin.y + sy * leaf),
                    strokeWidth = strokePx * 0.8f, cap = StrokeCap.Round
                )
            }
        }

        // 3. 星点：右上角内，直径 2~3px，实心圆
        if (starScale > 0.01f) {
            val r = (2.5.dp.toPx() / 2) * starScale
            drawCircle(
                color = sparkleColor.copy(alpha = 0.9f),
                radius = r,
                center = Offset(size.width - insetPx - 2.dp.toPx(), insetPx + 2.dp.toPx())
            )
        }
    }
}

/**
 * 便捷包装：给任意条目加雕花选中框。
 * 纯净模式下降级由调用方做浅色底填充，这里只负责不画框。
 * 注意：不要把按钮 / 头像 / 整块面板包进来。
 */
@Composable
fun OrnateSelectableBox(
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        OrnateFrame(selected = selected, modifier = Modifier.fillMaxSize())
    }
}
