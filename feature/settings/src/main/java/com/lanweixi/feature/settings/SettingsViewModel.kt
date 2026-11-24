package com.lanweixi.feature.settings

import androidx.lifecycle.ViewModel
import com.lanweixi.domain.prompt.ChatConfigStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** P70：疤痕开关（关掉后不再留疤、不写 scar 记忆）。 */
    /** P91：角色名字（可自定义）。 */
    val characterName: String = "兰未晞",
    /** P192：她是什么样的人（人设提示词）。 */
    val persona: String = "",
    /** P192：导入的长期记忆原文。 */
    val memory: String = "",
    /** P157：慕夏主题档位。 */
    val themeMode: String = "night_purple",
    /** P174：图标风格。 */
    val iconStyle: String = "vector",
    /** P180：背景遮罩强度。 */
    val bgVeil: Float = 0.50f,
    /** P180：顶/底栏不透明度。 */
    val barAlpha: Float = 0.62f,
    /** P103：动态用 LLM 生成（默认关）。 */
    /** P105：盲评当前轮次。 */
    /** P105：盲评结果文案。 */
    /** P110：体检结果。 */
    /** P111：评估历史（盲评/体检）。 */
    /** P112：可回滚的快照列表。 */
    val snapshots: List<com.lanweixi.domain.prompt.ConfigSnapshot> = emptyList(),
    /** P112：回滚结果提示。 */
    val rollbackMessage: String? = null,
    /** P99：基线访谈答案（问题 -> 回答）。 */
    /** P99：基线访谈已回答数量。 */
)

/**
 * P08b/P20：设置页 ViewModel。
 * 只依赖 ChatConfigStore；不碰 Room/DAO，也不接触 ConversationEngine。
 */
