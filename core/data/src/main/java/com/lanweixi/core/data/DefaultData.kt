package com.lanweixi.core.data

import com.lanweixi.core.data.entity.CharacterEntity

/**
 * 方案 5.2：空库首启写入默认角色配置，不依赖旧数据兜底。
 * 默认生理状态由 :domain:vitals 在 T04 提供，数据层只负责持久化。
 */
object DefaultData {
    const val DEFAULT_CHAR_ID = "default"
    const val DEFAULT_CHAR_NAME = "兰未晞"
    const val DEFAULT_STAGE = "初识"

    fun defaultCharacter(): CharacterEntity = CharacterEntity(
        id = DEFAULT_CHAR_ID,
        name = DEFAULT_CHAR_NAME,
        affection = 50,
        trust = 50,
        stage = DEFAULT_STAGE,
        modelId = null,
        avatar = null,
        voiceId = null,
    )
}
