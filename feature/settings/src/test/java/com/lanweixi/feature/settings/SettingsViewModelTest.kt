package com.lanweixi.feature.settings

import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P08b：设置 ViewModel 单测（JVM，无 Compose 组件测试）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeStore : ChatConfigStore {
        var config = ChatConfig("http://127.0.0.1/mock", "", "你是兰未晞。")
        val saved = mutableListOf<String>()

        override suspend fun current(): ChatConfig = config

    
    override suspend fun saveModel(model: String) {
        config = config.copy(model = model.ifBlank { "deepseek-chat" })
    }

    override suspend fun saveBaseUrl(baseUrl: String) {
        config = config.copy(baseUrl = baseUrl.ifBlank { "https://api.deepseek.com" })
    }

    override suspend fun saveApiKey(apiKey: String) {
            saved += apiKey
            config = config.copy(apiKey = apiKey.trim())
        }

        override suspend fun saveCharacterName(name: String) {
            config = config.copy(characterName = name.trim().ifBlank { "兰未晞" })
        }

        override suspend fun savePersona(persona: String) {
            config = config.copy(persona = persona)
        }

        override suspend fun saveMemory(memory: String) {
            config = config.copy(memory = memory)
        }
    }

    @Test
    fun `init loads existing key`() = runTest {
        val store = FakeStore().apply { config = config.copy(apiKey = "sk-existing") }
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        assertEquals("sk-existing", vm.state.value.apiKey)
    }

    @Test
    fun `save persists and invokes callback`() = runTest {
        val store = FakeStore()
        var returned = false
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        vm.onKeyChange("  sk-new  ")
        vm.save { returned = true }

        assertEquals(listOf("  sk-new  "), store.saved)
        assertTrue(vm.state.value.saved)
        assertTrue("保存成功应回调返回聊天", returned)
        assertEquals("sk-new", store.current().apiKey)
    }

    @Test
    fun `editing after save resets saved flag`() = runTest {
        val store = FakeStore()
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        vm.onKeyChange("sk-1")
        vm.save { }
        assertTrue(vm.state.value.saved)

        vm.onKeyChange("sk-2")
        assertTrue("再次编辑应重置已保存标记", !vm.state.value.saved)
    }

    @Test
    fun `blank key clears stored value`() = runTest {
        val store = FakeStore().apply { config = config.copy(apiKey = "sk-old") }
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        vm.onKeyChange("   ")
        vm.save { }
        assertEquals("", store.current().apiKey)
    }

    @Test
    fun `save failure keeps state and does not call back`() = runTest {
        val failingStore = object : ChatConfigStore {
            override suspend fun current(): ChatConfig =
                ChatConfig("http://127.0.0.1/mock", "", "你是兰未晞。")

            override suspend fun saveModel(model: String) = Unit
        override suspend fun saveBaseUrl(baseUrl: String) = Unit
        override suspend fun saveApiKey(apiKey: String) {
                throw RuntimeException("disk full")
            }
        }
        var returned = false
        val vm = SettingsViewModel(failingStore, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        vm.onKeyChange("sk-x")
        vm.save { returned = true }

        assertFalse("保存失败不得回调返回", returned)
        assertFalse("saving 不应残留", vm.state.value.saving)
        assertFalse("saved 不成立", vm.state.value.saved)
        // P216：异常消息要原样展示（加密失败、密文解不开这类原因才能指导用户）
        assertEquals("disk full", vm.state.value.error)

        // 重新编辑应清掉上一次错误
        vm.onKeyChange("sk-y")
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `save failure without message falls back to generic text`() = runTest {
        val failingStore = object : ChatConfigStore {
            override suspend fun current(): ChatConfig =
                ChatConfig("http://127.0.0.1/mock", "", "你是兰未晞。")

            override suspend fun saveModel(model: String) = Unit
            override suspend fun saveBaseUrl(baseUrl: String) = Unit
            override suspend fun saveApiKey(apiKey: String) {
                throw RuntimeException()
            }
        }
        val vm = SettingsViewModel(failingStore, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        vm.onKeyChange("sk-x")
        vm.save { }

        assertEquals("保存失败，请重试", vm.state.value.error)
    }

    // ── P201：改名同步人设 / 非法 Key 拦截 ──

    @Test
    fun `renaming the character updates the default persona`() = runTest {
        val store = FakeStore().apply {
            config = config.copy(
                characterName = "兰未晞",
                persona = com.lanweixi.domain.prompt.defaultPersona("兰未晞"),
            )
        }
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        vm.onCharacterNameChange("小满")
        vm.save { }

        assertEquals("小满", store.current().characterName)
        assertTrue(
            "默认人设里的名字应跟着改，实际：${store.current().persona}",
            store.current().persona.contains("小满"),
        )
        assertFalse("旧名字不该留在人设里", store.current().persona.contains("兰未晞"))
    }

    @Test
    fun `custom persona is not overwritten by renaming`() = runTest {
        val custom = "你说话像只猫，爱用感叹号。"
        val store = FakeStore().apply {
            config = config.copy(characterName = "兰未晞", persona = custom)
        }
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        vm.onCharacterNameChange("小满")
        vm.save { }

        assertEquals("用户自己写的人设不得被覆盖", custom, store.current().persona)
    }

    @Test
    fun `save rejects api key with full-width chars`() = runTest {
        val store = FakeStore()
        var returned = false
        val vm = SettingsViewModel(store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        // 输入法把 sk-test 写成「sk－test」：全角连字符
        vm.onKeyChange("sk－test")
        vm.save { returned = true }

        assertFalse("非法 Key 不应保存并返回", returned)
        assertEquals("非法 Key 不应落盘", emptyList<String>(), store.saved)
        assertTrue(
            "要提示用户重填，实际：${vm.state.value.error}",
            vm.state.value.error?.contains("全角") == true,
        )
    }
}
