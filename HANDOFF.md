# 交接文档

> 最后更新：2026-11-24。给**下一轮对话的自己**或换人接手时看。
> 项目根：`D:\work\lanweixi-native`（**必须是 ASCII 路径**）。

---

## 0. 一句话状态

**72 个测试 suite / 510 tests / 0 failures 全绿**，APK 18.2 MB，数据库 **v10**。
五个工作台模块 + AI 对话（含**消息引用**）+ 插件体系 + 视觉体系 + 多 Agent 群聊（含**一次 @ 多人 / @全体**）
+ 会话列表 + 任务提醒 / Markdown 导出 / 笔记标签筛选 / 全局搜索（P199 四件套）
+ **首次启动引导**（P201/P202）+ **AI 主导的笔记与任务**（P206–P208）+ **Anthropic（Claude）支持**（P209）
+ **上下文压缩**（P210）+ **数据安全与体验补齐**（P211）+ **发布准备**（P212：正式签名 / 隐私说明 / 崩溃兜底 / README 重写）。

---

## 1. 环境

| 项 | 值 |
|---|---|
| JDK | `D:\work\tools\jdk\jdk-17.0.21+9` |
| adb | `D:\Android\platform-tools\adb.exe` |
| 设备 | vivo V2171A（iQOO 9），1080×2400 |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| Room | v6，类名 `LanWeixiDatabase`（小写 x） |

---

## 2. 命令

```powershell
$env:JAVA_HOME = 'D:\work\tools\jdk\jdk-17.0.21+9'
Set-Location 'D:\work\lanweixi-native'
.\gradlew.bat :app:assembleDebug --console=plain
```

全量测试模块：`core:network domain:prompt domain:plugin domain:docs domain:tools core:data feature:chat feature:settings feature:notes feature:prompts feature:tasks feature:docs feature:review feature:plugins feature:search feature:onboarding app`

装机：

```powershell
& 'D:\Android\platform-tools\adb.exe' install -r 'D:\work\lanweixi-native\app\build\outputs\apk\debug\app-debug.apk'
& 'D:\Android\platform-tools\adb.exe' shell am force-stop com.lanweixi.client
& 'D:\Android\platform-tools\adb.exe' shell am start -n com.lanweixi.client/.MainActivity
```

---

## 3. 项目结构

```
app/                      单 Activity + Navigation Compose
core/design/              慕夏主题、玻璃栏、图标、通用组件
core/data/                Room、仓储、插件宿主、AgentStore
core/network/             OpenAI 兼容流式客户端
domain/prompt/            ChatConfig、PromptBuilder、AgentProfile、GroupSession
domain/plugin/            插件契约与权限表
domain/docs|tools/        文档分块、工具契约
feature/*/                九个功能模块
```

关键类：
- `ConversationEngine`（`feature/chat`）——对话主循环，支持工具调用
- `PromptBuilder`（`domain/prompt`）——拼系统提示词，支持 `memoryBlock`
- `LuaHostApis`（`core/data/plugin/lua`）——插件能碰到的所有宿主能力
- `MuchaTheme`（`core/design/mucha`）——三档主题

---

## 4. 已完成

完整清单见 `docs/DEVLOG.md` 的 `| Pnnn |` 表（P150–P197 是近期）。要点：

- **插件体系**：安装/授权/沙箱/声明式 UI/配置/远程源/更新检查/搜索/日志
- **视觉**：三档主题、玻璃栏、插画背景、双版本图标
- **数据**：备份恢复（导入前自动留底）
- **AI**：工具调用、会话隔离、防幻觉、性格编辑、记忆导入

---

## 5. 第 3 批（多 Agent 群聊 + 会话列表）已全部完成

**群聊地基**：
- `AgentProfile` + `AgentStore`（DataStore JSON）
- 6 个服务商预设、工具人管理页（设置 → 工具人）
- `GroupSession` + 群列表页（设置 → 群聊）
- `ConversationEngine.send` 支持 `overrideConfig`

**群聊页（P197）**：
- `feature/chat`：`GroupChatModels.kt`（端口/成员/状态）、`GroupChatEngine.kt`（发送）、
  `GroupChatViewModel.kt`、`GroupChatScreen.kt`
- `:app`：`RoomGroupChatPort`（`sessionId = 群 id`、`speaker = 发言者`）、路由 `group_chat/{groupId}`
- 不 @ → 兰未晞（全局配置 + 人设 + 记忆）；@ 谁 → 只调那个工具人的 BaseURL/Key/模型
- 别人的发言以 `[名字] 内容` 进上下文；自己在历史里是 assistant
- 气泡按 `speaker` 取成员色，头像/昵称/@ 选择器/未配 Key 明确提示都有了

**会话列表主页 + 路由重构（P198）**：
- 新的首屏 `Route.SESSIONS`（底部「聊天」Tab），单聊 + 群聊混排，按最后活动时间排序
- `Route.CONVERSATION + "/{sessionId}"` 参数化单聊页；`Route.CHAT` **已删除**
- `ChatViewModel(initialSessionId)`：进页面时**先 switch 再读历史**（顺序反了会读到上一个会话，
  引擎落库也会写错会话）
- 群 id 会出现在 `sessionDigest()` 里（群消息也存 messages 表），列表必须按群 id 过滤，
  否则同一个群会显示两次
- 长按删除会话/群（带确认）；删的是当前会话时切到最近还有消息的那条**单聊**——
  群 id 不能当 current，否则下一句单聊会写进群里
- 空会话（新建后一句没说）不进列表，否则会留下删不完的空坑
- `SessionsSource` 端口 + `AppSessionsSource`（:app 实现），9 个 JVM 单测

**还留着的**：
- 群聊暂**不带工具**（群里问笔记/任务会让用户去单聊问）——是否要接，看实际需要
- 单聊页的「会话列表」弹窗已移除，切换会话统一走首屏

---

## 5b. P199：四件套（任务提醒 / Markdown 导出 / 标签筛选 / 全局搜索）

| 项 | 落点 | 关键点 |
|---|---|---|
| 任务截止提醒 | `core/data/reminder/TaskReminderPlanner`（纯逻辑）+ `:app` 的 `TaskReminderRunner` / `TaskReminderWorker` / `TaskReminderNotifications` / `TaskReminderScheduler` | WorkManager 每小时一次；**去重键是 `任务id@截止时间`**（改期后能重新提醒）；过期超 24h 不补；点通知进任务页（Activity 需 `singleTop`，否则热启动不回调 `onNewIntent`） |
| Markdown 导出 | `core/data/export/NotesMarkdown` + 设置页按钮 | 一篇一节，带标签/时间；正文里的 Markdown 原样保留；走 SAF `CreateDocument("text/markdown")` |
| 笔记标签筛选 | `NotesViewModel.onTagSelect` + `NotesScreen.TagFilterRow` | 和搜索**叠加**；标签池从全部 active 笔记算；标签被改没了自动清掉筛选 |
| 全局搜索 | 新模块 `:feature:search` + 路由 `search` | 入口在「更多」页；`GlobalSearch` 五路合并；输入防抖 250ms |

