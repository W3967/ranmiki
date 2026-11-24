package com.lanweixi.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P201：改角色名后，默认人设里的名字要跟着变；用户自己写的人设一个字不动。
 */
class CharacterNameSyncTest {

    @Test
    fun `blank persona becomes the new default`() {
        val persona = CharacterNameSync.personaAfterRename("兰未晞", "小满", "")
        assertEquals(defaultPersona("小满"), persona)
    }

    @Test
    fun `old default template is renamed`() {
        val persona = CharacterNameSync.personaAfterRename(
            oldName = "兰未晞",
            newName = "小满",
            persona = defaultPersona("兰未晞"),
        )
        assertEquals(defaultPersona("小满"), persona)
        assertTrue("新模板里应该出现新名字", persona.contains("小满"))
        assertTrue("不应该还留着旧名字", !persona.contains("兰未晞"))
    }

    @Test
    fun `built-in default template is renamed even when the current name differs`() {
        // P91 的历史数据：改过名，但人设还是最早「兰未晞」的模板
        val persona = CharacterNameSync.personaAfterRename(
            oldName = "小满",
            newName = "阿澄",
            persona = defaultPersona(DEFAULT_CHARACTER_NAME),
        )
        assertEquals(defaultPersona("阿澄"), persona)
    }

    @Test
    fun `custom persona is left untouched`() {
        val custom = "你说话像只猫，爱用感叹号，讨厌被问在干什么。"
        val persona = CharacterNameSync.personaAfterRename("兰未晞", "小满", custom)
        assertEquals("用户自己写的人设不得改动", custom, persona)
    }

    @Test
    fun `blank new name falls back to the built-in default`() {
        val persona = CharacterNameSync.personaAfterRename("小满", "   ", defaultPersona("小满"))
        assertEquals(defaultPersona(DEFAULT_CHARACTER_NAME), persona)
    }
}
