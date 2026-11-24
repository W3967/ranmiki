package com.lanweixi.core.design.mucha

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 规范 §4.2 动效基础参数 —— 全库只用这 5 类动效，超出即违规。
 *
 * | 场景 | 时长 | 曲线 |
 * | 瞬时点击星点 | 220ms | EaseOutCubic |
 * | 状态切换动画 | 320ms | EaseOutCubic |
 * | 入场与退场 | 380ms | EaseOutCubic |
 * | 主题切换渐变 | 280ms | EaseOutCubic |
 * | 拖动跟随 | 实时 | 线性跟随 |
 *
 * 禁止：循环旋转、呼吸闪烁、悬浮浮动、自动飘星、文字/报错/气泡上的装饰动画、
 * 同一动作叠加多种动效、按时间自动切换主题、卡片悬浮自动触发装饰动画。
 */
val MuchaEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f) // EaseOutCubic

object MuchaMotion {
    const val SparkleBurstMs = 220
    const val StateChangeMs = 320
    const val EnterExitMs = 380
    const val ThemeChangeMs = 280
}