**顺手修掉的两个存量 bug**：
1. **从全局搜索点进详情页会闪退** —— 编辑/详情页原来直接 `navController.getBackStackEntry(父列表)`，
   而搜索直达时父列表不在回退栈里，抛 `IllegalArgumentException` 把 App 带走。
   改成 `parentEntryOrNull()`，拿不到就自己建一个 ViewModel。
2. **笔记编辑页布局错位** —— `NoteEditScreen` 的 `when` 块写在 `Column` 外面，
   顶栏和编辑区在同一位置重叠，「保存」按钮被盖住点不到。把 `when` 挪进 `Column`。

**任务提醒的真机验证有一个坑**：vivo 把 WorkManager 的作业压到「Minimum latency +58m」，
干等不到。所以抽了 `TaskReminderRunner` 出来，设置页「诊断 → 立即检查任务提醒」手动跑一轮——
vivo / 小米这类 ROM 上这个手动入口本身就是必要的兜底。

**重要**：`MessageEntity` 早就有 `sessionId` 和 `speaker` 字段——**不需要数据库迁移**。
`speaker` 存 agent id，空串 = 用户，`__host__` = 兰未晞。

---

## 5c. P200：复盘可视化 + 技术债清理

| 项 | 落点 | 关键点 |
|---|---|---|
| 复盘可视化 | `feature:review/ReviewTrend.kt` | 列表页顶部「最近 4 周」分组柱状图（完成任务 / 新建笔记）；`buildWeeklyTrend()` 是顶层 suspend 函数，周切分可单测；全 0 时显示「这几周还没有记录」 |
| 头像收敛 | `feature:chat/ChatAvatar.kt` | `QqAvatar` / `GroupAvatar` / `ListAvatar` 三份合成一份，`tint` 参数决定底色（null 走主题渐变） |
| 死字段清理 | `ChatUiState` | 删 `relationLabel`、`sessionId`、`sessions` 与 `ChatViewModel` 的 `switchSession/newSession/loadSession/reloadSession`（会话切换早已移到列表页，这些只写不读） |
| 提醒键清理 | `TaskReminderPlanner.pruneKeys` | 任务删掉后，`任务id@截止时间` 的旧键会被清掉，SharedPreferences 不会无限增长 |

**真机验证（补上了）**：USB 掉线后重新接上，P200 已装机验证——
- 复盘趋势图正常：本周 1 个完成任务 → 金色柱满格，前三周只剩 0 值细线，标签 `11/2 / 11/9 / 11/16 / 本周`，底部统计正确
- **顺手修了一个只有真机才看得出的坑**：趋势卡片最早用 `MuchaCard`，它内部有个 `fillMaxSize` 的角饰 Box，
  放在 `Column` 里（高度约束有限）会把卡片撑满整屏、把下面的复盘列表挤没。改用 `Surface` 包 `Column`，高度回到 wrap
- 头像收敛正常：会话列表群头像粉紫渐变、单聊头像真实图片；单聊页顶栏 38dp、消息 36dp 都对

---

## 5d. P201：首次启动引导

| 项 | 落点 | 关键点 |
|---|---|---|
| 首启判定 | `feature/onboarding/OnboardingGate` | 三选一：看过 → 主界面；没看过但有 Key → 老用户升级，补写标记不打扰；都没有 → 显示引导。标记存 `SharedPreferences("onboarding")`，同步读，首帧就能决定；判定中只画主题背景，主界面不闪 |
| 两屏引导 | `feature/onboarding/OnboardingScreen` + `OnboardingViewModel` | 第 1 屏起名字（默认兰未晞）；第 2 屏选服务商（6 预设自动填 BaseURL/模型）+ Key + **真发一次请求测连通性** + 完成/先跳过 |
| 连通性测试 | `core/network/ConnectivityProbe` | 非流式 `max_tokens=1`，自己的 15s 超时；结果 7 类（成功 / Key 无效 / 限流 / 余额不足 / 地址或模型不对 / 服务端错误 / 连不上）；Key 含全角字符本地就拦，不发请求 |
| 跳过后果 | 单聊 banner | 不静默失败：首条消息提示「未配置 API Key：请先在设置里填写」。第 2 轮可做成可点「去设置」 |
| 重看入口 | 设置 → 引导 → 重看首次引导 | 预填现有名字/服务商/Key；不重置已读标记 |
| 顺手修 | `CharacterNameSync` + 设置页 | ① 改名后人设模板里的名字跟着换（用户自写人设不动）；② 主 API Key 输入加非 ASCII 拦截，输入时就红字提示并禁用保存 |

真机验证（2026-11-24）：
- 覆盖安装新 APK → 有 Key 的老用户直接进主界面，**不弹引导**
- 设置 → 重看引导 → 预填正确（DeepSeek、baseUrl、模型、Key）→ 真 Key 测连通性 → 「连通了，服务器正常返回」
- 删掉 DataStore + onboarding 标记模拟全新安装 → 引导第 1 屏 → 改名为 `Xiao'xi` → 第 2 屏 → 选 Kimi 自动填 `api.moonshot.cn/v1` + `moonshot-v1-8k` → 先跳过 → 主界面名字生效、发消息提示未配 Key
- 重启不再弹引导；恢复 DataStore 后角色名/Key/人设原样回来

**重要（测试方法）**：`pm clear` 会连 Keystore 密钥一起清，恢复的 `enc:` Key 解不开。
模拟全新安装用 `run-as` 删 `files/datastore/lanweixi_settings.preferences_pb` + `shared_prefs/onboarding.xml`，
测完 `run-as cp` 回来即可，不丢真 Key。

---

## 5e. P202：跳过后的「去设置」出口

| 项 | 落点 | 关键点 |
|---|---|---|
| 单聊提示条 | `ChatBanner` + `BannerAction` + `ChatScreen.BannerArea` | 配置缺失 / Key 无效给「去设置」，普通失败仍是「重试」；ViewModel 只给文案，跳转由 UI 接 `onOpenSettings` |
| 会话列表 | `SessionsScreen(missingApiKey)` + `MainActivity` | 没 Key 时列表顶部常驻提示 +「去设置」；`LaunchedEffect(currentRoute, showOnboarding, firstRunChecked)` 在回到列表/走完引导时刷新 |

