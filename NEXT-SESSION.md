# 下一会话交接

> 写于 2026-11-24 22:20（P217 安全低危项收口）。
> 给**下一个会话**用：复制第 1 节的开场提示词，然后按第 2 节自检。

---

## 0. 30 秒版

- 项目：**兰未晞 Android 原生客户端**（Kotlin + Compose + Hilt + Room），应用名 **Ranmiki**
- 路径：`D:\work\lanweixi-native`（**必须是 ASCII 路径**，别放中文目录）
- 现状：**72 suite / 584 tests / 0 failures / 1 skipped**，数据库 **v10**，版本 **2.0.0**，APK 18.0 MB（debug）/ 13.7 MB（release，已签名，P217 后重出）
- 已完成到 **P217**：工作台五件套、AI 对话（引用/群聊/工具/上下文压缩）、插件体系、视觉体系、会话列表、提醒、搜索、引导、AI 主导的笔记任务（P206–P208）、Claude（P209）、压缩（P210）、备份翻页用量（P211）、发布准备（P212）、换插画（P213）、**安全审计 20 项修复（P215 高危 8 + P216 中危 8 + P217 低危 4）**
- **下一步：发布收尾**——重新出 release 包（当前 release 包还是 P213 的，不含安全修复）、README 截图、GitHub 首次提交、Play 材料
- 详细背景：`HANDOFF.md`（第 5q 节是三轮审计结论与清单）、`ROADMAP.md`、`docs/DEVLOG.md` 的 `| Pnnn |` 表

---

## 1. 开场提示词（直接粘贴给新会话）

```
读 D:\work\lanweixi-native\HANDOFF.md 第 5q 节、NEXT-SESSION.md，然后做发布收尾。

项目：兰未晞（应用名 Ranmiki）Android 原生客户端，根目录 D:\work\lanweixi-native
（必须是 ASCII 路径）。当前工作区可能不是这个项目目录，所有命令都要显式指定路径。

安全审计 20 项（P215 高危 8 / P216 中危 8 / P217 低危 4）已全部修完，测试 584 全绿。
这轮做发布收尾：
1. 重新出 release APK（当前 release 包是 P213 的，不含安全修复），签名/版本/aapt 校验
2. 补 README 真机截图（会话列表 / 聊天 / 待审核 / 复盘 / 设置）
3. GitHub 首次提交（git init；确认 .gitignore 没有漏 release keystore / 本地配置）
4. Play 上架材料：数据安全表单、内容分级、隐私政策 URL
5. 如果有 Anthropic Key，补端到端：切 Claude → 普通流式 → 「记一下」看 tool_use 回填

环境：
- JAVA_HOME=D:\work\tools\jdk\jdk-17.0.21+9
- adb=D:\Android\platform-tools\adb.exe（vivo V2171A，经常掉线：kill-server + start-server 或重插）
- 编译：.\gradlew.bat :app:assembleDebug --console=plain
- 装机：adb install -r app\build\outputs\apk\debug\app-debug.apk
- 全量测试：命令见 NEXT-SESSION 第 2 节（当前基线 584 tests 全绿）

用户偏好：中文、结论先行、简洁；讨厌空话，只关心具体行为问题；
被批评时先复查事实别辩解；说「按你的方法来」就是让你决策，别再反问。
```

---

## 2. 开工前自检（4 条命令 + 读一遍坑）

按顺序跑，任何一条不对先修环境再动代码：

```powershell
# 1. 设备在不在
& 'D:\Android\platform-tools\adb.exe' devices

# 2. 项目能编译
$env:JAVA_HOME = 'D:\work\tools\jdk\jdk-17.0.21+9'
Set-Location 'D:\work\lanweixi-native'
.\gradlew.bat :app:assembleDebug --console=plain

# 3. 测试基线（应该 584 全绿；数字变了说明上一轮有人改过）
# 注意：domain:* 是纯 JVM 模块，任务是 test 不是 testDebugUnitTest
.\gradlew.bat :core:network:testDebugUnitTest :core:data:testDebugUnitTest :domain:prompt:test :domain:plugin:test :domain:docs:test :domain:tools:test :feature:chat:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:notes:testDebugUnitTest :feature:prompts:testDebugUnitTest :feature:tasks:testDebugUnitTest :feature:docs:testDebugUnitTest :feature:review:testDebugUnitTest :feature:plugins:testDebugUnitTest :feature:search:testDebugUnitTest :feature:onboarding:testDebugUnitTest :feature:inbox:testDebugUnitTest :app:testDebugUnitTest --console=plain

# 4. App 还在不在设备上
& 'D:\Android\platform-tools\adb.exe' shell pm list packages | Select-String lanweixi
```

