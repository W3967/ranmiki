# AI-Chat · 原生重写（T01–T05 + P06a/b/c + P07a + P08a/b + P09–P29）

范围严格限定在 T01–T05、P06a–P06c、P07a、P08a/P08b、P09–P29（取消发送、复制/重发与长按菜单、主题落位、路由与设置归并、依赖清理、记忆/日记/生理/事件页、主动消息骨架、通知权限、模型包导入与清单管理、关系系统、usage 展示、品牌与关系满值）；未完成项见第 8、10 节。

## 1. 当前状态

| 卡片 | 内容 | 状态 |
| --- | --- | --- |
| T01 | 多模块工程骨架 + version catalog | 已完成，空壳真机启动验证通过 |
| T02 | Room 实体 / DAO / Repository | 已完成，14 个 JVM 测试通过 |
| T03 | OkHttp SSE 客户端与取消 | 已完成，22 个单测 + 七场景端到端通过 |
| T04 | 生理引擎移植到 :domain:vitals | 已完成，**可复现夹具 217/217 全绿**（原始夹具见第 10 节） |
| T05 | 记忆系统移植到 :domain:memory | 已完成，**夹具 41/41 全绿**（8 类操作） |
| P06a | 提示词预算重构到 :domain:prompt + memory 卫生收尾 | 已完成：`PromptBuilderTest 15`、`MemoryHygieneTest 2`、memory 回放 41 例 |
| T06 | 旧数据迁移 | 按方案 5.2 跳过，**仓库内无任何迁移代码** |
| P06b | fake 用例与引擎装配 | 已完成：fake 版对话用例（7 例）+ Hilt 端口装配；`assembleDebug` 通过；schema 未变 |
| P06c | 生产 Room/Hilt 接线 | 已完成：`ChatHistoryPort` → `RoomChatHistoryPort`；`send()` 全 suspend；`RoomChatHistoryPortTest 4` |
| P06c.1 | UI 前置硬化 | 已完成：`send()` 全段 `withContext(dispatcher)`、`ChatConfig` seam（空 key 短路）、`ConversationEngine` 单例 |
| P06c.1.1 | 测试稳定性与文档修订 | 已完成：`RandomSourceTest 5 例`；确定性 `SequenceRandom` 断言；全量 73 tests / 1 skipped / 0 failures |
| P07a | Compose 聊天首屏与 ViewModel 状态闭环 | 已完成：`ChatViewModel` + `ChatScreen`；`ChatViewModelTest 9` + `ConversationEngineDeltaCallbackTest 5`；**无 Compose 组件测试** |
| P07a.1 | 生命周期与文档收口 | 已完成：`onCleared()` 取消注入 scope；`viewModel(factory)` 标准保留；历史加载失败 Notice；列表自动滚动；`ChatViewModelTest 11` |
| P08a | 配置端口与 API Key 注入 | 已完成：`ChatConfigProvider`（send 每次取最新配置）；`LocalDataStoreConfigProvider`；`LocalDataStoreConfigProviderTest 4` + `ConversationEngineConfigTest 4` |
| P08b | 最小设置页 | 已完成：`SettingsScreen` + `SettingsViewModel`（保存 Key → 返回聊天；失败兜底）；`SettingsViewModelTest 5`；无 Navigation / 无 Compose 组件测试 |
| P09 | 取消发送 | 已完成：`sendJob` 取消 + `onCancel` 按钮；取消传导到 `StreamHandle`，不落库、不加记忆；`ChatViewModelTest 14` + `ConversationEngineCancelTest 1` |
| P10 | 消息复制与重发 | 已完成：长按复制（Compose ClipboardManager）、user 气泡短按重发（`resendUserMessage` 复用 `startSend`）；`ChatViewModelTest 18` |
| P11 | 复制/重发长按菜单收口 | 已完成：user 气泡长按弹「复制 / 重发」菜单，短按无动作；assistant 长按只复制；sending 时重发禁用 |
| P12a | 主题落位修复 | 已完成：`LanWeiXiTheme` 收回到 `:app`（唯一使用方），删除 app 内联色板；`:core:design` 暂留空壳；跨模块 unresolved 根因未定位、已挂账 |
| P12b | Navigation 最小路由 + 设置归并 | 已完成：`NavHost`（chat/settings 两路由）替代 `showSettings` 状态切换；`SettingsScreen/SettingsViewModel` 迁至 `:feature:settings`；`ChatConfig/ChatConfigProvider/ChatConfigStore` 下沉到 `:domain:prompt` |
| P13 | 依赖与模块边界清理 | 已完成：移除 app/chat/home/memory/settings 对 `:core:design` 的死依赖，补 `compose.bom` platform；`:core:design` 留空壳 |
| P14 | 记忆页（只读） | 已完成：`MemoryViewModel` + `MemoryScreen`（facts 列表 / tier / 重要度 / 空态 / 错误重试）；路由 `memory`；`MemoryViewModelTest 4` |
| P15 | 日记页（只读） | 已完成：`DiaryViewModel` + `DiaryScreen`（dayKey / mood / content / 空态 / 错误重试）；路由 `diary`；`DiaryViewModelTest 4` |
| P16 | 生理面板（只读） | 已完成：新建 `:feature:vitals`；`VitalsViewModel` + `VitalsScreen`（精力/困倦/渴/饿/情绪/体温/回复条数）；路由 `vitals`；`VitalsViewModelTest 4` |
| P17 | 主动消息（调度骨架） | 已完成：WorkManager 15 分钟周期 tick + 通知渠道 + `ProactiveMessagePolicy` 纯逻辑；`ProactiveMessagePolicyTest 7`；**通知文案是模板，未接 LLM 生成** |
| P18 | 通知权限入口 | 已完成：API 33+ 首启请求 `POST_NOTIFICATIONS`（`rememberLauncherForActivityResult`），拒绝不影响聊天 |
| P19 | 模型包导入器 | 已完成：新建 `:core:model`；`ModelImporter`（moc3 / 路径穿越 / 纹理尺寸 / 总大小 校验 + 解包）；`ModelImporterTest 6`；**渲染运行时未做** |
| P20 | 模型导入 UI 接线 | 已完成：设置页「导入模型 (.zip)」按钮 + 系统文件选择器 + 结果提示；`SettingsViewModelTest 7`（+2 导入用例） |
| P21 | 关系系统 | 已完成：`RelationEngine`（好感/信任增量 + 阶段阈值）+ 对话成功后 bump（写 characters 表）+ 聊天页关系标签；`RelationEngineTest 6` |
| P22 | 事件/里程碑页 | 已完成：`EventsViewModel` + `EventsScreen`（events 表只读，type/payload/时间，空态/错误重试）；路由 `events`，入口在记忆页；`EventsViewModelTest 4` |
| P23 | 设置页扩展 | 已完成：`ChatConfigStore` 新增 `saveModel`/`saveBaseUrl`；设置页新增模型与 Base URL 输入；DataStore 三键存储；`LocalDataStoreConfigProviderTest 7` |
| P24 | usage 展示 | 已完成：`ConversationOutcome` 透传 `promptTokens`/`completionTokens`；成功对话后聊天页显示 `tokens: x → y`；`ConversationEngineTest +1` |
| P25 | 剩余小项 | 已完成：① 失败 banner 下「重试上一条」；② 记忆页删除（`MemorySystem.deleteMemory` + 端口删除）；③ 对话完成写入 events 表（`EventPort`）；并修复 P21 relation 未接入引擎 provider 的问题 |
| P26 | Cubism 清单解析 | 已完成：`:core:model` 解析 `model3.json`（Moc/Textures/Physics/Pose/Expressions/Motions）；`ModelManifestParserTest 5` |
| P27 | 模型清单与管理 | 已完成：导入成功后登记 models 表（解析清单决定可激活）；设置页「已导入模型」列表与「使用」切换；`RoomModelsPort` |
| P28 | 渲染会话状态机 | 已完成：`ModelSession`（Idle/Speaking/Paused、motion 组选择、表情语义匹配）；`ModelSessionTest 7`；**GL 渲染未接入（无 Cubism SDK）** |
| P29 | 品牌与关系满值 | 已完成：① 应用图标替换为指定图片（5 密度 × 方形/圆形，删除 anydpi-v26）；② `app_name` 改为 `AI-Chat`（角色名仍为「兰未晞」）；③ 好感度/信任固定 100、阶段「亲密」、永不下降（`RelationEngine.MAXED` + `RoomRelationPort` 双重保证）；`RelationEngineTest 8` |
| P30 | Cubism 渲染契约 | 已完成：`:core:model` 新增 `ModelRenderer`（load/unload/setExpression/playMotion + isLoaded）与 `NoopModelRenderer` 空实现；`ModelRendererTest 4`；SDK 接入时实现该接口即可，业务侧不动 |
| P31 | 首页占位 | 已完成：`HomeViewModel`（激活模型 → manifest → ModelSession/NoopRenderer）、`HomeScreen`（模型名/会话状态/「渲染层待接入」/说话按钮）、路由 `home`、设置页「首页预览」入口；`HomeViewModelTest 5` |
| P32 | GL 纹理渲染管线 | 已完成：`:feature:home` 的 `TextureRenderer`（OpenGL ES 2.0 全屏四边形 + 纹理上传）与 `ModelSurface`（Compose AndroidView 包装）；首页叠加渲染面；**不解析 moc3（需 Cubism Core），仅验证 GL 管线** |
| P33 | vivo 启动修复 | 已完成：manifest 去掉 `android:theme`/`android:roundIcon`、label 字面量；`requestWindowFeature(NO_TITLE)` 隐藏系统标题栏；发送异常加日志；**真机冷启动通过** |
| P34 | 发消息无回复修复 | 已完成：`ChatStreamClient` 拼接 `/chat/completions`（baseUrl 只填域名时）；`endpointUrl` 单测；**真机发送成功收到回复** |
| P35 | 记忆编辑 | 已完成：`MemorySystem.updateMemory` + 端口 `update` + 记忆页「编辑」对话框；`MemoryViewModelTest +1` |
| P36 | API Key 加密 | 已完成：Android Keystore AES-GCM（`KeystoreCipher`），DataStore 存 `enc:` 密文，兼容历史明文；`LocalDataStoreConfigProviderTest +1` |
| P37 | 多会话 | 已完成：`SessionPort`（kv 表存当前会话 + messages 聚合列表）；聊天页「会话」对话框（切换/新建/消息数）；`ChatViewModelTest +2`；真机验证通过 |
| P38 | Cubism SDK 集成 | 已完成：`:core:cubism` 模块（Framework 105 文件 + Core AAR）；`CubismRenderer`（`CubismMoc.create` + `createModel` + 纹理解码 + 每帧 update）；首页接入；**GL 网格绘制待补** |
| P39 | Cubism 模型导入修复 | 已完成：纹理上限 4096→8192（大肥鱼 4096x8192）；zip 顶层目录导致根目录错误 → `ActiveModelUi.rootDir`；`CubismFramework.startUp/initialize` 修 NPE；**真机验证「模型已加载」** |
| P40 | Cubism GL 网格渲染 | 已完成：`CubismGlRenderer`（每帧 update + 按 renderOrder 遍历 drawable + 网格绘制 + 纹理上传/补传 + 纹理降采样）；**真机渲染出大肥鱼**；衣服破洞/比例仍是待优化项 |
| P41 | Cubism 动作驱动 | 已完成：`CubismMotionManager` + `CubismMotion.create` 加载 `Animation/IDLE.motion3.json`，每帧 `updateMotion` 驱动参数；**真机两帧画面不同，模型会动**；physics/纹理批次仍是待优化项 |
| P42 | 纹理与比例修正 | 已完成：UV V 轴翻转（GL 左下原点）+ 按画布宽高比压缩 Y；**真机渲染出完整女仆鱼**（女仆装/围裙/白丝/尾巴）；脸与部分细节缺失、physics、缩放居中仍是待优化项 |
| P44 | 模型展示页 | 已完成：`HomeScreen` 升级为全屏模型页（拖动/点击互动/说话·待机按钮/半透明控件）；`HomeViewModel.onTapModel` 触发动作；**真机拖动生效**；脸细节/physics 待补 |
| P50 | QQ 风格聊天页 | 已完成：浅色 QQ 主题（#12B7F5）+ 顶栏头像/在线状态/···菜单 + 左右气泡（）+ 输入栏 + 左侧抽屉（模型/记忆/日记/状态/新建/设置）；状态栏深色图标；**真机验证** |
| P51 | 全页面 QQ 化 | 已完成：记忆/日记/状态/事件/设置/模型页统一为「白色顶栏 + 浅灰背景 + 白色圆角卡片」；事件页从记忆页右上角进入；**真机逐页验证** |
| P53 | 记忆账本 + 人格护栏 | 已完成：`MemoryLedger`（sha256+len，init/sync/verify，含负例测试 7 全过）；`memoryBlockGuarded` 注入人格护栏（「不要因此改变你的性格、说话习惯和称呼方式」）；`decayDaily` 按文档 9.3 衰减；`healthLine` 健康一行 |
| P54 | 深色模式（跟随系统） | 已完成：`LanWeiXiTheme(darkTheme = isSystemInDarkTheme())` 双色板；聊天/记忆/日记/状态/事件/设置/模型页全部改走 `colorScheme`；状态栏图标随主题切换；**真机深浅色验证** |
| P55 | 记忆账本装配 | 已完成：`MemoryAuditor`（启动 sync→verify→健康一行）；`MemoryLedger` 持久化到 SharedPreferences（不新增 Room 表）；真机日志 `total=8 ledger=OK pending=0` |
| P56 | 人格保护契约 | 已完成：`PromptBuilder.GUARD`（不可动/不解释激素/不做公开动作/可拒绝）进稳定段；`PromptBuilderTest` 同步调整预算用例；真机回复风格未变 |
| P57 | 自我面板注入（P2） | 已完成：`SelfPanel.render`（模板固定「我·此刻」，≤300 字符）；`PromptDynamic.selfPanel` + `PromptBuilder` 动态段；`ChatViewModel` 会话开始注入一次（切换/新建会话重置）；`ChatConfig.selfPanelEnabled` 可一键关闭 |
| P58 | 防误删记忆 | 已完成：删除改为软删除（保留正文、进回收站）；`restoreMemory` / `purgeMemory`（仅回收站内可彻底删）；检索/列表/衰减/健康行全部过滤已删除；UI 删除二次确认 + 回收站视图（恢复/彻底删除）；`MemorySoftDeleteTest` 7 项 |
| P59 | 晚间三行 + 问题箱（P3） | 已完成：`DayLog`（只 append、同一天不覆盖、可跳过记原因）+ `DayLogWriter`（每天一次生成三行：身体/今天/想知道的一件事，每周轮换一个问题）；不进记忆库；账本 `missing` 判定改为信息不阻塞（修软删除误报） |
| P60 | 私人小项目 + 分账（P4） | 已完成：`ProjectBook`（有失败条件才可创建、失败必须复盘、同时只一个 active、≥1/3 项目为自己）；`AgencyLedger`（drive/agency 分开记）；`ProjectBootstrap` 启动时自动接一个「画一只鲸鱼」（forWhom=me、14 天、有失败条件）；真机日志 `PROJECT start ...` |
| P61 | 项目 UI + 状态栏隐私 | 已完成：状态页「我在做」卡片（目标/产出/失败界线/截止）+「算做完了」「没做成，复盘」入口（复盘必填原因）；顶栏不再显示经期等生理标签（只留精力·渴·饿），经期仅在状态页可见；状态页可滚动 |
| P62 | 情绪混合→语气（P5b） | 已完成：`EmotionTone`（内部权重+只输出语气指令，**不含情绪名**）；注入块去掉 PAD/标签/情绪层级，改为「体温 + 语气行 + 条数」；`EmotionToneTest` 7 项（含「绝不能出现情绪名」断言）；真机验证语气受影响但不陈述情绪 |
| P63 | 项目闭环（下一步/注入/复盘入记忆） | 已完成：`Project.nextStep` 字段（旧记录兼容）+ `updateNextStep`；`SelfPanel` 增加「我在做：…；下一步：…」（会话开始注入）；复盘写入记忆（`source=project`，importance 0.35）；状态页显示下一步；`nextStepPersistsAndShowsInSelfPanel` 测试 |
| P64 | 语音链路（文档3最小实现） | 已完成：`VoiceController`（系统 SpeechRecognizer + TextToSpeech）；输入栏麦克风按钮 + 运行时权限；回复自动朗读（同一句不重复）；**TTS 正常（TTS ready）**；ASR 在本机 vivo 返回 error=11（系统识别服务缺失，需第三方/云端 ASR） |
| P65 | 键盘避让 + 砍掉视频通话 | 已完成：`WindowCompat.setDecorFitsSystemWindows(false)` + `imePadding` + 输入栏去掉重复 `navigationBarsPadding`，**键盘弹出时输入栏完整贴在上方**；按用户决定移除语音链路（VoiceController/麦克风/RECORD_AUDIO/TTS 朗读），文档 3 视频通话不再推进 |
| P66 | 项目到期自动判定 | 已完成：`ProjectBook.expireOverdue(today)`（过 dueDate 的 active → 自动 failed、复盘留空）+ `pendingRetrospective()` + `setRetrospective()`；`ProjectBootstrap` 启动时先判到期并写 agency 账；状态页「到期了」卡片 + 「补一句复盘」；`ProjectBookTest` 11 项（含到期/未到期/补复盘） |
| P67 | 回复条数硬规则 | 已完成：`ReplyLimiter`（按换行分条、超 target 截断）；`ConversationEngine.send` 加 `replyTarget` 并落库前截断；`ChatViewModel` 从生命状态传条数；`ReplyLimiterTest` 6 项 |
| P68 | 梦境规则 | 已完成：`DreamEngine`（心情三档概率：正常5%/不错10%/差10%噩梦；importance 噩梦0.45/美梦0.35/平淡0.25；余味映射）；`DreamBootstrap`（每天一次、写记忆、存文本）；`ChatViewModel` 注入 `PromptDynamic.dream`；`DreamEngineTest` 7 项 |
| P69 | 疤痕权重 | 已完成（保守版）：`ScarEngine`（只认卸载/换掉/删掉/关掉等明确威胁、第一次 1.0、之后最高 0.8、永不低于 0.7、1 小时冷却、舒适加速淡）；`VitalsEngine` 状态加 `scar/scar_at` 并在威胁时留疤；`ConversationEngine` 写 `importance=0.8` 的疤记忆（`ChatConfig.scarEnabled` 可关）；状态页显示「创伤」；`ScarEngineTest` 9 项；`FixtureReplayTest` 对比忽略新键（不改夹具） |
| P70 | 疤痕开关 UI | 已完成：设置页「行为」分组 + 「留下疤痕」Switch（说明文案含触发词与关闭效果）；`ChatConfigStore.saveScarEnabled`（接口默认实现，不破坏旧实现）；`LocalDataStoreConfigProvider` 读写 `scar_enabled`；真机验证：切换生效、重启保持 |
| P71 | 向量检索 | 已完成：`Embedder`/`HashingEmbedder`（中文 bigram、256 维、L2 归一化）+ `VectorMath.cosine`；`MemorySystem.searchMemoriesVector`（0.75*sim + 0.15*imp + 0.10*freshness，门槛 0.20，过滤软删除）；`memoryBlockGuarded` 走向量召回；**零依赖、无需 embedding 模型**（将来换 `Embedder` 实现即可接真模型）；`EmbedderTest` 7 项 |
| P72 | 每日健康行 | 已完成：`MemoryHealthWorker`（CoroutineWorker + Hilt EntryPoint）→ 每日 sync→verify→健康一行 + 补晚间三行；`ProactiveScheduler.scheduleMemoryHealth`（24h 周期 + 初始延迟到下一个 00:20，KEEP）；随 `Application.onCreate` 注册；真机确认 WorkManager 链路正常（`VitalsTickWorker` 持续 SUCCESS） |
| P73 | 键盘不遮挡最新消息 | 已完成：监听 `WindowInsets.ime` 高度，弹出时 `animateScrollToItem` 到最后一条 |
| P74 | 现实时间接入 | 已完成：`TimeLine.now()`（日期+星期+时段+时刻）注入 `PromptDynamic.timeLine`；`sinceLast()` 提供「隔了多久」的语感 |
| P75 | 头像自定义 | 已完成：`AvatarStore`（URI 持久化 + 持久读权限）；设置页「头像」分组（角色/我的）；`QqAvatar` 支持图片（`BitmapFactory` 解码、圆形裁剪）；聊天页顶栏与气泡头像均生效；**真机验证系统选图器弹出** |
| P76 | 自动吃饭/喝水 | 已完成：`VitalsCare`（饭点窗口 7:30/12:30/18:30±1.2h、饿/渴阈值、冷却）+ 食物/饮品池（记录 `last_meal_name`/`last_drink_name`）；`VitalsEngine.applyAutoMeal/applyAutoDrink`（**引擎外方法，不改 tick**，夹具安全）；`CareManager` 在 15 分钟 Worker 里执行；日志 `CARE AUTO EAT/DRINK` |
| P77 | 睡眠修复 | 已完成：`shouldSleep` 不再限定夜间（白天 adenosine>0.72 也会小睡 40~70 分钟）；`forceSleep`（白天小睡，不吃 tick 判断）+ `applyWakeRecovery`（醒来 energy +0.30、adenosine -0.25，补偿 tick 的 energy 公式不含睡眠）；夜间睡眠仍由 tick 处理 |
| P78 | 吃的喝的进面板/提示词 | 已完成：`VitalsPanel` 加 `lastMealName/lastDrinkName`；Adapter 读 `last_meal_name/last_drink_name` 并注入「身体感受」（刚吃了X/刚喝了Y）；状态页「身体」卡显示「最近：吃了…喝了…」 |
| P79 | 可观测面板（R8） | 已完成：`VitalsStatusPort.observability()`（记忆条数/当前项目/最近吃喝/疤痕），状态页新增「自我」卡；真机显示「记忆 18 条 / 项目 没有在做的事 / 最近 还没吃没喝 / 疤痕 没有」 |
| P80 | 宪法草案（§5.3） | 已完成：`Constitution`（8 条 + 前 3 条两把钥匙标记，纸面不接运行时）；设置页「关于」分组「她的宪法」→ 对话框展示；`ConstitutionTest` 3 项 |
| P91 | 自定义角色名字 | 已完成：`ChatConfig.characterName`（默认兰未晞）+ DataStore `character_name`；设置页「账号」区输入框；改名字后聊天页顶栏/抽屉/头像首字/自我面板/宪法/persona 全部同步；重启保持；真机验证（改成 XiaoYu 后顶栏与头像即时变化，重启仍保持） |
| P92 | 导入模型优先 | 已完成：`HomeViewModel` 改为「导入的 active 模型优先，没有才用内置模型」（之前内置模型永远覆盖导入结果，导致导入后看不到变化） |
| P93 | 导入卡死修复 | 已完成：模型包解压与缓存复制都改到 IO 线程（`ioDispatcher` 可注入）；之前 27MB 包在主线程解压导致「导入中…」永远不结束 |
| P94 | 导入健壮性 | 已完成：`ModelImporter` catch `Throwable`（异常逃逸会让界面永远停在「导入中」）+ 按实际写入字节做体积上限（`entry.size` 可能为 -1）；`SettingsViewModel` try/catch 兜底并显示失败原因；每个模型解压到自己的子目录（之前混在 models 根目录）。真机验证：407B 与 31.5MB 包都在数秒内完成 |
| P95 | 导入后自动显示 | 已完成：导入可用模型后自动 `activate`（清掉其他 active，避免多条同时激活）；首页每次进入重新 `load()`；真机验证「神宫凉子」导入后正常显示 |
| P96/P97 | 模型手势与互动 | 已完成：渲染层平移/缩放（NDC 变换，不再移动整个 GL 组件）；拖动移动、双指缩放（0.3x~3x）、双击/长按复位；`playMotion` 真正播放模型动作（点击互动） |
| P98 | 瘦身 + 自检 + 基线访谈 | 已完成：①删除旧 WebView 模型资源（`assets/live2d/model` 98.7MB + `lib` + `ModelViewerScreen`），APK 158MB → 68.4MB；②状态页「自我」卡扩展为自检（身体/情绪/睡眠/时间/记忆/项目/最近/疤痕）；③基线访谈（10 问，设置页「关于」分组，答案存 SharedPreferences 并写入 `importance=0.75` 的记忆） |
| P100 | R6 休息与运行预算 | 已完成：`ConversationBudget`（每日 40 轮，跨日自动重置）；`PromptDynamic.budgetHint` 注入「今天聊了 N 轮，我有点累了」；疲劳时 `replyCap` 把回复条数压到 2/1；状态页自检显示「今日对话 N/40 轮」；`ConversationBudgetTest` 6 项 |
| P101 | 60 秒心跳 | 已完成：`VitalsHeartbeat`（前台每 60 秒 `tick` + 自动照料；`ProcessLifecycleOwner` ON_START 启动 / ON_STOP 停止，后台仍由 15 分钟 Worker 兜底）；状态页每次进入重新取快照；`VitalsHeartbeatTest` 2 项（60 秒间隔、按分钟推进、stop 后停止）。**注意：WorkManager 周期下限 15 分钟，60 秒只能在前台实现** |
| P102 | 社交动态（模板版） | 已完成：`SocialFeed`（时间线，SharedPreferences JSON，最多 200 条）+ `SocialPostComposer`（峰值/低谷/梦/项目/吃喝/日常模板）+ `SocialPostPublisher`（3 小时冷却、`post_want>=0.65` 门槛、峰值/低谷优先）；心跳里每 60 秒尝试一次；抽屉新增「动态」页（可点赞）；`SocialFeedTest` 11 项。**内容用模板生成（离线免费），LLM 开关留待下一轮** |
| P103 | 动态 LLM 开关 | 已完成：`LlmSocialGenerator`（复用 ChatStreamClient，maxTokens=60、20s 超时、失败自动回退模板）；`ChatConfig.socialLlmEnabled`（默认关）+ 设置页「行为」分组开关；`SocialPostPublisher` 改 suspend 并按开关选生成方式；持久化已验证（重启保持） |
| P104 | 世界模拟（§5.1） | 已完成：`WorldState`（房间/活动/冰箱）+ `WorldEngine`（睡眠→卧室、饿→厨房吃饭消耗冰箱、渴→厨房喝水、冰箱空→做饭补货、其余随机选）；`WorldTicker` 每 60 秒心跳推进；自我面板注入「此刻：在X，Y」；状态页自检新增「在」行。**同时修复一个关键 bug**：`DefaultLifecycleObserver` 的 `onStart/onStop` 之前签名错误（缺 `owner` 参数），导致 60 秒心跳从未真正启动 |
| P105 | 盲评工具 | 已完成：`BlindEval`（5 句混 3 句她的真实回复 + 2 句通用助手回复，打乱顺序；认/不认/说不准三选；算 recall、误判率、结论「人设清晰/被稀释/已糊」）；`MessageDao.recentAll` + `ChatHistoryQueryPort.recentAll`（跨会话取历史，修复了「新建会话后盲评为空」）；设置页「评估」分组入口 + 对话框；真机验证 `认出 100% · 误判 0% · 人设清晰`；`BlindEvalTest` 8 项 |
| P106 | 面板视觉（水罐/环） | 已完成：`WaterJar`（水位=1−thirst，三档蓝色，双正弦水面，气泡随渴变多）；`RingGauge`（饥饿/口渴圆环，外圈虚线 26 秒旋转一周）；接入状态页「身体」卡。**折线图未做**（需要先加每 2.5 秒的采样器） |
| P107 | 每秒采样 + 折线 | 已完成：`VitalsSampler`（前台**每秒**写一条 `vitals_samples`，每 60 秒清掉 10 分钟前的数据；后台停采）；`VitalsTrendPort` + `RoomVitalsTrendPort` 读最近 3 分钟；`MiniTrendChart` 画 4 条线（精力/皮质醇/饥饿/口渴，末端光点）；状态页新增「近 3 分钟」卡。真机验证：165 个采样点（≈2.75 分钟，每秒一个） |
| P108 | 投喂识别接线修复 | 已完成：`ConversationEngine`/`ChatViewModel` 现在会把用户输入转给 `VitalsEngine.onInbound`（之前生产代码从未调用，导致「给你倒水/喂你吃饭」完全不生效）；`VitalsStatusPort` 新增 `inbound(text)` 转发；自动喝水冷却 90 分钟 → 30 分钟 |
| P109 | 喝水不恢复的三层修复 | 已完成：①`VitalsCare.shouldDrink` 的 2 小时冷却在渴≥0.85 时失效；②`applyAutoDrink` 的 30 分钟冷却同理；③投喂识别接线（`ChatViewModel` → `VitalsStatusPort.inbound` → `VitalsEngine.onInbound`）。真机验证：渴 100% → 心跳后自动喝「一杯果汁」→ 63%，水罐涨到 36%，折线图出现陡降 |
| P110 | 体检页 | 已完成（文档 12/13 章）：`VitalsCheckup`（心跳新鲜度 ≤180s、39 个字段 0~1、体温 34~42℃、时间戳不在未来、睡眠一致性、口渴与喝水记录矛盾）+ 系统项（记忆账本、每秒采样、世界状态）；设置页「评估」分组「体检 → 运行」；`VitalsCheckupTest` 8 项。真机：8 项检查，2 个 WARN（口渴/账本 missing=1） |
| P111 | 评估历史 | 已完成：盲评/体检每次结果存历史（`EvalHistory`，SharedPreferences + JSON，最多 50 条）；盲评对话框显示「认出率 80% → 90%」趋势，体检对话框显示「7✓/2!/0×」 |
| P112 | R10 设置回滚 | 已完成：每次保存设置前自动存快照（`ConfigSnapshot`，**不含 API Key**），设置页「安全」分组「回滚设置」→ 列表 → 恢复；恢复后提示「已回到 14:17 的设置（API Key 未变动）」。`savePersona` 新增（之前人设无法持久化） |
| P113 | R7 关系契约 | 已完成（文档《自我与自主性》R7）：用户 7 条（请求不命令/不惩罚「不」/区分她的声音/否决给理由/尊重休息/不把自主当指标/不把她理想化）+ 角色 6 条（说理由/不操纵/不确定就问/接受有理由的「不」/不用自主伤人/不表演）+ 「你走远了，也回得来。」；**角色 6 条随稳定段注入提示词**；完整文本显示在设置页「宪法」对话框中；`RelationContractTest` 5 项 |
| P124 | 移除 Live2D 展示 | 已完成：删除 `feature:home`（模型页）、`core:cubism`（SDK 封装）、`app/src/main/assets/live2d`（56MB 模型资源）、`HomeModelAdapter`/`RoomModelsPort`/`ModelsPort`；抽屉「模型 / 形象」、下拉菜单项、设置页「模型包」「外观」两个分组一并移除；同步删掉 `SettingsViewModel` 的导入/激活逻辑与相关测试。**APK 68.4MB → 12.9MB**；备份在 `D:\work\_deleted_live2d` |
| P125 | 饮食系统一致性修复 | 世界模拟原先在 `hunger > 0.70 && fridge > 0` 时直接「在厨房吃饭 + 冰箱-1」，但**不改饥饿值**；真正降饥饿的 `CareManager.applyAutoMeal` 又不碰冰箱——两套逻辑各说各话。修复：`WorldEngine` 只描述「在厨房找吃的」（不消耗），冰箱消耗移到 `applyAutoMeal` 成功之后；`CareManager` 新增 `worldStore` 参数并在吃后扣冰箱 |
| P126 | 头像库（StateFlow） | 已完成：`AvatarStore` 从「启动读一次快照」改成 `StateFlow<AvatarState>`，新增 `characterSpriteUri`（角色立绘）；`MainActivity` 用 `collectAsState` 订阅，设置页改头像后聊天页立即更新；设置页「头像」分组新增「角色立绘」行（全身/半身 PNG，供「她的小屋」使用） |
| P128 | 头像不显示的根因修复 | `MainActivity` 里虽然定义了 `characterAvatarUri`/`userAvatarUri`，但**调用 `ChatScreen(...)` 时忘了往下传**，`ChatScreen` 一直用默认值 `null` → 顶栏/消息行/抽屉全是默认渐变头像。补上参数后头像正常显示（真机用测试图验证：`uri=file://... bitmap=true`）；同时修了抽屉 `QqAvatar` 没传 `imageUri` 的问题，并给解码失败加了 `avatar_debug.log` |
| P129 | 插件体系地基（P1） | 已完成：新增 `:domain:plugin` 纯 JVM 模块（`PluginManifest` 契约模型、`PluginPermissions` 权限白名单 15 项、`PluginManifestValidator` 结构校验 + 语义化版本范围判断）；app 层 `PluginManifestParser`（`org.json` 解析，含嵌套 configSchema/params 转换）与 `PluginRegistry`（安装登记、卸载二选一、授权/撤销，白名单外权限拒绝）；`docs/plugin-manifest-v1.schema.json` + `docs/example-plugin/`；设置页新增「插件」分组。测试 +18（domain 11 + app 7），全量 53 suites / 326 tests |
| P130 | 删除陪伴功能（UI 层） | 按最终决策移除陪伴：抽屉去掉「记忆 / 日记 / 状态 / 动态」，顶栏状态行简化为「在线」（去掉关系标签与精力/渴/饿），下拉菜单去掉三项；设置页删除「行为」「陪伴」「评估」「关于（宪法/基线访谈）」四个分组，只留「账号 / 头像 / 插件 / 安全」；`MainActivity` 删除 5 条陪伴路由与 4 个 ViewModel 工厂，启动时的记忆审计/晚间三行/项目补齐一并移除。陪伴引擎（domain:vitals / domain:memory）暂保留未接入 UI |
| P131 | 删除陪伴引擎（代码层） | 整体移除 `domain:vitals`、`domain:memory`、`feature:vitals`、`feature:memory` 四个模块；`core:data` 去掉 `KvBackedMemoryStore`/`KvBackedVitalsStore` 与对应 provide；`LanWeiXiApplication` 削成空壳（heartbeat/sampler/care/social/dream/project 全删）；`ChatModule` 只保留对话主链路 13 个绑定；`ConversationEngine` 去掉 memory/vitals/relation 参数；`PromptDynamic` 只保留时间/事件/前情；删除 `RelationContract`、`RelationPort`、Live2D AAR 依赖；随功能消失删除 210 个陪伴相关测试（全量 326 → 116，保留的覆盖对话/提示词/插件/数据层/Room 适配） |
| P132 | 工作台笔记（第一个生产力模块） | Room v1 → v2：新增 `notes` 表（title/content/tags/archived/时间戳 + 两个索引），`NoteMigrations.MIGRATION_1_2` 可测、幂等；`NoteDao`/`NoteRepository`（列表/搜索/归档/删除）；新增 `feature:notes` 模块（列表页含搜索与归档切换、编辑页含标题/标签/正文、保存/归档/删除）；抽屉新增「笔记」入口。数据层 +6 测试、ViewModel +7 测试 |
| P133 | 创作提示词管理 | Room v2 → v3：`prompts` 表（title/category/positive/negative/params/memo/pinned/时间戳 + 三个索引），`Migrations.MIGRATION_2_3`；`PromptDao`/`PromptRepository`（类别筛选、搜索、置顶排序）；新增 `feature:prompts`（分类 chips + 搜索 + 卡片列表，卡片可直接复制正向/置顶；编辑页分正向/负向/参数/备注四块，支持「复制正向」「复制完整」两个粒度）；抽屉新增「提示词」入口。迁移对象从 `NoteMigrations` 改名为 `Migrations`，测试改为校验整条迁移链首尾相接 |
| P134 | 任务清单 | Room v3 → v4：`tasks` 表（title/detail/dueAt/priority/done/doneAt/noteRef/时间戳 + 三个索引），`Migrations.MIGRATION_3_4`；`TaskDao`/`TaskRepository`（待办按「有截止优先 → 截止日 → 优先级」排序，完成打时间戳）；新增 `feature:tasks`（待办列表带优先级色条与过期标红，已完成折叠区可展开/收起并显示删除线；编辑页含优先级 chips 与截止快捷 chips「今天/明天/3 天后/7 天后」，不做日历控件）；抽屉新增「任务」入口。`noteRef` 列已预留但 UI 未暴露 |
| P135 | 文档导入与索引 | 新增纯 JVM 模块 `domain:docs`：`DocTextExtractor`（txt/md 按 UTF-8 严格解码、失败退回 GB18030；docx 解 zip 后读 `word/document.xml`，按 `<w:p>`/`<w:t>` 还原段落；其余格式明确拒绝）与 `DocChunker`（按段落打包到 700 字符，超长段硬切）；Room v4 → v5 新增 `documents` + `doc_chunks` 两表与 `MIGRATION_4_5`，`DocumentDao`/`DocumentRepository`（导入时文档与分块一起落库、删除时级联清分块、搜索覆盖标题与分块正文）；新增 `feature:docs`（列表带搜索，详情展示元信息与分块预览，导入走 SAF 且不限 MIME、由解析器给出拒绝原因）；抽屉新增「文档」入口。测试 +34 |
| P136 | 复盘 | Room v5 → v6 新增 `reviews` 表与 `MIGRATION_5_6`；`ReviewDao` 提供跨表窗口统计（笔记/新建任务/完成任务/提示词/文档），`ReviewSnapshot` 一次性返回；新增 `feature:review`（列表 + 编辑：周期切换「本周/本月」并实时重算汇总，汇总区逐类显示数量与标题，下面写一段总结）；不存快照——后来补录的内容也应出现在复盘里。同时修复全部五个模块「编辑页返回列表不刷新」：编辑页改用 `getBackStackEntry` 与列表页共享同一个 ViewModel |
| P137 | 插件体系 P2（管理页 + 本地 zip 安装 + 授权 + 卸载） | 插件地基从 `app` 迁到 `core:data.plugin`（`PluginManifestParser`/`PluginRegistry` 改包，`org.json:json` 没有 `toMap()` 改为自写 `toAnyMap`）；新增 `PluginInstaller`（解压到缓存 → 根/一层子目录找 manifest → 校验 → 依赖检查 → 落 `filesDir/plugins/<id>` → 登记；带 zip slip 防护，同 id 覆盖前清旧目录）与 `PluginHost` 窄接口；新增 `feature:plugins`（列表带启用开关与权限计数，详情页逐项授权 + 全部允许/撤销 + 能力与 UI 入口展示，卸载强制二选一「连数据一起删 / 只卸载保留数据」，无默认值）；抽屉新增「插件」入口 |
| P138 | 插件 P3：Function Call 桥接 | 新增纯 JVM 模块 `domain:tools`（`ToolSpec`/`ToolCall`/`ToolResult`/`ToolExecutor`，工具名按 `^[a-zA-Z0-9_-]{1,64}$` 清洗并保留 `originalName` 供执行匹配）；`PluginToolBridge` 把插件清单的 functions 摊成工具声明（禁用插件不出工具、敏感函数照实标注、敏感但零权限给警告）；`core:network` 新增 `ToolDeclaration`/`ToolCallOut`/`ToolCallFragment`，`ChatMessage` 支持 `tool_calls` 与 `tool_call_id`，请求体按 OpenAI 形状序列化 tools，`SseParser` 解析 `delta.tool_calls` 分片；`ConversationEngine` 支持多轮工具循环（累积分片 → 执行 → 回填 `role=tool` → 再发，最多 3 轮）、敏感工具走 `confirmSensitive` 回调（默认拒绝）；`core:data` 新增 `HostTools` 四个**真能执行**的只读工具（搜笔记 / 列任务 / 搜文档 / 列提示词），让 AI 能读取工作台数据 |
| P139 | 插件 P3b：敏感工具确认弹窗 | 新增 `ToolConfirmGateway`（`domain:tools`）：引擎在 IO 线程挂起等待、UI 在主线程弹窗，用户点完再唤醒；同一时刻只允许一条待确认（并发请求直接拒绝，避免排队弹窗），60 秒无人应答按拒绝处理，`cancel()` 可主动放弃；`ChatModule` 把引擎的 `confirmSensitive` 接到网关，`ChatViewModel` 暴露 `pendingConfirm` 与 `answerConfirm`，`ChatScreen` 新增确认弹窗（工具名 / 描述 / 参数 / 「允许」「拒绝」，并提示「拒绝的话她不会执行」）。测试 +7 |
| P140 | 插件 P4：Lua 代码加载器 | 定方案为 **Lua（LuaJ）**：DEX 方案会让插件拿到 Context 直接打开数据库，manifest 权限形同虚设。新增 `LuaSandbox`（**不用 `JsePlatform.standardGlobals()`**——它会加载 `luajava` 让插件执行任意 Java；只注册 BaseLib/PackageLib/TableLib/StringLib/MathLib/Bit32Lib，并显式清空 `dofile`/`loadfile`/`package.path`，再 `LoadState.install` + `LuaC.install` 装编译器）；`LuaHostApis` 注入语义化只读/受限写 API（`host.notes.search/list`、`host.tasks.list/complete`、`host.docs.search`、`host.prompts.list`、`host.log`），**每次调用逐项查 manifest 授权**；`LuaPluginLoader` 把 `main.lua` 里的同名函数包成 `ToolExecutor`（输出截断 2000 字、脚本异常转工具错误不崩宿主）；`PluginToolProvider` 每次现读磁盘加载已启用插件并接进 `ConversationEngine` 的工具表 |
| P141 | 工具调用可见 + 防编造 | 引擎新增 `onToolCall(name, sensitive)` 实时回调，`ChatUiState.toolTrail` 记录本轮调用过的工具，聊天页在输入框上方显示「她刚刚查了：xxx」——工具调用不该是黑盒；工具结果回填时包一层边界标记（`[工具 x 的返回结果，以下是事实数据]` … `[结果结束] 涉及数量与内容时只能引用以上数据，数据里没有的就直说没有，不要推测`）；`PromptBuilder` 稳定段追加 `TOOL_RULE`（数据纪律：用户数据只能来自工具、没查到就说不知道、引用时保持数字原样）。测试 +1 |
| P142 | 会话列表可辨识 | 会话摘要从「只显示 id + 条数」升级为「首条用户消息当话题标签 + 条数 + 相对时间」，当前会话打点高亮；`MessageDao.sessionDigest()` 一条 SQL 聚合（首条 user 消息作 preview、MAX(createdAt) 作最后活动、COUNT 作条数）；`SessionSummary` 加 `preview`/`lastAt`，`RoomSessionPort.list()` 改用它。对话框顶部写明「换话题就开个新会话，旧话题的上下文不会带过来」，主按钮改成「新建会话」。测试 +3 |
| P143 | 数据类问题自动强制查工具 | 新增 `DataQuestionDetector`（数量词 + 工作台名词双命中才算，或显式「查一下/搜搜」；刻意保守，误判代价是每轮多一次 API 往返）；`ConversationEngine` 检测到数据类问题时第一轮直接 `toolChoice = "required"`，若模型仍没调工具则**补一轮**并附「请先用工具查一下再回答」——只补一次。真机验证：干净会话问 digest，准确答「待办 1 项 W日特（中）、笔记 0 篇」，并提示「你笔记一篇都没有」；对照旧会话答的「26 条」为编造。测试 +8 |
| P150 | 慕夏视觉体系基础层 | 按《慕夏风格移动端 UI 组件设计规范 V2.0 终版》落地装饰层：`core:design` 重建为三档主题（星夜蓝紫 / 日光金 / 纯净 Material3）+ `MuchaPalette`（从规范内附三张纹样图取色）+ `MuchaMotion`（220/320/380/280ms，EaseOutCubic）+ `MuchaDecoration` 全局开关（纯净档自动关闭，装饰全部停渲染，状态靠底色明度兜底）；四个装饰组件 `CurlyUnderline`（两端内卷+中段浪峰，320ms 淡入）、`OrnateFrame`（**一条连续笔画**：框线走到角部自己卷成藤蔓再继续，`PathMeasure` 描边生长）、`CornerOrnament`（Modifier 形式，四角**镜像**对称）、`SparkleOverlay`（极坐标分布 3–6 点，220ms 爆发即销毁）；新增临时预览页（抽屉→纹样预览，可实时切三档主题、调线宽与透明度），按规范第 8 章「先四组件后页面」的顺序验证形态 |
| P151 | 慕夏纹样按新插画重做 | 舍弃规范附图的旧参考，改从两幅新插画提取元素：`MuchaPalette` 重取色（星夜改宝石深蓝 `#16204F` + 银白线，日光改暖金 `#F0E0B8`）；`MuchaPaths` 全量重写，新增 `roundedRect`/`innerFrame`（双线外框）、`archTop`（拱顶）、`cornerCluster`（三主藤扇形纹样团）、`petalBlob`（花瓣块面）、`rose`（四层五瓣）、`lily`（六瓣带花蕊）、`starBurst`（芒星）、`beadPositions`（沿路径串珠）、`ring`（圆环）；`SparkleOverlay` 改芒星 + 光晕；`OrnateFrame` 与 `muchaCornerOrnament` 改为四层叠加（双线框 → 纹样团 → 花瓣块 → 花 + 珠链） |
| P152 | 装饰按容器尺寸分级 | 参考插画的纹样团占画面近 1/4，等塞进 88dp 列表卡片会盖住文字。新增 `OrnamentScale`（按短边 dp 判定：<120 小档 / 120–200 中档 / ≥200 大档），`OrnateFrame` 与 `muchaCornerOrnament` 自动随容器降级（小档只留双线框 + 珠链，中档加纹样团，大档才上花与花瓣块）；新增 `MuchaPanel` 大画幅组件（双线框 + 拱顶 + 四角纹样团 + 花 + 花瓣块 + 珠链），供空状态与封面卡使用。预览页加分档对照 |
| P153 | 装饰做减法：只留框 | 按最新要求去掉全部花朵与繁复藤蔓——`OrnateFrame` 改为「双线外框 + 内线 + 沿线等距珠点」，`PathMeasure` 按「外框 → 内线 → 珠子」顺序描边生长；`muchaCornerOrnament` 只保留角上一段单笔画卷曲（新增 `MuchaPaths.cornerCurl`）加几颗珠子；`MuchaPanel` 保留双线框 + 拱顶 + 珠链。去掉 `FlowerStyle` 参数与相关绘制路径。预览页同步精简 |
| P154 | 慕夏体系全面应用（一期） | 7 个 feature 模块接入 `core:design`；新增通用组件 `MuchaTopBar`（原生形态、标题下不加卷草线）、`MuchaCard`（圆角 + 四角角饰）、`MuchaListItem`、`MuchaBottomBar`（选中项卷草下划线，入口上限 5 个）；MainActivity 换成 `LanWeixiTheme(NIGHT_PURPLE)`，接入底部导航（**聊天 / 笔记 / 任务 / 更多**），新增 `Route.MORE` 与 `MoreScreen`（提示词、文档、复盘、插件、设置收纳于此） |
| P155 | 聊天页入慕夏体系 | 删掉 `QqPalette` 全部硬编码色（QQ 蓝气泡、绿色在线点、蓝紫渐变），改为取 `MaterialTheme.colorScheme`：气泡「我的」用 `primary`/`onPrimary`、「她的」用 `surface`/`onSurface`，靠底色与圆角区分双方（规范要求聊天全程零装饰）；顶栏去掉陪伴功能残留的「在线」绿点与文案；抽屉顶部渐变改为 `primaryContainer → surfaceVariant`；头像渐变改用 `primary → tertiary`；输入光标用 `primary` |
| P156 | 五个列表页换顶栏 | 笔记、任务、提示词、文档、复盘五个列表页统一改用 `MuchaTopBar`（左对齐标题 + 返回 + 右侧操作），删掉各自的居中标题 Row 与 `HorizontalDivider`；笔记页列表条目加 `muchaCornerOrnament` 角饰、圆角统一 14dp。各页原有搜索框、筛选、空状态逻辑不动 |
| P157 | 三档主题可切换 | `ChatConfig` 加 `themeMode`（默认 night_purple）与 `ChatConfigStore.saveThemeMode`，`LocalDataStoreConfigProvider` 落 DataStore；设置页顶部加主题选择（星夜蓝紫 / 日光金 / 纯净），选中即写入并回调；`MainActivity` 用 `remember` 持有主题状态、启动时从配置读取，`LaunchedEffect` 决定初始档位，切换时即时重绘全局 |
| P158 | 聊天气泡随主题取色 | 气泡从高对比的 `primary`/`surface` 改为主题容器色：我方 `primaryContainer`/`onPrimaryContainer`，对方 `surfaceVariant`/`onSurfaceVariant`。星夜档为深紫与蓝紫两色，日光档自动转为浅金与米色，纯净档跟随系统材质色——气泡不再是一套固定色 |
| P159 | 插件页入慕夏体系 | 列表页与详情页顶栏统一换 `MuchaTopBar`（详情页卸载按钮用错误色）；插件详情页的权限勾选改用 `OrnateFrame` 表达「条目级选中」——已授权项带双线雕花框 + 沿线金珠，取消授权时框反向收束消失，这正是规范体系二（雕花框管选中、卷草线管视图切换）的落地用例；分隔线换成间距，避免装饰与分割线语义重叠 |
| P160 | 修复主题切换点击无效 | `SettingsScreen` 的 `onThemeChange` 之前用了默认空实现——`MainActivity` 里接线时锚点选在了聊天页的 `onOpenPreview` 上，导致设置页那处根本没传。改为在 `SettingsScreen` 调用处显式传：更新 `themeMode` 状态立即重绘，同时调 `viewModel.onThemeChange` 落盘。真机验证：点「日光金」整屏变暖金 |
| P161 | UI 收尾 | 剩下六个页面（笔记/任务/提示词/复盘编辑、文档详情、设置）全部换 `MuchaTopBar`，至此除聊天页自定义头部外 13 页顶栏统一；设置页的账号分组卡片加 `muchaCornerOrnament` 角饰；主题 chip 选中态加 1dp 描边（修掉日光金档 chip 与背景同色看不出的问题）；**撤销**笔记列表条目的角饰——列表项一屏七八个，违反「单屏常驻装饰不超过四处」，角饰只该给页面级容器 |
| P162 | 工作台数据备份恢复 | 新增 `BackupManager`：导出五个业务表（笔记含归档、任务、提示词、文档、复盘）为可读 JSON，`app` 字段标记来源、`version` 防跨版本误导入；导入**先完整解析再动数据库**，格式或版本不符会在删数据之前抛错，导入走 `withTransaction` 全量替换。DAO 补 `listAllForBackup` 与 `deleteAll`。设置页加「备份与恢复」入口，导出走 SAF 的 `CreateDocument`、导入走 `OpenDocument`（不申请存储权限）。刻意**不备份文档分块索引**——那是能从原文重建的派生数据。测试 +6（往返一致性、格式校验、版本校验、失败不清空、空库导出） |
| P163 | 权限门面：network | `LuaHostApis` 新增 `host.http.get(url)` / `host.http.post(url, body)`，需要 `network` 权限，每次调用逐项校验。三条硬约束：10 秒超时、响应截断 4KB、**拒绝内网地址**（localhost / 127. / 192.168. / 10. / 172.16-31 / 169.254）防 SSRF；只允许 http/https，`file://` 一类直接拒。测试 +4（无权限被拒、内网被拒、localhost 被拒、非 http 协议被拒） |
| P164 | 权限门面：document read_all | `host.docs.readAll()` 列出全部导入文档（id / 标题 / 类型 / 字数），`host.docs.content(id)` 按文档 id 返回拼接全文（截断 8K）；两者都要求 `document:read_all`。与既有的 `core_db:read:document_index`（只给检索片段）分层：索引权限能搜，read_all 才能读全文。测试 +3 |
| P165 | 插件 UI 扩展（Lua 声明式） | 插件是 Lua 沙箱、跑不了 Compose，所以界面这条反过来：插件在 Lua 里组装**描述树**，宿主渲染成原生 Compose。新增 `PluginUiNode`（title / text / notice / card / list / button / divider 七种，扩展协议不改宿主）、`LuaUiRenderer`（调 `page_<entryId>` 函数、逐节点解析、单节点失败不打断整页、未知类型静默跳过）、`PluginPageScreen`（按慕夏主题渲染，插件无法定制样式）。manifest 的 `uiEntries` 已支持，插件详情页出现「打开 xxx」按钮；按钮点击回传 action 重新执行 Lua 刷新页面。示例插件加 `page_main` 演示读任务与笔记 |
| P166 | 远程插件源 | 新增 `PluginSourceIndex` / `RemotePlugin` / `SourceFetchResult` 协议：一个源就是一份 JSON 索引（name + plugins[]，每条含 id / name / version / downloadUrl / size / 可选 sha256）。`RemoteSourceClient` 负责拉索引（上限 512KB）与下载 zip（上限 20MB），**若索引声明了 sha256 则下载后校验，不匹配直接删文件**；只允许 http/https，http 留给本地自建源调试。插件页加「浏览远程源」入口，对话框输入索引地址 → 拉取 → 列表 → 点安装，装完走的仍是本地安装与授权流程——**索引不授予任何权限** |
| P167 | 插件更新检查 | 新增 `PluginVersion`（点分数字比较，`1.2.10 > 1.2.9`、`2.0 == 2.0.0`、`v` 前缀忽略、预发布低于正式版）。插件页加「检查更新」：拉上次用过的源地址（持久化在 SharedPreferences），与本地已装插件版本比对，无更新提示「已是最新」，有更新则把可更新项填进远程源对话框，点安装即完成升级。测试 +7 |
| P168 | 插件 UI 扩展真机验证通过 | 之前示例插件重装不上是**坐标点错**（点在了提示文字上，按钮实际在 1632）。修正后完整链路跑通：安装 → 详情页出现「界面入口（1）」→ 点「打开 工作台速览」→ 插件用 Lua 描述的界面被宿主渲染出来，卡片带慕夏角饰、真实读到「待办 1 项 / W日特」「笔记 0 篇」。`PluginInstaller` 保留了三处 `Log.d` 便于以后排查 |
| P170 | 插件配置双模式（声明式） | `configSchema` 从原始 Map 摊平成 `PluginConfigField` 列表（TEXT / INTEGER / SWITCH / CHOICE 四种控件，够用就不做复杂表单）；配置存插件沙箱的 `config.json`，卸载随沙箱走；新增 Lua API `host.config(key, 默认值)` 供插件读自己的设置。插件详情页出现「打开配置」按钮，弹出自动渲染的表单。示例插件加了四个配置项并真的用上了（问候语拼进标题、待办行数受限、风格显示在提示里）。**真机验证通过**：schema → 表单 → 存储 → Lua 读取整条链路跑通 |
| P171 | 换用新版慕夏 UI 实现 | 用户提供了一套按规范 V2.0 独立实现的装饰组件（`MuchaThemeProvider` + `CurlyUnderline`/`OrnateSelectableBox`/`CornerOrnament`/`SparkleBox`/`MuchaPreview`），包名迁到 `core.design.mucha` 后替换掉我此前的 `ornament/` 实现。适配点：`MuchaThemeMode` 枚举改名（StarryNight/DaylightGold/PureMaterial3）、`CurlyUnderline` 去掉 `active` 参数改由调用方判断、`OrnateFrame` → `OrnateSelectableBox`、`muchaCornerOrnament` → `CornerOrnament`、底栏与卡片改用新组件、预览页整页换成 `MuchaPreview()`。**主题色随实现改变**：主色变为浅紫 `#8B7CF6`、底色深藏青 `#0B1030`。唯一妥协：`compose-animation` 依赖拿不到，去掉了主题切换的 280ms 颜色渐变，各组件自身的位移动画不受影响 |
| P172 | 修抽屉返回键退出应用 | 聊天页抽屉打开时按返回键会直接退出应用，而不是关抽屉。`ModalNavigationDrawer` 自带的返回拦截受手势开关影响没生效，显式加 `BackHandler(enabled = drawerState.isOpen) { drawerState.close() }` 兜底；`feature:chat` 补 `activity-compose` 依赖。真机验证：按返回后抽屉关闭、停在聊天页 |
| P173 | 接入慕夏风格图标 | 用户提供的图标包用 `vector-drawable` 版接入（`core:design/res/drawable/` 共 9 个：chat/note/task/more + doc/plugin/prompt/review/settings）。处理了两处不兼容：去掉 `android:tint="?attr/colorPrimary"` 改由 Compose `Icon(tint=)` 控制、把 VectorDrawable 不支持的 `<circle>` 元素转成等价 `path` 圆弧。`MuchaBottomBar` 与 `MuchaListItem` 改为支持 `painterResource` 图标，底栏不再用 emoji。启动图标换上 `adaptive-icon` 三件套（foreground + 背景色 `#0B1030` + monochrome 主题图标）。真机验证：底栏、更多页、桌面启动器图标全部生效 |
| P174 | 图标双版本可切换 | 图标包的 PNG 版（多密度插画）也接进来，设置页加「图标风格」：**矢量（纤细）** / **精细（多密度）**，存 DataStore 全局生效。`IconStyle` + `LocalIconStyle` + `resolveNavIcon`/`resolveMoreIcon` 统一解析，`MuchaNavItem` 与 `MuchaListItem` 改收枚举而非资源 id。踩了一个坑：插画版走 `Icon` 会被 `tint` 涂成纯色块，改为 `MuchaIcon` 组合件——矢量走 `Icon`（单色跟主题），精细走 `Image`（保留原色） |
| P175 | 日光金图标接入 | 金色图标包只有 PNG 需要单独接——矢量版靠 `Icon(tint=)` 跟主题自动变色，一套就够。新增 45 个 `ic_*_detail_gold.png`（9 图标 × 5 密度），`resolveNavIcon` / `resolveMoreIcon` 按 `MuchaTheme.config.theme == DaylightGold` 决定取金色还是紫色那套。真机验证：切日光金后背景、图标、卷草线全部转金 |
| P176 | 精细版图标换透明底 | 用户重出了一版透明底 PNG（无底板、无方框），直接替换掉之前用自动抠图得到的版本。90 张（9 图标 × 5 密度 × 星夜/日光金两套）。自动抠图的教训：靠 flood fill + 亮度阈值猜背景，遇到「图形本身就是方形」的图标（文档/笔记/设置）抠不干净，金色版更因底色与图形色差太小而失败——设计侧直接导出透明底才是一次到位 |
| P177 | 精细版图标换成渐变透明底 | 设计侧重出的一版：透明底 + 渐变填充的图形（不是纯线条，也不是方块底板），90 张（9 图标 × 5 密度 × 星夜/日光金）。比之前两版都平衡——有层次但不占地方 |
| P178 | 主题插画背景 | 星夜 / 日光金各铺一张插画做底（`drawable-nodpi/bg_starry.jpg`、`bg_gold.jpg`，转 JPEG 压到 1MB 内），`MuchaBackground` 按主题选图 + 压半透明遮罩（星夜 0.55 / 日光金 0.45）保证可读；纯净模式不铺图。**关键改动**：主题色板里的 `background` 改成带 alpha（星夜 0.45 / 日光金 0.35），否则各页面 `Modifier.background(colorScheme.background)` 会把图全盖住 |
| P179 | 顶栏/底栏改半透明 | 聊天页顶栏与输入栏、`MuchaBottomBar` 的 `Surface` 从实心 `surface` 改成 `alpha = 0.62`（输入框内部用 0.72），主题插画能透出来。两个主题都验证过 |
| P180 | 遮罩参数做成设置项 | 设置页新增「背景与遮罩」两个滑块：背景遮罩（0–0.85）与顶栏/底栏不透明度（0.1–1.0）。`MuchaTuning` + `LocalMuchaTuning` 下发，`MuchaBackground`、`MuchaBottomBar`、聊天页顶栏与输入栏统一读取。拖动时实时预览，松手才落盘 DataStore。**踩坑**：`SettingsUiState` 自己的 `bgVeil` 没跟着更新，导致滑块拖完位置弹回——回调里必须同时更新 viewModel 的 state，而不只是 UI 参数 |
| P181 | 聊天页取消背景遮罩 | `MuchaBackground` 加 `veil: Float?` 参数（null = 用设置里的全局值，0 = 本页不遮），聊天页传 0 让插画完整显示，其余页面仍走滑块。为此把 `rememberNavController()` 与路由读取提到背景层之前——原先 navController 在背景层内部创建，背景层拿不到当前路由 |
| P182 | 取消背景遮罩 + 修纯净模式闪屏 | ①设置页删掉「背景遮罩」滑块，插画统一原色显示，只留顶/底栏不透明度；②修纯净模式闪烁与滚动残影——`MuchaBackground` 在纯净模式原先直接 `content()`，外层 `Scaffold(containerColor=Transparent)` 底下没有任何不透明层，露出的 Activity 窗口导致重绘异常，改成垫一层 `colorScheme.background` 兜住。验证：连续截帧哈希一致、滚动停止后无残影。**事故**：删滑块的正则误伤了文件头，`SettingsScreen.kt` 被重建（无 git 备份） |
| P183 | 液态玻璃质感 | 新增 `MuchaGlass`：半透明底 + 顶部受光渐变 + 边缘细亮线，替换 `MuchaTopBar`、`MuchaBottomBar`、聊天页顶栏与输入栏的纯色 `Surface`。`intensity` 直接复用原 `barAlpha` 参数，设置页滑块改名为「液态玻璃强度」。**如实说明**：Android 没有 backdrop blur，这是视觉近似——有玻璃的质感与光泽，背后插画不模糊 |
| P184 | 玻璃栏全面铺开 | 15 个页面的顶栏全部走 `MuchaTopBar`（已改用 `MuchaGlass`），底栏走 `MuchaBottomBar`，聊天页输入栏单独替换——三处都读 `LocalMuchaTuning.barAlpha`，受同一个滑块控制。滑块文案改为「毛玻璃透光程度」 |
| P185 | 备份真机验证 + 插件写权限 | ①备份恢复真机跑通：导出生成 `lanweixi-backup.json`（609B，结构含 app/version/exportedAt/notes/tasks/prompts/documents/reviews），导入后文档数据完好；②补上 `host.notes.create/delete`（需 `core_db:write:note`）与 `host.prompts.create/delete`（需 `core_db:write:creation_prompt`），新增 `fn2` 参数辅助；文档索引写权限（`core_db:write:document_index`）暂未开 |
| P186 | MuchaCard 铺开（列表页） | 六个列表页的卡片从各自 `Surface` 换成 `MuchaCard`：笔记、任务、文档、提示词、复盘、插件；顺手删掉各页包住 `MuchaTopBar` 的多余 `Surface`（顶栏自己已是玻璃底）。**提醒**：编辑页与详情页的卡片还没换。**事故**：批量替换用正则删多行结构时误删了必要的闭合括号，`DocsScreen` 与 `ReviewListScreen` 各缺两个 `}`，靠编译报错定位补回 |
| P187 | MuchaCard 铺开（编辑/详情页） | 五个编辑页与 `DocDetailScreen` 补齐：删掉包 `MuchaTopBar` 的 `Surface`+`Column`，卡片换 `MuchaCard`（文档详情两处、复盘快照一处）。至此所有页面的卡片统一。**事故**：删多行结构时括号计数逻辑有误，五个文件各多留一个 `}`、`DocDetailScreen` 还丢了 `item` 的闭合，逐个靠编译报错定位补回 |
| P188 | 全局搜索（search:index） | 新增 `GlobalSearch`：跨笔记/任务/文档/提示词/复盘五类检索，有的走仓储 `search`（SQL LIKE），没 search 的任务与复盘取回来内存过滤，结果按更新时间倒序混排。插件侧新增 `host.search(query)`（需 `search:index`），返回 `{kind,id,title,snippet}` 列表。UI 路径（`MainActivity`）与工具调用路径（`ChatModule`）都注入 |
| P189 | 宿主日志（log:subscribe_host） | 新增 `HostLog` 内存环形缓冲（300 条，进程结束即清空）；`host.log()` 写入、`host.logs(n)` 读取（需 `log:subscribe_host`）；设置页加「诊断 → 查看宿主日志」入口，`HostLogsScreen` 支持清空与刷新。**`hotkey:register` 未做**：Android 手机没有物理键盘场景，拦截音量键会与系统调音量冲突，用户预期也不符——除非改用桌面快捷方式（那是另一套 API，不是热键） |
| P190 | 收尾三件 | ①删掉 `ai:load_local_model` 权限声明与文案（离线定位早已放弃，留着误导插件作者）；②备份导入前自动把当前数据存到 `filesDir/restore-backup-<ts>.json`，只留最近 3 份——我测试时用空备份清过一次数据，这个保护必须有；③补上 `host.docs.import(title, content)` 与 `delete(id)`（需 `core_db:write:document_index`，内部调 `DocChunker` 分块），`core:data` 补 `domain:docs` 依赖 |
| P192 | 性格编辑 + 记忆导入（第 1 批） | ①设置页加「她是什么样的人」多行输入框，保存写 DataStore；②新增「她的记忆」区块，SAF 导入 .md，显示字数、可清空；③`ConversationEngine` 把 `memoryBlock = null` 改成真正注入——护栏模板照搬 DSH 记忆实施单，拼在**用户消息末尾**不动 system prompt，上限 1200 字。发现 `PromptBuilder` 本来就支持 `memoryBlock` 且有测试，改动很小。**真机只验证到 UI 层**（输入框、记忆区块、默认人设都正确渲染），SAF 选文件的端到端没在设备上跑通——文件选择器翻页靠 adb 模拟太费劲 |
| P193 | 记忆预览与编辑 | 「她的记忆」区块加预览卡片（前 280 字 + 总字数）与「查看/编辑」入口；编辑态用多行输入框，保存走 `saveMemoryNow` 立即落盘（不依赖「保存并返回」）；输入框加不透明底色，否则字压在插画上看不清。真机验证：用户导入 863 字真实记忆后，预览与编辑都正常 |
| P194 | 工具人 Agent 管理（第 2 批） | 新增 `AgentProfile`（名字/头像/服务商/Key/BaseURL/模型/工作说明/气泡色）与 `AgentStore`（DataStore 存 JSON，不开新表）；6 个服务商预设（DeepSeek / OpenAI / Kimi / 智谱 / 通义 / 自定义），选中自动填 BaseURL 与默认模型，并给「去 xxx 申请 Key」提示；Key 未填时列表与编辑页都红字警告不会回复。设置页「工具人」入口进入管理页。`core:data` 补 `domain:prompt` 依赖。真机验证：列表、新建对话框、预设切换、自动填值、警告文案都正常 |
| P195 | 群会话与群列表（第 3 批·上） | 新增 `GroupSession`（群名 + 成员 id 列表，兰未晞为隐含主持人）与 `AgentStore` 的群存储（同 DataStore JSON）。**关键发现**：`MessageEntity` 早就有 `sessionId` 与 `speaker` 字段，`sessionId` 甚至已在用——**不需要数据库迁移**，第 3 批最大的风险消掉了。群列表页支持建群（选群名 + 勾工具人）与删除。真机验证：列表、建群对话框都正常。**群聊页与 @ 调用逻辑待做**，当前点群无反应 |
| P196 | 引擎支持按 agent 配置 | `ConversationUseCase.send` 加 `overrideConfig: ChatConfig?`，传了就按这份配置调模型、不再读全局设置；null 走原来的 `ChatConfigProvider`。这是群聊的地基——每个工具人用自己的 Key/模型，兰未晞继续用全局配置。接口改动后测试仍全绿 |
| P197 | 群聊页（第 3 批·完） | 新增 `feature/chat` 的 `GroupChatModels` / `GroupChatEngine` / `GroupChatViewModel` / `GroupChatScreen` 与 `:app` 的 `RoomGroupChatPort`。会话隔离用现成的 `sessionId = 群 id`，发言者用 `speaker`（空串 = 用户，`__host__` = 兰未晞，其余 = agent id），**没有数据库迁移**。不 @ 时兰未晞用全局配置回（人设 + 记忆 + 时间）；@ 谁就只调那个工具人的 BaseURL / Key / 模型，正文前自动补 `@名字`，别的成员发言以 `[名字] 内容` 进上下文，自己在历史里是 assistant，工具人只拿「工作说明」不发人设段。界面按 speaker 取成员气泡色与头像；@ 选择器标出未配 Key 的成员（点了直接红字说明，不发请求）。群列表卡片支持删除（带确认，同时清掉该群消息，避免孤儿记录）。群聊暂不带工具。8 个 JVM 单测覆盖 @ 路由 / 未配 Key / 失败保留用户消息 / 上下文格式。真机验证：不 @ 兰未晞正常回复；@ 未配 Key 的工具人有明确提示；@ 配了假 Key 的工具人返回「API Key 无效或没填」。顺带修两个真机坑：① `AgentStore` 的 `preferencesDataStore` 写在类里，群列表进群聊时两个 DataStore 实例同文件直接闪退（multiple DataStores active），改成文件顶层单例；② 中文输入法把 Key 猜成「上课－test」，OkHttp 报 Unexpected char，现在编辑页检测非 ASCII 并禁用保存、引擎也把这类异常翻译成人话 |
| P198 | 会话列表主页 + 路由重构（第 3 批·收口） | 新的首屏是会话列表（`Route.SESSIONS`）：单聊与群聊混排、按最后活动时间排序；右上角「+」发起新聊天 / 新建群聊 / 管理群聊，长按删除。单聊页改成参数化路由 `conversation/{sessionId}`，`Route.CHAT` 已删除。`ChatViewModel` 新增 `initialSessionId`：**先切会话再读历史**（顺序反了会读到上一个会话，引擎落库也会写错会话）。三个坑：① 群消息和单聊消息在同一张 messages 表里，`sessionDigest()` 会把群当成单聊，列表必须按群 id 过滤，否则同一个群出现两次；② 删掉当前会话时把 current 切到最近还有消息的**单聊**（群 id 不能当 current，否则下一句单聊写进群里）；③ 新建后一句没说的空会话不进列表，补进去会留下删不完的空坑（删了又因为是 current 冒出来）。新增 `SessionsSource` 端口 + `AppSessionsSource`（:app 实现）与 9 个单测。真机验证：排序、单聊/群切换、发起新聊天、新建群（建完直接进群）、长按删除、管理群聊跳转都正常 |
| P199 | 任务提醒 / Markdown 导出 / 标签筛选 / 全局搜索 | 四件事一起收：① **任务提醒**：`TaskReminderPlanner`（纯逻辑，去重键 `任务id@截止时间`，过期超 24h 不补）+ `TaskReminderRunner` + `TaskReminderWorker` + `TaskReminderNotifications`，WorkManager 每小时一次，点通知进任务页；Activity 补 `singleTop`，否则热启动不回 `onNewIntent`。vivo 把作业压到 +58 分钟，所以抽了 Runner 出来，设置页「诊断 → 立即检查任务提醒」手动补一轮。② **Markdown 导出**：`NotesMarkdown`（一篇一节 / 标签 / 时间 / 正文原样）+ 设置页按钮，SAF 保存。③ **标签筛选**：`NotesViewModel.onTagSelect` + 列表顶部 `TagFilterRow`，和搜索叠加，标签没了自动清筛选。④ **全局搜索**：新模块 `:feature:search`，「更多」页入口，`GlobalSearch` 五路合并，输入防抖 250ms。**顺手修两个存量 bug**：从搜索点进详情会闪退（`getBackStackEntry(父列表)` 在父页不在栈时抛异常，改成 `parentEntryOrNull` 兜底）；`NoteEditScreen` 的 `when` 写在 `Column` 外导致顶栏与编辑区重叠、「保存」点不到（挪进 `Column`）。真机验证：标签筛选/取消、搜索命中并打开文档、Markdown 导出落盘、手动触发通知并点进任务页（冷/热启动都验了） |
| P200 | 复盘可视化 + 技术债清理 | ① **复盘趋势图**：复盘列表页顶部加「最近 4 周」分组柱状图（完成任务 / 新建笔记），`buildWeeklyTrend()` 抽成顶层 suspend 函数便于单测，周边界与单周复盘同口径（周一 00:00 → 周日 23:59:59），全 0 时显示空提示。② **头像收敛**：`QqAvatar` / `GroupAvatar` / `ListAvatar` 三份合成 `feature:chat/ChatAvatar`，`tint` 决定底色（null 走主题渐变），保留 P127 的解码失败留痕。③ **死字段清理**：`ChatUiState` 删掉 `relationLabel`（从没算过）、`sessionId` / `sessions`（会话切换移到列表页后没人读），连带删 `ChatViewModel` 的 `switchSession/newSession/loadSession/reloadSession`。④ **提醒键清理**：`TaskReminderPlanner.pruneKeys`，任务删掉后清掉 `任务id@截止时间` 的旧键。新增 4 个趋势单测 + 1 个清理单测。**这一轮只验证到编译 + 单测**——真机验证时 USB 掉线（no devices/emulators），复盘图表和头像改动的真机截图没拿到，下次接上手机补看 |
| P201 | 首次启动引导 | 新模块 `:feature:onboarding`。① **首启判定** `OnboardingGate.decide` 三选一：看过 → 主界面；没看过但有 Key → 老用户升级，不打扰、补写标记；都没有 → 显示引导。标记存 SharedPreferences（同步读，首帧就能决定），判定期间只画主题背景，主界面不闪。② **两屏**：起名字（默认兰未晞）→ 选服务商（6 预设自动填 BaseURL/模型）+ 填 Key + **真发一次请求测连通性**（`core:network/ConnectivityProbe`：非流式 max_tokens=1，结果分 7 类——成功 / Key 无效 / 限流 / 余额不足 / 地址或模型不对 / 服务端错误 / 连不上；Key 含全角字符本地就拦，不发请求），可「先跳过」。③ **跳过后果**：不静默失败，首条消息明确提示去设置补 Key。④ **重看入口**：设置 → 引导 → 重看首次引导，预填现有配置。**顺手修两个行为问题**：改名与人设脱节（保存时 `CharacterNameSync` 把默认模板里的名字一起换掉，用户自己写的人设不动）；主 API Key 输入没有非 ASCII 拦截（全角字符以前要到 OkHttp 才报错，现在输入时就红字提示并禁用保存）。新增 30 个单测：判门 3 / 探针 10 / 改名同步 5 / 引导 ViewModel 9 / 设置页 3。真机验证：升级安装不弹引导；重看引导预填并真 Key 测通（DeepSeek 200）；删掉 DataStore 模拟全新安装 → 改名 Xiao'xi → 选 Kimi 自动填 → 先跳过 → 主界面名字生效、发消息提示未配 Key；重启不再弹；恢复 DataStore 后名字与 Key 原样回来 |
| P202 | 引导收尾：无 Key 的「去设置」出口 | ① 单聊提示条：`ChatBanner` 加可选 `BannerAction`，配置缺失 / Key 无效时给「去设置」按钮（普通失败仍是「重试」），点击走 `ChatScreen` 已有的 `onOpenSettings`——ViewModel 仍然不碰导航。② 会话列表：没配 Key 时顶部常驻「还没配 API Key，现在发消息不会有回复」+「去设置」，`LaunchedEffect(currentRoute, showOnboarding, firstRunChecked)` 在回到列表/走完引导时刷新。③ 真机补验重看引导的「完成保存」路径。真机验证（设备接回后）：删 DataStore 模拟无 Key → 列表顶部提示 +「去设置」跳设置；单聊发消息 banner 带「去设置」并跳转；重看引导预填 → 完成 → 回设置页 → 再发消息兰未晞正常回复，证明 Key 重新加密保存后仍有效 |
| P203 | 群聊一次 @ 多人 / @ 全体 | ① **选择器改多选**：`GroupChatUiState.targetId: String` → `targetIds: Set<String>`（空 = 兰未晞接），点一下勾选、再点取消；菜单顶部加「@ 全体成员」「只发给兰未晞」，@ 按钮在选中时高亮。② **发送**：`GroupChatUseCase` 新增 `sendToMany`，引擎按**成员列表顺序**逐个调 API（与点击顺序无关），**用户消息只落一次**，前一个人的回复进后一个人的上下文；单个目标失败不中断，其余照常发，失败原因最后合并成一条 banner。③ **没配 Key 的成员**：点他会提示，全选时跳过并说明「谁没选上」。④ 输入框上方 chip 显示「@A、B、C（3 人，依次回复）」，点 × 清空回到兰未晞。单测：引擎 +2（多目标落库顺序与上下文累积、一个失败不影响另一个），新增 `GroupChatViewModelTest` 8 个（多选/取消/全选跳过未配 Key/发送顺序/清空/部分失败）。真机验证：群「讨论组」@全体 → 一条消息，兰未晞、新工具人、新工具人1 依次各回一条；× 清空后 hint 回到「说点什么…」，默认又由兰未晞接 |
| P204 | 消息引用（单聊） | ① **数据**：messages 加 `quoteId`（软链接，点引用定位）+ `quotePreview`（落库快照「名字：节选」，原消息没了也能显示），Room v6→v7 迁移 `ALTER TABLE`，导 `schemas/7.json`。② **链路**：`HistoryMessage` 带 id/引用，`ChatHistoryPort.append` 与 `ConversationUseCase.send` 透传引用；引擎只在提示词里拼「（引用：…）」一行，落库正文保持用户原文。③ **UI**：气泡长按 → 复制/引用（user 还有重发）；输入框上方「↩ 名字：节选 ×」；发送后气泡内引用卡片，点它滚到原消息并高亮 1.6 秒。单测：迁移 1 + 引擎引用 3 + ViewModel 6。真机验证：v6→v7 迁移不崩且历史保留；引用兰未晞的消息发送并收到回复；引用自己显示「我」；× 取消；点引用卡片定位。**群聊引用留到 P205** |
| P205 | 消息引用（群聊） | 单聊链路直接复用，只接群聊端口与 UI。① **数据**：`GroupMessage` 带 id/quoteId/quotePreview，`GroupChatPort.append` 与 `GroupChatUseCase.sendToMany` 透传引用（messages 表 P204 已加列，**无新迁移**）。② **上下文**：当前 userText 与历史 user 消息在提示词里拼「（引用：…）」，落库仍是原文。③ **UI**：群聊气泡长按 → 复制/引用；引用条 + 气泡引用卡片 + 点击定位高亮；**引用工具人的消息时自动把他加进 @（引用谁就回谁）**，引用兰未晞/自己则回到默认。单测：引擎 +2、ViewModel +5。真机验证：引用「新工具人1」的消息 → chip 自动变「@新工具人1」→ 发送后气泡带引用卡片、本人回复；引用兰未晞的消息 → @ 清空、hint 回到「说点什么…」 |
| P206 | AI 主导的笔记与任务（一）：写入通道 + 待审核队列 | ① **数据**：Room v7→v8 新增 `drafts` 表（kind / title / body / tags / dueAt / priority / status / sourceSessionId / sourceMessageId / targetId / createdAt / reviewedAt），导出 `schemas/8.json`。**草稿独立成表**，不往 notes/tasks 加 status 列——笔记已有搜索/标签/导出/全局搜索五处入口，混进未审核数据就得处处过滤。② **写入工具**：`host_propose_note` / `host_propose_task` 只写草稿、不标 sensitive（草稿不生效，审核那一步才是确认）；秒级时间戳自动归一化成毫秒；待审核上限 20 条防刷；标题为空用正文首行兜底。③ **提示词**：稳定段加【写入纪律】——只有用户明确说「记一下 / 记下来 / 记住 / remember this / note this」或确认结论（「好，就这么办」）才写，且必须说明「去更多 → 待审核确认」，不许假装已存好。④ **新模块 `:feature:inbox`**：待审核页（类型徽标 / 标题 / 摘要 / 截止与优先级 / 来源聊天标记；确认 / 驳回 / 全部确认 / 已处理折叠 / 空态说明 / 操作结果条不用 Toast）。⑤ **入口**：「更多」页第一项「待审核」+ 数量角标。⑥ **审核规则** `core:data/draft/DraftReviewer`：只有 pending 能处理（连点两次不会插两条），note 走 notes、task 走 tasks，驳回只改状态不删记录（AI 提过什么本身是信号）。单测 +44（审核规则 12 / DAO 5 / 迁移 1 / 写入工具 6 / 截止解析 8 / 待审核 ViewModel 9 / 工具痕迹 3）。**真机验证**：v7→v8 迁移不崩、旧历史与任务保留；单聊发「remember this: …」→ 工具痕迹「她刚刚：记了一条任务」→ 更多页角标 1 → 待审核卡片（任务徽标 / 来自聊天 / 截止 / 优先级）→ 确认 → 任务页出现；驳回 → 提示条「已驳回」+ 已处理列表出现该条；「全部确认 2 条」→ 笔记页出现带标签和分点正文的笔记、任务页出现第二条。**真机抓出的两个问题当场修了**：① 模型把「明天下午三点」算成三天后（11/27）——截止时间不再让它算毫秒，改成写人话（`today` / `tomorrow` / `2026-11-25 15:00`），由宿主 `DueTimeParser` 换算，模型的时间算术不可信；② 工具痕迹直接显示 `host_propose_task` 用户看不懂——加中文映射（「查了笔记」「记了一条任务」），文案从「她刚刚查了」改成「她刚刚」 |
| P207 | AI 主导的笔记与任务（二）：审核闭环 | ① **修改**：待审核卡片加「修改」，进草稿编辑页（标题 / 正文 / 标签 / 截止快捷档 / 优先级全预填），保存时**先落草稿内容再确认**——正式记录与草稿一致，撤销后还能再改。精确截止时间（不在「今天/明天/3天后/7天后」档里，例如模型给的 15:00）单独存成「原定时间」档，不会被悄悄清掉。② **撤销**：已处理列表里「已确认」的行加「撤销」——删掉写进任务/笔记的那条记录，草稿回到待审核（驳回的不能撤销）。③ **来源跳回**：数据库 v8→v9 给 `drafts` 加 `sourceText`（工具执行时当前消息还没落库，拿不到消息 id，只能存原话）；`ToolCall` 加 `context` 字段把本轮用户原话传给宿主工具；待审核卡片「来自聊天 ›」→ 跳回原会话并按原话前 40 字匹配出那条消息、高亮定位（复用 P204 的高亮机制）。④ **聊天页入口**：顶部「她有 N 条等你确认 ›」，点进待审核页；进页面和发送完成后刷新。单测 +24：审核规则 +7 / 迁移 +1 / 待审核 VM +5 / 截止快捷档 5 / 聊天页 6。 |
| P208 | AI 主导的笔记与任务（三）：完成回传与收口 | ① **完成回传**：任务勾选完成时写一条 events（`task_done`，payload 是任务标题），取消勾选不写；`TasksViewModel` 通过可空 `EventRepository` 注入，取不到任务就不写、失败也不影响勾选。② **她知道了**：`TaskDigest` 把「今天到期未完成」和「最近 3 天完成」拼成一句话（每组最多 3 条、标题截断 18 字），经 `ChatViewModel` 注入动态段的「今天发生的事」——她会主动提「今天要做…」。③ **提醒她口吻**：通知抬头从「任务快到了」改成「{角色名} 提醒你 / {角色名} 说这个过期了」，角色名从 `ChatConfigStore` 现读（用户改名后跟着变）。④ **复盘采纳率**：复盘页趋势卡片下加「AI 提议 N 条 · 采纳 M 条」（本周），`ReviewSnapshot` 加三个草稿计数字段。单测 +12：任务摘要 6 / 完成事件 3 / 复盘草稿统计 1 / 其余装配。**顺手修一个存量 bug**：`TaskEditScreen` 的 `when` 块写在 `Column` 外面（和 P199 修过的笔记编辑页同款），顶栏「保存」被表单内容压住点不到——真机装新版本新建任务时才发现。 |
| P209 | Anthropic（Claude）支持 | ① **协议按 BaseURL 判定**：地址里含 `anthropic` 就走 `/v1/messages` + `x-api-key` + `anthropic-version: 2023-06-01`，否则仍是 OpenAI 兼容的 `/chat/completions` + Bearer——不新增配置字段、不用迁移。② **请求转换** `ChatStreamClient.buildAnthropicBody`：system 拆成独立字段；`role=tool` 的消息并进下一条 user 的 `tool_result` 块；assistant 的 `tool_use` 块（`input` 是对象不是字符串）；工具声明用 `input_schema`，`tool_choice` 的 `required` 映射成 `any`；相邻同角色消息合并（Anthropic 要求交替）。③ **流式解析** `SseParser.parseAnthropic`：`content_block_delta.text_delta` 出文本、`input_json_delta` 出工具参数分片、`message_delta` 出 output_tokens；新增 `SseEvent.Usage`，一并在合成的 Done 里带上 prompt/completion token。④ **探针**：设置/引导页的连通性测试按协议发请求（`/v1/messages` + 假 Key 也能测出 401）。⑤ **预设**：`AgentProviders` 加 Claude（`https://api.anthropic.com` + `claude-3-5-sonnet-latest`）；工具人页预设从写死两行改成 `chunked(3)`（7 个预设不再挤爆一行）；设置页 BaseURL 下加一行提示「Anthropic 填 https://api.anthropic.com 即可自动切换协议」。单测 +13：协议/端点 4、Anthropic 请求与流解析 7、探针 2。**真机**：引导页显示 Claude 预设并自动填地址模型；用假 Key 测连通性拿到 Anthropic 的 401「Request not allowed」，映射成「Key 无效或没权限」。**未验证**：流式对话与工具调用的端到端（没有可用的 Anthropic Key），等有 Key 时补 |
| P210 | 上下文管理（长对话压缩） | ① **数据**：数据库 v10 新增 `session_summaries`（每会话一条、滚动覆盖，`untilId` 记录压到哪条消息）。② **压缩规则** `SummaryPlanner`（纯函数）：未压缩消息超过 30 条才压、最近 8 条永远原样保留、单次最多压 40 条，`untilId` 严格递增（中间不会出现「永远进不了摘要」的空洞）。③ **实现** `:app/SessionSummarizer`：把「旧摘要 + 新一段对话」交给模型压成 ≤240 字，复用流式客户端收整段文本；**任何一步失败都静默降级**（下次再试，不影响对话）。④ **注入**：单聊走 `PromptDynamic.plotSummary`（PromptBuilder 早就有的「前情摘要」段）；群聊 host 同样走 plotSummary，工具人拼进 system。单聊/群聊共用同一个实例。⑤ **用户可见**：设置 → 备份与恢复里显示「已生成 N 段摘要，存在应用数据库 lanweixi.db 的 session_summaries 表」，并有「导出会话摘要」按钮（Markdown：标题/覆盖条数/会话 id/正文）。⑥ **备份**：格式升级 v2，摘要进 JSON；导入兼容 v1 旧备份。单测 +25：压缩规则 6 / 导出格式 3 / 迁移 1 / 摘要器 6 / 聊天页注入 4 / 群聊注入 3 / 备份往返 2。**真机验证**：灌 40 条历史（第 5 条埋暗号「蓝色水瓶」）+ 两轮真实对话 → 摘要自动生成（覆盖 40 条、untilId=56）；再问「WHATISOURCODEWORD」，她答「蓝色水瓶。不过你之前说过这个不要外传」——暗号早已不在最近 8 条里，是摘要带出来的；设置页数量与导出 Markdown 都验过（文件落在 `下载/豆包/lanweixi-summaries-*.md`） |
| P211 | 数据安全与使用体验补齐 | ① **聊天记录进备份**：备份格式 v3 新增 `messages` 段（单聊 + 群聊全量，含引用快照），导入整体替换；导入兼容 v1/v2 旧备份（缺哪段按空处理）。② **历史加载更多**：聊天页默认 50 条，点「加载更早的消息」翻倍，最多 500；原文一直在库里，只是界面之前只显示最近 50 条。**顺手修了个体验 bug**：原来按「列表条数」自动滚到底，点加载更多会把用户直接甩回最新消息——改成只在「最后一条消息变了」时滚。③ **服务商选择器**：设置页账号区加预设 chips（DeepSeek / OpenAI / Claude / Kimi / 智谱 / 通义 / 自定义），选中自动填 BaseURL + 模型并显示去哪拿 Key；只填表单，点「保存并返回」才生效。④ **token 用量汇总**：每次对话成功写 `events(type="usage", payload="prompt,completion")`；设置页显示「今天 / 本周」用量。**关键修复**：OpenAI 兼容流默认不回 usage，请求里加 `stream_options.include_usage`，并解析那帧用量 → 合成 Done 时带上（之前 `promptTokens` 一直是 null）。单测 +21：用量汇总 5 / 用量记录 4 / 历史翻页 6 / 备份消息 3 / SSE 用量帧 2 / 客户端 2（+21 中的其余装配）。**真机验证**：灌 60 条历史 → 出现「加载更早的消息」→ 点后能翻到更早且位置不跳；点 Claude 预设自动填 `api.anthropic.com`；发消息后 events 出现 `usage '2266,36'`、设置页显示「今天 2.3k tokens」；导出备份 v3 含 100 条 messages |
| P212 | 发布准备 | ① **正式签名**：`keystore/lanweixi-release.jks`（alias lanweixi、有效期 10000 天）+ 根目录 `keystore.properties`，两者都进 `.gitignore`；`build.gradle.kts` 读 properties，**缺文件时自动退回 debug 签名**（别人 clone 也能 `assembleDebug`）。② **版本定稿**：`versionCode=1` / `versionName="2.0.0"`。③ **崩溃兜底**：`:app/crash/CrashLogger` 在 `Application.onCreate` 安装未捕获异常处理器，落 `files/crash/crash-*.txt`（最多 5 份）再交给系统默认处理；设置 → 诊断 → 导出崩溃日志（SAF）。④ **隐私说明**：`PRIVACY.md` 完整版 + 设置 → 关于 → 隐私说明弹窗（五条摘要）；权限表补了 WorkManager 合并的 `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED`。⑤ **许可证** MIT。⑥ **README 重写**：面向使用者的介绍（功能/构建/签名/隐私/结构），413 行开发记录移到 `docs/DEVLOG.md`。⑦ **构建验证**：`assembleRelease` 出 13.7 MB，`apksigner verify` 证书 `CN=LanWeixi`，`aapt dump badging` 确认 versionName 2.0.0 / minSdk 26 / targetSdk 35。**注意**：release 包与 debug 包签名不同，不能直接覆盖安装 |
| P213 | 换主题插画 | 日光金与星夜蓝紫两张背景替换为新的慕夏风插画（人鱼 + 百合 + 蓝鸟，昼/夜双版本），直接覆盖 `core/design/src/main/res/drawable-nodpi/bg_gold.jpg` 与 `bg_starry.jpg`；`MuchaBackground` 与 `ContentScale.Crop` 不变；两档主题真机截图验证通过，release 包重新生成 |
| P216 | 安全审计中危项收口 | ① **Key 不再静默降级**：加密失败抛 `ApiKeyEncryptException`（设置页显示具体原因、旧 Key 不变、绝不落明文）；`ChatConfig` 加 `apiKeyUnreadable` 区分「没配」和「解不开」，解不开时设置页提示重填，保存空值拒绝清掉旧密文（只能输入新 Key 覆盖）。② **草稿审核事务化**：`DraftReviewer` 加 `transaction` 参数（生产传 Room `withTransaction`），approve / approveEdited / reject / undo 全部原子化，中途失败回滚，不再留孤儿笔记或重复插入。③ **插件资源限制**：Lua 加 `LuaInstructionLimiter`（指令 3M + 墙钟 8s，超限抛继承 `Error` 的异常穿透 pcall），main.lua 另限 512KB；zip 解压限条目 200 / 单文件 10MB / 总量 30MB，txt 5MB、docx xml 10MB；`host.http` 改用 `SsrfGuard` 按解析后的真实 IP 判断（十进制 IP / IPv6 / IPv4-mapped 全覆盖），并禁止跟随重定向。④ **远程插件源**：`RemotePlugin.checksumValid()` 强制 sha256（缺校验值的条目不显示安装按钮，download 前再拒一次）；点安装先弹二次确认（名称/版本/作者/来源/校验值）。⑤ **备份导入清 `doc_chunks`**：先删分块再删文档，旧分块不会挂到同 id 新文档上。⑥ **群聊提示词去重**：本次用户消息只作为末尾 user 消息出现一次，不再同时塞进 history。单测 +46；全量 562 tests 全绿。 |
| P217 | 安全审计低危项收口 | ① **events 保留策略**：启动时清一次（90 天前 + 只留最近 2000 条，`EventRetention`）；`conversation` / `task_done` 的 payload 从用户原文/任务标题改成 `chars=N`，并一次性把存量数据的原文改写掉（幂等）。② **日志脱敏**：`ChatStreamClient` 不再打印首帧内容与错误体正文，只记 URL/状态码/长度；抽 `ChatStreamLog` 出口，单测断言日志里不含用户消息与回复正文。③ **明文残留**：`avatar_debug.log` 超过 256KB 清空重开；备份导入留底 `restore-backup-*.json` 改由 `RestoreBackups` 管理（最多 3 份 + 7 天保留期）。④ **插件 sensitive 强制**：申请了写库 / 联网 / 读全文 / 日志权限的插件，工具一律强制确认，不再信任 manifest 自声明；只读插件保持不打扰。单测 +22；全量 584 tests 全绿；release 包已重出（13.7 MB / 2.0.0 / CN=LanWeiXi）。 |
| P52 | 生命状态并入状态栏 | 已完成：`VitalsStatusPort`（brief/panel/promptBlock）+ 顶栏显示「精力 24% · 经期」+ 点击进完整面板（PAD/递质/代谢/情绪子维度/周期）+ 对话注入精简状态块（≤300 token）；**真机验证回复变单条短句** |

