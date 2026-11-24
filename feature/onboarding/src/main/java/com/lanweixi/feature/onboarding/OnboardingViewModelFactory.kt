package com.lanweixi.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lanweixi.core.network.ProbeResult
import com.lanweixi.domain.prompt.ChatConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * P201：手工工厂（与项目其它页面一致，不用 @HiltViewModel）。
 *
 * 探针以函数形式注入，UI 层不需要知道 OkHttp。
 */
class OnboardingViewModelFactory(
    private val store: ChatConfigStore,
    private val probe: suspend (baseUrl: String, apiKey: String, model: String) -> ProbeResult,
    private val replay: Boolean = false,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OnboardingViewModel(
            store = store,
            probe = probe,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            replay = replay,
        ) as T
    }
}
