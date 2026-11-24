package com.lanweixi.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaTopBar
import com.lanweixi.core.network.ProbeResult
import com.lanweixi.domain.prompt.AgentProviders

/**
 * P201：首次启动引导（两屏）。
 *
 * 第 1 屏起名字，第 2 屏配置服务商并真测一次连通性。
 * 不强制、不教学动画：每屏都能走掉，「先跳过」直接进应用。
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onProviderSelect: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    /** 重看引导时返回设置；首启流程不传（没有可退的路）。 */
    onExit: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        if (state.replay && onExit != null) {
            MuchaTopBar(title = "引导", onBack = onExit)
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(40.dp))
        }

        when (state.step) {
            0 -> FirstStep(
                name = state.characterName,
                replay = state.replay,
                onNameChange = onNameChange,
                onNext = onNext,
            )

            else -> SecondStep(
                state = state,
                onBack = if (state.replay) null else onBack,
                onProviderSelect = onProviderSelect,
                onBaseUrlChange = onBaseUrlChange,
                onModelChange = onModelChange,
                onKeyChange = onKeyChange,
                onTest = onTest,
                onFinish = onFinish,
                onSkip = if (state.replay) null else onSkip,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FirstStep(
    name: String,
    replay: Boolean,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        if (replay) "她的名字" else "先认识一下",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "她会用这个名字和你说话。默认叫兰未晞，不喜欢可以改。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("她的名字") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "随时可以在「设置 → 账号」里改；改名后默认人设里的称呼也会跟着变。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("下一步")
    }
}

@Composable
private fun SecondStep(
    state: OnboardingUiState,
    onBack: (() -> Unit)?,
    onProviderSelect: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onFinish: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    Text(
        "接上模型",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "她需要一个能对话的大模型。选一家服务商，填上你的 Key，再点「测试连通性」确认能用。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(18.dp))

    Text(
        "服务商",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    AgentProviders.ALL.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { preset ->
                FilterChip(
                    selected = state.provider == preset.id,
                    onClick = { onProviderSelect(preset.id) },
                    label = { Text(preset.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val selected = AgentProviders.byId(state.provider)
    if (selected.keyHint.isNotBlank()) {
        Text(
            "Key 去哪拿：${selected.keyHint}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
    }

    OutlinedTextField(
        value = state.baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = state.model,
        onValueChange = onModelChange,
        label = { Text("模型") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onKeyChange,
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "只保存在本机，不上传。粘贴时别用中文输入法——全角字符会让请求直接失败。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )

    if (state.keyHasBadChars) {
        Text(
            "Key 里有中文或全角字符，删掉重新粘贴一次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = onTest,
        enabled = !state.testing && state.apiKey.isNotBlank() && !state.keyHasBadChars,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(if (state.testing) "测试中…" else "测试连通性")
    }

    state.probe?.let { result ->
        val (text, color) = probeText(result)
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    state.error?.let { error ->
        Text(
            error,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Spacer(Modifier.height(22.dp))
    Button(
        onClick = onFinish,
        enabled = state.canFinish,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(if (state.saving) "保存中…" else "完成，开始用")
    }

    if (onSkip != null) {
        TextButton(
            onClick = onSkip,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("先跳过，稍后再配")
        }
        Text(
            "跳过也能进应用，但还不能对话。第一次发消息时会提示你去设置里补 Key。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (onBack != null) {
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("上一步")
        }
    }
}

@Composable
private fun probeText(result: ProbeResult): Pair<String, Color> {
    val error = MaterialTheme.colorScheme.error
    return when (result) {
        is ProbeResult.Ok -> "连通了，服务器正常返回" to MaterialTheme.colorScheme.primary
        ProbeResult.InvalidKeyChars ->
            "Key 里有中文或全角字符，复制粘贴重填一次" to error

        is ProbeResult.InvalidKey -> "Key 无效或没权限：${result.detail}" to error
        is ProbeResult.Busy -> "被限流或额度用完了：${result.detail}" to error
        is ProbeResult.NoBalance -> "余额不足：${result.detail}" to error
        is ProbeResult.WrongEndpoint -> "地址或模型名不对（404）：${result.detail}" to error
        is ProbeResult.ServerError -> "服务端报错 HTTP ${result.code}：${result.detail}" to error
        is ProbeResult.Network -> "连不上：${result.detail}" to error
    }
}