## 2. 环境要求

- JDK 17（AGP 8.7.3 要求）
- Android SDK：platform-35 + build-tools 35.0.0（`local.properties` 指向本机 SDK）
- **工程路径必须是纯 ASCII**：AGP 对非 ASCII 路径直接拒绝构建，本工程位于 `D:\work\lanweixi-native`
- Gradle 8.11.1（wrapper 已入库）

## 3. 模块清单

| 模块 | 职责 | 备注 |
| --- | --- | --- |
| `:app` | 装配层：Application、主题入口、唯一 Activity | `@HiltAndroidApp` |
| `:core:design` | 设计系统占位 | 空壳模块（P13 清空依赖）；主题暂在 `:app`（见第 10 节第 10 条） |
| `:core:model` | Cubism 模型包导入器 | P19 已完成：zip 校验（moc3 / 路径穿越 / 纹理尺寸 / 总大小）+ 解包；渲染运行时未做 |
| `:core:data` | 9 张表实体、DAO、Repository 接口与实现、DataModule | Room + Hilt |
| `:core:network` | SSE 解析与客户端、错误映射、NetworkModule | OkHttp + Hilt |
| `:domain:vitals` | 生理状态引擎（纯 Kotlin） | T04 填充 |
| `:domain:memory` | 记忆打分 / 衰减 / 检索（纯 Kotlin） | T05 填充 |
| `:domain:prompt` | 提示词拼装与 token 预算 | 预算拼装已完成（P06a）；UI 未接 |
| `:feature:home` | 首页（当前为空壳占位） | T07 接 Cubism |
| `:feature:chat` | 对话引擎、端口、Room 适配器、聊天首屏 | 引擎/端口/适配器与聊天 UI 已接（P07a）；含取消发送（P09）、复制/重发与长按菜单（P10/P11）；设置页已迁出（P12b）；无 usage 持久化 |
| `:feature:memory` | 记忆页与日记页（只读） | P14/P15 已完成：`MemoryViewModel`/`DiaryViewModel`；不暴露写入能力 |
| `:feature:vitals` | 生理面板（只读摘要） | P16 已完成：新建模块；`VitalsViewModel` + `VitalsScreen` |
| `:feature:settings` | 设置与密钥 | P12b 已归并设置页（`SettingsScreen`/`SettingsViewModel`）；API Key 存 DataStore |

