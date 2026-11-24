package com.lanweixi.feature.onboarding

import com.lanweixi.core.network.ProbeResult
import com.lanweixi.domain.prompt.AgentProviders
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P201：引导 ViewModel（JVM，探针与存储都是假实现）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeStore(initial: ChatConfig) : ChatConfigStore {
        var config = initial
        var savedApiKey: String? = null

        override suspend fun current(): ChatConfig = config

        override suspend fun saveApiKey(apiKey: String) {
            savedApiKey = apiKey.trim()
            config = config.copy(apiKey = apiKey.trim())
        }

        override suspend fun saveModel(model: String) {
            config = config.copy(model = model.ifBlank { "deepseek-chat" })
        }

        override suspend fun saveBaseUrl(baseUrl: String) {
            config = config.copy(baseUrl = baseUrl.ifBlank { "https://api.deepseek.com" })
        }

        override suspend fun saveCharacterName(name: String) {
            config = config.copy(characterName = name)
        }

        override suspend fun savePersona(persona: String) {
            config = config.copy(persona = persona)
        }
    }

    private fun freshConfig() = ChatConfig(
        baseUrl = AgentProviders.DEFAULT_BASE_URL,
        apiKey = "",
        persona = "",
        model = AgentProviders.DEFAULT_MODEL,
        characterName = DEFAULT_CHARACTER_NAME,
    )

    private fun vm(
        store: FakeStore,
        replay: Boolean = false,
        probe: suspend (String, String, String) -> ProbeResult = { _, _, _ -> ProbeResult.Ok("HTTP 200") },
        scope: CoroutineScope,
    ) = OnboardingViewModel(store, probe, scope, replay)

    @Test
    fun `fresh install prefills the built-in defaults`() = runTest {
        val store = FakeStore(freshConfig())
        val viewModel = vm(store, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        assertEquals(DEFAULT_CHARACTER_NAME, viewModel.state.value.characterName)
        assertEquals(AgentProviders.DEEPSEEK, viewModel.state.value.provider)
        assertEquals(AgentProviders.DEFAULT_MODEL, viewModel.state.value.model)
        assertEquals("", viewModel.state.value.apiKey)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `replay prefills the saved config including the key`() = runTest {
        val store = FakeStore(
            freshConfig().copy(
                apiKey = "sk-keep",
                characterName = "小满",
                baseUrl = "https://api.moonshot.cn/v1",
            ),
        )
        val viewModel = vm(store, replay = true, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        assertEquals("sk-keep", viewModel.state.value.apiKey)
        assertEquals("小满", viewModel.state.value.characterName)
        assertEquals("kimi", viewModel.state.value.provider)
        assertTrue(viewModel.state.value.replay)
    }

    @Test
    fun `picking a provider fills its base url and model`() = runTest {
        val viewModel = vm(FakeStore(freshConfig()), scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.onProviderSelect("openai")

        assertEquals("openai", viewModel.state.value.provider)
        assertEquals("https://api.openai.com/v1", viewModel.state.value.baseUrl)
        assertEquals("gpt-4o-mini", viewModel.state.value.model)
    }

    @Test
    fun `test connection reports the probe result`() = runTest {
        var calls = 0
        val viewModel = vm(
            FakeStore(freshConfig()),
            probe = { _, _, _ ->
                calls++
                ProbeResult.InvalidKey("Invalid API key provided")
            },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        viewModel.onKeyChange("sk-test")
        viewModel.testConnection()

        assertEquals(1, calls)
        assertEquals(ProbeResult.InvalidKey("Invalid API key provided"), viewModel.state.value.probe)
        assertFalse(viewModel.state.value.testing)
    }

    @Test
    fun `test connection refuses an empty key without calling the network`() = runTest {
        var calls = 0
        val viewModel = vm(
            FakeStore(freshConfig()),
            probe = { _, _, _ ->
                calls++
                ProbeResult.Ok("")
            },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        viewModel.testConnection()

        assertEquals("空 Key 不该发请求", 0, calls)
        assertTrue(viewModel.state.value.error?.contains("Key") == true)
    }

    @Test
    fun `complete saves identity and connection settings`() = runTest {
        val store = FakeStore(freshConfig())
        val viewModel = vm(store, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        var doneName: String? = null

        viewModel.onNameChange("小满")
        viewModel.onKeyChange("sk-new")
        viewModel.onBaseUrlChange("https://api.example.com/v1")
        viewModel.onModelChange("my-model")
        viewModel.complete { doneName = it }

        assertEquals("小满", doneName)
        assertEquals("小满", store.config.characterName)
        assertTrue(
            "默认人设里的名字要跟着改，实际：${store.config.persona}",
            store.config.persona.contains("小满"),
        )
        assertEquals("sk-new", store.config.apiKey)
        assertEquals("https://api.example.com/v1", store.config.baseUrl)
        assertEquals("my-model", store.config.model)
    }

    @Test
    fun `complete refuses a full-width key and saves nothing`() = runTest {
        val store = FakeStore(freshConfig())
        val viewModel = vm(store, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        var called = false

        // 输入法把 sk-test 写成 sk－test
        viewModel.onKeyChange("sk－test")
        viewModel.complete { called = true }

        assertFalse("非法 Key 不应完成", called)
        assertNull("非法 Key 不应落盘", store.savedApiKey)
        assertTrue(viewModel.state.value.error?.contains("全角") == true)
    }

    @Test
    fun `skip saves the renamed character but not the key`() = runTest {
        val store = FakeStore(freshConfig())
        val viewModel = vm(store, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        var doneName: String? = null

        viewModel.onNameChange("阿澄")
        viewModel.onKeyChange("sk-half-typed")
        viewModel.skip { doneName = it }

        assertEquals("阿澄", doneName)
        assertEquals("阿澄", store.config.characterName)
        assertNull("跳过不应保存 Key", store.savedApiKey)
        assertEquals("", store.config.apiKey)
    }

    @Test
    fun `custom persona is preserved when the name changes`() = runTest {
        val custom = "你说话像只猫，爱用感叹号。"
        val store = FakeStore(freshConfig().copy(persona = custom))
        val viewModel = vm(store, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        viewModel.onNameChange("小满")
        viewModel.complete { }

        assertEquals("用户自己写的人设不得被覆盖", custom, store.config.persona)
    }
}