第 5 条：**先读 `HANDOFF.md` 第 7 节「踩过的坑」**，再写代码。坑列表也在下面第 5 节。

---

## 3. 本轮之后的状态（2026-11-24 22:20）

| 项 | 值 |
|---|---|
| 测试 | 72 suite / 584 tests / 0 failures / 1 skipped |
| APK | debug 18.0 MB（已装机）；release 13.7 MB（正式签名 `CN=LanWeiXi`，2.0.0 / versionCode 1，P217 后于 22:17 重出，SHA-256 `E24062A0…FEA5D`） |
| 应用名/图标 | **Ranmiki**；图标是白发紫瞳女孩那张（5 密度 PNG，方形 + 圆形，adaptive 已删） |
| 主题 | 两档插画换成了新图（人鱼 + 百合 + 蓝鸟，昼/夜）；纯净模式纯色 |
| 引导 | 首启判定 + 两屏引导 + 连通性测试 + 重看入口；预设含 Claude |
| 群聊 | 一次 @ 多人 / @全体；群聊**不带工具**；群聊上下文也会被压缩；**P216 起 prompt 里用户消息只出现一次** |
| 引用 | 单聊 + 群聊长按引用、引用条、气泡快照、点击定位 |
| 待审核 | P206/P207 真机验过（写入 / 确认 / 驳回 / 修改 / 撤销 / 来源跳转 / 聊天页入口） |
| Anthropic | P209：协议按 BaseURL 判定；假 Key 验到 401 映射；**流式+工具端到端未验**（无 Key） |
| 上下文压缩 | P210：v10 `session_summaries`，30 条触发、保留最近 8 条；设置页可看数量 + 导出 Markdown |
| 备份/翻页/用量 | P211：备份 v3 含聊天记录；历史翻页到 500；服务商 chips；今天/本周 token |
| 发布 | P212：正式签名、版本 2.0.0、崩溃兜底（本地 5 份 + 导出）、`PRIVACY.md` + 应用内隐私弹窗、MIT LICENSE、README 重写 |
| 安全审计 | **P215 + P216 + P217**：20 项已修（高危 8 / 中危 8 / 低危 4），HANDOFF 5q 未修清单已清空 |
| 数据 | 数据库 v10；审计与 P216 验证期间发了几条测试消息（hi / oi / test / HELLO），没清；主配置仍是 DeepSeek + 真 Key |
| 文档 | README / HANDOFF（5q 是两轮审计结论）/ ROADMAP / NEXT-SESSION / docs/DEVLOG |

---

## 4. 建议的下一步

| 顺序 | 任务 | 量级 | 说明 |
|---|---|---|---|
| 1 | Play 上架材料 | 中 | **截图已就绪**（`docs/screenshots/*.jpg`，5 张 540×1200，符合 Play 要求）；还差数据安全表单、内容分级、隐私政策 URL |
| 2 | GitHub 仓库维护 | 小 | ~~首次提交~~ **已完成**：<https://github.com/W3967/ranmiki>（main，`e91f07c`，已设 topics）；后续 push 需代理，见坑 20 |
| 3 | Anthropic 端到端补验 | 小 | 有 Key 后：切 Claude → 普通流式 → 「记一下」看 tool_use 回填 |
| 4 | 真实使用校准 | — | 长会话摘要阈值 / 历史翻页 / 用量展示 |

---

## 5. 绝对别踩的坑（完整版在 HANDOFF 第 7 节）