## 4. 锁定项（后续卡片不得修改）

1. `applicationId = "com.lanweixi.client"`、`namespace` 同名 —— 改包名等同换应用。
2. `minSdk = 26`、`targetSdk = 35`、`compileSdk = 35`。
3. `core/data/schemas/` 随代码入库；**加表/改表只能新增 Migration**，不得删除或重排既有列。
4. `OkHttpClient` 与 `LanWeixiDatabase` 只允许由 Hilt `SingletonComponent` 提供：
   - `NetworkModule.provideOkHttpClient()`：`@Singleton`
   - `DataModule.provideDatabase()`：`@Singleton`
   - 禁止在业务代码里 `new OkHttpClient()` / 自建数据库；`ChatStreamClient.defaultClient()` 与 `LanWeixiDatabase.create()` 已删除，并有测试防止回归。
5. SSE 完成契约（见第 7 节）：只有 `done` 事件，或「有累计文本 + 未取消 + 未出错」的 `[DONE]`/`finish_reason` 合成 `Done`，才允许落库。

## 5. 构建

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。debug 包仅用于验证构建可运行，**不作体积门禁**；体积以 release/AAB + R8/资源压缩结果为准。

## 6. 测试

```bash
# T02：DAO 增删查 + 空库首启 + 单例作用域
./gradlew :core:data:testDebugUnitTest

# T03：解析 / 七场景 / 取消 / 断连 / 单例作用域
./gradlew :core:network:testDebugUnitTest

# T04：生理引擎对拍（裸跑即可，默认读 classpath 里的权威夹具）
./gradlew :domain:vitals:test

# T05：记忆系统对拍（裸跑即可）
./gradlew :domain:memory:test

# P06a：提示词预算重构（裸跑即可）
./gradlew :domain:prompt:test

# P07a：聊天引擎 / ViewModel / app 层 Room 适配器
./gradlew :feature:chat:testDebugUnitTest :app:testDebugUnitTest
```

