package com.lanweixi.client

import android.Manifest
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lanweixi.core.design.mucha.MuchaThemeProvider
import com.lanweixi.core.design.mucha.MuchaThemeMode
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.feature.chat.ChatHistoryQueryPort
import com.lanweixi.feature.chat.ChatScreen
import com.lanweixi.feature.chat.ChatViewModel
import com.lanweixi.feature.chat.ConversationUseCase
import com.lanweixi.feature.chat.SessionPort
import com.lanweixi.feature.settings.SettingsScreen
import com.lanweixi.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * P07a–P12b：应用入口。
 *
 * P12b：用 Navigation Compose 最小路由（chat / settings）替代 `showSettings` 状态切换；
 * 设置页已归并到 `:feature:settings`，配置端口下沉到 `:domain:prompt`。
 * 注入只限于用例、只读历史端口与配置存储；不触碰 DAO。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** P75：待选择头像的种类（character / user）。 */
    private var pendingAvatarKind: String? = null

    /** P199：通知点进来要落的页面（现在只有 "tasks"）。null = 正常启动。 */
    private val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // manifest 里是 singleTop：App 在后台/前台时点通知都会走到这里
        pendingRoute.value = intent.getStringExtra(
            com.lanweixi.client.reminder.TaskReminderNotifications.EXTRA_ROUTE,
        )
    }


    @Inject
    lateinit var conversationUseCase: ConversationUseCase

    @Inject
    lateinit var historyQuery: ChatHistoryQueryPort

    @Inject
    lateinit var configStore: ChatConfigStore

    /** P139：敏感工具确认通道。 */
    @Inject
    lateinit var toolConfirmGateway: com.lanweixi.domain.tools.ToolConfirmGateway

    /** P162：备份用。 */
    @Inject
    lateinit var database: com.lanweixi.core.data.LanWeixiDatabase

    /** P140：插件注册表（与对话链路共用同一个实例）。 */
    @Inject
    lateinit var pluginRegistry: com.lanweixi.core.data.plugin.PluginRegistry

    @Inject
    lateinit var noteRepository: com.lanweixi.core.data.repository.NoteRepository

    @Inject
    lateinit var promptRepository: com.lanweixi.core.data.repository.PromptRepository

    @Inject
    lateinit var taskRepository: com.lanweixi.core.data.repository.TaskRepository

    @Inject
    lateinit var documentRepository: com.lanweixi.core.data.repository.DocumentRepository

    @Inject
    lateinit var reviewRepository: com.lanweixi.core.data.repository.ReviewRepository

    /** P206：AI 草稿（待审核队列）。 */
    @Inject
    lateinit var draftRepository: com.lanweixi.core.data.repository.DraftRepository

    @Inject
    lateinit var draftReview: com.lanweixi.core.data.draft.DraftReview

    /** P208：任务完成回传（勾掉一项写 events）。 */
    @Inject
    lateinit var eventRepository: com.lanweixi.core.data.repository.EventRepository

    /** P210：会话摘要（上下文压缩）。 */
    @Inject
    lateinit var summaryRepository: com.lanweixi.core.data.repository.SessionSummaryRepository


    @Inject
    lateinit var sessionPort: SessionPort

    /** P197：群聊用——同一套流式客户端，独立的群聊端口。 */
    @Inject
    lateinit var chatStreamRunner: com.lanweixi.feature.chat.ChatStreamClientRunner

    @Inject
    lateinit var messageRepository: com.lanweixi.core.data.repository.MessageRepository

    /** P201：首次启动引导的连通性测试（真发一次请求）。 */
    @Inject
    lateinit var connectivityProbe: com.lanweixi.core.network.ConnectivityProbe




















    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // P33：manifest 不能带 android:theme（vivo 只加载首个 dex），
        // 因此用程序化方式隐藏系统默认标题栏
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        enableEdgeToEdge()
        // P65：键盘避让——显式关闭 decor fits，交给 Compose 的 imePadding 处理
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // P50/P54：状态栏/导航栏图标跟随深色模式（P54 起在 Compose 内按主题设置）
        runCatching {
            window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val notesFactory = com.lanweixi.feature.notes.NotesViewModelFactory(noteRepository)
        val promptsFactory = com.lanweixi.feature.prompts.PromptsViewModelFactory(promptRepository)
        val tasksFactory = com.lanweixi.feature.tasks.TasksViewModelFactory(taskRepository, eventRepository)
        val docsFactory = com.lanweixi.feature.docs.DocsViewModelFactory(documentRepository)
        val reviewFactory = com.lanweixi.feature.review.ReviewViewModelFactory(reviewRepository)
        // P217：events 只增不删的兜底（90 天 + 最近 2000 条），失败不影响启动
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { com.lanweixi.client.chat.EventRetention.prune(eventRepository) }
        }
        // P199：通知点进来时带的跳转意图（冷启动才有；热启动走 onNewIntent）
        pendingRoute.value = intent?.getStringExtra(
            com.lanweixi.client.reminder.TaskReminderNotifications.EXTRA_ROUTE,
        )
        val pluginHost: com.lanweixi.core.data.plugin.PluginHost =
            com.lanweixi.core.data.plugin.PluginHostImpl(
                pluginRegistry,
                com.lanweixi.core.data.plugin.PluginInstaller(this@MainActivity, pluginRegistry),
            )
        // P197：群聊——与单聊共用流式客户端，消息落同一张表（sessionId = 群 id）。
        val groupChatPort = com.lanweixi.client.chat.RoomGroupChatPort(messageRepository)
        // P210：上下文压缩（单聊/群聊共用同一个实例）
        val summarizer = com.lanweixi.client.chat.SessionSummarizer(
            messages = messageRepository,
            summaries = summaryRepository,
            runner = chatStreamRunner,
            configProvider = configStore,
        )
        // P211：token 用量记录
        val usageRecorder = com.lanweixi.client.chat.SessionUsageRecorder(eventRepository)
        val groupChatEngine = com.lanweixi.feature.chat.GroupChatEngine(
            stream = chatStreamRunner,
            promptBuilder = com.lanweixi.domain.prompt.PromptBuilder(),
            port = groupChatPort,
            configProvider = configStore,
            summaryProvider = summarizer,
            summaryEngine = summarizer,
        )
        val pluginsFactory = com.lanweixi.feature.plugins.PluginsViewModelFactory(pluginHost)
        val settingsFactory = SettingsViewModelFactory(
            configStore,
        )
        setContent {
            // P126：头像仓库（StateFlow），设置页改完聊天页立刻跟着变
            val avatarStore = remember { com.lanweixi.client.avatar.AvatarStore(this@MainActivity) }
            val avatar by avatarStore.state.collectAsState()
            LaunchedEffect(avatar) {
            }
            // P91：角色名字（设置页可改；改完立即影响聊天页/首页/提示词）
            var characterName by remember {
                mutableStateOf(com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME)
            }
            LaunchedEffect(Unit) {
                characterName = runCatching { configStore.current().characterName }
                    .getOrDefault(com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME)
            }
            // P201：首次启动引导。
            // 标记存 SharedPreferences（同步读，首帧就能决定显示什么）；
            // 老用户升级已经有 Key 的话直接放行并补写标记，不打扰。
            val onboardingPrefs = remember { getSharedPreferences("onboarding", MODE_PRIVATE) }
            var showOnboarding by remember { mutableStateOf(false) }
            var firstRunChecked by remember { mutableStateOf(false) }
            // P202：没配 Key 时在会话列表顶部提示（设置页保存返回、引导跳过后都会刷新）
            var missingApiKey by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val cfg = runCatching { configStore.current() }.getOrNull()
                val decision = com.lanweixi.feature.onboarding.OnboardingGate.decide(
                    done = onboardingPrefs.getBoolean("done", false),
                    hasApiKey = !cfg?.apiKey.isNullOrBlank(),
                )
                when (decision) {
                    com.lanweixi.feature.onboarding.OnboardingGate.Decision.AlreadyDone -> Unit
                    com.lanweixi.feature.onboarding.OnboardingGate.Decision.SkipButMarkDone ->
                        onboardingPrefs.edit().putBoolean("done", true).apply()

                    com.lanweixi.feature.onboarding.OnboardingGate.Decision.ShowOnboarding ->
                        showOnboarding = true
                }
                firstRunChecked = true
            }
            // P198：会话级工厂在 conversation/{sessionId} 里按 id 重建（每个会话一份 VM）
            // P18：API 33+ 首次进入时请求通知权限（拒绝也不影响聊天）。
            // P201：引导页还在的时候不弹系统权限框，走完引导进主界面再问。
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(firstRunChecked, showOnboarding) {
                if (!firstRunChecked || showOnboarding) return@LaunchedEffect
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect {
                val view = window.decorView
                androidx.core.view.WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !dark
                androidx.core.view.WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightNavigationBars = !dark
            }
            val avatarPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                val kind = pendingAvatarKind
                pendingAvatarKind = null
                if (uri != null && kind != null) {
                    com.lanweixi.client.avatar.AvatarStore.takePersistable(this@MainActivity, uri)
                    when (kind) {
                        "character" -> avatarStore.setCharacter(uri.toString())
                        "sprite" -> avatarStore.setCharacterSprite(uri.toString())
                        else -> avatarStore.setUser(uri.toString())
                    }
                }
            }
            // P218：默认纯净（无插画、纯色底），首帧就用它，避免启动闪一下星夜
            var themeMode by remember { mutableStateOf(MuchaThemeMode.PureMaterial3) }
            var iconStyle by remember { mutableStateOf(com.lanweixi.core.design.components.IconStyle.VECTOR) }
            var muchaTuning by remember { mutableStateOf(com.lanweixi.core.design.mucha.MuchaTuning()) }
            LaunchedEffect(Unit) {
                val stored = runCatching { configStore.current().themeMode }.getOrDefault("pure")
                themeMode = when (stored) {
                    "daylight_gold" -> MuchaThemeMode.DaylightGold
                    "night_purple" -> MuchaThemeMode.StarryNight
                    else -> MuchaThemeMode.PureMaterial3
                }
                val cfg = runCatching { configStore.current() }.getOrNull()
                iconStyle = if (cfg?.iconStyle == "detailed") {
                    com.lanweixi.core.design.components.IconStyle.DETAILED
                } else {
                    com.lanweixi.core.design.components.IconStyle.VECTOR
                }
                muchaTuning = com.lanweixi.core.design.mucha.MuchaTuning(
                    backgroundVeil = cfg?.bgVeil?.toFloatOrNull() ?: 0.50f,
                    barAlpha = cfg?.barAlpha?.toFloatOrNull() ?: 0.62f,
                )
            }
            val scope = rememberCoroutineScope()
            // P166：远程插件源状态
            var remoteOpen by remember { mutableStateOf(false) }
            var remoteUrl by remember { mutableStateOf("") }
            var remoteLoading by remember { mutableStateOf(false) }
            var remoteError by remember { mutableStateOf<String?>(null) }
            var remotePlugins by remember { mutableStateOf<List<com.lanweixi.domain.plugin.RemotePlugin>>(emptyList()) }
            var updateNotice by remember { mutableStateOf<String?>(null) }
            // P216：远程插件安装前的二次确认（来源 / 版本 / sha256 都给用户看一遍）
            var pendingRemoteInstall by remember {
                mutableStateOf<com.lanweixi.domain.plugin.RemotePlugin?>(null)
            }
            val sourcePrefs = remember { getSharedPreferences("plugin_source", MODE_PRIVATE) }
            val remoteClient = remember { com.lanweixi.core.data.plugin.RemoteSourceClient() }
            var remoteSourceName by remember { mutableStateOf("") }
            val checkUpdates: () -> Unit = {
                val saved = sourcePrefs.getString("url", "").orEmpty()
                if (saved.isBlank()) {
                    updateNotice = "还没设置插件源，先点「浏览远程源」填一个"
                    remoteOpen = true
                } else {
                    scope.launch {
                        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            remoteClient.fetchIndex(saved)
                        }
                        when (result) {
                            is com.lanweixi.domain.plugin.SourceFetchResult.Success -> {
                                val installed = pluginRegistry.installed()
                                val newer = result.index.plugins.filter { remote ->
                                    val local = installed.firstOrNull { it.manifest.id == remote.id }
                                    local != null && com.lanweixi.domain.plugin.PluginVersion
                                        .isNewer(remote.version, local.manifest.version)
                                }
                                if (newer.isEmpty()) {
                                    updateNotice = "已是最新"
                                } else {
                                    remotePlugins = newer
                                    remoteSourceName = "可更新 " + newer.size + " 个"
                                    remoteOpen = true
                                }
                            }
                            is com.lanweixi.domain.plugin.SourceFetchResult.Failure -> {
                                updateNotice = "检查失败：" + result.message
                            }
                        }
                    }
                }
            }
            // P167：源地址持久化，检查更新时复用
            // P170：插件配置对话框
            var configDialogOpen by remember { mutableStateOf(false) }
            var configFields by remember { mutableStateOf<List<com.lanweixi.domain.plugin.PluginConfigField>>(emptyList()) }
            var configValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var configPluginDir by remember { mutableStateOf<java.io.File?>(null) }
            val configStore = remember { com.lanweixi.core.data.plugin.PluginConfigStore() }
            val noteRepositoryForUi = remember {
                com.lanweixi.core.data.repository.RoomNoteRepository(database.noteDao())
            }
            val taskRepositoryForUi = remember {
                com.lanweixi.core.data.repository.RoomTaskRepository(database.taskDao())
            }
            val documentRepositoryForUi = remember {
                com.lanweixi.core.data.repository.RoomDocumentRepository(database.documentDao())
            }
            val promptRepositoryForUi = remember {
                com.lanweixi.core.data.repository.RoomPromptRepository(database.promptDao())
            }
            val backupManager = remember { com.lanweixi.core.data.backup.BackupManager(database) }
            var pendingBackupJson by remember { mutableStateOf<String?>(null) }
            var backupMessage by remember { mutableStateOf<String?>(null) }
            val backupExport = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                val json = pendingBackupJson
                pendingBackupJson = null
                if (uri != null && json != null) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }.onSuccess { backupMessage = "已导出备份" }
                        .onFailure { backupMessage = it.message ?: "写入失败" }
                }
            }
            var pendingNotesMd by remember { mutableStateOf<String?>(null) }
            val notesMdExport = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/markdown")
            ) { uri ->
                val md = pendingNotesMd
                pendingNotesMd = null
                if (uri != null && md != null) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) }
                    }.onSuccess { backupMessage = "已导出笔记 Markdown" }
                        .onFailure { backupMessage = it.message ?: "写入失败" }
                }
            }
            val backupImport = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val text = runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text == null) {
                        backupMessage = "读取备份失败"
                    } else {
                        scope.launch {
                            // P190：导入会整体替换数据，先把当前状态留一份，
                            // 误操作时能从 filesDir/restore-backup-*.json 找回。
                            // P217：留底文件最多 3 份、保留 7 天（明文 JSON，别长期堆着）
                            runCatching {
                                com.lanweixi.client.backup.RestoreBackups.save(
                                    filesDir,
                                    backupManager.exportJson(),
                                )
                            }
                            runCatching { backupManager.importJson(text) }
                                .onSuccess { backupMessage = "已恢复：" + it.describe() }
                                .onFailure { backupMessage = it.message ?: "导入失败" }
                        }
                    }
                }
            }
            LaunchedEffect(updateNotice) {
                val msg = updateNotice
                if (msg != null) {
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    updateNotice = null
                }
            }
            LaunchedEffect(backupMessage) {
                val msg = backupMessage
                if (msg != null) {
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                    backupMessage = null
                }
            }
            if (configDialogOpen) {
                com.lanweixi.feature.plugins.PluginConfigDialog(
                    title = "插件配置",
                    fields = configFields,
                    initial = configValues,
                    onSave = { newValues ->
                        configPluginDir?.let { dir -> configStore.save(dir, newValues) }
                        configDialogOpen = false
                    },
                    onDismiss = { configDialogOpen = false },
                )
            }
            if (remoteOpen) {
                com.lanweixi.feature.plugins.RemoteSourceDialog(
                    sourceUrl = remoteUrl,
                    onSourceUrlChange = { remoteUrl = it },
                    onFetch = {
                        sourcePrefs.edit().putString("url", remoteUrl.trim()).apply()
                        remoteLoading = true
                        remoteError = null
                        scope.launch {
                            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                remoteClient.fetchIndex(remoteUrl.trim())
                            }
                            when (result) {
                                is com.lanweixi.domain.plugin.SourceFetchResult.Success -> {
                                    remotePlugins = result.index.plugins
                                    remoteSourceName = result.index.name
                                }
                                is com.lanweixi.domain.plugin.SourceFetchResult.Failure -> {
                                    remotePlugins = emptyList()
                                    remoteError = result.message
                                }
                            }
                            remoteLoading = false
                        }
                    },
                    // P216：点「安装」先弹二次确认（以前下载完直接装，用户没机会看到来源与校验值）
                    onInstall = { plugin -> pendingRemoteInstall = plugin },
                    onDismiss = { remoteOpen = false },
                    loading = remoteLoading,
                    error = remoteError,
                    plugins = remotePlugins,
                    sourceName = remoteSourceName,
                )
            }
            pendingRemoteInstall?.let { plugin ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { if (!remoteLoading) pendingRemoteInstall = null },
                    title = { Text("确认安装插件") },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(plugin.name + "  v" + plugin.version)
                            Text("作者：" + plugin.author.ifBlank { "未知" })
                            Text("来源：" + remoteSourceName.ifBlank { remoteUrl.ifBlank { "远程插件源" } })
                            Text("sha256：" + plugin.sha256.take(16) + "…")
                            Text(
                                "安装后会走本地权限授权，不会自动给任何权限。",
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                pendingRemoteInstall = null
                                remoteError = null
                                remoteLoading = true
                                scope.launch {
                                    val zip = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        var err: String? = null
                                        val f = remoteClient.download(plugin, cacheDir) { err = it }
                                        if (f == null) remoteError = err ?: "下载失败"
                                        f
                                    }
                                    if (zip != null) {
                                        var installed = false
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            installed = com.lanweixi.core.data.plugin.PluginInstaller(
                                                this@MainActivity, pluginRegistry,
                                            ).installZip(zip).let { it is com.lanweixi.core.data.plugin.PluginInstallResult.Success }
                                        }
                                        if (installed) {
                                            remoteOpen = false
                                            android.widget.Toast.makeText(
                                                this@MainActivity,
                                                "已安装 " + plugin.name,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            remoteError = "安装失败（校验或清单不通过，详情见插件页提示）"
                                        }
                                    }
                                    remoteLoading = false
                                }
                            },
                        ) { Text("确认安装") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { pendingRemoteInstall = null },
                        ) { Text("取消") }
                    },
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.lanweixi.core.design.components.LocalIconStyle provides iconStyle,
                com.lanweixi.core.design.mucha.LocalMuchaTuning provides muchaTuning,
            ) {
            val navController = rememberNavController()
            // P206/P207：待审核页与草稿修改页共用一个 ViewModel 工厂
            val inboxFactory = remember {
                com.lanweixi.feature.inbox.InboxViewModelFactory(
                    drafts = draftRepository,
                    review = draftReview,
                )
            }
            // P199：任务提醒点进来直接落到任务页（App 在后台时走 onNewIntent）
            val pendingRouteValue by pendingRoute.collectAsState()
            LaunchedEffect(pendingRouteValue) {
                val target = pendingRouteValue ?: return@LaunchedEffect
                // 先导航再清空：清空会改变 key 并取消本协程，顺序反了导航会被吃掉
                if (target == com.lanweixi.client.reminder.TaskReminderNotifications.ROUTE_TASKS) {
                    runCatching { navController.navigate(Route.TASKS) }
                }
                pendingRoute.value = null
            }
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            // P202：回到会话列表或刚走完引导时，刷新「有没有 Key」
            LaunchedEffect(currentRoute, showOnboarding, firstRunChecked) {
                if (currentRoute == Route.SESSIONS && firstRunChecked && !showOnboarding) {
                    // 注意局部有个同名的 PluginConfigStore，这里取 Activity 的 ChatConfigStore
                    missingApiKey = runCatching { this@MainActivity.configStore.current().apiKey.isBlank() }
                        .getOrDefault(false)
                }
            }
            MuchaThemeProvider(mode = themeMode) {
                // 聊天页不压遮罩，让插画完整显示；其余页面沿用设置里的值
                com.lanweixi.core.design.mucha.MuchaBackground(
                    theme = themeMode,
                    veil = if (currentRoute == null ||
                        currentRoute.startsWith(Route.CONVERSATION) ||
                        currentRoute.startsWith(Route.GROUP_CHAT)
                    ) 0f else null,
                ) {
                Surface(
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // P201：首启判定中只渲染主题背景，不闪主界面
                    if (!firstRunChecked) {
                        Spacer(Modifier.fillMaxSize())
                    } else if (showOnboarding) {
                        FirstRunOnboarding(
                            // 注意：setContent 里有个同名的 PluginConfigStore 局部变量，这里必须显式取 Activity 属性
                            configStore = this@MainActivity.configStore,
                            probe = connectivityProbe,
                            onDone = { name ->
                                characterName = name
                                onboardingPrefs.edit().putBoolean("done", true).apply()
                                showOnboarding = false
                            },
                        )
                    } else {
                        val mainTabs = listOf(Route.SESSIONS, Route.NOTES, Route.TASKS, Route.MORE)
                        val showBottomBar = currentRoute in mainTabs
                        Scaffold(
                            // 不用 colorScheme.background 铺底，否则会把主题插画盖掉
                            containerColor = Color.Transparent,
                            bottomBar = {
                                if (showBottomBar) {
                                    com.lanweixi.core.design.components.MuchaBottomBar(
                                        items = listOf(
                                            com.lanweixi.core.design.components.MuchaNavItem("聊天", com.lanweixi.core.design.components.MuchaNavIcon.CHAT),
                                            com.lanweixi.core.design.components.MuchaNavItem("笔记", com.lanweixi.core.design.components.MuchaNavIcon.NOTE),
                                            com.lanweixi.core.design.components.MuchaNavItem("任务", com.lanweixi.core.design.components.MuchaNavIcon.TASK),
                                            com.lanweixi.core.design.components.MuchaNavItem("更多", com.lanweixi.core.design.components.MuchaNavIcon.MORE),
                                        ),
                                        selectedIndex = mainTabs.indexOf(currentRoute).coerceAtLeast(0),
                                        onSelect = { index ->
                                            val target = mainTabs[index]
                                            if (target != currentRoute) {
                                                navController.navigate(target) {
                                                    popUpTo(Route.SESSIONS) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                    )
                                }
                            },
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {

                    NavHost(navController = navController, startDestination = Route.SESSIONS) {
                        composable(Route.PLUGIN_PAGE + "/{pluginId}/{entryId}") { backStack ->
                            val pluginId = backStack.arguments?.getString("pluginId").orEmpty()
                            val entryId = backStack.arguments?.getString("entryId").orEmpty()
                            val plugin = remember(pluginId) {
                                pluginRegistry.installed().firstOrNull { it.manifest.id == pluginId }
                            }
                            var nodes by remember { mutableStateOf<List<com.lanweixi.domain.plugin.PluginUiNode>>(emptyList()) }
                            var pageError by remember { mutableStateOf<String?>(null) }
                            var busy by remember { mutableStateOf(false) }
                            var reloadKey by remember { mutableStateOf(0) }

                            LaunchedEffect(pluginId, entryId, reloadKey) {
                                val p0 = plugin ?: return@LaunchedEffect
                                busy = true
                                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val entry = p0.manifest.uiEntries.firstOrNull { it.entryId == entryId }
                                    if (entry == null) {
                                        com.lanweixi.domain.plugin.PluginUiPage(error = "插件没有声明这个页面")
                                    } else {
                                        val renderer = com.lanweixi.core.data.plugin.lua.LuaUiRenderer(
                                            com.lanweixi.core.data.plugin.lua.LuaHostApis(
                                                notes = noteRepositoryForUi,
                                                tasks = taskRepositoryForUi,
                                                documents = documentRepositoryForUi,
                                                prompts = promptRepositoryForUi,
                                                granted = p0.grants,
                                                globalSearch = com.lanweixi.core.data.search.GlobalSearch(
                                                    notes = noteRepositoryForUi,
                                                    tasks = taskRepositoryForUi,
                                                    documents = documentRepositoryForUi,
                                                    prompts = promptRepositoryForUi,
                                                    reviews = reviewRepository,
                                                ),
                                                logger = { },
                                            ),
                                        )
                                        renderer.render(
                                            java.io.File(p0.dirPath), p0.manifest, entry,
                                        )
                                    }
                                }
                                nodes = result.nodes
                                pageError = result.error
                                busy = false
                            }

                            com.lanweixi.feature.plugins.PluginPageScreen(
                                title = plugin?.manifest?.uiEntries
                                    ?.firstOrNull { it.entryId == entryId }?.title
                                    ?: plugin?.manifest?.name ?: "插件",
                                nodes = nodes,
                                error = pageError,
                                busy = busy,
                                onAction = { reloadKey++ },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.GROUPS) {
                            val agentStore = remember {
                                com.lanweixi.core.data.agent.AgentStore(applicationContext)
                            }
                            var groupList by remember { mutableStateOf(agentStore.listGroups()) }
                            com.lanweixi.feature.settings.GroupListScreen(
                                groups = groupList,
                                agents = agentStore.list(),
                                onBack = { navController.popBackStack() },
                                onCreate = { g -> agentStore.upsertGroup(g); groupList = agentStore.listGroups() },
                                onOpen = { g -> navController.navigate(Route.GROUP_CHAT + "/" + g.id) },
                                onDelete = { id ->
                                    agentStore.deleteGroup(id)
                                    groupList = agentStore.listGroups()
                                    // 群删了，消息也别留成孤儿（否则库里会一直堆着看不见的记录）
                                    scope.launch {
                                        runCatching { messageRepository.deleteSession(id) }
                                    }
                                },
                            )
                        }
                        composable(
                            route = Route.GROUP_CHAT + "/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
                        ) { backStack ->
                            val groupId = backStack.arguments?.getString("groupId").orEmpty()
                            val agentStore = remember {
                                com.lanweixi.core.data.agent.AgentStore(applicationContext)
                            }
                            val group = remember(groupId) { agentStore.listGroups().firstOrNull { it.id == groupId } }
                            if (group == null) {
                                // 群被删了：别卡在空白页，说明白再退回去
                                LaunchedEffect(groupId) { navController.popBackStack() }
                                return@composable
                            }
                            val factory = remember(groupId, characterName, avatar.characterUri) {
                                GroupChatViewModelFactory(
                                    groupId = group.id,
                                    groupTitle = group.title,
                                    hostName = characterName,
                                    hostAvatarUri = avatar.characterUri,
                                    memberIdsProvider = {
                                        agentStore.listGroups()
                                            .firstOrNull { it.id == groupId }?.memberIds
                                            ?: emptyList()
                                    },
                                    agentStore = agentStore,
                                    port = groupChatPort,
                                    useCase = groupChatEngine,
                                )
                            }
                            val vm: com.lanweixi.feature.chat.GroupChatViewModel =
                                viewModel(factory = factory)
                            val groupState by vm.state.collectAsState()
                            // P218：群聊里随时加人/移人；改完刷新 VM 的成员快照
                            val allAgents = remember { agentStore.list() }
                            val updateMembers: (List<String>) -> Unit = { ids ->
                                val current = agentStore.listGroups().firstOrNull { it.id == groupId }
                                if (current != null) {
                                    agentStore.upsertGroup(current.copy(memberIds = ids))
                                }
                                vm.refreshMembers()
                            }
                            com.lanweixi.feature.chat.GroupChatScreen(
                                state = groupState,
                                userAvatarUri = avatar.userUri,
                                onBack = { navController.popBackStack() },
                                onDraftChange = vm::onDraftChange,
                                onSend = vm::send,
                                onCancel = vm::cancelSend,
                                onToggleTarget = vm::toggleTarget,
                                onSelectAll = vm::selectAllTargets,
                                onClearTargets = vm::clearTargets,
                                // P205：引用
                                onQuote = vm::quoteMessage,
                                onClearQuote = vm::clearQuote,
                                onLocateQuote = vm::locateQuote,
                                onClearHighlight = vm::clearHighlight,
                                // P218：成员面板里加减工具人
                                allAgents = allAgents,
                                onAddMember = { agentId ->
                                    val current = agentStore.listGroups()
                                        .firstOrNull { it.id == groupId }
                                    if (current != null && agentId !in current.memberIds) {
                                        updateMembers(current.memberIds + agentId)
                                    }
                                },
                                onRemoveMember = { agentId ->
                                    val current = agentStore.listGroups()
                                        .firstOrNull { it.id == groupId }
                                    if (current != null) {
                                        updateMembers(current.memberIds - agentId)
                                    }
                                },
                            )
                        }
                        composable(Route.AGENTS) {
                            val agentStore = remember {
                                com.lanweixi.core.data.agent.AgentStore(applicationContext)
                            }
                            var agentList by remember { mutableStateOf(agentStore.list()) }
                            com.lanweixi.feature.settings.AgentsScreen(
                                agents = agentList,
                                onBack = { navController.popBackStack() },
                                onSave = { profile ->
                                    agentStore.upsert(profile)
                                    agentList = agentStore.list()
                                },
                                onDelete = { id ->
                                    agentStore.delete(id)
                                    agentList = agentStore.list()
                                },
                            )
                        }
                        composable(Route.LOGS) {
                            com.lanweixi.client.HostLogsScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.MORE) {
                            var pendingDrafts by remember { mutableStateOf(0) }
                            // 从待审核页返回时会重新进组合，LaunchedEffect(Unit) 再跑一次
                            LaunchedEffect(Unit) {
                                pendingDrafts = withContext(Dispatchers.IO) {
                                    runCatching { draftRepository.countPending() }.getOrDefault(0)
                                }
                            }
                            MoreScreen(
                                onOpenSearch = { navController.navigate(Route.SEARCH) },
                                onOpenInbox = { navController.navigate(Route.INBOX) },
                                pendingDraftCount = pendingDrafts,
                                onOpenPrompts = { navController.navigate(Route.PROMPTS) },
                                onOpenDocs = { navController.navigate(Route.DOCS) },
                                onOpenReview = { navController.navigate(Route.REVIEW) },
                                onOpenPlugins = { navController.navigate(Route.PLUGINS) },
                                onOpenSettings = { navController.navigate(Route.SETTINGS) },
                            )
                        }
                        composable(Route.INBOX) {
                            val vm: com.lanweixi.feature.inbox.InboxViewModel =
                                viewModel(factory = inboxFactory)
                            val inboxState by vm.state.collectAsState()
                            // 每次回到本页都刷新：可能在别的页面确认过草稿
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.inbox.InboxScreen(
                                state = inboxState,
                                onApprove = vm::approve,
                                onReject = vm::reject,
                                onApproveAll = vm::approveAll,
                                onEdit = { id -> navController.navigate("${Route.DRAFT_EDIT}/$id") },
                                onUndo = vm::undo,
                                onOpenSource = { draft ->
                                    if (draft.sourceSessionId.isNotBlank()) {
                                        // P207：跳回原会话，按原话高亮那条消息
                                        val target = Route.CONVERSATION + "/" + draft.sourceSessionId +
                                            "?highlight=" + android.net.Uri.encode(draft.sourceText.take(200))
                                        navController.navigate(target)
                                    }
                                },
                                onToggleReviewed = vm::toggleReviewed,
                                onClearMessage = vm::clearMessage,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "${Route.DRAFT_EDIT}/{draftId}",
                            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
                        ) { entry ->
                            val draftId = entry.arguments?.getLong("draftId") ?: 0L
                            // 与待审核页共用一个 VM：保存后列表直接跟着更新
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.INBOX) }
                            val vm: com.lanweixi.feature.inbox.InboxViewModel = if (parent != null) {
                                viewModel(parent, factory = inboxFactory)
                            } else {
                                viewModel(factory = inboxFactory)
                            }
                            val editState by vm.edit.collectAsState()
                            LaunchedEffect(draftId) { vm.openEditor(draftId) }
                            com.lanweixi.feature.inbox.DraftEditScreen(
                                state = editState,
                                onTitleChange = vm::onTitleChange,
                                onBodyChange = vm::onBodyChange,
                                onTagsChange = vm::onTagsChange,
                                onDuePresetChange = vm::onDuePresetChange,
                                onPriorityChange = vm::onPriorityChange,
                                onSave = { vm.saveEdit { navController.popBackStack() } },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.SEARCH) {
                            val engine = remember {
                                com.lanweixi.core.data.search.GlobalSearch(
                                    notes = noteRepositoryForUi,
                                    tasks = taskRepositoryForUi,
                                    documents = documentRepositoryForUi,
                                    prompts = promptRepositoryForUi,
                                    reviews = reviewRepository,
                                )
                            }
                            val factory = remember {
                                com.lanweixi.feature.search.SearchViewModelFactory { query ->
                                    engine.search(query, perKind = 5)
                                }
                            }
                            val vm: com.lanweixi.feature.search.SearchViewModel =
                                viewModel(factory = factory)
                            val searchState by vm.state.collectAsState()
                            com.lanweixi.feature.search.SearchScreen(
                                state = searchState,
                                onQueryChange = vm::onQueryChange,
                                onOpen = { hit ->
                                    when (hit.kind) {
                                        "note" -> navController.navigate("${Route.NOTE_EDIT}/${hit.id}")
                                        "task" -> navController.navigate("${Route.TASK_EDIT}/${hit.id}")
                                        "doc" -> navController.navigate("${Route.DOC_DETAIL}/${hit.id}")
                                        "prompt" -> navController.navigate("${Route.PROMPT_EDIT}/${hit.id}")
                                        "review" -> navController.navigate("${Route.REVIEW_EDIT}/${hit.id}")
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.SESSIONS) {
                            val agentStore = remember {
                                com.lanweixi.core.data.agent.AgentStore(applicationContext)
                            }
                            val sessionsSource = remember {
                                com.lanweixi.client.chat.AppSessionsSource(
                                    session = sessionPort,
                                    agents = agentStore,
                                    messages = messageRepository,
                                    // P215：删会话/删群时连摘要一起删
                                    summaries = summaryRepository,
                                )
                            }
                            val sessionsFactory = remember(characterName, avatar.characterUri) {
                                SessionsViewModelFactory(
                                    source = sessionsSource,
                                    characterName = characterName,
                                    characterAvatarUri = avatar.characterUri,
                                )
                            }
                            val vm: com.lanweixi.feature.chat.SessionsViewModel =
                                viewModel(factory = sessionsFactory)
                            val sessionsState by vm.state.collectAsState()
                            // 从会话页返回时刷新一下（最后一条消息变了）
                            LaunchedEffect(Unit) { vm.refresh() }
                            var agents by remember { mutableStateOf(agentStore.list()) }
                            // P218：聊天页「+ → 新建工具人」直接弹编辑框，不跳设置页
                            var newAgentDraft by remember {
                                mutableStateOf<com.lanweixi.domain.prompt.AgentProfile?>(null)
                            }
                            com.lanweixi.feature.chat.SessionsScreen(
                                state = sessionsState,
                                characterName = characterName,
                                agents = agents,
                                onOpen = { item ->
                                    if (item.isGroup) {
                                        navController.navigate(Route.GROUP_CHAT + "/" + item.id)
                                    } else {
                                        navController.navigate(Route.CONVERSATION + "/" + item.id)
                                    }
                                },
                                onNewChat = {
                                    vm.createChat { id ->
                                        navController.navigate(Route.CONVERSATION + "/" + id)
                                    }
                                },
                                onDelete = vm::delete,
                                onOpenGroups = { navController.navigate(Route.GROUPS) },
                                onCreateGroup = { group ->
                                    agentStore.upsertGroup(group)
                                    vm.refresh()
                                    navController.navigate(Route.GROUP_CHAT + "/" + group.id)
                                },
                                onNewAgent = {
                                    newAgentDraft = com.lanweixi.domain.prompt.AgentProfile(
                                        id = "agent_" + System.currentTimeMillis(),
                                        name = "新工具人",
                                    )
                                },
                                missingApiKey = missingApiKey,
                                onOpenSettings = { navController.navigate(Route.SETTINGS) },
                            )
                            newAgentDraft?.let { draft ->
                                com.lanweixi.feature.settings.AgentEditDialog(
                                    initial = draft,
                                    onDismiss = { newAgentDraft = null },
                                    onSave = { profile ->
                                        agentStore.upsert(profile)
                                        agents = agentStore.list()
                                        newAgentDraft = null
                                    },
                                    onDelete = null,
                                )
                            }
                        }
                        composable(
                            route = Route.CONVERSATION + "/{sessionId}?highlight={highlight}",
                            arguments = listOf(
                                navArgument("sessionId") { type = NavType.StringType },
                                // P207：从草稿来源跳进来时要高亮的原话
                                navArgument("highlight") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { backStack ->
                            val sessionId = backStack.arguments?.getString("sessionId").orEmpty()
                            val highlightQuery = backStack.arguments?.getString("highlight").orEmpty()
                            // 每个会话一个 ViewModel：不同 backStackEntry 各自持有，互不串历史
                            val factory = remember(sessionId, characterName, highlightQuery) {
                                ChatViewModelFactory(
                                    conversationUseCase,
                                    historyQuery,
                                    sessionPort,
                                    characterName = characterName,
                                    confirmGateway = toolConfirmGateway,
                                    initialSessionId = sessionId.ifBlank { null },
                                    pendingDrafts = { draftRepository.countPending() },
                                    highlightQuery = highlightQuery.ifBlank { null },
                                    // P208：她会知道今天要做什么、最近做完了什么
                                    taskContext = {
                                        com.lanweixi.core.data.task.TaskDigest.build(
                                            open = taskRepository.listOpen(),
                                            done = taskRepository.listDone(),
                                            now = System.currentTimeMillis(),
                                        )
                                    },
                                    // P210：长会话的「前情摘要」与压缩
                                    summaryProvider = summarizer,
                                    summaryEngine = summarizer,
                                    // P211：token 用量
                                    usageRecorder = usageRecorder,
                                )
                            }
                            val viewModel: ChatViewModel = viewModel(factory = factory)
                            // P75：自定义头像（每次进入聊天页重新读取）
                            val characterAvatarUri = avatar.characterUri
                            val userAvatarUri = avatar.userUri
                            val state by viewModel.state.collectAsState()
                            val pendingConfirm by viewModel.pendingConfirm.collectAsState()
                            // P207：从待审核页返回时刷新「她有 N 条等你确认」
                            LaunchedEffect(Unit) { viewModel.refreshPendingDrafts() }
                            ChatScreen(
                                state = state,
                                onDraftChange = viewModel::onDraftChange,
                                onSend = viewModel::send,
                                onCancel = viewModel::cancelSend,
                                onResend = viewModel::resendUserMessage,
                                onOpenNotes = { navController.navigate(Route.NOTES) },
                            onOpenPrompts = { navController.navigate(Route.PROMPTS) },
                            pendingConfirm = pendingConfirm,
                            onAnswerConfirm = viewModel::answerConfirm,
                            onOpenTasks = { navController.navigate(Route.TASKS) },
                            onOpenPreview = { navController.navigate(Route.PREVIEW) },
                            onOpenDocs = { navController.navigate(Route.DOCS) },
                            onOpenReview = { navController.navigate(Route.REVIEW) },
                            onOpenPlugins = { navController.navigate(Route.PLUGINS) },
                            onOpenSettings = { navController.navigate(Route.SETTINGS) },
                                onRetry = viewModel::retryLast,
                                onBack = { navController.popBackStack() },
                                // P204：引用
                                onQuote = viewModel::quoteMessage,
                                onClearQuote = viewModel::clearQuote,
                                onLocateQuote = viewModel::locateQuote,
                                onClearHighlight = viewModel::clearHighlight,
                                characterName = characterName,
                                // P207：她有草稿等你确认
                                pendingDrafts = state.pendingDrafts,
                                onOpenInbox = { navController.navigate(Route.INBOX) },
                                // P211：长会话往上看
                                onLoadMore = viewModel::loadMoreHistory,
                                // P128：原来这两个变量定义了却忘了往下传，头像永远是默认值
                                characterAvatarUri = characterAvatarUri,
                                userAvatarUri = userAvatarUri,
                            )
                        }
                        composable(Route.NOTES) {
                            val vm: com.lanweixi.feature.notes.NotesViewModel =
                                viewModel(factory = notesFactory)
                            val state by vm.list.collectAsState()
                            // 从编辑页返回时重新拉一次（两个页面各持一个 VM 实例）
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.notes.NotesScreen(
                                state = state,
                                onQueryChange = vm::onQueryChange,
                                onToggleArchived = vm::toggleArchived,
                                onOpen = { id -> navController.navigate("${Route.NOTE_EDIT}/$id") },
                                onCreate = { navController.navigate("${Route.NOTE_EDIT}/0") },
                                onUnarchive = vm::unarchive,
                                onBack = { navController.popBackStack() },
                                onTagSelect = vm::onTagSelect,
                            )
                        }
                        composable(
                            route = "${Route.NOTE_EDIT}/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                        ) { entry ->
                            val noteId = entry.arguments?.getLong("noteId") ?: 0L
                            // 与列表页共用同一个 VM：保存后列表状态直接跟着更新
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.NOTES) }
                            val vm: com.lanweixi.feature.notes.NotesViewModel = if (parent != null) {
                                viewModel(parent, factory = notesFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = notesFactory)
                            }
                            val state by vm.edit.collectAsState()
                            LaunchedEffect(noteId) { vm.edit(noteId) }
                            com.lanweixi.feature.notes.NoteEditScreen(
                                state = state,
                                onTitleChange = vm::onTitleChange,
                                onContentChange = vm::onContentChange,
                                onTagsChange = vm::onTagsChange,
                                onSave = { vm.save { navController.popBackStack() } },
                                onDelete = { vm.delete { navController.popBackStack() } },
                                onArchive = { vm.archive { navController.popBackStack() } },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.PROMPTS) {
                            val vm: com.lanweixi.feature.prompts.PromptsViewModel =
                                viewModel(factory = promptsFactory)
                            val state by vm.list.collectAsState()
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.prompts.PromptsScreen(
                                state = state,
                                onQueryChange = vm::onQueryChange,
                                onSelectCategory = vm::selectCategory,
                                onOpen = { id -> navController.navigate("${Route.PROMPT_EDIT}/$id") },
                                onCreate = { navController.navigate("${Route.PROMPT_EDIT}/0") },
                                onCopy = { row ->
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.copyOf(row)))
                                    android.widget.Toast.makeText(this@MainActivity, "已复制正向提示词", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onTogglePin = { row -> vm.togglePinnedFromList(row.id, !row.pinned) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "${Route.PROMPT_EDIT}/{promptId}",
                            arguments = listOf(navArgument("promptId") { type = NavType.LongType }),
                        ) { entry ->
                            val promptId = entry.arguments?.getLong("promptId") ?: 0L
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.PROMPTS) }
                            val vm: com.lanweixi.feature.prompts.PromptsViewModel = if (parent != null) {
                                viewModel(parent, factory = promptsFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = promptsFactory)
                            }
                            val state by vm.edit.collectAsState()
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            LaunchedEffect(promptId) { vm.edit(promptId) }
                            com.lanweixi.feature.prompts.PromptEditScreen(
                                state = state,
                                onTitleChange = vm::onTitleChange,
                                onCategoryChange = vm::onCategoryChange,
                                onPositiveChange = vm::onPositiveChange,
                                onNegativeChange = vm::onNegativeChange,
                                onParamsChange = vm::onParamsChange,
                                onMemoChange = vm::onMemoChange,
                                onSave = { vm.save { navController.popBackStack() } },
                                onTogglePin = { vm.togglePinned { } },
                                onDelete = { vm.delete { navController.popBackStack() } },
                                onCopyPositive = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.copyPositive()))
                                    vm.notifyCopied("已复制正向")
                                    android.widget.Toast.makeText(this@MainActivity, "已复制正向提示词", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onCopyFull = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.copyFull()))
                                    vm.notifyCopied("已复制完整提示词")
                                    android.widget.Toast.makeText(this@MainActivity, "已复制完整提示词", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.TASKS) {
                            val vm: com.lanweixi.feature.tasks.TasksViewModel =
                                viewModel(factory = tasksFactory)
                            val state by vm.list.collectAsState()
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.tasks.TasksScreen(
                                state = state,
                                isOverdue = vm::isOverdue,
                                onToggleDone = { task, done -> vm.setDone(task.id, done) },
                                onToggleDoneSection = vm::toggleDoneSection,
                                onOpen = { id -> navController.navigate("${Route.TASK_EDIT}/$id") },
                                onCreate = { navController.navigate("${Route.TASK_EDIT}/0") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "${Route.TASK_EDIT}/{taskId}",
                            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
                        ) { entry ->
                            val taskId = entry.arguments?.getLong("taskId") ?: 0L
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.TASKS) }
                            val vm: com.lanweixi.feature.tasks.TasksViewModel = if (parent != null) {
                                viewModel(parent, factory = tasksFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = tasksFactory)
                            }
                            val state by vm.edit.collectAsState()
                            LaunchedEffect(taskId) { vm.edit(taskId) }
                            com.lanweixi.feature.tasks.TaskEditScreen(
                                state = state,
                                onTitleChange = vm::onTitleChange,
                                onDetailChange = vm::onDetailChange,
                                onPriorityChange = vm::onPriorityChange,
                                onDuePresetChange = vm::onDuePresetChange,
                                onSave = { vm.save { navController.popBackStack() } },
                                onDelete = { vm.delete { navController.popBackStack() } },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.DOCS) {
                            val vm: com.lanweixi.feature.docs.DocsViewModel =
                                viewModel(factory = docsFactory)
                            val state by vm.list.collectAsState()
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val docPicker = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument(),
                            ) { uri ->
                                if (uri != null) {
                                    val name = runCatching {
                                        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                                        }
                                    }.getOrNull() ?: "imported.txt"
                                    vm.import(name) {
                                        context.contentResolver.openInputStream(uri)
                                            ?: java.io.ByteArrayInputStream(ByteArray(0))
                                    }
                                }
                            }
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.docs.DocsScreen(
                                state = state,
                                onQueryChange = vm::onQueryChange,
                                // 不在选择器上做格式过滤：各厂商文件管理器对 MIME 的支持参差不齐，
                                // 交给解析器判断并给出明确提示更可靠
                                onImportClick = { docPicker.launch(arrayOf("*/*")) },
                                onOpen = { id -> navController.navigate("${Route.DOC_DETAIL}/$id") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "${Route.DOC_DETAIL}/{docId}",
                            arguments = listOf(navArgument("docId") { type = NavType.LongType }),
                        ) { entry ->
                            val docId = entry.arguments?.getLong("docId") ?: 0L
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.DOCS) }
                            val vm: com.lanweixi.feature.docs.DocsViewModel = if (parent != null) {
                                viewModel(parent, factory = docsFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = docsFactory)
                            }
                            val state by vm.detail.collectAsState()
                            LaunchedEffect(docId) { vm.detail(docId) }
                            com.lanweixi.feature.docs.DocDetailScreen(
                                state = state,
                                onDelete = { vm.delete(docId) { navController.popBackStack() } },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.REVIEW) {
                            val vm: com.lanweixi.feature.review.ReviewViewModel =
                                viewModel(factory = reviewFactory)
                            val state by vm.list.collectAsState()
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.review.ReviewListScreen(
                                state = state,
                                onNew = { navController.navigate("${Route.REVIEW_EDIT}/0") },
                                onOpen = { id -> navController.navigate("${Route.REVIEW_EDIT}/$id") },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "${Route.REVIEW_EDIT}/{reviewId}",
                            arguments = listOf(navArgument("reviewId") { type = NavType.LongType }),
                        ) { entry ->
                            val reviewId = entry.arguments?.getLong("reviewId") ?: 0L
                            val parent = remember(entry) { navController.parentEntryOrNull(Route.REVIEW) }
                            val vm: com.lanweixi.feature.review.ReviewViewModel = if (parent != null) {
                                viewModel(parent, factory = reviewFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = reviewFactory)
                            }
                            val state by vm.edit.collectAsState()
                            // 列表页与编辑页各持一个 VM 实例，新建必须在这里初始化
                            LaunchedEffect(reviewId) {
                                if (reviewId == 0L) vm.newReview() else vm.open(reviewId)
                            }
                            com.lanweixi.feature.review.ReviewEditScreen(
                                state = state,
                                onPeriodChange = vm::switchPeriod,
                                onSummaryChange = vm::onSummaryChange,
                                onSave = { vm.save { navController.popBackStack() } },
                                onDelete = { vm.delete(reviewId) { navController.popBackStack() } },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.PLUGINS) {
                            val vm: com.lanweixi.feature.plugins.PluginsViewModel =
                                viewModel(factory = pluginsFactory)
                            val state by vm.list.collectAsState()
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val zipPicker = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument(),
                            ) { uri ->
                                if (uri != null) {
                                    // SAF 的 Uri 只在这一刻可读：先落成临时文件再安装
                                    val tmp = java.io.File(
                                        context.cacheDir,
                                        "plugin_import_${System.currentTimeMillis()}.zip",
                                    )
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            tmp.outputStream().use { output -> input.copyTo(output) }
                                        }
                                    }.onSuccess {
                                        vm.install(tmp) {
                                            navController.navigate(Route.PLUGIN_DETAIL)
                                        }
                                    }.onFailure {
                                        tmp.delete()
                                    }
                                }
                            }
                            LaunchedEffect(Unit) { vm.refresh() }
                            com.lanweixi.feature.plugins.PluginsScreen(
                                state = state,
                                onInstallClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                onInstallDemo = { vm.installDemo { navController.navigate(Route.PLUGIN_DETAIL) } },
                                onOpenRemote = { remoteOpen = true },
                                onCheckUpdates = checkUpdates,
                                onOpen = { id ->
                                    vm.open(id)
                                    navController.navigate(Route.PLUGIN_DETAIL)
                                },
                                onToggleEnabled = { id, enabled -> vm.toggleEnabled(id, enabled) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.PLUGIN_DETAIL) {
                            val parent = remember { navController.parentEntryOrNull(Route.PLUGINS) }
                            val vm: com.lanweixi.feature.plugins.PluginsViewModel = if (parent != null) {
                                viewModel(parent, factory = pluginsFactory)
                            } else {
                                // P199：从全局搜索直达时父列表不在栈里，自己建一个
                                viewModel(factory = pluginsFactory)
                            }
                            val state by vm.detail.collectAsState()
                            com.lanweixi.feature.plugins.PluginDetailScreen(
                                state = state,
                                onToggleGrant = vm::toggleGrant,
                                onGrantAll = vm::grantAll,
                                onRevokeAll = vm::revokeAll,
                                onSetEnabled = vm::setEnabled,
                                onUninstall = { deleteSandbox ->
                                    vm.uninstall(deleteSandbox) { navController.popBackStack() }
                                },
                                onOpenConfig = {
                                    val m = state.manifest
                                    val installed = pluginRegistry.installed()
                                    val target = installed.firstOrNull { it.manifest.id == m?.id }
                                    if (m != null && target != null) {
                                        configFields = com.lanweixi.domain.plugin.PluginConfigSchemaParser
                                            .parse(m.configSchema)
                                        val dir = java.io.File(target.dirPath)
                                        configPluginDir = dir
                                        configValues = configStore.withDefaults(
                                            configFields,
                                            configStore.load(dir),
                                        )
                                        configDialogOpen = true
                                    }
                                },
                                onOpenPage = { combined ->
                                    navController.navigate(Route.PLUGIN_PAGE + "/" + combined)
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Route.PREVIEW) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                com.lanweixi.core.design.mucha.MuchaPreview()
                                Text(
                                    "返回",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(androidx.compose.ui.Alignment.TopEnd)
                                        .padding(top = 16.dp, end = 20.dp)
                                        .clickable { navController.popBackStack() },
                                )
                            }
                        }
                        composable(Route.ONBOARDING) {
                            val probeFn:
                                suspend (String, String, String) -> com.lanweixi.core.network.ProbeResult =
                                { baseUrl, key, model ->
                                    connectivityProbe.probe(baseUrl, key, model)
                                }
                            val factory = remember {
                                com.lanweixi.feature.onboarding.OnboardingViewModelFactory(
                                    store = this@MainActivity.configStore,
                                    probe = probeFn,
                                    replay = true,
                                )
                            }
                            val onboardingVm: com.lanweixi.feature.onboarding.OnboardingViewModel =
                                viewModel(factory = factory)
                            val onboardingState by onboardingVm.state.collectAsState()
                            com.lanweixi.feature.onboarding.OnboardingScreen(
                                state = onboardingState,
                                onNameChange = onboardingVm::onNameChange,
                                onNext = onboardingVm::next,
                                onBack = onboardingVm::back,
                                onProviderSelect = onboardingVm::onProviderSelect,
                                onBaseUrlChange = onboardingVm::onBaseUrlChange,
                                onModelChange = onboardingVm::onModelChange,
                                onKeyChange = onboardingVm::onKeyChange,
                                onTest = onboardingVm::testConnection,
                                onFinish = {
                                    onboardingVm.complete { name ->
                                        characterName = name
                                        navController.popBackStack()
                                    }
                                },
                                onSkip = {
                                    onboardingVm.skip { name ->
                                        characterName = name
                                        navController.popBackStack()
                                    }
                                },
                                onExit = { navController.popBackStack() },
                            )
                        }
                        composable(Route.SETTINGS) {
                            val viewModel: SettingsViewModel = viewModel(factory = settingsFactory)
                            // P210：摘要导出（SAF）
                            var pendingSummaryMd by remember { mutableStateOf<String?>(null) }
                            val summaryMdExport = rememberLauncherForActivityResult(
                                ActivityResultContracts.CreateDocument("text/markdown")
                            ) { uri ->
                                val md = pendingSummaryMd
                                pendingSummaryMd = null
                                if (uri != null && md != null) {
                                    runCatching {
                                        contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) }
                                    }.onSuccess { backupMessage = "已导出会话摘要" }
                                        .onFailure { backupMessage = it.message ?: "写入失败" }
                                }
                            }
                            var summaryCount by remember { mutableStateOf(0) }
                            LaunchedEffect(Unit) {
                                summaryCount = withContext(Dispatchers.IO) {
                                    runCatching { summaryRepository.count() }.getOrDefault(0)
                                }
                            }
                            // P211：今天 / 本周 token 用量
                            var usageToday by remember { mutableStateOf(0L) }
                            var usageWeek by remember { mutableStateOf(0L) }
                            LaunchedEffect(Unit) {
                                val zone = java.time.ZoneId.systemDefault()
                                val today = java.time.LocalDate.now(zone)
                                val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                                val weekStart = today
                                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                                    .atStartOfDay(zone).toInstant().toEpochMilli()
                                val nowTs = System.currentTimeMillis()
                                val totals = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val type = com.lanweixi.core.data.usage.UsageDigest.EVENT_TYPE
                                        com.lanweixi.core.data.usage.UsageDigest
                                            .sumOf(eventRepository.listByTypeBetween(type, dayStart, nowTs)).total to
                                            com.lanweixi.core.data.usage.UsageDigest
                                                .sumOf(eventRepository.listByTypeBetween(type, weekStart, nowTs)).total
                                    }.getOrDefault(0L to 0L)
                                }
                                usageToday = totals.first
                                usageWeek = totals.second
                            }
                            // P212：崩溃日志（只在本机，可导出）
                            var crashCount by remember { mutableStateOf(0) }
                            LaunchedEffect(Unit) {
                                crashCount = com.lanweixi.client.crash.CrashLogger.count(this@MainActivity)
                            }
                            var pendingCrashLog by remember { mutableStateOf<String?>(null) }
                            val crashLogExport = rememberLauncherForActivityResult(
                                ActivityResultContracts.CreateDocument("text/plain")
                            ) { uri ->
                                val text = pendingCrashLog
                                pendingCrashLog = null
                                if (uri != null && text != null) {
                                    runCatching {
                                        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                                    }.onSuccess { backupMessage = "已导出崩溃日志" }
                                        .onFailure { backupMessage = it.message ?: "写入失败" }
                                }
                            }
                            // P210：导出摘要时给群聊用群名
                            val agentStore = remember {
                                com.lanweixi.core.data.agent.AgentStore(applicationContext)
                            }
                            // P192：记忆导入（.md / .txt 都行）
                            val memoryImport = rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    scope.launch {
                                        runCatching {
                                            contentResolver.openInputStream(uri)
                                                ?.bufferedReader()?.use { it.readText() }
                                        }.getOrNull()?.let { raw ->
                                            if (raw.isNotBlank()) viewModel.onMemoryChange(raw)
                                        }
                                    }
                                }
                            }
                            val state by viewModel.state.collectAsState()
                            SettingsScreen(
                                onOpenLogs = { navController.navigate(Route.LOGS) },
                                onOpenAgents = { navController.navigate(Route.AGENTS) },
                                onOpenGroups = { navController.navigate(Route.GROUPS) },
                                onOpenOnboarding = { navController.navigate(Route.ONBOARDING) },
                                state = state,
                                onCharacterNameChange = viewModel::onCharacterNameChange,
                                onPersonaChange = viewModel::onPersonaChange,
                                onImportMemory = {
                                    memoryImport.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                                },
                                // P211：选服务商预设，自动填 BaseURL / 模型
                                onProviderSelect = viewModel::onProviderSelect,
                                onClearMemory = { viewModel.onMemoryChange("") },
                                onSaveMemory = { text -> viewModel.saveMemoryNow(text) },
                                onKeyChange = viewModel::onKeyChange,
                                onModelChange = viewModel::onModelChange,
                                onBaseUrlChange = viewModel::onBaseUrlChange,
                                onSave = {
                                    viewModel.save {
                                        characterName = viewModel.state.value.characterName
                                        navController.popBackStack()
                                    }
                                },
                                onPickAvatar = { kind ->
                                    pendingAvatarKind = kind
                                    avatarPicker.launch("image/*")
                                },
                                onBarAlphaChange = { value, persist ->
                                    muchaTuning = muchaTuning.copy(barAlpha = value)
                                    viewModel.onBarAlphaChange(value, persist)
                                },
                                onIconStyleChange = { id ->
                                    iconStyle = if (id == "detailed") {
                                        com.lanweixi.core.design.components.IconStyle.DETAILED
                                    } else {
                                        com.lanweixi.core.design.components.IconStyle.VECTOR
                                    }
                                    viewModel.onIconStyleChange(id) { }
                                },
                                onThemeChange = { id ->
                                    themeMode = when (id) {
                                        "daylight_gold" -> MuchaThemeMode.DaylightGold
                                        "night_purple" -> MuchaThemeMode.StarryNight
                                        else -> MuchaThemeMode.PureMaterial3
                                    }
                                    viewModel.onThemeChange(id) { }
                                },
                                    onExportBackup = {
                                        scope.launch {
                                            runCatching { backupManager.exportJson() }
                                                .onSuccess { json ->
                                                    pendingBackupJson = json
                                                    backupExport.launch("lanweixi-backup.json")
                                                }
                                                .onFailure { backupMessage = it.message ?: "导出失败" }
                                        }
                                    },
                                    onImportBackup = { backupImport.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                    onExportNotesMarkdown = {
                                        scope.launch {
                                            val all = runCatching {
                                                noteRepository.listActive() + noteRepository.listArchived()
                                            }.getOrDefault(emptyList())
                                            if (all.isEmpty()) {
                                                backupMessage = "还没有笔记可以导出"
                                            } else {
                                                pendingNotesMd = com.lanweixi.core.data.export.NotesMarkdown.export(all)
                                                val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                                                    .format(java.util.Date())
                                                notesMdExport.launch("lanweixi-notes-$day.md")
                                            }
                                        }
                                    },
                                    onExportSummaries = {
                                        scope.launch {
                                            val rows = runCatching { summaryRepository.listAll() }
                                                .getOrDefault(emptyList())
                                            if (rows.isEmpty()) {
                                                backupMessage = "还没有生成过会话摘要"
                                            } else {
                                                // 会话标题：群聊显示群名，单聊显示角色名
                                                val groups = runCatching { agentStore.listGroups() }
                                                    .getOrDefault(emptyList())
                                                    .associate { it.id to it.title }
                                                val rendered = com.lanweixi.core.data.export.SummariesMarkdown.render(
                                                    rows.map { row ->
                                                        com.lanweixi.core.data.export.SummariesMarkdown.Row(
                                                            sessionId = row.sessionId,
                                                            title = groups[row.sessionId] ?: characterName,
                                                            content = row.content,
                                                            coveredCount = row.coveredCount,
                                                            updatedAt = row.updatedAt,
                                                        )
                                                    },
                                                )
                                                pendingSummaryMd = rendered
                                                val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                                                    .format(java.util.Date())
                                                summaryMdExport.launch("lanweixi-summaries-$day.md")
                                            }
                                        }
                                    },
                                    summaryCount = summaryCount,
                                    // P211：格式化在 app 层做，settings 模块不依赖 core:data
                                    usageTodayText = com.lanweixi.core.data.usage.UsageDigest.format(usageToday),
                                    usageWeekText = com.lanweixi.core.data.usage.UsageDigest.format(usageWeek),
                                    // P212：崩溃日志与版本
                                    crashCount = crashCount,
                                    onExportCrashLog = {
                                        pendingCrashLog =
                                            com.lanweixi.client.crash.CrashLogger.exportText(this@MainActivity)
                                        crashLogExport.launch("lanweixi-crash-${System.currentTimeMillis()}.txt")
                                    },
                                    appVersion = runCatching {
                                        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                                    }.getOrDefault(""),
                                    onCheckReminders = {
                                        scope.launch {
                                            val outcome = runCatching {
                                                com.lanweixi.client.reminder.TaskReminderRunner.run(this@MainActivity)
                                            }.getOrNull()
                                            backupMessage = when (outcome) {
                                                is com.lanweixi.client.reminder.TaskReminderRunner.Outcome.Sent ->
                                                    "已发 ${outcome.count} 条任务提醒"
                                                is com.lanweixi.client.reminder.TaskReminderRunner.Outcome.Planned ->
                                                    "有 ${outcome.planned} 个任务该提醒，但通知没发出去——检查通知权限"
                                                com.lanweixi.client.reminder.TaskReminderRunner.Outcome.Nothing ->
                                                    "没有要提醒的任务"
                                                null -> "检查失败"
                                            }
                                        }
                                    },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                            }
                        }
                    }
                }
                }
            }
        }
            }
    }
}

/** P201：首启引导（渲染在 NavHost 之外，走完直接切主界面，不占回退栈）。 */
@androidx.compose.runtime.Composable
private fun FirstRunOnboarding(
    configStore: ChatConfigStore,
    probe: com.lanweixi.core.network.ConnectivityProbe,
    onDone: (String) -> Unit,
) {
    val probeFn: suspend (String, String, String) -> com.lanweixi.core.network.ProbeResult =
        { baseUrl, key, model -> probe.probe(baseUrl, key, model) }
    val factory = remember {
        com.lanweixi.feature.onboarding.OnboardingViewModelFactory(
            store = configStore,
            probe = probeFn,
            replay = false,
        )
    }
    val vm: com.lanweixi.feature.onboarding.OnboardingViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    com.lanweixi.feature.onboarding.OnboardingScreen(
        state = state,
        onNameChange = vm::onNameChange,
        onNext = vm::next,
        onBack = vm::back,
        onProviderSelect = vm::onProviderSelect,
        onBaseUrlChange = vm::onBaseUrlChange,
        onModelChange = vm::onModelChange,
        onKeyChange = vm::onKeyChange,
        onTest = vm::testConnection,
        onFinish = { vm.complete(onDone) },
        onSkip = { vm.skip(onDone) },
        onExit = null,
    )
}

/** P12b：最小路由表。 */
object Route {
    /** P198：会话列表主页（单聊 + 群聊混排）。 */
    const val SESSIONS = "sessions"

    /** P198：单聊页（参数化：conversation/{sessionId}）。 */
    const val CONVERSATION = "conversation"

        /** P154：「更多」一级入口。 */
        const val MORE = "more"

        /** P165：插件界面页。 */
        const val PLUGIN_PAGE = "plugin_page"

        /** P150：慕夏纹样预览（临时）。 */
        const val PREVIEW = "mucha_preview"
    const val SETTINGS = "settings"
    const val HOME = "home"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note_edit"
    const val PROMPTS = "prompts"
    const val PROMPT_EDIT = "prompt_edit"
    const val TASKS = "tasks"
    const val TASK_EDIT = "task_edit"
    const val DOCS = "docs"
    const val DOC_DETAIL = "doc_detail"
    const val REVIEW = "review"
    const val REVIEW_EDIT = "review_edit"
    const val PLUGINS = "plugins"
    const val PLUGIN_DETAIL = "plugin_detail"
    const val LOGS = "host_logs"
    const val AGENTS = "agents"
    const val GROUPS = "groups"

    /** P197：群聊页（参数化：group_chat/{groupId}）。 */
    const val GROUP_CHAT = "group_chat"

    /** P199：全局搜索。 */
    const val SEARCH = "search"

    /** P206：AI 草稿待审核列表。 */
    const val INBOX = "inbox"

    /** P207：草稿修改页。 */
    const val DRAFT_EDIT = "draft_edit"

    /** P201：首次启动引导（设置里的「重看引导」也走这个路由）。 */
    const val ONBOARDING = "onboarding"
}

/** P07a 轻量工厂：不引入 @HiltViewModel，手工注入用例与只读端口。 */
class ChatViewModelFactory(
    private val useCase: ConversationUseCase,
    private val historyQuery: ChatHistoryQueryPort,
    private val session: SessionPort? = null,
    private val confirmGateway: com.lanweixi.domain.tools.ToolConfirmGateway? = null,
    private val characterName: String = com.lanweixi.domain.prompt.DEFAULT_CHARACTER_NAME,
    /** P198：从会话列表点进来时指定打开哪条会话。 */
    private val initialSessionId: String? = null,
    /** P207：待审核条数（进页面/发送后刷新）。 */
    private val pendingDrafts: (suspend () -> Int)? = null,
    /** P207：从草稿来源跳进来时要定位的原话。 */
    private val highlightQuery: String? = null,
    /** P208：任务摘要（今天到期 / 最近完成）。 */
    private val taskContext: (suspend () -> String?)? = null,
    /** P210：会话摘要（前情摘要 + 压缩）。 */
    private val summaryProvider: com.lanweixi.feature.chat.SummaryProvider? = null,
    private val summaryEngine: com.lanweixi.feature.chat.SummaryEngine? = null,
    /** P211：token 用量记录。 */
    private val usageRecorder: com.lanweixi.feature.chat.UsageRecorder? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            useCase = useCase,
            historyQuery = historyQuery,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            session = session,
            confirmGateway = confirmGateway,
            characterName = characterName,
            initialSessionId = initialSessionId,
            pendingDrafts = pendingDrafts,
            highlightQuery = highlightQuery,
            taskContext = taskContext,
            summaryProvider = summaryProvider,
            summaryEngine = summaryEngine,
            usageRecorder = usageRecorder,
        ) as T
    }
}

