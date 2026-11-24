package com.lanweixi.core.design.mucha

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 慕夏风格 UI 规范 V2.0 —— 主题与全局开关
 *
 * 来源：workspace/user/files/UI.docx
 * 三档主题：星夜蓝紫（默认）、日光金、纯净 Material3
 * 核心原则：
 *  1. 装饰服从内容
 *  2. 动效服务状态（A 方案轻动效，无循环动画）
 *  3. 纯净模式一键降级：所有慕夏纹样 + 自定义动画同时关闭
 */
enum class MuchaThemeMode {
    /** 星夜蓝紫，默认。对应兰未晞星空礼服裙配色。 */
    StarryNight,
    /** 日光金 */
    DaylightGold,
    /** 纯净 Material3：无纹样、无自定义动画，回退原生反馈 */
    PureMaterial3
}

@Immutable
data class MuchaColors(
    /** 主色：卷草线 / 雕花框 / 角饰 / 星点取色 */
    val primary: Color,
    /** 星点偏亮色 */
    val sparkle: Color,
    /** 装饰线在深色底上的透明度略降版本已在组件内处理 */
    val onBackground: Color,
    val background: Color,
    val surface: Color
)

val StarryNightColors = MuchaColors(
    primary = Color(0xFF8B7CF6),      // 浅紫，星空礼服裙上半身渐变
    sparkle = Color(0xFFB9C8FF),     // 偏亮的星点
    onBackground = Color(0xFFEDEAFF),
    background = Color(0xFF0B1030),  // 深藏青
    surface = Color(0xFF171D45)
)

val DaylightGoldColors = MuchaColors(
    primary = Color(0xFFB98A2F),      // 紫金藤蔓 / 金色卷草
    sparkle = Color(0xFFFFD98A),
    onBackground = Color(0xFF2B2110),
    background = Color(0xFFFFF8E7),
    surface = Color(0xFFFFFFFF)
)

val PureMaterialColors = MuchaColors(
    primary = Color(0xFF6750A4),      // 仅作 Material3 主色占位，不渲染纹样
    sparkle = Color(0xFF6750A4),
    onBackground = Color(0xFF1C1B1F),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE)
)

/** 全局装饰配置：业务页面只读这个，不自己判断主题。 */
@Immutable
data class MuchaDecorationConfig(
    val theme: MuchaThemeMode,
    val colors: MuchaColors,
    /** 纯净模式 = false，其余 = true。切到纯净时纹样与自定义动画全部停止。 */
    val decorationsEnabled: Boolean,
    /** 与 decorationsEnabled 同步，动画总开关 */
    val animationsEnabled: Boolean
)

val LocalMuchaConfig = compositionLocalOf {
    MuchaDecorationConfig(
        theme = MuchaThemeMode.StarryNight,
        colors = StarryNightColors,
        decorationsEnabled = true,
        animationsEnabled = true
    )
}

val LocalMuchaColors = compositionLocalOf { StarryNightColors }

object MuchaTheme {
    val colors: MuchaColors
        @Composable @ReadOnlyComposable get() = LocalMuchaColors.current

    val config: MuchaDecorationConfig
        @Composable @ReadOnlyComposable get() = LocalMuchaConfig.current

    /** 业务层判断：能不能画纹样 / 跑自定义动画 */
    val decorationsEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalMuchaConfig.current.decorationsEnabled

    val animationsEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalMuchaConfig.current.animationsEnabled
}

private fun starryScheme() = darkColorScheme(
    primary = StarryNightColors.primary,
    onPrimary = Color.White,
    background = StarryNightColors.background.copy(alpha = 0.45f),
    surface = StarryNightColors.surface,
    onBackground = StarryNightColors.onBackground,
    onSurface = StarryNightColors.onBackground
)

private fun goldScheme() = lightColorScheme(
    primary = DaylightGoldColors.primary,
    onPrimary = Color.White,
    background = DaylightGoldColors.background.copy(alpha = 0.35f),
    surface = DaylightGoldColors.surface,
    onBackground = DaylightGoldColors.onBackground,
    onSurface = DaylightGoldColors.onBackground
)

private fun pureScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()

/**
 * 应用级入口。用法：
 * ```
 * MuchaThemeProvider(mode = MuchaThemeMode.StarryNight) {
 *     // 业务页面
 * }
 * ```
 * 主题切换时所有纹样颜色 280ms 同步渐变（见各组件内的 animateColorAsState）。
 */
@Composable
fun MuchaThemeProvider(
    mode: MuchaThemeMode,
    content: @Composable () -> Unit
) {
    val colors = when (mode) {
        MuchaThemeMode.StarryNight -> StarryNightColors
        MuchaThemeMode.DaylightGold -> DaylightGoldColors
        MuchaThemeMode.PureMaterial3 -> PureMaterialColors
    }
    // 主题切换渐变需要 compose-animation 依赖，当前工程里拿不到，
    // 先直接切色；各组件自身的位移动画不受影响。
    val animatedColors = colors

    val config = MuchaDecorationConfig(
        theme = mode,
        colors = animatedColors,
        decorationsEnabled = mode != MuchaThemeMode.PureMaterial3,
        animationsEnabled = mode != MuchaThemeMode.PureMaterial3
    )
    val scheme: ColorScheme = when (mode) {
        MuchaThemeMode.StarryNight -> starryScheme()
        MuchaThemeMode.DaylightGold -> goldScheme()
        MuchaThemeMode.PureMaterial3 -> pureScheme(isSystemInDarkTheme())
    }

    CompositionLocalProvider(
        LocalMuchaConfig provides config,
        LocalMuchaColors provides animatedColors
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
