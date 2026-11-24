package com.lanweixi.domain.prompt

/**
 * P112：设置快照（R10 回滚的基础）。
 *
 * 每次保存设置前存一份快照，出问题时可以一键回到上一版。
 *
 * **安全取舍：快照不含 apiKey**——密钥只留在 DataStore 的加密存储里，
 * 回滚只恢复人设/模型/Base URL/角色名/开关，不会把密钥写进另一个地方。
 *
 * 注意：本模块是纯 JVM，不做 JSON 编解码；序列化由调用方（app 层）负责。
 */
data class ConfigSnapshot(
    val at: Long,
    val persona: String,
    val model: String,
    val baseUrl: String,
    val characterName: String,
    val scarEnabled: Boolean,
    val socialLlmEnabled: Boolean,
) {
    /** 显示用摘要。 */
    fun summary(): String {
        val personaShort = persona.take(18).replace("\n", " ")
        return "$model · $characterName · $personaShort"
    }
}

object ConfigSnapshots {

    const val MAX_KEPT = 10

    fun of(config: ChatConfig, at: Long): ConfigSnapshot = ConfigSnapshot(
        at = at,
        persona = config.persona,
        model = config.model,
        baseUrl = config.baseUrl,
        characterName = config.characterName,
        scarEnabled = config.scarEnabled,
        socialLlmEnabled = config.socialLlmEnabled,
    )

    /** 追加并保留最近 N 份。 */
    fun append(list: List<ConfigSnapshot>, item: ConfigSnapshot): List<ConfigSnapshot> =
        (list + item).takeLast(MAX_KEPT)
}

/** P112：快照存储接口（实现放在 app 层）。 */
interface ConfigSnapshotStore {
    fun list(): List<ConfigSnapshot>
    fun save(snapshot: ConfigSnapshot)
}
