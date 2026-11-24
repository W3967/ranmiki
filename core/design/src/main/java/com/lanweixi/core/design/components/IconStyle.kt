package com.lanweixi.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.lanweixi.core.design.R
import com.lanweixi.core.design.mucha.MuchaTheme
import com.lanweixi.core.design.mucha.MuchaThemeMode

/**
 * P174：图标风格。
 *
 * 同一套图标提供两个版本：
 * - [VECTOR]：纤细矢量版，单色，跟主题 tint 走
 * - [DETAILED]：多密度位图版，保留原色，因此星夜 / 日光金各有一套
 */
enum class IconStyle { VECTOR, DETAILED }

val LocalIconStyle = compositionLocalOf { IconStyle.VECTOR }

/** 底部导航的四个入口。 */
enum class MuchaNavIcon { CHAT, NOTE, TASK, MORE }

/** 「更多」页的入口。P199：加 SEARCH。 */
enum class MuchaMoreIcon { SEARCH, PROMPT, DOC, REVIEW, PLUGIN, SETTINGS }

/** 当前是不是日光金主题（决定精细版取金色还是紫色那套）。 */
@Composable
private fun isGoldTheme(): Boolean =
    MuchaTheme.config.theme == MuchaThemeMode.DaylightGold

@Composable
fun resolveNavIcon(icon: MuchaNavIcon): Int {
    if (LocalIconStyle.current != IconStyle.DETAILED) {
        return when (icon) {
            MuchaNavIcon.CHAT -> R.drawable.ic_chat
            MuchaNavIcon.NOTE -> R.drawable.ic_note
            MuchaNavIcon.TASK -> R.drawable.ic_task
            MuchaNavIcon.MORE -> R.drawable.ic_more
        }
    }
    val gold = isGoldTheme()
    return when (icon) {
        MuchaNavIcon.CHAT -> if (gold) R.drawable.ic_chat_detail_gold else R.drawable.ic_chat_detail
        MuchaNavIcon.NOTE -> if (gold) R.drawable.ic_note_detail_gold else R.drawable.ic_note_detail
        MuchaNavIcon.TASK -> if (gold) R.drawable.ic_task_detail_gold else R.drawable.ic_task_detail
        MuchaNavIcon.MORE -> if (gold) R.drawable.ic_more_detail_gold else R.drawable.ic_more_detail
    }
}

@Composable
fun resolveMoreIcon(icon: MuchaMoreIcon): Int {
    if (LocalIconStyle.current != IconStyle.DETAILED) {
        return when (icon) {
            MuchaMoreIcon.SEARCH -> R.drawable.ic_search
            MuchaMoreIcon.PROMPT -> R.drawable.ic_prompt
            MuchaMoreIcon.DOC -> R.drawable.ic_doc
            MuchaMoreIcon.REVIEW -> R.drawable.ic_review
            MuchaMoreIcon.PLUGIN -> R.drawable.ic_plugin
            MuchaMoreIcon.SETTINGS -> R.drawable.ic_settings
        }
    }
    val gold = isGoldTheme()
    return when (icon) {
        // P199：搜索暂时只有矢量版，精细模式下也用它（没有配套位图）
        MuchaMoreIcon.SEARCH -> R.drawable.ic_search
        MuchaMoreIcon.PROMPT -> if (gold) R.drawable.ic_prompt_detail_gold else R.drawable.ic_prompt_detail
        MuchaMoreIcon.DOC -> if (gold) R.drawable.ic_doc_detail_gold else R.drawable.ic_doc_detail
        MuchaMoreIcon.REVIEW -> if (gold) R.drawable.ic_review_detail_gold else R.drawable.ic_review_detail
        MuchaMoreIcon.PLUGIN -> if (gold) R.drawable.ic_plugin_detail_gold else R.drawable.ic_plugin_detail
        MuchaMoreIcon.SETTINGS -> if (gold) R.drawable.ic_settings_detail_gold else R.drawable.ic_settings_detail
    }
}

/**
 * 按当前风格渲染图标。
 *
 * 矢量版走 [Icon]（单色 + tint，跟主题走）；
 * 精细版是**彩色插画**，必须走 [Image] 保留原色——用 Icon 会被 tint 成纯色块。
 */
@Composable
fun MuchaIcon(
    iconRes: Int,
    contentDescription: String?,
    tint: androidx.compose.ui.graphics.Color,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val detailed = LocalIconStyle.current == IconStyle.DETAILED
    if (detailed) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
    }
}
