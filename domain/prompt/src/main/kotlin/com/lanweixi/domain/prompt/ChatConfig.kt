package com.lanweixi.domain.prompt

/**
 * P12b：对话配置端口下沉到 :domain:prompt。
 *
 * feature:chat（引擎）与 feature:settings（设置页）都要用同一份配置契约，
 * 放在 domain 层可避免两个 feature 模块互相依赖。
 */
/** P91：默认角色名字。 */
const val DEFAULT_CHARACTER_NAME = "兰未晞"

/**
 * P91/P201：默认人设模板（名字可变）。
 *
 * 放在 domain 层，让「改名同步人设」与设置页/引导页共用同一份文案，
 * 避免两处各写一遍、改一处忘另一处。
 */
fun defaultPersona(name: String): String =
    "你是${name}，一个热情、有点黏人、说话很短的 AI 助手。你会用手机和主人聊天，不会解释自己的设定。"

data class ChatConfig(
    val baseUrl: String,
    val apiKey: String,
    val persona: String,
    /** P91：角色名字（可自定义；改名字后所有显示与提示词同步）。 */
    val characterName: String = DEFAULT_CHARACTER_NAME,
    /** P192：用户导入的长期记忆（Markdown 原文），注入时加护栏并截断。 */
    val memory: String = "",
    val model: String = "deepseek-chat",
    /** P57：自我面板开关（P2 要求可一键关闭；关掉即完全退回旧 prompt）。 */
    val selfPanelEnabled: Boolean = true,
    /** P69：疤痕开关（关掉后不再留疤、不写 scar 记忆）。 */
    val scarEnabled: Boolean = true,
    /** P103：社交动态用 LLM 生成（默认关，用模板）。 */
    val socialLlmEnabled: Boolean = false,
    /** P157：慕夏主题档位（night_purple / daylight_gold / pure）。 */
    val themeMode: String = "night_purple",
    /** P174：图标风格（vector / detailed）。 */
    val iconStyle: String = "vector",
    /** P180：背景插画遮罩（0–0.85）。 */
    val bgVeil: String = "0.50",
    /** P180：顶栏/底栏/输入栏不透明度（0.1–1.0）。 */
    val barAlpha: String = "0.62",
    /**
     * P216：本地密文解不开（换设备/清过数据/Keystore 密钥丢失）。
     *
     * 为 true 时 [apiKey] 是空串，但**不能当成「没配 Key」**：
     * 设置页要提示用户重填，而且保存空值不能清掉旧密文。
     * 放在末尾是为了不破坏历史的位置参数构造。
     */
    val apiKeyUnreadable: Boolean = false,
)

/** 只读配置端口：send() 每次发送前取最新配置。 */
interface ChatConfigProvider {
    suspend fun current(): ChatConfig
}

/** 可写配置端口：只有设置层持有。 */
interface ChatConfigStore : ChatConfigProvider {
    suspend fun saveApiKey(apiKey: String)

    /** P23：保存模型名；空串表示恢复默认。 */
    suspend fun saveModel(model: String)

    /** P23：保存 baseUrl；空串表示恢复默认。 */
    suspend fun saveBaseUrl(baseUrl: String)

    /** P91：保存角色名字；空白表示恢复默认。 */
    suspend fun saveCharacterName(name: String) { /* 默认无操作 */ }

    /** P103：保存「动态用 LLM」开关。 */
    suspend fun saveSocialLlmEnabled(enabled: Boolean) { /* 默认无操作 */ }

    /** P157：保存主题档位。 */
    suspend fun saveThemeMode(mode: String) { /* 默认无操作 */ }

    /** P174：保存图标风格。 */
    suspend fun saveIconStyle(style: String) { /* 默认无操作 */ }

    /** P180：保存遮罩参数。 */
    suspend fun saveTuning(bgVeil: String, barAlpha: String) { /* 默认无操作 */ }

    /** P112：保存人设（回滚用）。 */
    suspend fun savePersona(persona: String) { /* 默认无操作 */ }

    /** P192：保存记忆原文。 */
    suspend fun saveMemory(memory: String) { /* 默认无操作 */ }

    /** P70：疤痕开关（默认实现；不支持的存储可忽略）。 */
    suspend fun saveScarEnabled(enabled: Boolean) { /* 默认无操作 */ }
}