真机验证（设备接回后补上）：
- 删 DataStore 模拟无 Key → 会话列表顶部「还没配 API Key，现在发消息不会有回复」+「去设置」→ 点进设置 ✓
- 单聊发消息 → banner 带「去设置」→ 点击进设置 ✓
- 重看引导 → 预填 → 完成 → 回设置页 → 再发消息，兰未晞正常回复（**Key 重新加密保存后仍有效**）✓
- 恢复 DataStore 后提示条自动消失 ✓

---

## 5f. P203：群聊一次 @ 多人 / @全体

| 项 | 落点 | 关键点 |
|---|---|---|
| 多选状态 | `GroupChatUiState.targetIds: Set<String>` | 空 = 兰未晞接；按**成员列表顺序**发（与点击先后无关）；没配 Key 的点不上，全选时跳过并说明 |
| 选择器 | `GroupChatScreen.GroupInputBar` | 点一下勾、再点取消；顶部「@ 全体成员」「只发给兰未晞」；@ 按钮选中时高亮 |
| 发送 | `GroupChatUseCase.sendToMany` + `GroupChatEngine.performSendMany` | **用户消息只落一次**；目标依次调 API，前一个人的回复进后一个人的上下文；单个失败不中断，失败原因合并成一条 banner |
| 提示 | chip 行 + 输入框 hint | 「@A、B、C（3 人，依次回复）」，点 × 清空回到兰未晞 |

真机验证：群「讨论组」（3 人）@全体发「hi」→ 兰未晞、新工具人、新工具人1 依次各回一条；
chip 显示「3 人，依次回复」，点 × 后 hint 回到「说点什么…」。
单测：引擎 8 → 10（多目标落库顺序/上下文累积、一个失败不影响另一个）；新增 `GroupChatViewModelTest` 8 个。

---

## 5g. P204：消息引用（单聊）

| 项 | 落点 | 关键点 |
|---|---|---|
| 数据 | `MessageEntity.quoteId / quotePreview` + `Migrations.MIGRATION_6_7` | 快照 + 软链接两列都存：快照保证原消息没了也能显示，id 用来点引用定位。v6→v7 `ALTER TABLE`，schema 导出 `7.json` |
| 链路 | `HistoryMessage(id, quoteId, quotePreview)`、`ChatHistoryPort.append`、`ConversationUseCase.send` | 落库正文保持用户原文（UI 干净），引擎只在提示词里拼一行「（引用：名字：节选）」；历史里的引用同样还原给模型 |
| UI | `ChatScreen` | 气泡长按 → 复制/引用（user 另加重发）；输入框上方「↩ 名字：节选 ×」；发送后气泡内引用卡片，点它滚到原消息并高亮 1.6 秒；发送成功自动收起引用条，失败把引用放回来 |
| 单测 | `MessageQuoteMigrationTest` 1 / `ConversationEngineQuoteTest` 3 / `ChatViewModelQuoteTest` 6 | |

真机验证（v6→v7 第一次迁移）：启动不崩、历史保留；长按 assistant 是「复制/引用」，长按 user 是「复制/引用/重发」；
引用后输入条显示快照，发送后气泡带引用卡片并正常回复；引用自己显示「我」；× 取消、点引用卡片定位都正常。
**群聊引用留到 P205。**

---

## 5h. P205：消息引用（群聊）

| 项 | 落点 | 关键点 |
|---|---|---|
| 数据 | `GroupMessage(id/quoteId/quotePreview)` + `GroupChatPort.append` | 复用 P204 的 messages 两列，**无新迁移** |
| 上下文 | `GroupChatEngine` | 当前 userText 与历史 user 消息都拼「（引用：…）」；落库仍是原文 |
| 交互 | `GroupChatScreen` | 长按 → 复制/引用；引用条 + 气泡引用卡片 + 点击定位高亮；**引用工具人消息时自动把他加进 @（引用谁就回谁）**，引用兰未晞/自己则清空 @ 回默认 |
| 单测 | 引擎 +2、ViewModel +5 | |

真机验证：引用「新工具人1」的消息 → chip 自动变「@新工具人1」→ 发送后气泡带引用卡片、本人回复；
引用兰未晞的消息 → @ 清空、hint 回到「说点什么…」；引用条/卡片显示正常。

---

## 5i. P206：AI 主导的笔记与任务（一）——写入通道 + 待审核队列

| 项 | 落点 | 关键点 |
|---|---|---|
| 数据 | `DraftEntity` + `DraftDao` + `RoomDraftRepository` + `Migrations.MIGRATION_7_8`（v8，schema `8.json`） | 草稿**独立成表**，不往 notes/tasks 加 status——那五处入口（列表/搜索/标签/导出/全局搜索）全得过滤。`kind` 用 `DraftKind`，`status` 用 `DraftStatus`（pending / approved / rejected） |
| 写入工具 | `HostTools.ProposeNote` / `ProposeTask` | 只写草稿，**不标 sensitive**（草稿不生效）。秒级时间戳自动 ×1000；待审核满 20 条拒写；标题空用正文首行兜底。工具描述里注明「正文可以分点/Markdown，这是数据」——否则人设的「不要列表」会传染进参数 |
| 提示词 | `PromptBuilder.WRITE_RULE` | 稳定段 +2 句：只有「记一下/记下来/记住/remember this」或确认结论才写；只能放进待审核，别假装存好。稳定段底子从 ~470 token 涨到 ~556（预算 1200，仍宽）；`PromptBuilderTest` 的 total 越界用例 persona 从 700 压到 400 |
| 审核规则 | `core:data/draft/DraftReviewer` + `DraftReview` 接口 | 只有 pending 能处理（连点两次不会插两条）；note→notes、task→tasks；驳回只改状态不删记录。接口是为了 ViewModel 单测能塞假实现 |
| 待审核页 | 新模块 `:feature:inbox`（`InboxViewModel` + `InboxScreen`） | 类型徽标 / 标题 / 3 行摘要 / 截止与优先级 / 「来自聊天」/ 确认·驳回·全部确认 / 已处理折叠 / 空态写明怎么产生草稿。**操作结果用页面内提示条，不用 Toast**（真机 Toast 常被系统弹窗盖住） |
| 入口 | `MoreScreen` 第一项「待审核」+ 角标 | 数量在读 `Route.MORE` 时现查（`LaunchedEffect(Unit)`，从待审核页返回会重跑） |
| 截止时间 | `core:data/tools/DueTimeParser` | **不让模型算毫秒**。真机实测「明天下午三点」被算成 11/27（差两天）。工具参数改成 `due` 人话（`today` / `tomorrow 15:00` / `2026-11-25` / `2026-11-25 15:00`），宿主换算；旧 `dueAt` 时间戳仍兼容（秒自动 ×1000） |
| 工具痕迹 | `feature:chat/ChatUiState.toolTrailLabel` | 直接显示 `host_propose_task` 用户看不懂；映射成「查了笔记 / 记了一条任务」，文案「她刚刚：…」 |
| 单测 | +44 | 审核规则 12 / DAO 5 / 迁移 1 / 写入工具 6 / 截止解析 8 / ViewModel 9 / 工具痕迹 3 |