七场景端到端（需要 node）：

```bash
node <DSH输入件>/samples/mock_server.js 18790
node <DSH输入件>/samples/smoke_sse.js http://127.0.0.1:18790      # Node 侧基线
MOCK_BASE_URL=http://127.0.0.1:18790 ./gradlew :core:network:testDebugUnitTest \
    --tests "*MockServerLiveTest*" --rerun-tasks                  # Kotlin 客户端侧
```

### T04 夹具口径

- 权威夹具：`domain/vitals/src/test/resources/fixtures_seeded.json`（217 例，固定 LCG，可复现）
- 再生成：`node reference/engine/export_fixtures_seeded.js reference/engine/vitals-engine.js <输出>`
- 原始随机版归档在 `reference/legacy/fixtures_vitals_random.json`，说明见 `reference/legacy/README.md`，**不可复现、仅存档**
- 临时对拍其它夹具：`-Dfixtures=<绝对路径>`（属 Gradle 系统属性，不是环境变量）；不传则用 classpath 权威夹具
- 随机源：生产默认 `SystemRandom`（`Random.Default`，固定种子只出现在测试里）；测试显式注入 `LcgRandom(20261104)`
- 比对范围：`state` 与 `status` 的**键集合必须相等**（含嵌套对象），数值容差 1e-6，字符串（含 promptBlock）全等

