package com.lanweixi.client.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigStore
import kotlinx.coroutines.flow.first

/**
 * P216：API Key 加密失败。
 *
 * 以前失败会静默回退明文——用户以为加密存着，其实 DataStore 里是明文。
 * 现在直接抛，交给设置页提示「没有保存」，旧 Key 保持不变。
 */
class ApiKeyEncryptException(message: String) : IllegalStateException(message)

/**
 * P216：本地密文解不开（换过设备 / 清过应用数据 / Keystore 密钥丢了）。
 *
 * 关键区别：**「没有 Key」和「Key 解不开」是两回事**。
 * 前者会让用户去填一个，后者再点是「保存空 Key」会把旧密文删掉，再也找不回来。
 */
class ApiKeyDecryptException(message: String) : IllegalStateException(message)

/**
 * P08a/P23：API Key、模型名、baseUrl 的 DataStore 实现。
 *
 * - 只存偏好字段，不写 Room、不改 schema；
 * - `current()` 每次重新读取 DataStore 并**新建** ChatConfig，避免缓存旧值；
 * - 空串表示恢复默认（remove）；
 * - 不打印明文 Key（本类无任何日志）。
 */
class LocalDataStoreConfigProvider(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ApiKeyCipher? = null,
) : ChatConfigStore {

    override suspend fun current(): ChatConfig {
        val prefs = dataStore.data.first()
        val key = readApiKey(prefs[API_KEY].orEmpty())
        val baseUrl = prefs[BASE_URL].orEmpty().ifBlank { DEFAULT_BASE_URL }
        val model = prefs[MODEL].orEmpty().ifBlank { DEFAULT_MODEL }
        val characterName = prefs[CHARACTER_NAME].orEmpty().ifBlank { com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME }
        return ChatConfig(
            baseUrl = baseUrl,
            apiKey = key.value,
            apiKeyUnreadable = key.unreadable,
            persona = prefs[PERSONA].orEmpty().ifBlank {
                com.lanweixi.domain.prompt.defaultPersona(characterName)
            },
            memory = prefs[MEMORY].orEmpty(),
            model = model,
            characterName = characterName,
            scarEnabled = prefs[SCAR_ENABLED] ?: true,
            socialLlmEnabled = prefs[SOCIAL_LLM] ?: false,
            themeMode = prefs[THEME_MODE].orEmpty().ifBlank { "night_purple" },
            iconStyle = prefs[ICON_STYLE].orEmpty().ifBlank { "vector" },
            bgVeil = prefs[BG_VEIL].orEmpty().ifBlank { "0.50" },
            barAlpha = prefs[BAR_ALPHA].orEmpty().ifBlank { "0.62" },
        )
    }

    /** 保存/更新 Key；空串等同清除（设置页的空白输入走这里，不另留死方法）。 */
    override suspend fun saveMemory(memory: String) {
        putOrRemove(MEMORY, memory)
    }

    override suspend fun savePersona(persona: String) {
        putOrRemove(PERSONA, persona)
    }

    override suspend fun saveSocialLlmEnabled(enabled: Boolean) {
        dataStore.edit { it[SOCIAL_LLM] = enabled }
    }

    override suspend fun saveTuning(bgVeil: String, barAlpha: String) {
        dataStore.edit {
            it[BG_VEIL] = bgVeil
            it[BAR_ALPHA] = barAlpha
        }
    }

    override suspend fun saveIconStyle(style: String) {
        putOrRemove(ICON_STYLE, style)
    }

    override suspend fun saveThemeMode(mode: String) {
        putOrRemove(THEME_MODE, mode)
    }

    override suspend fun saveScarEnabled(enabled: Boolean) {
        dataStore.edit { it[SCAR_ENABLED] = enabled }
    }

    override suspend fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            // P216：当前若是「解不开的密文」，保存空值不能把它静默删掉——
            // 用户很可能只是在设置页看到 Key 是空的（解密失败），顺手点了保存。
            val stored = dataStore.data.first()[API_KEY].orEmpty()
            if (stored.isNotEmpty() && readApiKey(stored).unreadable) {
                throw ApiKeyDecryptException(
                    "存储的 API Key 无法解密（可能换过设备或清除过应用数据）。" +
                        "请重新粘贴一次 Key 再保存，直接保存空值会丢掉旧 Key。",
                )
            }
            putOrRemove(API_KEY, "")
            return
        }
        putOrRemove(API_KEY, encodeApiKey(trimmed))
    }

    /** P216：有 cipher 时加密存储（带 enc: 前缀）；加密失败直接抛，绝不回退明文。 */
    private fun encodeApiKey(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val encoder = cipher ?: return trimmed
        val encrypted = try {
            encoder.encrypt(trimmed)
        } catch (e: Exception) {
            throw ApiKeyEncryptException(
                "API Key 加密失败（本机安全存储不可用），没有保存。旧 Key 保持不变。",
            )
        }
        if (encrypted.isBlank()) {
            throw ApiKeyEncryptException("API Key 加密失败（结果是空的），没有保存。旧 Key 保持不变。")
        }
        return "enc:" + encrypted
    }

    /**
     * P216：兼容历史明文（无 enc: 前缀）。
     *
     * 解密失败不再无声地当成空 Key：`unreadable = true` 让 UI 提示用户重填，
     * 同时 [saveApiKey] 拒绝用空值覆盖。
     */
    private fun readApiKey(stored: String): StoredKey {
        if (stored.isEmpty()) return StoredKey("", unreadable = false)
        if (!stored.startsWith("enc:")) return StoredKey(stored, unreadable = false)
        val decoder = cipher ?: return StoredKey("", unreadable = true)
        val plain = try {
            decoder.decrypt(stored.removePrefix("enc:"))
        } catch (e: Exception) {
            return StoredKey("", unreadable = true)
        }
        if (plain.isEmpty()) return StoredKey("", unreadable = true)
        return StoredKey(plain, unreadable = false)
    }

    /** P216：读 Key 的结果；[unreadable] 区分「没配」和「解不开」。 */
    private data class StoredKey(val value: String, val unreadable: Boolean)

    override suspend fun saveCharacterName(name: String) {
        putOrRemove(CHARACTER_NAME, name)
    }

    override suspend fun saveModel(model: String) {
        putOrRemove(MODEL, model)
    }

    override suspend fun saveBaseUrl(baseUrl: String) {
        putOrRemove(BASE_URL, baseUrl)
    }

    private suspend fun putOrRemove(key: Preferences.Key<String>, value: String) {
        val trimmed = value.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(key) else prefs[key] = trimmed
        }
    }

    companion object {
        /** P157：主题档位。 */
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val BG_VEIL = stringPreferencesKey("bg_veil")
        val BAR_ALPHA = stringPreferencesKey("bar_alpha")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val CHARACTER_NAME = stringPreferencesKey("character_name")
        val SOCIAL_LLM = androidx.datastore.preferences.core.booleanPreferencesKey("social_llm_enabled")
        val PERSONA = stringPreferencesKey("persona")
        val MEMORY = stringPreferencesKey("memory")
    val SCAR_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("scar_enabled")

        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