**真机验证（2026-11-24，v7→v8 第一次迁移）**：启动不崩、旧历史与任务保留、`drafts` 表 + 索引建对；
单聊发「remember this: interview tomorrow 3pm…」→ 她回「记好了，已经放进待审核了」→ 底部「她刚刚：记了一条任务」；
更多页角标 `1` → 待审核卡片（任务徽标 / 11月24日 19:04 / 来自聊天 / 标题 / 详情 / 截止与优先级）；
「驳回」→ 提示条「已驳回」+ 查看已处理里出现该条；「全部确认 2 条」（笔记 + 任务）→ 笔记页出现带 `#工作 #面试`
和分点正文的笔记、任务页出现第二条任务；截止展示「11月25日 15:00」正确。
测试产生的假数据已清掉（tasks 3→1、notes 1→0、drafts 4→0），聊天记录保留。

**已知限制**：群聊不带工具（HANDOFF 第 6 节决策），所以群聊里说「记一下」她现在办不到；AI 写草稿只在单聊生效。
另外模型在真机上有防重复行为——重复发同样的「记一下」她会先问「是改时间还是补充」，不会硬塞第二条草稿。

---

## 5j. P207：审核闭环（修改 / 撤销 / 来源跳转 / 聊天页入口）

| 项 | 落点 | 关键点 |
|---|---|---|
| 数据 | v8→v9：`drafts` 加 `sourceText`（schema `9.json`） | 工具执行时当前消息**还没落库**，拿不到消息 id，只能存原话片段；跳回时按它找消息 |
| 来源传递 | `ToolCall.context`（domain:tools）+ `ConversationEngine` 执行时传 `userText` | 插件工具忽略即可；`HostTools` 把它写进 `sourceText.take(200)` |
| 修改 | `DraftReviewer.approveEdited` + `:feature:inbox/DraftEditScreen` | **先 updateContent 再 approve**：正式记录与草稿内容一致，撤销后还能再改再确认。截止用 `DraftDuePreset`（今天/明天/3天后/7天后 + `EXACT` 保留原精确时间），「不设」才清零 |
| 撤销 | `DraftReviewer.undo` + `DraftDao.markPending` | 只对 `approved` 生效：删掉写进 notes/tasks 的那条，草稿回 pending；驳回的不能撤销 |
| 来源跳转 | `Route.CONVERSATION/{sessionId}?highlight=…` + `ChatViewModel.highlightQuery` | 进页面读历史后按前 40 字找 user 消息，设 `highlightId` 复用 P204 的高亮/滚动；匹配不到就不高亮 |
| 聊天页入口 | `ChatScreen` 顶部「她有 N 条等你确认 ›」 | 数量由 `pendingDrafts` provider 给，进页面与发送完成后刷新；样式用实底 + 描边（0.12 主题色在插画上看不见） |
| 单测 | +24 | 审核规则 7 / 迁移 1 / 待审核 VM 5 / 截止档 5 / 聊天页 6 |

**真机验证（v8→v9 迁移）**：待审核卡片出现「修改 / 驳回 / 确认」三个按钮和可点的「来自聊天 ›」；
修改页预填正确（标题 / 详情 / 优先级 / 截止显示「11月25日 15:00」原定档）；
把优先级改成「低」、截止点「明天」→ 保存并确认 → 任务页显示「低 · 11月25日」；
已处理里点「撤销」→ 提示「已撤销，草稿回到待审核」、草稿回到待审核（修改后的值还在）、已处理变空；
「来自聊天」跳回原会话并滚到那条「remember this…」用户消息（**高亮边框没截到**：vivo 转场动画慢，截图时还在淡入）。

---

## 5k. P208：完成回传与收口

| 项 | 落点 | 关键点 |
|---|---|---|
| 完成回传 | `TasksViewModel.setDone` + 可空 `EventRepository` | 勾选完成写 `events(type="task_done", payload=标题, at=时钟)`；取消勾选不写；取不到任务/写失败都不影响勾选 |
| 她知道了 | `core:data/task/TaskDigest` + `ChatViewModel.taskContext` | 「今天到期未完成」+「最近 3 天完成」，每组最多 3 条、标题截 18 字，拼一句进动态段 `recentEvent`（预算 300 token） |
| 提醒口吻 | `TaskReminderNotifications.notify(..., characterName)` + `ReminderEntryPoint.configStore()` | 抬头「{角色名} 提醒你 / {角色名} 说这个过期了」；角色名现读，改名跟着变 |
| 复盘采纳率 | `ReviewDao` 三个草稿计数 + `ReviewSnapshot` + `ReviewTrendChart` | 本周「AI 提议 N 条 · 采纳 M 条」，0 条不显示 |
| 单测 | +12 | 任务摘要 6 / 完成事件 3 / 复盘草稿统计 1 / 其余装配 |
| 顺手修 | `TaskEditScreen` | `when` 块写在 `Column` 外面（和 P199 笔记页同款 bug），顶栏「保存」被表单压住点不到——新建任务时真机发现，已修并验证保存可用 |

**真机验证（2026-11-24）**：
- 任务编辑页修复后顶栏「新建任务」+「保存」正常显示、能输入标题并保存成功；
- 勾选「R3 check」完成 → 库里 `events` 新增 `task_done / 'R3 check'`（19:36:46）；
- 复盘页显示「AI 提议 1 条 · 采纳 0 条」；
- 设置 → 诊断 → 立即检查任务提醒 → 通知栏「兰未晞 提醒你 / 交周报（提醒测试）· 11月24日 20:07」。
测试数据已清理（tasks 3→1、drafts 1→0、task_done 事件清空）。

---

## 5l. P209：Anthropic（Claude）支持

| 项 | 落点 | 关键点 |
|---|---|---|
| 协议判定 | `core:network/ChatProtocol.kt` | **按 BaseURL 判定**：含 `anthropic` 就走 Anthropic，否则 OpenAI 兼容。不新增配置字段、不用迁移；用户填 `https://api.anthropic.com` 即可 |
| 端点 | `messagesUrl(baseUrl)` | 域名 / `/v1` / 完整 `/v1/messages` 三种都归一到 `/v1/messages` |
| 请求 | `ChatStreamClient.buildAnthropicBody` + `anthropicMessages` | system 拆独立字段；`role=tool` 并进 user 的 `tool_result` 块；assistant 用 `tool_use` 块（`input` 是对象）；工具声明 `input_schema`；`tool_choice.required` → `any`；相邻同角色合并 |
| 流解析 | `SseParser.parseAnthropic` | `message_start`（带 input_tokens）/ `content_block_delta`（text_delta、input_json_delta）/ `message_delta`（output_tokens）/ `message_stop`；新增 `SseEvent.Usage`，合成 Done 时带 usage |
| 探针 | `ConnectivityProbe` | 按协议选端点与 header，Anthropic 用 `x-api-key`，不再发 Bearer |
| 预设 | `AgentProviders` | 加 Claude（`claude-3-5-sonnet-latest`）；工具人页预设从写死两行改成 `chunked(3)` |
| 提示 | 设置页 BaseURL 下 | 「Anthropic 填 https://api.anthropic.com 即可自动切换协议」；地址含 anthropic 时改成「当前走 Anthropic 协议」 |
| 单测 | +13 | 协议/端点 4、Anthropic 请求与流解析 7、探针 2（MockWebServer 断言 URL/headers/body 结构与 SSE 事件） |