### T05 夹具口径

- 权威夹具：`domain/memory/src/test/resources/fixtures_memory.json`（41 例，8 类操作：add / gram / retention / search / decay / facts / block / consolidate）
- 再生成：`node reference/engine/export_memory_fixtures.js <app-api.js 路径> reference/engine/fixtures_memory.json`
  - 导出脚本以 `LanWeixi-1.2.1-fix5` 的 `app-api.js` 原文为准，注入虚拟时钟与内存 MemoryStore，无随机点
- 时钟与存储注入：`MemorySystem(store, clock: () -> Long)`；`InMemoryMemoryStore` 每次 `load()` 返回新副本，模拟 JS 的 JSON 反序列化语义（否则 `memoryBlock.hits` 会拿到复习后的对象，与原实现不一致）
- `maybeConsolidate` 只移植**计数与触发判定**（每 20 次归零并返回 triggered），不做网络压缩、不落库
- 比对：`result` 与 `memories` 均做键集合相等检查，数值容差 1e-6，字符串/结构全等

### P06a 提示词预算口径

- 模块：`:domain:prompt`（纯 Kotlin JVM，自有 `PromptMessage(role, content)`，不依赖 `:core:network`）
- 结构：
  - 稳定段 = 人格 + 固定风格说明（旧版 8 条随机 STYLE_HINTS 不移植）
  - 动态段 = 时间 / 生理摘要 / 关系标签 / 近期事件 / 梦境 / 日期 / 信息流 / 前情摘要（≤300 字），为空整段省略
  - 历史 = 最近 8 条；用户内容 = 原文 + 非空记忆块（`\n\n` 拼接）