1. **DataStore 委托写在类里会崩** —— `AgentStore` 的 `preferencesDataStore` 必须在文件顶层，否则两个实例同时读同一文件直接 `IllegalStateException`。
2. **中文输入法污染 API Key** —— 百度/vivo 输入法会把 `sk-test` 猜成「上课－test」。编辑页已加非 ASCII 拦截；测试时别用 `adb shell input text` 打 Key，要改就改 DataStore。
3. **`MuchaCard` 在 Column 里会撑满屏** —— 内部有 `fillMaxSize` 角饰 Box；需要 wrap 高度时用 `Surface` + `Column`。
4. **跨页面 `getBackStackEntry(父路由)` 会崩** —— 用 `parentEntryOrNull()` 兜底。
5. **会话归属别读全局 `current()`** —— P215 踩过：A 页还在返回栈时进 B，返回 A 后发消息会写进 B。本页固定 `pageSessionId`，发送前把 current 掰回自己（ChatViewModel 已修）。
6. **vivo 会把 WorkManager 压到一小时后** —— 验证任务提醒用设置页「诊断 → 立即检查任务提醒」。
7. **改结构别用正则批量删** —— 改一个文件 → 编译 → 确认 → 再改下一个。
8. **测「全新安装」别用 `pm clear`** —— 会连 Keystore 密钥一起清，恢复的 `enc:` Key 解不开。用 `run-as` 删 `files/datastore/lanweixi_settings.preferences_pb` + `shared_prefs/onboarding.xml`，测完拷回去。
9. **改完模块后 `assembleDebug` 可能 UP-TO-DATE** —— 装之前看 APK 修改时间，不对就 `--rerun-tasks`。
10. **`adb shell input text` 在 vivo 上基本不可用** —— 中文直接 NPE；英文会被输入法转拼音（`yesnewtimetomorrow1600` →「也是呢问题么偷摸揉揉我600」）；`%s` 和 `keyevent 62` 的空格都会被吞。替代：全大写英文（`WHATISOURCODEWORD` 能上屏）、让用户手打、或直接改库造数据（坑 11）。
11. **改库造数据要先停 App** —— `am force-stop` → 拉 `lanweixi.db` + `-wal`（**用 cmd 重定向，别用 PowerShell 的 `>`**，见坑 17）→ 本地 sqlite3 改 → `PRAGMA wal_checkpoint(TRUNCATE)` → 删本地 `-wal/-shm` → 推回设备（base64 分块，见坑 17）→ `run-as rm -f databases/lanweixi.db-wal databases/lanweixi.db-shm` → 启动。
12. **Compose 编辑页「顶栏被内容盖住、按钮点不到」= `when`/内容块写到了 `Column` 外面** —— P199 在笔记页修过，P208 在任务页又碰到同款。
13. **灌长对话测试数据别用未来时间戳** —— 列表和上下文都按 `createdAt` 排序，用未来时间戳会让真实消息「看不见」。
14. **release 包和调试包签名不同，不能直接覆盖安装** —— 会报签名冲突；要装 release 包得先卸载（丢数据）。
15. **插件 id 会变成目录名** —— P215 修过：`id=".."` 曾能 `deleteRecursively()` 掉 filesDir，`"../.."` 能删整个应用私有目录。改插件相关代码时别再拼路径，用 `PluginInstaller.safeTarget()`。
16. **测试文件编译错误要先看 `:模块:testDebugUnitTest` 的完整输出** —— PowerShell 过滤 `BUILD ` 会误匹配 `preBuild`，用 `-cmatch` 区分大小写。
17. **拉/推设备数据库别用 PowerShell 的 `>`** —— P216 真机验证踩过：`adb exec-out ... > lw.db` 会用文本编码写文件，sqlite3 报 `file is not a database`。拉：`cmd /c "adb exec-out run-as com.lanweixi.client cat databases/lanweixi.db > local.db"`。推：`adb shell` 的 stdin 不透传（`cat > db < local.db` 得到 0 字节），用 base64 分块（每块 ≤12KB，Windows 命令行上限 32767 字符）：`adb shell "run-as com.lanweixi.client sh -c 'echo <b64> | base64 -d >> databases/lanweixi.db'"`，第一块用 `>`。
18. **改完数据后核对再清理** —— 推回设备后先 `exec-out cat` 回来 `SELECT COUNT(*)` 核对（messages/documents/doc_chunks），确认 App 启动会话列表正常，再删本地临时库。
19. **vivo 这台设备屏蔽 App 自己的日志** —— P217 验证时发现 `adb logcat` 里连 `AI-Chat`、`PluginInstaller`、`AndroidRuntime` 都没有（App 在跑、消息能收发）。别把「logcat 看不到」当成「没打日志」；验证日志行为用单测里的内存日志出口（`ChatStreamLog.sink`）。
20. **git 推送必须显式走代理** —— 这台机器的系统代理是 `127.0.0.1:7890`（IE 设置里），但 git 不读系统代理，直接 push 会 `Recv failure: Connection was reset`。用 `git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push ...`。git 便携版在 `D:\work\tools\git\cmd\git.exe`（系统 PATH 里没有 git）。认证用临时 `-c http.extraheader="AUTHORIZATION: basic <base64(user:token)>"`，不要 token 写进 remote URL。

---

## 6. 交接方法（怎么换人/换会话）

如果换一个完全新的会话来接手：

1. 把第 1 节的开场提示词粘过去（它指向 HANDOFF 第 5q 节的漏洞清单）
2. 让新会话先跑第 2 节自检，确认环境没坏
3. 由它先给出「这一轮修哪几条 + 怎么验证」，确认后再动手
4. 每修完一条就更新 `HANDOFF.md` 5q 的清单（未修 → 已修）和 `docs/DEVLOG.md` 的 `| Pnnn |` 表

如果只是**我自己下次接着聊**：直接说「读 HANDOFF 5q，接着修漏洞」即可，不用整段提示词。