**真机验证（2026-11-24）**：引导页出现 Claude 预设，选中后自动填 `https://api.anthropic.com` + `claude-3-5-sonnet-latest`；
用假 Key 点「测试连通性」→ 收到 Anthropic 的真实 401「Request not allowed」→ 映射成「Key 无效或没权限」。
**没验证到的**：流式对话与工具调用的端到端（没有可用的 Anthropic Key）。有 Key 后按这个顺序补：
设置里把主配置切到 Claude → 发一句普通消息看流式 → 说「记一下」看工具调用（`host_propose_task` 的 `tool_use` 回填）。

---

## 5m. P210：上下文压缩（长对话不再「忘事」）

| 项 | 落点 | 关键点 |
|---|---|---|
| 数据 | v9→v10 新增 `session_summaries`（schema `10.json`） | 每会话**一条**，滚动覆盖；`untilId` 记录压到哪条消息，下次只处理之后的 |
| 规则 | `core:data/summary/SummaryPlanner`（纯函数） | 未压缩消息 **>30 条**才触发；最近 **8 条**永远原样保留；单次最多压 **40 条**；`untilId` 严格递增——不会出现中间一段永远进不了摘要 |
| 实现 | `:app/chat/SessionSummarizer`（实现 `SummaryEngine` + `SummaryProvider`） | 「旧摘要 + 新一段」重新压成 ≤240 字；复用流式客户端收整段文本；**任何失败都静默降级**，下次再试 |
| 注入 | 单聊 `ChatViewModel` / 群聊 `GroupChatEngine` | 单聊与群聊 host 都走 `PromptDynamic.plotSummary`（PromptBuilder 本来就有的「前情摘要」段）；群聊工具人拼进 system |
| 用户可见 | 设置 → 备份与恢复 | 「已生成 N 段摘要，存在应用数据库 lanweixi.db 的 session_summaries 表」+「导出会话摘要」（Markdown：标题/覆盖条数/会话 id/正文） |
| 备份 | `BackupManager` FORMAT_VERSION 2 | 摘要进 JSON；导入兼容 v1（没有该段就按空处理） |
| 单测 | +25 | 规则 6 / 导出格式 3 / 迁移 1 / 摘要器 6 / 聊天页注入 4 / 群聊注入 3 / 备份 2 |

**真机验证（2026-11-24）**：灌 40 条历史，第 5 条埋暗号「蓝色水瓶」；发「hi」触发压缩 →
库里 `session_summaries` 出现一条（覆盖 40 条、untilId=56、含暗号）；再发 `WHATISOURCODEWORD`（全大写，否则又被输入法转拼音），
她答「**蓝色水瓶。** 不过你之前说过这个不要外传，问得这么大声，好像还在测试我」——暗号早不在最近 8 条里，是摘要带出来的。
设置页数字与导出 Markdown 都验过（文件落在 `下载/豆包/lanweixi-summaries-*.md`，内容含摘要正文）。
测试数据已清理（messages 66→20，摘要 0）。

**注意**：摘要请求走当前配置（DeepSeek/OpenAI/Anthropic 都行），每次压缩多花一次小请求的 token；
它只在跨过阈值时跑一次，不是每轮。

---

## 5n. P211：数据安全与使用体验补齐

| 项 | 落点 | 关键点 |
|---|---|---|
| 聊天记录进备份 | `BackupManager` FORMAT_VERSION 3 + `messages` 段 | messages 全量（含 sessionId / speaker / quote*）；导入整体替换；v1/v2 旧备份缺段按空处理 |
| 历史加载更多 | `ChatViewModel.loadMoreHistory` + `ChatScreen` 顶部按钮 | 默认 50 条，点一次翻倍（上限 500）；**滚底条件从「条数变化」改成「最后一条消息 id 变化」**，否则点加载更多会被甩回底部 |
| 服务商选择器 | `SettingsScreen` 账号区 + `SettingsViewModel.onProviderSelect` | 预设 chips 自动填 BaseURL / 模型 + 显示 keyHint；只填表单不保存，和页面其他字段节奏一致 |
| token 用量 | `events(type="usage")` + `UsageDigest` + `SessionUsageRecorder` + 设置页 | 请求加 `stream_options.include_usage`（OpenAI 兼容流默认不回 usage），`SseParser` 解析用量帧 → 合成 Done 带上；设置页显示今天/本周 |
| 单测 | +21 | 用量汇总 5 / 用量记录 4 / 历史翻页 6 / 备份消息 3 / SSE 用量帧 2 / 客户端 2 |

**真机验证（2026-11-24）**：
- 灌 60 条历史 → 列表顶部出现「加载更早的消息」→ 点后能翻到第 9 条历史（之前最早只到第 31 条），且列表位置没跳；
- 设置页点 Claude → 模型/地址自动填 `claude-3-5-sonnet-latest` / `https://api.anthropic.com`，下方提示「当前走 Anthropic 协议」；
- 发一条消息 → 库里 `events` 出现 `usage '2266,36'`，设置页显示「今天 2.3k tokens · 本周 2.3k tokens」；
- 「导出备份」JSON v3：`messages = 100`、`sessionSummaries = 1`，字段齐全。
测试数据已清理（messages 84→19、摘要 0、usage 事件 0）。

---

## 5o. P212：发布准备

| 项 | 落点 | 关键点 |
|---|---|---|
| 正式签名 | `app/build.gradle.kts` + 根目录 `keystore.properties` + `keystore/lanweixi-release.jks` | 密码只在 properties 里（已 gitignore）；**没有 properties 时自动退回 debug 签名**，别人 clone 也能跑。密钥：alias `lanweixi`、有效期 10000 天、SHA-256 `bd81d6ca…` |
| 版本定稿 | `versionCode = 1` / `versionName = "2.0.0"` | 去掉了 `-dev`；以后每次发版 versionCode +1 |
| 崩溃兜底 | `:app/crash/CrashLogger` + `Application.onCreate` | 未捕获异常落 `files/crash/crash-*.txt`（最多 5 份），再交给系统默认 handler；**不上传**。设置 → 诊断 → 导出崩溃日志（SAF） |
| 隐私说明 | `PRIVACY.md` + 设置 → 关于 → 隐私说明弹窗 | 应用内是五条摘要；仓库里是完整版。权限表补了 WorkManager 合并的 `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` |
| 许可证 | `LICENSE`（MIT） | 可改 |
| README 重写 | `README.md`（面向使用者）+ 开发记录移到 `docs/DEVLOG.md` | 截图位置留了占位；构建/签名步骤写进去了 |
| 构建验证 | `assembleRelease` | 13.7 MB；`apksigner verify` 证书 `CN=LanWeixi`；`aapt dump badging` 确认 `versionName=2.0.0`、minSdk 26、targetSdk 35 |