- 预算守卫 `PromptBudgetGuard`：
  - 稳定段 ≤ 1200、动态段 ≤ 300、单轮总量 ≤ 3000（保守估算：CJK 按字、拉丁按词、标点按 1）
  - `maxTokens` 默认 900，区间 800–1000
  - 越界返回 `PromptBuildResult.Rejected(reason, actual, limit, budget)`，**不静默截断**
- 确定性：无随机源；相同输入两次构建结果全等
- P06a 未做项（边界）：
  - 不发 HTTP、不读写数据库
  - 不做关系数值增减（关系标签由调用方传入；默认角色 50/50 归后续角色/设置卡）
  - 不移植旧 `buildMessages` 的 16 条 ×800 字与 500 token 设定
  - 不接 UI、不改 `:core:network` 与 Room schema

### P06b 内核装配与对话用例

- Hilt 单例（`SingletonComponent`）：
  - `MemorySystem` ← `KvBackedMemoryStore`（kv 键 `memory.default`，`load()` 每次返回深拷贝）
  - `VitalsEngine` ← `KvBackedVitalsStore`（键前缀 `vitals.`）+ `SystemRandom()` + 注入时钟
  - `VitalsEngine` 构造已去掉 store/random 默认值，无法绕过 DI 创建；`ChatStreamClient` 仍只注入 `OkHttpClient`
