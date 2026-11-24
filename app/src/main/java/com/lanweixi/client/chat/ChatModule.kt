package com.lanweixi.client.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.withTransaction
import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.core.network.ChatStreamClient
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.domain.prompt.PromptBuilder
import com.lanweixi.feature.chat.ChatHistoryPort
import com.lanweixi.feature.chat.ChatHistoryQueryPort
import com.lanweixi.feature.chat.ChatStreamClientRunner
import com.lanweixi.feature.chat.ConversationEngine
import com.lanweixi.feature.chat.ConversationUseCase
import com.lanweixi.feature.chat.EventPort
import com.lanweixi.feature.chat.SessionPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 依赖注入总装（app 层）。
 *
 * 只做「接口 → 生产实现」的绑定，不写业务逻辑。
 * 陪伴引擎已整体移除，这里只剩对话主链路需要的绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    /** 设置用的 DataStore。 */
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("lanweixi_settings") },
        )

    @Provides
    @Singleton
    fun provideChatConfigStore(
        dataStore: DataStore<Preferences>,
        cipher: ApiKeyCipher,
    ): ChatConfigStore = LocalDataStoreConfigProvider(dataStore, cipher)

    @Provides
    @Singleton
    fun provideSessionPort(
        kvDao: com.lanweixi.core.data.dao.KvDao,
        messageDao: com.lanweixi.core.data.dao.MessageDao,
    ): SessionPort = RoomSessionPort(kvDao, messageDao)

    /** P36：API Key 加密器（Android Keystore AES-GCM）。 */
    @Provides
    @Singleton
    fun provideApiKeyCipher(): ApiKeyCipher = KeystoreCipher()

    @Provides
    @Singleton
    fun provideChatConfigProvider(store: ChatConfigStore): ChatConfigProvider = store

    @Provides
    @Singleton
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    @Provides
    @Singleton
    fun provideChatStreamClientRunner(client: ChatStreamClient): ChatStreamClientRunner =
        ChatStreamClientRunner(client)

    @Provides
    @Singleton
    fun provideRoomChatHistoryPort(
        messages: MessageRepository,
        session: SessionPort,
    ): RoomChatHistoryPort = RoomChatHistoryPort(messages, session)

    @Provides
    @Singleton
    fun provideChatHistoryPort(port: RoomChatHistoryPort): ChatHistoryPort = port

    /** P07a：ViewModel 只拿只读端口。 */
    @Provides
    @Singleton
    fun provideChatHistoryQueryPort(port: RoomChatHistoryPort): ChatHistoryQueryPort = port

    /** P25：对话完成事件落库（只写，不读）。 */
    @Provides
    @Singleton
    fun provideEventPort(dao: com.lanweixi.core.data.dao.EventDao): EventPort =
        RoomEventPort(dao)

    /** P139：敏感工具确认通道（引擎挂起、UI 弹窗）。 */
    @Provides
    @Singleton
    fun provideToolConfirmGateway(): com.lanweixi.domain.tools.ToolConfirmGateway =
        com.lanweixi.domain.tools.ToolConfirmGateway()

    /** P138：宿主内置工具（只读查库 + P206 写草稿）。 */
    @Provides
    @Singleton
    fun provideHostTools(
        notes: com.lanweixi.core.data.repository.NoteRepository,
        tasks: com.lanweixi.core.data.repository.TaskRepository,
        documents: com.lanweixi.core.data.repository.DocumentRepository,
        prompts: com.lanweixi.core.data.repository.PromptRepository,
        drafts: com.lanweixi.core.data.repository.DraftRepository,
        session: SessionPort,
    ): com.lanweixi.core.data.tools.HostTools =
        com.lanweixi.core.data.tools.HostTools(
            notes = notes,
            tasks = tasks,
            documents = documents,
            prompts = prompts,
            drafts = drafts,
            // 草稿要记住从哪个会话来的（P207 跳回原消息用）
            sessionId = { session.current() },
        )

    /** P206：草稿审核（待审核页的确认 / 驳回）。 */
    @Provides
    @Singleton
    fun provideDraftReview(
        db: com.lanweixi.core.data.LanWeixiDatabase,
        drafts: com.lanweixi.core.data.repository.DraftRepository,
        notes: com.lanweixi.core.data.repository.NoteRepository,
        tasks: com.lanweixi.core.data.repository.TaskRepository,
    ): com.lanweixi.core.data.draft.DraftReview =
        com.lanweixi.core.data.draft.DraftReviewer(
            drafts = drafts,
            notes = notes,
            tasks = tasks,
            // P216：「写正式记录 + 标草稿已处理」必须原子，中断时不能留孤儿数据
            transaction = { block -> db.withTransaction { block() } },
        )

    /** P140：插件注册表（Context 依赖，Hilt 不知道怎么造，手动提供）。 */
    @Provides
    @Singleton
    fun providePluginRegistry(
        @ApplicationContext context: Context,
    ): com.lanweixi.core.data.plugin.PluginRegistry =
        com.lanweixi.core.data.plugin.PluginRegistry(context)

    /** P140：把已启用插件加载成工具（每次现读磁盘，装完立刻生效）。 */
    @Provides
    @Singleton
    fun providePluginToolProvider(
        registry: com.lanweixi.core.data.plugin.PluginRegistry,
        notes: com.lanweixi.core.data.repository.NoteRepository,
        tasks: com.lanweixi.core.data.repository.TaskRepository,
        documents: com.lanweixi.core.data.repository.DocumentRepository,
        prompts: com.lanweixi.core.data.repository.PromptRepository,
        reviews: com.lanweixi.core.data.repository.ReviewRepository,
    ): com.lanweixi.core.data.plugin.lua.PluginToolProvider =
        com.lanweixi.core.data.plugin.lua.PluginToolProvider(registry) { granted, _ ->
            com.lanweixi.core.data.plugin.lua.LuaHostApis(
                notes = notes,
                tasks = tasks,
                documents = documents,
                prompts = prompts,
                granted = granted,
                globalSearch = com.lanweixi.core.data.search.GlobalSearch(
                    notes = notes,
                    tasks = tasks,
                    documents = documents,
                    prompts = prompts,
                    reviews = reviews,
                ),
                logger = { android.util.Log.d("AI-Chat", "[plugin] " + it) },
            )
        }

    @Provides
    @Singleton
    fun provideConversationUseCase(engine: ConversationEngine): ConversationUseCase = engine

    @Provides
    @Singleton
    fun provideConversationEngine(
        runner: ChatStreamClientRunner,
        promptBuilder: PromptBuilder,
        history: ChatHistoryPort,
        configProvider: ChatConfigProvider,
        eventPort: EventPort,
        session: SessionPort,
        hostTools: com.lanweixi.core.data.tools.HostTools,
        confirmGateway: com.lanweixi.domain.tools.ToolConfirmGateway,
        pluginTools: com.lanweixi.core.data.plugin.lua.PluginToolProvider,
    ): ConversationEngine = ConversationEngine(
        stream = runner,
        promptBuilder = promptBuilder,
        history = history,
        configProvider = configProvider,
        eventPort = eventPort,
        sessionProvider = session,
        // 每次发送现取，插件启用/停用能立刻生效（P4 接入插件执行器后这里会变）
        tools = {
            val all = hostTools.executors() + pluginTools.executorsOnly()
            all.map { it.spec }
        },
        executors = {
            val all = hostTools.executors() + pluginTools.executorsOnly()
            all.associateBy { it.spec.name }
        },
        // 敏感工具一律交给 UI 确认；用户不点就是拒绝
        confirmSensitive = { spec, call -> confirmGateway.ask(spec, call) },
    )
}