**注意**：release 包签名和现在装机用的 debug 包不同，直接 `adb install -r` 会报签名冲突。
要真机试 release 包得先卸载（会丢数据）——测试前先导出备份，或用 `versionName` 区分装的设备。

---

## 5p. P213：换主题插画

两张背景（日光金 / 星夜蓝紫）替换为新的慕夏风插画（人鱼 + 百合 + 蓝鸟，昼/夜双版本）：

- 直接覆盖 `core/design/src/main/res/drawable-nodpi/bg_gold.jpg` 与 `bg_starry.jpg`（各约 825 KB / 989 KB）
- `MuchaBackground` 不用改：按主题选资源 + `ContentScale.Crop`
- 真机两档主题都截图看过；`assembleRelease` 重新出了一版（13.7 MB）

---

## 5q. P215/P216/P217：安全审计与高/中/低危修复

两路只读审计（安全面 / 逻辑与崩溃面）+ 自查，全部结论都有代码位置。**已修的高危如下**，后续中危 / 低危在对应小节。

### 已修（数据毁灭 / 数据丢失 / 串会话）

| # | 问题 | 位置 | 修法 |
|---|---|---|---|
| 1 | **插件 `id` 可路径穿越删库**：id 正则放行 `..`，安装器 `File(pluginsRoot, id).deleteRecursively()`；`id=".."` 删 filesDir（DataStore 里的 Key 一起没），`id="../.."` 删整个应用私有目录（databases：聊天、笔记、任务） | `PluginManifestValidator` / `PluginInstaller` | id 收紧为反向域名（字母开头、单点分段、≤64 字符）；安装器加 `safeTarget()` canonicalPath 校验；远程源下载文件名也做安全化 |
| 2 | **会话串号**：落库/读历史用全局 `sessionProvider.current()`；A 页还在返回栈时进 B（待审核「来自会话」），A 的 VM 不重建，之后在 A 发消息写进 B | `ChatViewModel` / `ConversationEngine` | 本页固定 `pageSessionId`，读历史/发送前把 current 掰回自己；引擎在 `send` 开始快照会话 id，全程用它 |
| 3 | **工具轮数超限直接 return，消息与回复都不落库**（completed=true，UI 清草稿、重读历史读不到，用户以为发出去了） | `ConversationEngine` | 超限分支先 `persist()` 再返回 |
| 4 | **导入 v1/v2 旧备份必然清空聊天记录与摘要**（没有该段也先 deleteAll） | `BackupManager` | 只在备份确实带 `messages`/`sessionSummaries` 段时才替换；不带则保留现有 |
| 5 | 删会话/删群不删摘要（摘要表永久堆着已删会话内容）；删非当前会话也会把 current 切走 | `AppSessionsSource` | 删聊天/群时一起 `summaries.deleteBySession`；只有删的确实是当前会话才 switch |
| 6 | 摘要器 `runner.run` 同步抛异常时不 settle → 协程永久挂起（群聊整轮卡在「正在输入」） | `SessionSummarizer` | try/catch，同步失败也 settle(null) |
| 7 | `ChatStreamClient` 只 catch IOException，回调里抛 RuntimeException 会静默断流（上层永久挂起） | `ChatStreamClient` | 补一层 Exception 兜底 → `onError` |
| 8 | 插件源索引「先读后判」大小上限形同虚设（远程 OOM） | `RemoteSourceClient` | 改边读边计数，超限立刻断开 |

单测 +6（P215 新增）：坏 id 拒绝 2 / 旧备份不清空 2 / 工具超限落库 1 / 会话快照 1 + ChatViewModel 页面会话 1。

### P216：中危清单收口（本轮）

| # | 问题 | 位置 | 修法 |
|---|---|---|---|
| 9 | **Key 加密失败静默回退明文**：`runCatching{encrypt}.getOrDefault(trimmed)`，Keystore 不可用时 DataStore 里直接存明文，用户以为加密了 | `LocalDataStoreConfigProvider` | 加密失败抛 `ApiKeyEncryptException`（设置页原样显示原因），旧密文保持不变，绝不落明文 |
| 10 | **解密失败静默清密文**：`current()` 把解不开的 Key 当空串，设置页显示空 Key，点保存就 `remove()` 掉旧密文（换设备/清数据后必然发生） | `LocalDataStoreConfigProvider` / `ChatConfig` | 新增 `ChatConfig.apiKeyUnreadable` 区分「没配」与「解不开」；设置页提示重填；空值保存遇到解不开的密文直接拒绝（只能输入新 Key 覆盖） |
| 11 | **草稿审核多步写库无事务**：插笔记 → 标草稿已确认是两次独立写，中断会留孤儿笔记 + 草稿仍 pending，再点确认插第二条 | `DraftReviewer` / `ChatModule` | `DraftReviewer` 加 `transaction` 参数（生产传 Room `withTransaction`）；approve / approveEdited / reject / undo 全部包进事务，改内容与确认原子化 |
| 12 | **Lua 无 CPU 上限**：`while true do end`（或 `while true do pcall(...) end`）占死执行线程 | `LuaSandbox` / `LuaPluginLoader` | `LuaInstructionLimiter`（DebugLib 子类）挂到 `globals.debuglib`，按指令数（3M）+ 墙钟（8s）计费；超限抛继承 `Error` 的 `LuaBudgetExceededError` 穿透 pcall；main.lua 加载期同样受控，另加 512KB 脚本大小上限 |
| 13 | **zip 解压无上限**（zip bomb）：`zip.copyTo` 边解边写，解压后可能几个 GB | `PluginInstaller` | `ExtractionLimits`：条目 ≤200、单文件 ≤10MB、总量 ≤30MB，边读边计数超限立即中止；txt/docx 解析同步加 5MB/10MB 上限（`DocTextExtractor`） |
| 14 | **host.http SSRF 可绕过**：只查 host 字符串前缀，十进制 IP（`2130706433`）、IPv6（`[::1]` / `[::ffff:10.0.0.1]`）、302 重定向到内网都能过 | `LuaHostApis` / 新增 `SsrfGuard` | 解析成真实 `InetAddress` 再判（回环/私网/链路本地/CGNAT/组播/ULA），任一解析结果为内网就拒；连接设 `instanceFollowRedirects=false`，3xx 直接报错不跟随 |
| 15 | **远程插件源 sha256 可选、下载后免确认直接装**：索引方或中间人可省掉校验值静默换包 | `RemoteSourceClient` / `PluginSource` / `MainActivity` / `RemoteSourceDialog` | `RemotePlugin.checksumValid()`（必须 64 位十六进制）；download 前先拒、下载后强制比对；UI 对缺校验值的条目标「不能安装」并禁用按钮；点安装先弹二次确认（名称/版本/作者/来源/sha256 前缀） |
| 16 | **备份导入替换 documents 但不碰 doc_chunks**：旧分块留在表里，搜索会命中同 id 新文档 | `BackupManager` / `DocumentDao` | 导入事务里先 `deleteAllChunks()` 再 `deleteAll()`；导入空文档的备份也清 |
| 17 | **群聊 prompt 用户消息重复**：本次用户消息既进 history 又作为末尾 user 消息，模型每轮看到同一句两遍 | `GroupChatEngine` | 落库照旧，但不再追加进 context；多目标时前一个人的回复仍进后一个人的上下文 |