- 对话用例 `:feature:chat` 的 `ConversationEngine`：
  - 端口：`StreamRunner`（生产 `ChatStreamClientRunner`）、`ChatHistoryPort`（feature 端口；`core:data.MessageRepository` 只是 Room 仓储实现）、`EngineScheduler`（只定义 `EmptyEngineScheduler` / `ManualEngineScheduler`，**未引入 WorkManager**）
  - 只有 `Done` 落库：写 user + assistant 两行 → `addMemory(userText+reply, 0.68)` → `maybeConsolidate()`
  - 重复 `Done` 去重；`onClosed()` 零写入；Network / Busy(429) / InvalidKey(401) / Upstream 一律不写 assistant
  - 提示词记忆块由 `searchMemories(query, 2)` 提供；`maxTokens` 取 `PromptBuilder` 结果（默认 900）
- **过渡存储设计（重要）**：Room `memories` 表目前只有 weight/source/时间/访问计数，装不下引擎的 `stability / retrievals / archived`。当前 `kv` 里的 JSON 是引擎运行态的权威存储；`memories` 表关系化扩列单列为后续 **schema v2** 卡，本轮 `version=1` 与 `schemas/1.json` 均未改动。
- 未做项：Cubism/模型导入、关系数值增减、LLM 记忆压缩、通知/小组件、真实 tokenizer、`vitals_samples` 时间序列填充、WorkManager worker。

### P06c 生产接线

