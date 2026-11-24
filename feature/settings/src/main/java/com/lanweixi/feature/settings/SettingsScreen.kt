package com.lanweixi.feature.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lanweixi.core.design.components.MuchaTopBar

/**
 * P180：设置页。
 *
 * 分四块：主题 / 图标风格 / 背景与遮罩 / 账号，再加头像、备份、配置快照。
 * 背景遮罩滑块已取消（P182）——插画原色显示，只留顶/底栏不透明度可调。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onCharacterNameChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    /** P192：编辑人设。 */
    onPersonaChange: (String) -> Unit = {},
    /** P192：导入 .md 记忆文件。 */
    onImportMemory: () -> Unit = {},
    /** P192：清空记忆。 */
    onClearMemory: () -> Unit = {},
    /** P193：编辑后立即保存。 */
    onSaveMemory: (String) -> Unit = {},
    onBack: () -> Unit,
    /** P194：打开工具人管理。 */
    onOpenAgents: () -> Unit = {},
    /** P195：打开群聊列表。 */
    onOpenGroups: () -> Unit = {},
    /** P201：重看首次启动引导。 */
    onOpenOnboarding: () -> Unit = {},
    /** P189：打开宿主日志。 */
    onOpenLogs: () -> Unit = {},
    onPickAvatar: (String) -> Unit = {},
    onBarAlphaChange: (Float, Boolean) -> Unit = { _, _ -> },
    onIconStyleChange: (String) -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    /** P199：把笔记导成可读的 .md。 */
    onExportNotesMarkdown: () -> Unit = {},
    /** P211：选服务商预设（填 BaseURL / 模型）。 */
    onProviderSelect: (String) -> Unit = {},
    /** P210：导出会话摘要（上下文压缩产物）。 */
    onExportSummaries: () -> Unit = {},
    /** P210：已有摘要段数（0 = 还没生成过）。 */
    summaryCount: Int = 0,
    /** P211：今天/本周 token 用量，已经格式化好（feature:settings 不依赖 core:data）。 */
    usageTodayText: String = "0",
    usageWeekText: String = "0",
    /** P199：手动跑一次任务提醒检查（后台调度在部分系统上会被压后）。 */
    onCheckReminders: () -> Unit = {},
    onApplySnapshot: (Long) -> Unit = {},
    /** P212：本地崩溃日志份数（0 = 没崩过）。 */
    crashCount: Int = 0,
    /** P212：导出崩溃日志。 */
    onExportCrashLog: () -> Unit = {},
    /** P212：版本号（关于区显示）。 */
    appVersion: String = "",
) {
    var showPrivacy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        MuchaTopBar(title = "设置", onBack = onBack)

        // ── 主题 ──
        SectionTitle("主题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "night_purple" to "星夜蓝紫",
                "daylight_gold" to "日光金",
                "pure" to "纯净",
            ).forEach { (id, label) ->
                Chip(label = label, active = state.themeMode == id) { onThemeChange(id) }
            }
        }

        // ── 图标风格 ──
        SectionTitle("图标风格")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "vector" to "矢量（纤细）",
                "detailed" to "精细（多密度）",
            ).forEach { (id, label) ->
                Chip(label = label, active = state.iconStyle == id) { onIconStyleChange(id) }
            }
        }

        // ── 背景与遮罩 ──
        SectionTitle("界面质感")
        Text(
            "毛玻璃透光程度 ${"%.0f".format(state.barAlpha * 100)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = state.barAlpha,
            onValueChange = { onBarAlphaChange(it, false) },
            onValueChangeFinished = { onBarAlphaChange(state.barAlpha, true) },
            valueRange = 0.1f..1f,
        )
        Spacer(Modifier.height(8.dp))

        // ── 账号 ──
        SectionTitle("账号")
        val keyHasBadChars = state.apiKey.trim().any { it.code !in 33..126 }
        SettingCard {
            OutlinedTextField(
                value = state.characterName,
                onValueChange = onCharacterNameChange,
                label = { Text("角色名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.persona,
                onValueChange = onPersonaChange,
                label = { Text("她是什么样的人") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "写她怎么说话、什么脾气。会拼进系统提示词，改完保存生效。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            // P211：服务商预设——只填 BaseURL / 模型，保存后才生效
            Text(
                "服务商",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            com.lanweixi.domain.prompt.AgentProviders.ALL.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { preset ->
                        val matched = preset.baseUrl.isNotBlank() &&
                            state.baseUrl.trim().trimEnd('/') == preset.baseUrl.trimEnd('/')
                        Chip(label = preset.label, active = matched) {
                            onProviderSelect(preset.id)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            com.lanweixi.domain.prompt.AgentProviders.ALL
                .firstOrNull {
                    it.baseUrl.isNotBlank() &&
                        state.baseUrl.trim().trimEnd('/') == it.baseUrl.trimEnd('/')
                }
                ?.keyHint?.takeIf { it.isNotBlank() }?.let { hint ->
                    Text(
                        "Key 去哪拿：$hint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                "仅保存在本机 DataStore，不上传、不写数据库。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (keyHasBadChars) {
                Text(
                    "Key 里有中文或全角字符（输入法猜词造成的），删掉重新粘贴一次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // P209：协议按地址自动判定，这里说清楚，免得用户以为还要配别的东西
            Text(
                if (state.baseUrl.contains("anthropic", ignoreCase = true)) {
                    "当前走 Anthropic 协议（/v1/messages + x-api-key）"
                } else {
                    "Anthropic（Claude）填 https://api.anthropic.com 即可自动切换协议"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.saving && !keyHasBadChars,
        ) {
            Text(if (state.saving) "保存中…" else "保存并返回")
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ── 头像 ──
        SectionTitle("头像")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onPickAvatar("character") }, shape = RoundedCornerShape(12.dp)) {
                Text("换角色头像")
            }
            Button(onClick = { onPickAvatar("user") }, shape = RoundedCornerShape(12.dp)) {
                Text("换我的头像")
            }
        }

        // ── 记忆 ──
        SectionTitle("她的记忆")
        var editingMemory by remember { mutableStateOf(false) }
        var memoryDraft by remember(state.memory) { mutableStateOf(state.memory) }

        if (editingMemory) {
            OutlinedTextField(
                value = memoryDraft,
                onValueChange = { memoryDraft = it },
                label = { Text("记忆内容（Markdown）") },
                minLines = 8,
                maxLines = 20,
                // 给个不透明底，否则字压在插画上看不清
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSaveMemory(memoryDraft)
                        editingMemory = false
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("保存")
                }
                Button(
                    onClick = {
                        memoryDraft = state.memory
                        editingMemory = false
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("取消")
                }
            }
        } else {
            Text(
                if (state.memory.isBlank()) {
                    "还没有导入记忆。写一个 .md 文件，她会记住里面的事。"
                } else {
                    "已导入 ${state.memory.length} 字。点「查看/编辑」可以改。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.memory.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.memory.take(280) +
                            if (state.memory.length > 280) "\n…（共 ${state.memory.length} 字）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImportMemory, shape = RoundedCornerShape(12.dp)) {
                    Text("导入 .md")
                }
                if (state.memory.isNotBlank()) {
                    Button(
                        onClick = {
                            memoryDraft = state.memory
                            editingMemory = true
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("查看/编辑")
                    }
                    Button(onClick = onClearMemory, shape = RoundedCornerShape(12.dp)) {
                        Text("清空")
                    }
                }
            }
        }

        // ── 备份与恢复 ──
        SectionTitle("备份与恢复")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onExportBackup, shape = RoundedCornerShape(12.dp)) {
                Text("导出备份")
            }
            Button(onClick = onImportBackup, shape = RoundedCornerShape(12.dp)) {
                Text("从备份恢复")
            }
        }
        Text(
            "导出为 JSON 文件；恢复会整体替换现有数据。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExportNotesMarkdown, shape = RoundedCornerShape(12.dp)) {
            Text("导出笔记为 Markdown")
        }
        Text(
            "给人看的版本：一篇一节，带标签和时间，可以直接丢进别的编辑器。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // P210：上下文压缩的产物——告诉用户它在哪、能导出
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExportSummaries, shape = RoundedCornerShape(12.dp)) {
            Text("导出会话摘要")
        }
        Text(
            if (summaryCount > 0) {
                "已生成 $summaryCount 段摘要，存在应用数据库 lanweixi.db 的 session_summaries 表；" +
                    "这里是导出给人看的副本。"
            } else {
                "还没有生成摘要：某个会话聊过 30 条以上后，她会自动把前面的内容压成摘要。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // ── 用量 ──（P211）
        SectionTitle("用量")
        Text(
            "今天 $usageTodayText tokens · 本周 $usageWeekText tokens",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "每次对话成功后记录（上游没返回 usage 的不算；自动摘要的请求也不在内）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        SectionTitle("工具人")
        Text(
            "干活的助手，各配各的 API。跟兰未晞不同，他们没有性格。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenAgents, shape = RoundedCornerShape(12.dp)) {
                Text("管理工具人")
            }
            Button(onClick = onOpenGroups, shape = RoundedCornerShape(12.dp)) {
                Text("群聊")
            }
        }

        // ── 引导 ──
        SectionTitle("引导")
        Button(onClick = onOpenOnboarding, shape = RoundedCornerShape(12.dp)) {
            Text("重看首次引导")
        }
        Text(
            "名字、服务商和 Key 会预填；改完保存即可。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // ── 诊断 ──
        SectionTitle("诊断")
        Button(onClick = onOpenLogs, shape = RoundedCornerShape(12.dp)) {
            Text("查看宿主日志")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCheckReminders, shape = RoundedCornerShape(12.dp)) {
            Text("立即检查任务提醒")
        }
        Text(
            "后台每小时自动查一次；系统压后时点这里手动补一次。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExportCrashLog, shape = RoundedCornerShape(12.dp)) {
            Text(if (crashCount > 0) "导出崩溃日志（$crashCount 份）" else "导出崩溃日志")
        }
        Text(
            "崩溃只记在本机 files/crash 下（最多 5 份），不上传。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // ── 关于 ──（P212）
        SectionTitle("关于")
        Text(
            // P214：应用名改为 Ranmiki；她本人还是兰未晞，两个名字分开显示
            if (appVersion.isBlank()) "Ranmiki" else "Ranmiki v$appVersion",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "角色：兰未晞",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "所有数据都在本机：对话、笔记、任务、记忆都不上传；API Key 用系统 Keystore 加密保存。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showPrivacy = true }, shape = RoundedCornerShape(12.dp)) {
            Text("隐私说明")
        }

        // ── 配置快照 ──
        if (state.snapshots.isNotEmpty()) {
            SectionTitle("配置快照")
            state.snapshots.forEach { snapshot ->
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            snapshot.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "恢复",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onApplySnapshot(snapshot.at) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            state.rollbackMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // P212：隐私说明（应用内入口，完整版在仓库 PRIVACY.md）
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = { Text(PRIVACY_SUMMARY) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            },
        )
    }
}

/** P212：应用内能看完的隐私摘要（完整版见 PRIVACY.md）。 */
private const val PRIVACY_SUMMARY = "" +
    "① 不收集：没有统计、广告、崩溃上报 SDK，不申请通讯录/定位/短信权限。\n" +
    "② 存在哪：对话、笔记、任务、记忆、配置都在本机（应用数据库 + DataStore）；" +
    "API Key 用系统 Keystore 加密后保存，卸载或清除数据即失效。\n" +
    "③ 会联网的地方：你发消息时，内容（用户消息 + 人设 + 记忆 + 最近对话 + 摘要）" +
    "直接发给你自己配置的服务商（DeepSeek / OpenAI / Anthropic 等），不经过第三方中转；" +
    "导入备份、选头像、导出文件都只在本机读写。\n" +
    "④ 通知只用于任务提醒，可在系统里单独关闭。\n" +
    "⑤ 删除：应用内删对话/笔记/任务，或在系统设置里清除应用数据即可全部删除。"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            )
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