单测 +46（本轮新增）：Key 存储 5 / 草稿事务 5 / Lua 预算 5 / Lua 加载器 3 / SSRF 11 / zip 上限 3 / 文档解析 2 / 插件校验值 3 / 远程下载 3 / 备份分块 2 / 群聊重复 3 / 设置页错误文案 1。

**P216 验证情况**：
- 真机验过：设置页保存 Key（加密成功路径不回归、错误文案展示）；备份导入后 `doc_chunks` 清空（真库：导入前 2 条旧分块 → 导入后 0 条，documents 只有备份里的新文档）；群聊发消息正常收发（修复后落库/prompt 不回归）。
- 单测里跑的是真实实现：草稿事务用 Robolectric 真 Room（SQLite 回滚）、Lua 死循环用真 LuaJ 执行、SSRF 用真 `InetAddress` 解析、zip bomb 用真 zip 解压。
- **没做端到端真机验**：远程插件源二次确认对话框（需要本地 HTTP 源 + 手动 UI）、插件死循环工具的整轮调用、草稿审核进程中断回滚（真机无法模拟）。这三条依赖单测结论。

### P217：低危清单收口（本轮）

| # | 问题 | 位置 | 修法 |
|---|---|---|---|
| 18 | **events 只增不删**；`conversation` / `task_done` 无人读，payload 还存着用户原话 / 任务标题 | `EventDao` / `EventRepository` / `MainActivity` / `ConversationEngine` / `TasksViewModel` | 启动时清理（90 天前 + 只留最近 2000 条，`EventRetention`）；payload 改成 `chars=N`；启动清理时顺带把**存量** `conversation` / `task_done` 的 payload 一次性改写成 `chars=N`（幂等），历史原文不再留在库里 |
| 19 | **日志打印首帧 / 错误体**（可能含用户内容） | `ChatStreamClient` | 日志只记元信息（URL / 状态码 / 长度）；抽出 `ChatStreamLog` 日志出口，测试可断言「日志里没有用户消息和回复正文」 |
| 20 | **明文残留 / 无上限追加**：`avatar_debug.log` 一直追加；`restore-backup-*.json` 含聊天记录明文 | `ChatAvatar` / `RestoreBackups` / `MainActivity` | 头像日志超 256KB 清空重开；导入留底最多 3 份 + 7 天保留期（`RestoreBackups.prune`） |
| 21 | **插件 `sensitive` 自声明**：插件写 `sensitive: false` 就能绕过宿主确认弹窗 | `PluginPermissions` / `LuaPluginLoader` / `PluginToolBridge` | 申请了写库 / 联网 / 读全文 / 日志权限的插件，工具**一律强制确认**；纯只读插件保持不打扰 |

单测 +22：events 清理 6 / 启动清理策略 2 / 日志脱敏 2 / 头像日志 3 / 留底清理 4 / 插件强制确认 5（桥接 3 + 加载器 2）。

**P217 验证情况**：
- 真机验过：新 APK 启动后 `events` 里历史 `conversation` 事件全部变成 `chars=N`（原 "hi" → `chars=2`、"WHATISOURCODEWORD" → `chars=18`、"remember this: interview…" → `chars=40`，usage 不动）；新发一条消息后新事件也是 `chars=6`；发消息、收回复正常。
- 单测里跑的是真实实现：events 用真 Room（trim + 脱敏 + 幂等）、头像日志/留底清理用真文件系统、日志脱敏用 MockWebServer 真请求 + 内存日志出口断言不含用户消息与回复正文。
- **没做端到端真机验**：vivo 这台设备把 App 的 `Log.d` 全部屏蔽了（logcat 里连 `AndroidRuntime` 都没有），日志脱敏只能靠单测断言；插件强制确认需要造一个带写权限的插件再让模型触发工具，没做（示例插件只有只读权限，正好验证了「只读不打扰」不回归）。

### 未修清单（按风险排，等决定）

已清空：P215 高危 8 项、P216 中危 8 项、P217 低危 4 项全部修完。



---

## 6. 关键决策（别推翻）

| 决策 | 原因 |
|---|---|
| 兰未晞是**唯一有性格**的角色 | 其他 agent 是工具人，只有「工作说明」没有性格 |
| 笔记/任务**都归兰未晞** | 多 agent 写会重复 |
| 群聊**发言权归用户** | 不让 agent 自主判断谁接话，成本失控 |
| 一次 @ 多人**按成员列表顺序逐个回** | 顺序可预期；用户消息只落一次，免得历史里重复；失败不连坐 |
| 记忆**手动导入 md** | 不自动提取，避免噪音 |
| 记忆注入走**用户消息**，不动 system prompt | 防止人设被记忆污染 |
| 不做 `hotkey:register` | Android 无合理场景 |
| Anthropic 暂不支持 | API 格式与 OpenAI 不兼容 |
| 首启引导「有 Key 就不打扰」 | 老用户升级不该被引导页拦住；没配 Key 又不是新装的才会看一次（能跳过，无害） |
| 引导页在 NavHost 外渲染 | 走完直接切主界面，不占回退栈；设置里的重看走 `Route.ONBOARDING`，预填模式 |

---

## 7. 踩过的坑（血泪）

**① 多行结构别用正则批量删**
删 `Surface { Column { ... } }` 这类包裹时，正则数括号出错过**三次**，每次都把文件改坏。**正确做法**：改一个文件→编译→确认→再改下一个。

**② 注释是 GBK 乱码**
源码里的中文注释读出来是乱码，用中文注释做 `replace` 的锚点会失败。**只匹配代码行**。