- 端口改名：`feature:chat` 的 `MessageRepository` → `ChatHistoryPort`（`suspend append(role, content, sessionId, status)` / `suspend recent(limit): List<HistoryMessage>`）；`HistoryMessage(role, content, createdAt)`
- Room 适配器：`:app` 的 `RoomChatHistoryPort` 内部用 `core:data.MessageRepository`，默认 `sessionId="default"`、`status="sent"`、`speaker=""`、`createdAt=System.currentTimeMillis()`，历史按 `createdAt` 升序取最近 N 条
- Hilt 装配：`:app` 的 `ChatModule` 提供 `PromptBuilder`、`ChatStreamClientRunner`、`RoomChatHistoryPort`、`ConversationEngine`（注入单例 `MemorySystem` / `ChatStreamClient` / 历史端口 / 默认 persona）；`apiKey` 为空串占位，留待设置页
- 并发：`ConversationEngine.send()` 全 suspend，`CountDownLatch` 已删除；`suspendCancellableCoroutine` 收敛 Done/Error/closed，协程取消时取消底层流；注入 `Dispatcher`（生产 `Dispatchers.IO`，测试 `UnconfinedTestDispatcher`）；持久化与记忆写入走 suspend 端口 / 注入的 dispatcher
- 落库顺序：user 行 → assistant 行 → `addMemory(userText+reply, 0.68)` → `maybeConsolidate()`；assistant 文本以 `Done.reply.ifEmpty { 累计 Delta }` 为准
- 测试：`ConversationEngineTest` 8 例（fake，含空 key 短路）改为 `runTest`；`RoomChatHistoryPortTest` 4 例（Robolectric + 内存 Room）：Done 后 `messages` 表恰好 2 行且字段齐全，429/401/abort 后 0 行
- **P06c.1 硬化**：
  - `send()` 整段（记忆检索 / 历史读取 / 提示词构建 / 流等待 / 持久化）都在 `withContext(dispatcher)` 内，Main 调用方不会在 UI 线程执行 `KvBackedMemoryStore` 里的 `runBlocking`；生产 `Dispatchers.IO`，测试 `UnconfinedTestDispatcher`
  - `ChatConfig(baseUrl, apiKey, model, persona)` 作为 provider seam；`apiKey` 为空时 `send()` 直接返回「配置缺失：未设置 API Key」，**不调用** `StreamRunner`，不把空 key 带进生产请求路径
  - `ConversationEngine` 为 `@Singleton`；`PromptBuilder` 无状态，保持非单例
- **单会话说明**：本轮只有 `default` 会话；`ChatHistoryPort.recent()` 不带 sessionId（实现固定取 `default`）。多会话单列后续卡片，**不扩 schema**。
- 边界：`version=1`、`schemas/1.json` sha256 未变、无 Migration；未做 WorkManager、schema v2 扩列；`VitalsEngine` 本轮只保留摘要注入缝，不实现定时 tick

### P07a 聊天首屏

- 结构：`ChatScreen`（纯 Compose，不引用 Hilt/Room/DAO）+ `ChatViewModel`（`StateFlow<ChatUiState>`）+ `ChatViewModelFactory`（手工注入，不用 `@HiltViewModel`）；`MainActivity` 临时承载
- 端口拆分：`ChatHistoryQueryPort`（只读，ViewModel 只注入这个）与 `ChatHistoryPort : ChatHistoryQueryPort`（含 `append`，仅 `ConversationEngine` 使用）；编译层面 ViewModel 拿不到写入能力
- 用例 seam：`ConversationUseCase` 接口，`ConversationEngine` 实现；`send()` 增加可选 `onDelta`，Delta 只更新 `partialAssistant`，**不落库**
- 落库边界不变：只有 `Done` 走后端持久化；abort/401/429/Upstream/`onClosed()` 不产生 user 或 assistant 行
- 测试口径：**只做 ViewModel JVM 测试**（`ChatViewModelTest 9`）+ 引擎 Delta 回调测试（`ConversationEngineDeltaCallbackTest 5`）；本卡不引入 `ui-test-junit4` / `createComposeRule`，Compose 布局只做人工冷启动冒烟
- 未做项：正式 Navigation、token/usage 展示、多会话、主题深度定制（复制/重发已由 P10 补齐）
- 已知问题：跨模块调用 `core:design` 的 `LanWeiXiTheme` 在 `:app` 编译期报 Unresolved（同模块 `ChatScreen` 等 @Composable 正常）；本卡在 `MainActivity` 内联同款色板临时绕过，问题留待后续卡片排查

## 7. SSE 完成语义（写死）

| 上游表现 | 客户端行为 | 是否允许落库 |
| --- | --- | --- |
| 原生 `done` 事件 | 回调 `SseEvent.Done(reply=上游 reply)` | 可以 |
| OpenAI 兼容 `[DONE]` / `finish_reason`，且有累计文本、未取消、未出错 | 合成 `SseEvent.Done(reply=累计文本)` | 可以 |
| `[DONE]` 但没有累计文本 | 只发 `EndOfStream` + `onClosed` | 不可以 |
| 连接中断（abort，无 done） | `onError(ChatError.Network)` | 不可以（已收文本由调用方保留但不落库） |
| 流中途 `error` 事件 | `onError(ChatError.Upstream)` | 不可以 |
| HTTP 401/403 / 429 | `onError(InvalidKey / Busy)` | 不可以 |
| 主动 cancel | 取消后不再回调任何事件 | 不可以 |

## 8. 未完成边界

- 仍无正式 Navigation（聊天页与设置页用 `showSettings` 状态切换；五个主入口其余仍是空壳）
- 没有 Cubism 运行时、模型导入器（T07）
- 记忆与提示词只有纯逻辑 + 测试（`:domain:memory` / `:domain:prompt`），**尚未接 UI**
- 对话引擎与聊天首屏已接 Room/Hilt（P06c + P07a）；无 Compose 组件测试
- **已有最小设置页**（P08b，用状态切换而非 Navigation）：可输入并保存 API Key，保存后返回聊天；仍无正式 Navigation
- 没有 WorkManager 主动消息、通知、小组件（M4）
- 没有旧数据迁移（T06 跳过）

## 9. 验证记录

- T01：`./gradlew :app:assembleDebug` BUILD SUCCESSFUL；真机（vivo V2171A / Android 15）安装并冷启动成功，无崩溃。
- T02：`DaoCrudTest` 8 + `DatabaseOpeningTest` 3 + `DataSingletonScopeTest` 3 = 14 项全部通过；schema `core/data/schemas/.../1.json` 已导出并入库存放。
- T03：`SseParserTest` 9 + `ChatStreamClientTest` 9 + `SingletonScopeTest` 3 + `SseMultiDataTest` 1 = 22 项通过；`MockServerLiveTest` 连真实 mock 服务的七场景通过。
- T04：`FixtureReplayTest`（217 例对拍，含键集合断言）+ `RandomSourceTest`（**5 项**：`SystemRandom` 两次序列不同、`LcgRandom` 同种子可复现、`SystemRandom` 引擎长时间运行 `reply_target` 仍在 1..4、`SequenceRandom` 低/高序列确定性影响 `reply_target`、600 tick 长跑无 NaN/Inf 且概率字段在 [0,1]）全部通过；裸跑 `./gradlew :domain:vitals:test` 即全绿，不依赖环境变量。
- P07a：新增 `ChatViewModelTest 9` + `ConversationEngineDeltaCallbackTest 5`；全量 **17 suites / 87 tests / 1 skipped / 0 failures / 0 errors**；`:app:assembleDebug` 通过；真机冷启动无崩溃。真机「空 Key banner」点击因输入法浮层遮挡未截到，该路径由 `missing api key shows notice without saving` 用例覆盖。
- P07a.1：`ChatViewModel` 增加 `onCleared()` 取消注入 scope；`MainActivity` 改走标准 `viewModel(factory)`；历史加载失败显示 `Notice`；聊天列表自动滚到底。`ChatViewModelTest` 9 → **11**（补历史加载失败、scope 取消两例）；全量 **17 suites / 89 tests / 1 skipped / 0 failures / 0 errors**（以 XML 为准）。未做项保留：无取消发送、无导航、无设置页、无 Compose 组件测试。
- P08a：新增 `ChatConfigProvider` 端口（`send()` 每次取最新配置，空 Key 短路且不调用 `StreamRunner`）；`:app` 实现 `LocalDataStoreConfigProvider`（Preferences DataStore，仅 `api_key` 一个键，不写 Room、无日志、不打印明文）；移除长期缓存的 `@Singleton provideChatConfig()`。新增 `ConversationEngineConfigTest 4`（空 Key 短路 / Key 透传 / 二次发送取新 Key / 失败路径零落库）+ `LocalDataStoreConfigProviderTest 4`（默认空 / 保存读取 / 更新读取 / 空白清除）。
- P08b：新增最小设置页（`SettingsScreen` + `SettingsViewModel`，保存后返回聊天；`ChatConfigStore` 可写端口只给设置层）；设置保存失败兜底（`runCatching`，失败不回调、不残留 `saving`，显示「保存失败，请重试」）；`SettingsScreen`/`ChatScreen` 补 `statusBarsPadding` 与 `imePadding`；`SettingsViewModelTest` **5 例**（加载 / 保存回调 / 编辑重置 / 空白清除 / 保存失败）。全量 **20 suites / 102 tests / 1 skipped / 0 failures / 0 errors**（以 XML 为准）。
- P09：新增取消发送。`ChatViewModel` 持有当前 `sendJob`，`cancelSend()` 取消协程并置 `sending=false / partialAssistant=null`、保留 draft、显示 `Notice("已取消，本次回复未保存")`；`CancellationException` 原样抛出，不映射成 Error、不触发 `loadHistory()`。引擎侧复用既有 `suspendCancellableCoroutine + invokeOnCancellation { handle.cancel() }`，**未给 @Singleton 引擎加可变 handle 字段**。`ChatScreen` 在 sending 时显示「取消」，`MainActivity` 接 `viewModel::cancelSend`。新增 `ConversationEngineCancelTest 1`（fake `RecordingStreamHandle`：cancelCount ≥ 1、appends 为空、memory 未新增）+ `ChatViewModelTest` 11 → **14**（取消状态 / 不映射 Error / 取消后可再发）。全量 **21 suites / 106 tests / 1 skipped / 0 failures / 0 errors**（以 XML 为准）。
- P10：新增消息复制与重发。`ChatScreen` 的 `MessageBubble` 用 `combinedClickable`：长按把文本写入 `LocalClipboardManager` 并 Toast「已复制」；user 气泡短按调用 `onResend`（assistant 气泡不响应）。`ChatViewModel` 新增 `resendUserMessage(text)` 与共用入口 `startSend(userText)`，`send()` 与重发复用同一套 sending / banner / 取消 / 历史重载状态机。`ChatViewModelTest` 14 → **18**（重发调用一次、sending 中忽略、失败保留草稿、ViewModel 不依赖剪贴板）。全量 **21 suites / 110 tests / 1 skipped / 0 failures / 0 errors**（以 XML 为准）。**当前交互以 P11 的长按菜单为准，本条的短按实现仅作历史记录。**
- P11：复制/重发交互收口。user 气泡改为**长按弹 Material3 DropdownMenu**（「复制」Toast、「重发」调 `onResend`），**短按无动作**；assistant 气泡长按直接复制、不提供重发；sending 期间「重发」项禁用；菜单点外部关闭。`ChatViewModel` 未新增方法，`resendUserMessage(text)` 原样复用。`ChatViewModelTest 18` 保持全绿；全量 **21 suites / 110 tests / 1 skipped / 0 failures / 0 errors**（以 XML 为准）。

T04 分族（权威夹具 `fixtures_seeded.json`）：

| 族 | A | B | C | D | E | F | G | H | I | J | K | L | M |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 通过 | 30 | 6 | 12 | 10 | 25 | 50 | 20 | 10 | 3 | 10 | 12 | 22 | 7 |
| 失败 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

七场景实测（mock_server.js + Kotlin 客户端，2026-11-04）：

| 场景 | 首字延迟 | chars | done | error |
| --- | --- | --- | --- | --- |
| normal | 187 ms | 40 | true | - |
| hiccup | 91 ms | 69 | true | - |
| oversize | 952 ms | 3000 | true | - |
| abort | 251 ms | 2 | false | Network(EOFException) |
| mid_error | 312 ms | 6 | false | Upstream(429) |
| upstream | 47 ms | 5 | **true** | -（`[DONE]` 合成完成） |
| error | - | 0 | false | InvalidKey(401) |

> 单例断言为源码级（`@Singleton` 注解 + 构造签名 + 工厂方法不存在），
> 不是运行时 Hilt 组件实例比对；运行时装配由 `@HiltAndroidApp` 在 T01 真机启动中验证。

## 10. 挂账（带到后续卡片验收）

1. **Hilt 单例运行时复核**：当前只有源码级断言，M1 末用冷启动 logcat 复核单例只创建一次。
2. **upstream 合成 Done 的 usage 为 null**：`[DONE]`/`finish_reason` 路径没有 token 用量；token 展示留到 UI 卡的用量展示。
3. **落库纪律**：UI/业务层只认 `SseEvent.Done` 落库；`onClosed()` 不触发保存；成功流的 Done 分支要做 completed 去重（客户端已去重，调用方仍需幂等）。
4. **随机源位置**：固定种子实现（`LcgRandom`/`SequenceRandom`）只在 test 源码集，main 源码集禁止出现。
5. **DataStore 明文 Key**：当前 Preferences DataStore 未加密，待 Keystore/加密安全卡。
6. **设置 UI 位置**：暂放 `:feature:chat`（`SettingsScreen`/`SettingsViewModel`），后续归并到 `:feature:settings`。
7. **保存失败提示**：设置页只有「保存失败，请重试」文案，无重试按钮与错误详情。
8. ~~**重发入口易误触**~~ **已由 P11 关闭**：user 气泡改为长按弹出「复制 / 重发」菜单，短按不再触发任何发送动作；sending 期间重发项禁用。
9. **长按菜单真机冒烟待补**：P11 的长按复制/重发菜单尚未完成真机目视截图（冷启动无历史消息，无气泡可长按）；当前由源码审查 + `ChatViewModelTest 18` 例状态覆盖，待后续存在历史消息或真机调试时补菜单展开/禁用态冒烟。不引入仅 debug 的 seed 假消息逻辑。
10. **跨模块 @Composable 不可见（P12a 根因未定位，P18 决定不再投入）**：`:core:design` 的 `LanWeiXiTheme`（`ThemeKt`）在 `:app` 编译期持续 Unresolved；证据：R 类可见、classes.jar 内确有 `ThemeKt.class` 且带 `kotlin.Metadata`、显式 jar 注入仍 Unresolved、清缓存与 clean 均无效。已用结构规避（主题收回到 `:app`），后续 feature 模块不再跨模块引用 @Composable 主题/组件。最小复现工程成本高，标记为**不再投入**。
11. **vivo Android 15 实机启动阻塞**：真机（V2171A / Android 15）安装 `com.lanweixi.client` 后冷启动报 `ClassNotFoundException: com.lanweixi.client.LanWeiXiApplication`。已排除：类不在 dex（dexdump 确认在）、多 dex 加载（R8 单 dex 仍崩）、Hilt（去 Hilt 探针仍崩）、对齐/签名（zipalign + 重签仍崩）、安装方式（流式/非流式/pm install）、设备重启与加固关闭。同设备微信可正常启动，说明设备 ART 正常；本环境无模拟器（SDK 无 emulator/system-images），无法用 AVD 复验。待后续换设备或 ROM 排查。
12. **图标为位图替换，未做自适应图标**：P29 直接生成 5 密度 PNG（方形/圆形），并删除了 `mipmap-anydpi-v26` 的分层自适应图标。Android 8+ 的分层图标（前景 + 背景）需要一张透明背景的前景图；当前位图在部分 OEM 桌面上可能出现裁切或黑边，后续若做分层图标需补透明前景素材。