/** P198 会话列表主页工厂。 */
class SessionsViewModelFactory(
    private val source: com.lanweixi.feature.chat.SessionsSource,
    private val characterName: String,
    private val characterAvatarUri: String?,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return com.lanweixi.feature.chat.SessionsViewModel(
            source = source,
            characterName = characterName,
            characterAvatarUri = characterAvatarUri,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ) as T
    }
}

/** P197 群聊页工厂：成员现取（改名/新增/移出立即生效），引擎与端口在 Activity 里建一次。 */
class GroupChatViewModelFactory(
    private val groupId: String,
    private val groupTitle: String,
    private val hostName: String,
    private val hostAvatarUri: String?,
    /** P218：每次刷新成员时现取群的最新 memberIds（群聊页里可以随时加人/移人）。 */
    private val memberIdsProvider: () -> List<String>,
    private val agentStore: com.lanweixi.core.data.agent.AgentStore,
    private val port: com.lanweixi.feature.chat.GroupChatPort,
    private val useCase: com.lanweixi.feature.chat.GroupChatUseCase,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return com.lanweixi.feature.chat.GroupChatViewModel(
            groupId = groupId,
            groupTitle = groupTitle,
            hostName = hostName,
            hostAvatarUri = hostAvatarUri,
            membersProvider = {
                // 群成员关系现取：群里加人/移人后 refreshMembers() 立即生效
                val ids = memberIdsProvider().toSet()
                agentStore.list().filter { it.id in ids }
            },
            port = port,
            useCase = useCase,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ) as T
    }
}

/** P08b 设置页工厂：只注入可写的配置存储。 */
class SettingsViewModelFactory(
    private val store: ChatConfigStore,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            store = store,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ) as T
    }
}


/**
 * P199：父页面不一定在回退栈里——从全局搜索可以直达编辑页 / 详情页。
 * 拿不到返回 null，调用方自己建 VM，别让 getBackStackEntry 抛异常把 App 带走。
 */
private fun androidx.navigation.NavController.parentEntryOrNull(
    route: String,
): androidx.navigation.NavBackStackEntry? = runCatching { getBackStackEntry(route) }.getOrNull()
