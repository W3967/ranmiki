package com.lanweixi.core.design.mucha

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 容器点缀：慕夏四角角饰 —— 规范 §2.3 / §3.3
 *
 * 身份：只表明「这是一个独立内容单元」，不承载任何选中含义。
 * 全程静态，唯一的动效是主题切换时的颜色渐变 280ms。
 *
 * 适用：严格限定两类容器 —— 内容分组卡片 与 轮播卡片容器。
 * 禁止：弹窗、底部弹层、下拉面板、列表项、输入框、图片容器一律不加。
 *
 * 形态：容器四个内角各放一段短小卷曲纹样，只做四角，不做整圈边框。
 * 单段长 12~16px，纤细单线勾勒，不用厚涂填充与渐变着色。
 *
 * 选定纹样是同一套角饰的四款强度序列（规范图3）：
 * - HeavyBlue：左上，重花瓣配深蓝藤蔓 —— 重点展示卡 / 首屏主卡
 * - LightPurple：右上，轻量紫瓣 —— 常规内容卡
 * - PurpleGold：左下，紫金藤蔓配小花 —— 常规内容卡
 * - Golden：右下，金色繁复卷草 —— 仅大尺寸容器
 *
 * 约束：
 * - 按卡片尺寸与层级择一，不做混搭；同一页面内所有卡片必须用同一款，深浅疏密一致
 * - 不得侵入内容安全区，不得与内部图片、缩略图边缘重叠
 * - 同屏两个以上卡片的角饰形态必须完全一致
 * - 降级：纯净模式移除角饰，卡片回到普通圆角矩形
 */
enum class CornerOrnamentStyle {
    LightPurple, // 常规内容卡，推荐默认
    HeavyBlue,   // 首屏主卡 / 重点展示卡
    PurpleGold,  // 常规内容卡
    Golden       // 仅大尺寸容器
}

private enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

@Composable
fun CornerOrnament(
    modifier: Modifier = Modifier,
    style: CornerOrnamentStyle = CornerOrnamentStyle.LightPurple,
    color: Color = MuchaTheme.colors.primary
) {
    if (!MuchaTheme.decorationsEnabled) return
    // 颜色由 MuchaThemeProvider 的 animateColorAsState 做 280ms 渐变，这里直接取色即可
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = 1.dp.toPx()
        // 单段 12~16px，取 14px 近似（dp 换算后在移动端仍清晰）
        val len = 14.dp.toPx()
        val w = size.width
        val h = size.height

        fun drawOneCorner(corner: Corner) {
            // 四个角用同一段 Path 旋转复用，保证同屏形态一致
            val (ox, oy, sx, sy) = when (corner) {
                Corner.TopLeft -> arrayOf(0f, 0f, 1f, 1f)
                Corner.TopRight -> arrayOf(w, 0f, -1f, 1f)
                Corner.BottomLeft -> arrayOf(0f, h, 1f, -1f)
                Corner.BottomRight -> arrayOf(w, h, -1f, -1f)
            }
            val o = Offset(ox as Float, oy as Float)
            val sX = sx as Float; val sY = sy as Float

            val p = Path().apply {
                moveTo(o.x, o.y + sY * len * 0.55f)
                // 主卷曲：沿边向内卷
                cubicTo(
                    o.x + sX * len * 0.15f, o.y + sY * len * 0.35f,
                    o.x + sX * len * 0.45f, o.y + sY * len * 0.15f,
                    o.x + sX * len * 0.75f, o.y + sY * len * 0.08f
                )
                moveTo(o.x + sX * len * 0.55f, o.y)
                cubicTo(
                    o.x + sX * len * 0.35f, o.y + sY * len * 0.15f,
                    o.x + sX * len * 0.15f, o.y + sY * len * 0.45f,
                    o.x + sX * len * 0.08f, o.y + sY * len * 0.75f
                )
            }
            drawPath(p, color, style = Stroke(width = strokePx, cap = StrokeCap.Round))

            // 强度差异只做两处示意，不堆叠真实藤蔓：
            // HeavyBlue / Golden 多一小卷，LightPurple / PurpleGold 保持最简
            if (style == CornerOrnamentStyle.HeavyBlue || style == CornerOrnamentStyle.Golden) {
                val p2 = Path().apply {
                    moveTo(o.x + sX * len * 0.32f, o.y + sY * len * 0.32f)
                    cubicTo(
                        o.x + sX * len * 0.5f, o.y + sY * len * 0.28f,
                        o.x + sX * len * 0.55f, o.y + sY * len * 0.45f,
                        o.x + sX * len * 0.48f, o.y + sY * len * 0.58f
                    )
                }
                drawPath(p2, color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
            }
            if (style == CornerOrnamentStyle.PurpleGold) {
                // 紫金配小花：一个小圆点示意，不用花瓣块面
                drawCircle(
                    color = color,
                    radius = 1.2.dp.toPx(),
                    center = Offset(o.x + sX * len * 0.38f, o.y + sY * len * 0.38f)
                )
            }
        }
        Corner.values().forEach { drawOneCorner(it) }
    }
}

/** 便捷包装：给分组卡片 / 轮播卡片加四角角饰。内容须留安全区，避免角饰压图。 */
@Composable
fun CornerOrnamentBox(
    modifier: Modifier = Modifier,
    style: CornerOrnamentStyle = CornerOrnamentStyle.LightPurple,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        CornerOrnament(style = style, modifier = Modifier.fillMaxSize())
    }
}