class SettingsViewModel(
    private val store: ChatConfigStore,
    private val scope: CoroutineScope,
    /** P93：导入解压的 IO 调度器（测试可注入）。 */
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    /** P99：基线访谈存储。 */
    /** P99：写入访谈记忆（可选）。 */
    /** P111：评估历史。 */
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** P201：加载时的角色名，保存时用来判断「默认人设要不要跟着改名」。 */
    private var loadedCharacterName: String = com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME

    init {
        scope.launch {
            val config = store.current()
            loadedCharacterName = config.characterName
            _state.update {
                it.copy(
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    characterName = config.characterName,
                    persona = config.persona,
                    memory = config.memory,
                    themeMode = config.themeMode,
                    iconStyle = config.iconStyle,
                    bgVeil = config.bgVeil.toFloatOrNull() ?: 0.50f,
                    barAlpha = config.barAlpha.toFloatOrNull() ?: 0.62f,
                    // P216：密文解不开时别装作「没配 Key」——直接让用户重填，
                    // 同时下面的 save() 会拒绝用空值覆盖旧密文。
                    error = if (config.apiKeyUnreadable) {
                        "本机保存的 API Key 解不开（换过设备或清过应用数据），请重新粘贴一次 Key 再保存"
                    } else {
                        null
                    },
                )
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    /** P192：编辑人设。 */
    fun onPersonaChange(text: String) {
        _state.update { it.copy(persona = text, saved = false) }
    }

    /** P193：编辑记忆后立即落盘，不依赖「保存并返回」。 */
    fun saveMemoryNow(text: String, onDone: () -> Unit = {}) {
        _state.update { it.copy(memory = text.take(20000)) }
        scope.launch {
            runCatching { store.saveMemory(text.take(20000)) }
            onDone()
        }
    }

    /** P192：导入记忆（传空串等于清空）。 */
    fun onMemoryChange(text: String) {
        _state.update { it.copy(memory = text.take(20000), saved = false) }
    }

    fun onKeyChange(text: String) {
        // 用户重新编辑时，清掉上一次的保存结果与错误文案
        _state.update { it.copy(apiKey = text, saved = false, error = null) }
    }

    /**
     * P211：选服务商预设。
     *
     * 只把 BaseURL / 模型填进表单（Key 是用户自己的，不动）；
     * 点「保存并返回」才真正生效——和这个页面其他字段的节奏一致。
     */
    fun onProviderSelect(id: String) {
        val preset = com.lanweixi.domain.prompt.AgentProviders.byId(id)
        if (preset.id == "custom" || preset.baseUrl.isBlank()) return
        _state.update {
            it.copy(baseUrl = preset.baseUrl, model = preset.defaultModel, saved = false, error = null)
        }
    }

    /** P112：回滚到某个快照（不恢复 API Key）。 */
    fun rollback(snapshot: com.lanweixi.domain.prompt.ConfigSnapshot) {
        scope.launch {
            runCatching {
                store.savePersona(snapshot.persona)
                store.saveModel(snapshot.model)
                store.saveBaseUrl(snapshot.baseUrl)
                store.saveCharacterName(snapshot.characterName)
            }
            _state.update {
                it.copy(
                    model = snapshot.model,
                    baseUrl = snapshot.baseUrl,
                    characterName = snapshot.characterName,
                    rollbackMessage = "已回到 " + java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        .format(java.time.Instant.ofEpochMilli(snapshot.at).atZone(java.time.ZoneId.systemDefault())) +
                        " 的设置（API Key 未变动）",
                )
            }
        }
    }

    /** P112：关闭回滚对话框。 */
    fun closeRollback() {
        _state.update { it.copy(snapshots = emptyList(), rollbackMessage = null) }
    }

    fun onCharacterNameChange(text: String) {
        _state.update { it.copy(characterName = text, saved = false, error = null) }
    }

    /** P157：切主题——立即落盘并回调，外层立刻换色。 */
    fun onThemeChange(mode: String, onApplied: () -> Unit) {
        _state.update { it.copy(themeMode = mode) }
        scope.launch {
            runCatching { store.saveThemeMode(mode) }
            onApplied()
        }
    }

    /** P174：切图标风格，立即落盘并回调。 */
    fun onIconStyleChange(style: String, onApplied: () -> Unit) {
        _state.update { it.copy(iconStyle = style) }
        scope.launch {
            runCatching { store.saveIconStyle(style) }
            onApplied()
        }
    }

    /** P180：拖滑块时即时更新，松手再存。 */
    fun onBgVeilChange(value: Float, persist: Boolean) {
        _state.update { it.copy(bgVeil = value) }
        if (persist) {
            scope.launch {
                runCatching { store.saveTuning(value.toString(), _state.value.barAlpha.toString()) }
            }
        }
    }

    fun onBarAlphaChange(value: Float, persist: Boolean) {
        _state.update { it.copy(barAlpha = value) }
        if (persist) {
            scope.launch {
                runCatching { store.saveTuning(_state.value.bgVeil.toString(), value.toString()) }
            }
        }
    }

    fun onModelChange(text: String) {
        _state.update { it.copy(model = text, saved = false, error = null) }
    }

    fun onBaseUrlChange(text: String) {
        _state.update { it.copy(baseUrl = text, saved = false, error = null) }
    }

    fun save(onSaved: () -> Unit) {
        if (_state.value.saving) return
        // P201：Key 里混进中文/全角字符时本机就拦住，别等 OkHttp 抛异常。
        // 先 trim：首尾空格不算非法，空串代表清除 Key（合法）。
        if (_state.value.apiKey.trim().any { it.code !in 33..126 }) {
            _state.update {
                it.copy(error = "API Key 里有中文或全角字符（输入法猜词造成的），复制粘贴重填一次")
            }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            val newName = _state.value.characterName.trim()
                .ifBlank { com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME }
            // P201：改角色名时，默认人设里的名字跟着变；用户自己写的人设不动
            val personaToSave = com.lanweixi.domain.prompt.CharacterNameSync.personaAfterRename(
                oldName = loadedCharacterName,
                newName = newName,
                persona = _state.value.persona,
            )
            val result = runCatching {
                store.saveCharacterName(newName)
                store.savePersona(personaToSave)
                store.saveMemory(_state.value.memory)
                store.saveApiKey(_state.value.apiKey)
                store.saveModel(_state.value.model)
                store.saveBaseUrl(_state.value.baseUrl)
            }
            if (result.isSuccess) {
                _state.update { it.copy(saving = false, saved = true, error = null) }
                onSaved()
            } else {
                // 保存失败：不回调、不清草稿，留在设置页提示重试。
                // P216：加密失败/密文解不开的异常消息要原样给用户看，否则他只会反复点保存。
                val reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        saving = false,
                        saved = false,
                        error = reason ?: "保存失败，请重试",
                    )
                }
            }
        }
    }
}
