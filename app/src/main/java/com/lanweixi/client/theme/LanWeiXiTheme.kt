package com.lanweixi.client.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * P50 / P54：QQ 风格主题（浅色 + 深色，跟随系统）。
 *
 * 浅色：背景 #F2F3F5、白卡片、QQ 蓝 #12B7F5
 * 深色：背景 #121212、卡片 #1E1E1E、同样 QQ 蓝强调色
 *
 * 业务色（气泡/卡片/顶栏）一律走 MaterialTheme.colorScheme，深色自动跟随；
 * 只有品牌渐变（头像）在 QqPalette 里固定。
 */
private val QqLightColors = lightColorScheme(
    primary = Color(0xFF12B7F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F6FE),
    onPrimaryContainer = Color(0xFF005C7A),
    secondary = Color(0xFF00A0E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F7FE),
    onSecondaryContainer = Color(0xFF005C7A),
    tertiary = Color(0xFF7C5CFF),
    onTertiary = Color.White,
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF7F8FA),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceTint = Color.White,
    outline = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFEDEDED),
    error = Color(0xFFE54D42),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF8A1F17),
)

private val QqDarkColors = darkColorScheme(
    primary = Color(0xFF3EC6FF),
    onPrimary = Color(0xFF00222E),
    primaryContainer = Color(0xFF0A3A4A),
    onPrimaryContainer = Color(0xFFB8E9FF),
    secondary = Color(0xFF5FD3FF),
    onSecondary = Color(0xFF00222E),
    secondaryContainer = Color(0xFF0A3A4A),
    onSecondaryContainer = Color(0xFFB8E9FF),
    tertiary = Color(0xFF9B84FF),
    onTertiary = Color(0xFF1A0F3D),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceTint = Color(0xFF1E1E1E),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0906),
    errorContainer = Color(0xFF4A1210),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val QqShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val QqTypography = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun LanWeiXiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) QqDarkColors else QqLightColors,
        shapes = QqShapes,
        typography = QqTypography,
        content = content,
    )
}