**③ 坐标要用 uiautomator，不要按截图比例算**
截图预览会被压缩，比例换算经常错 200px。用 `uiautomator dump` 拿 `bounds`。

**④ Compose Slider 不能点击改值**
必须 `input swipe ... 800`（慢拖）。

**⑤ 备份导入会整体覆盖**
测试时用空备份清过一次数据。导入前已加自动留底（`filesDir/restore-backup-*.json`）。

**⑥ 纯净模式需要不透明底**
`Scaffold(containerColor = Transparent)` + 无背景图 = 露 Activity 窗口 = 闪屏残影。

**⑦ DataStore 委托不能写在类里**
`AgentStore` 原来把 `preferencesDataStore` 写成类内属性，每 new 一次就建一个 DataStore。
从群列表点进群聊页时两个实例同时存在，第二个直接抛
`IllegalStateException: There are multiple DataStores active for the same file`，**App 闪退**。
正确写法：委托放**文件顶层**（或伴生对象），多个实例共用一份。

**⑧ 中文输入法会污染 API Key**
百度/vivo 输入法把 `sk-test` 猜成「上课－test」，`-` 变全角。
OkHttp 报 `Unexpected char 0x4e0a at 7 in Authorization value`，用户只看到「点了没反应」。
现在工具人编辑页会检测非 ASCII 字符并**禁用保存**，群聊引擎也会把这类异常翻译成人话。
测试时别用 `adb shell input text` 往输入框打 Key，会被 IME 改写；要直接改 DataStore 或用剪贴板。

**⑨ `pm clear` 会丢 Keystore 里的 Key**
API Key 是 `enc:` 密文存在 DataStore，密钥在 Android Keystore；清数据/卸载会连密钥一起清，
恢复的密文解不开（按空 Key 处理）。真机测「全新安装」时别用 `pm clear`：
`run-as` 删 `files/datastore/lanweixi_settings.preferences_pb` + `shared_prefs/onboarding.xml` 即可，
测完拷回去真 Key 还在。

**⑩ `adb shell input text` 在 vivo 上基本不可用**
- 中文直接抛 `NullPointerException: Attempt to get length of null array`（`InputShellCommand.sendText`）；
- **英文也会被输入法转拼音**：`yesnewtimetomorrow1600` 被上屏成「也是呢问题么偷摸揉揉我600」，`3pm` 变「3屏幕」；
- `%s` 空格、`input keyevent 62`（空格键）都会被吞；切到另一个输入法（chaoxing 拼音）一样。
- 可行替代：① 让用户手动打；② **长按旧消息 → 重发**（能复用已有文本，模型照样调工具）；
  ③ 造数据直接改库（见 ⑪）。（写入纪律里加 `remember this` / `note this` 英文触发词，也是为这条路留的余地。）

**⑪ 造草稿/改数据别在 App 运行时动库**
`run-as` 拉出来的主文件可能不含 WAL 里的最新写入。正确流程：
`am force-stop` → 拉 `lanweixi.db` + `-wal`（放同一目录）→ 本地 sqlite3 改 → `PRAGMA wal_checkpoint(TRUNCATE)` →
删掉本地 `-wal/-shm` → `adb push` + `run-as cp databases/lanweixi.db` → `run-as rm -f databases/lanweixi.db-wal databases/lanweixi.db-shm` → 启动。
P206 真机验证时就用这个办法造了 1 条 task 草稿和 2 条（note+task）草稿来验确认/全部确认。

**⑫ 「顶栏被内容盖住 / 按钮点不到」= `when`/内容块写到了 `Column` 外面**
P199 在 `NoteEditScreen` 修过一次，P208 又在 `TaskEditScreen` 碰到一模一样的：
`Column { MuchaTopBar(...) }` 后面多了一个 `}`，`when` 整个跑到 Column 外面，
于是顶栏和表单在同一位置重叠、保存按钮被压住。**新写编辑页时对一下括号**，
或者跑起来看顶栏在不在——真机上「新建任务没有标题栏」就是这个。

**⑬ 拉/推设备数据库别用 PowerShell 的 `>`**
P216 真机验证踩到：`adb exec-out ... > lw.db` 会按文本编码写文件，sqlite3 直接报
`file is not a database`。拉库必须走 cmd 重定向：
`cmd /c "adb exec-out run-as com.lanweixi.client cat databases/lanweixi.db > local.db"`。
推回去时 `adb shell "cat > db" < local.db` 不可靠（得到 0 字节），用 base64 分块
（每块 ≤12KB，Windows 命令行上限 32767 字符）：
`adb shell "run-as com.lanweixi.client sh -c 'echo <b64> | base64 -d >> databases/lanweixi.db'"`
（第一块 `>`）。推完先把库拉回来 `SELECT COUNT(*)` 核对，再启动 App。

---

## 8. 下一步

功能补齐到 P213、发布准备到 P212、安全审计到 P217：正式签名、隐私说明、崩溃兜底、README 重写都有了；
P215–P217 把审计里的高危 + 中危 + 低危项（20 项）全部修完，未修清单已清空。

1. **重新出 release 包**：~~已完成~~ P217 后于 22:17 重出（13.7 MB、`CN=LanWeiXi`、versionCode 1 / 2.0.0、zipalign 通过、SHA-256 `E24062A0…FEA5D`），dex 已确认含 EventRetention / ChatStreamLog / RestoreBackups / SsrfGuard / LuaInstructionLimiter。下次发版记得 versionCode +1。
2. **补截图**：~~已完成~~ README 已嵌入 5 张真机截图（`docs/screenshots/*.jpg`，会话列表 / 群聊 / 待审核 / 复盘 / 设置，540×1200 也符合 Play 截图要求）。Play 还需要数据安全表单、内容分级、隐私政策 URL。
3. **GitHub 开源**：~~已完成~~ <https://github.com/W3967/ranmiki>（public，main 分支，首次提交 `e91f07c`，已配 topics：android / kotlin / jetpack-compose / ai / llm / privacy / offline-first）。
4. **Play 商店**：数据安全表单（本应用不收集数据）、内容分级、隐私政策 URL（用 PRIVACY.md 的 raw 链接）。
5. 有 Anthropic Key 时补端到端：切 Claude → 普通流式 → 「记一下」看 tool_use 回填。
6. 其他待办见 ROADMAP（插件会话授权 / 群聊接工具）。

---

## 9. 用户偏好

- **中文思考与回复**，结论先行，简洁
- 讨厌空话，喜欢**具体行为问题的修复**（"点了没反应"比"体验不好"有用）
- 会直接批评（"你眼瞎吗"）——**被批评时先复查事实，别辩解**
- 认可"诚实说明没验证到的部分"，反感假装完成
- 说"按你的方法来"就是**让你决策**，不要再反问
