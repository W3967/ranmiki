package com.lanweixi.domain.prompt

/**
 * P201：改角色名后，默认人设里的名字要跟着变。
 *
 * 背景：P91 加了 `characterName`，但设置页保存时会把「当时的默认人设」持久化，
 * 之后再改名，提示词里还是旧名字——用户只看到「改名没生效」。
 * 这里定一条明确规则：
 *
 * - 人设空白 → 换成新名字的默认模板；
 * - 人设仍是默认模板（旧名字的，或内置「兰未晞」的）→ 换成新名字的模板；
 * - 用户自己写过的人设 → 一个字都不动。
 */
object CharacterNameSync {

    fun personaAfterRename(oldName: String, newName: String, persona: String): String {
        val current = persona.trim()
        val target = newName.trim().ifBlank { DEFAULT_CHARACTER_NAME }
        val old = oldName.trim().ifBlank { DEFAULT_CHARACTER_NAME }
        val stillDefault = current.isEmpty() ||
            current == defaultPersona(old) ||
            current == defaultPersona(DEFAULT_CHARACTER_NAME)
        return if (stillDefault) defaultPersona(target) else persona
    }
}
