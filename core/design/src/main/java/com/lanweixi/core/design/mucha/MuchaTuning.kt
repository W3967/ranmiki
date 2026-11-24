package com.lanweixi.core.design.mucha

import androidx.compose.runtime.compositionLocalOf

/**
 * P180：可调的遮罩参数。
 *
 * 背景插画和顶/底栏都压在内容后面，遮罩太重看不出图、太轻读不清字，
 * 不同人对这个平衡的偏好不一样——所以做成设置项，让用户自己拖。
 *
 * @property backgroundVeil 背景插画上那层黑的透明度（0 = 不遮，0.85 = 几乎全黑）
 * @property barAlpha 顶栏 / 底栏 / 输入栏自身底色的不透明度（越小越透）
 */
data class MuchaTuning(
    val backgroundVeil: Float = 0.50f,
    val barAlpha: Float = 0.62f,
) {
    companion object {
        const val VEIL_MIN = 0f
        const val VEIL_MAX = 0.85f
        const val BAR_MIN = 0.10f
        const val BAR_MAX = 1.00f
    }
}

val LocalMuchaTuning = compositionLocalOf { MuchaTuning() }
