package com.lanweixi.feature.onboarding

import androidx.lifecycle.ViewModel
import com.lanweixi.core.network.ProbeResult
import com.lanweixi.domain.prompt.AgentProviders
import com.lanweixi.domain.prompt.CharacterNameSync
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P201：引导页状态。
 *
 * 两屏：0 = 起名字，1 = 选服务商 / 填 Key / 测试。
 */
data class OnboardingUiState(
    val step: Int = 0,
    val loading: Boolean = true,
    /** 从设置里「重看引导」进来：预填现有配置、可返回。 */
    val replay: Boolean = false,
    val characterName: String = DEFAULT_CHARACTER_NAME,
    val provider: String = AgentProviders.DEEPSEEK,
    val baseUrl: String = AgentProviders.DEFAULT_BASE_URL,
    val model: String = AgentProviders.DEFAULT_MODEL,
    val apiKey: String = "",
    val testing: Boolean = false,
    val probe: ProbeResult? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** P201：输入法猜词造成的中文/全角字符。 */
    val keyHasBadChars: Boolean
        get() = apiKey.trim().any { it.code !in 33..126 }

    val canFinish: Boolean
        get() = apiKey.isNotBlank() && !keyHasBadChars && !saving
}

/**
 * P201：首次启动引导。
 *
 * 依赖两个端口：配置存储（[ChatConfigStore]）和连通性探针（函数注入，
 * 这样 JVM 单测不用起网络）。保存规则与设置页一致——名字/人设走
 * [CharacterNameSync]，Key 走非 ASCII 拦截。
 */
class OnboardingViewModel(
    private val store: ChatConfigStore,
    private val probe: suspend (baseUrl: String, apiKey: String, model: String) -> ProbeResult,
    private val scope: CoroutineScope,
    private val replay: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(replay = replay))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** 进页面时的配置，用来判断「默认人设要不要跟着改名」。 */
    private var loaded: ChatConfig? = null

    init {
        scope.launch {
            val config = runCatching { store.current() }.getOrNull()
                ?: ChatConfig(
                    baseUrl = AgentProviders.DEFAULT_BASE_URL,
                    apiKey = "",
                    persona = "",
                )
            loaded = config
            val preset = AgentProviders.ALL.firstOrNull {
                it.id != "custom" && it.baseUrl.isNotBlank() && it.baseUrl == config.baseUrl
            }
            _state.update {
                it.copy(
                    loading = false,
                    characterName = config.characterName,
                    provider = preset?.id ?: "custom",
                    baseUrl = config.baseUrl.ifBlank { AgentProviders.DEFAULT_BASE_URL },
                    model = config.model.ifBlank { AgentProviders.DEFAULT_MODEL },
                    // 重看引导时把现有 Key 带出来，用户能直接「测一下」；全新流程不预填
                    apiKey = if (replay) config.apiKey else "",
                )
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    fun onNameChange(text: String) {
        _state.update { it.copy(characterName = text, error = null) }
    }

    fun next() {
        _state.update { it.copy(step = 1, error = null) }
    }

    fun back() {
        _state.update { it.copy(step = 0, error = null) }
    }

    /** 选服务商：自动填 BaseURL / 模型（「自定义」则保留用户已填的）。 */
    fun onProviderSelect(id: String) {
        val preset = AgentProviders.byId(id)
        _state.update {
            it.copy(
                provider = id,
                baseUrl = if (id == "custom") it.baseUrl else preset.baseUrl,
                model = if (id == "custom") it.model else preset.defaultModel,
                probe = null,
                error = null,
            )
        }
    }

    fun onBaseUrlChange(text: String) {
        _state.update {
            it.copy(baseUrl = text, provider = "custom", probe = null, error = null)
        }
    }

    fun onModelChange(text: String) {
        _state.update { it.copy(model = text, probe = null, error = null) }
    }

    fun onKeyChange(text: String) {
        _state.update { it.copy(apiKey = text, probe = null, error = null) }
    }

    /** P201：真发一次请求，结果原样交给 UI 展示。 */
    fun testConnection() {
        val current = _state.value
        if (current.testing) return
        if (current.apiKey.isBlank()) {
            _state.update { it.copy(error = "先填 API Key 再测") }
            return
        }
        if (current.keyHasBadChars) {
            _state.update { it.copy(probe = ProbeResult.InvalidKeyChars, error = null) }
            return
        }
        _state.update { it.copy(testing = true, probe = null, error = null) }
        scope.launch {
            val result = runCatching { probe(current.baseUrl, current.apiKey, current.model) }
                .getOrElse { ProbeResult.Network(it.message ?: "测试失败") }
            _state.update { it.copy(testing = false, probe = result) }
        }
    }

    /** 完成：保存名字、人设、Key、地址、模型，然后回调。 */
    fun complete(onDone: (String) -> Unit) {
        val current = _state.value
        if (current.saving) return
        if (current.apiKey.isBlank()) {
            _state.update { it.copy(error = "Key 还没填，可以点「先跳过」") }
            return
        }
        if (current.keyHasBadChars) {
            _state.update {
                it.copy(error = "API Key 里有中文或全角字符，复制粘贴重填一次")
            }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            val result = runCatching {
                val name = saveIdentity(current.characterName)
                store.saveApiKey(current.apiKey)
                store.saveBaseUrl(current.baseUrl)
                store.saveModel(current.model)
                name
            }
            result.onSuccess { name ->
                _state.update { it.copy(saving = false) }
                onDone(name)
            }.onFailure {
                _state.update { it.copy(saving = false, error = "保存失败，再试一次") }
            }
        }
    }

    /**
     * 跳过配置：只保存名字（用户可能已经改了名字）。
     * 不写 Key，进应用后发消息会明确提示去设置里补。
     */
    fun skip(onDone: (String) -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            val result = runCatching { saveIdentity(_state.value.characterName) }
            result.onSuccess { name ->
                _state.update { it.copy(saving = false) }
                onDone(name)
            }.onFailure {
                _state.update { it.copy(saving = false, error = "保存失败，再试一次") }
            }
        }
    }

    private suspend fun saveIdentity(rawName: String): String {
        val name = rawName.trim().ifBlank { DEFAULT_CHARACTER_NAME }
        val old = loaded
        val persona = if (old == null) {
            com.lanweixi.domain.prompt.defaultPersona(name)
        } else {
            CharacterNameSync.personaAfterRename(old.characterName, name, old.persona)
        }
        store.saveCharacterName(name)
        store.savePersona(persona)
        return name
    }
}
