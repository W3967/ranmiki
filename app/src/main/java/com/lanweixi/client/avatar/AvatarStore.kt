package com.lanweixi.client.avatar

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 头像三件套：角色头像 / 我的头像 / 角色立绘（小屋用）。 */
data class AvatarState(
    val characterUri: String? = null,
    val userUri: String? = null,
    /** P126：角色立绘（全身/半身，PNG 透明背景），「她的小屋」里站着的形象。 */
    val characterSpriteUri: String? = null,
)

/**
 * P75/P126：头像自定义（角色 / 用户 / 立绘）。
 *
 * 只存图片 URI（用户通过系统文件选择器授权），不复制图片、不占应用空间。
 * 读取时用 contentResolver 解码。
 *
 * **P126 改造**：从「每次读 SharedPreferences」改成 `StateFlow`——
 * 设置页改头像后，聊天页和「她的小屋」会**立刻**跟着变，不用重启。
 */
class AvatarStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("avatars", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AvatarState> = _state.asStateFlow()

    /** 兼容旧调用：读当前快照。 */
    fun snapshot(): AvatarState = _state.value

    fun characterUri(): String? = _state.value.characterUri
    fun userUri(): String? = _state.value.userUri
    fun characterSpriteUri(): String? = _state.value.characterSpriteUri

    fun setCharacter(uri: String?) {
        put(KEY_CHARACTER, uri)
        _state.value = _state.value.copy(characterUri = uri?.takeIf { it.isNotBlank() })
    }

    fun setUser(uri: String?) {
        put(KEY_USER, uri)
        _state.value = _state.value.copy(userUri = uri?.takeIf { it.isNotBlank() })
    }

    /** P126：设置角色立绘。 */
    fun setCharacterSprite(uri: String?) {
        put(KEY_SPRITE, uri)
        _state.value = _state.value.copy(characterSpriteUri = uri?.takeIf { it.isNotBlank() })
    }

    /** 清除（恢复默认渐变头像）。 */
    fun clear(kind: String) {
        put(kind, null)
        _state.value = when (kind) {
            KEY_SPRITE -> _state.value.copy(characterSpriteUri = null)
            KEY_USER -> _state.value.copy(userUri = null)
            else -> _state.value.copy(characterUri = null)
        }
    }

    private fun load(): AvatarState = AvatarState(
        characterUri = prefs.getString(KEY_CHARACTER, null),
        userUri = prefs.getString(KEY_USER, null),
        characterSpriteUri = prefs.getString(KEY_SPRITE, null),
    )

    private fun put(key: String, uri: String?) {
        prefs.edit().apply {
            if (uri.isNullOrBlank()) remove(key) else putString(key, uri)
        }.apply()
    }

    companion object {
        const val KEY_CHARACTER = "character"
        const val KEY_USER = "user"
        const val KEY_SPRITE = "character_sprite"

        /** 授权持久读取（否则重启后 URI 失效）。 */
        fun takePersistable(context: Context, uri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
