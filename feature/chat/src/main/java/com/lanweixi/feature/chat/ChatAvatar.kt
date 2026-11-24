package com.lanweixi.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * P199：统一的圆形头像。
 *
 * 之前聊天页 / 群聊页 / 会话列表各写了一份（`QqAvatar`、`GroupAvatar`、`ListAvatar`），
 * 逻辑完全一样——解码、失败留痕、渐变兜底——合成这一份。
 *
 * @param tint 底色；null 走主题的 primary→tertiary 渐变。
 */
@Composable
fun ChatAvatar(
    name: String,
    imageUri: String?,
    size: androidx.compose.ui.unit.Dp,
    tint: Color? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) {
        imageUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    // P127：解码失败时留痕，避免「头像不显示」只能靠猜
    // P217：日志文件有大小上限（见 appendAvatarDebugLog）
    if (imageUri != null && bitmap == null) {
        appendAvatarDebugLog(
            context.filesDir,
            System.currentTimeMillis().toString() + " decode FAILED: " + imageUri + "\n",
        )
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }

    val colors = if (tint != null) {
        listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.55f))
    } else {
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(Brush.linearGradient(colors), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** P217：头像调试日志的大小上限（超过就清空重开，避免无上限追加）。 */
internal const val AVATAR_LOG_MAX_BYTES: Long = 256L * 1024

/**
 * P127/P217：头像解码失败留痕。
 *
 * 之前直接 `appendText`，一张坏头像被反复加载就能把文件写到几十 MB。
 * 现在超过 [AVATAR_LOG_MAX_BYTES] 先删掉再写；写失败也不影响界面。
 */
internal fun appendAvatarDebugLog(filesDir: java.io.File, line: String) {
    runCatching {
        val file = java.io.File(filesDir, "avatar_debug.log")
        if (file.length() > AVATAR_LOG_MAX_BYTES) file.delete()
        file.appendText(line)
    }
}
