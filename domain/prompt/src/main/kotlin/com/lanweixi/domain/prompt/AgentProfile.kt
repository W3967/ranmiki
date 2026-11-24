package com.lanweixi.domain.prompt

/**
 * P194：工具人 agent。
 *
 * 跟兰未晞的区别：他们没有"人设"，只有**工作说明**（干什么活、输出格式）。
 * 各自可独立配 API（服务商 / BaseURL / Key / 模型）。
 */
data class AgentProfile(
    /** 稳定标识，创建后不变（写进消息的 senderId）。 */
    val id: String,
    val name: String,
    val avatarUri: String? = null,
    /** 服务商 id，见 [AgentProviders]。 */
    val provider: String = AgentProviders.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = AgentProviders.DEFAULT_BASE_URL,
    val model: String = AgentProviders.DEFAULT_MODEL,
    /** 工作说明：负责什么、输出要求。不是性格。 */
    val workPrompt: String = "",
    /** 气泡色（十六进制），用于群聊里区分。 */
    val color: String = "#7C8CF6",
) {
    /** Key 没填时点它不会回复，界面上要明确提示。 */
    val configured: Boolean get() = apiKey.isNotBlank()
}

/**
 * 预置服务商。
 *
 * P209：收录了 Anthropic（Claude）。它的 API 格式和 OpenAI 不一样，
 * 网络层按 BaseURL 是否含 `anthropic` 判断走哪套协议——所以预设里
 * 只要把 protocol 标出来给 UI 提示，不用另存配置。
 */
object AgentProviders {

    const val DEEPSEEK = "deepseek"
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-chat"

    /** P209：协议标识（与 core:network 的 ChatProtocol 对应）。 */
    const val PROTOCOL_OPENAI = "openai"
    const val PROTOCOL_ANTHROPIC = "anthropic"

    data class Preset(
        val id: String,
        val label: String,
        val baseUrl: String,
        val defaultModel: String,
        /** 去哪拿 Key，给用户一句提示。 */
        val keyHint: String,
        /** P209：接口协议；默认 OpenAI 兼容。 */
        val protocol: String = PROTOCOL_OPENAI,
    )

    val ALL: List<Preset> = listOf(
        Preset(DEEPSEEK, "DeepSeek", "https://api.deepseek.com", "deepseek-chat", "platform.deepseek.com"),
        Preset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "platform.openai.com"),
        Preset(
            "anthropic",
            "Claude",
            "https://api.anthropic.com",
            "claude-3-5-sonnet-latest",
            "console.anthropic.com",
            PROTOCOL_ANTHROPIC,
        ),
        Preset("kimi", "Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "platform.moonshot.cn"),
        Preset("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "open.bigmodel.cn"),
        Preset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "dashscope.console.aliyun.com"),
        Preset("custom", "自定义", "", "", ""),
    )

    fun byId(id: String): Preset = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** 气泡可选色。 */
    val COLORS: List<String> = listOf(
        "#7C8CF6", // 紫
        "#5BA8F5", // 蓝
        "#5FCFA0", // 绿
        "#F0A85C", // 橙
        "#E07A9B", // 粉
        "#9B8AF0", // 薰衣草
    )
}
