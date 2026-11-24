package com.lanweixi.feature.chat

import androidx.lifecycle.ViewModel
import com.lanweixi.domain.prompt.AgentProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P197：群聊页 ViewModel。
 *
 * 只依赖 [GroupChatPort]（读写该群消息）与 [GroupChatUseCase]（发一轮）——
 * 编译面上拿不到引擎实现，JVM 单测可整个替换。
 *
 * 消息落库顺序：发送前乐观插入一条用户消息（立刻可见），引擎自己也会落库；
 * 结束后重新读一遍，以库里为准——避免重复或漏条。
 */
class GroupChatViewModel(
    private val groupId: String,
    private val groupTitle: String,
    private val hostName: String,
    private val hostAvatarUri: String?,
    /** 每次发送前现取成员：改名/新增立即生效。 */
    private val membersProvider: () -> List<AgentProfile>,
    private val port: GroupChatPort,
    private val useCase: GroupChatUseCase,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupChatUiState(title = groupTitle))
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        refreshMembers()
        loadHistory()
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    /** 群成员 = 兰未晞（固定）+ 当前工具人。 */
    fun refreshMembers() {
        val members = buildList {
            add(
                GroupMember(
                    id = GroupSpeaker.HOST,
                    name = hostName,
                    avatarUri = hostAvatarUri,
                    color = HOST_COLOR,
                    isHost = true,
                    configured = true,
                ),
            )
            membersProvider().forEach { agent ->
                add(
                    GroupMember(
                        id = agent.id,
                        name = agent.name,
                        avatarUri = agent.avatarUri,
                        color = agent.color,
                        isHost = false,
                        configured = agent.configured,
                    ),
                )
            }
        }
        _state.update { current ->
            // @ 的成员被删/被移出群了就从选择里去掉（否则发送时会命中「不在群里」）
            val validIds = current.targetIds.filter { id -> members.any { it.id == id } }.toSet()
            current.copy(members = members, targetIds = validIds)
        }
    }

    fun loadHistory() {
        scope.launch {
            val result = runCatching { port.history(groupId, HISTORY_LIMIT) }
            if (result.isFailure) {
                _state.update { it.copy(banner = ChatBanner.Notice("历史加载失败，退出重进试试")) }
                return@launch
            }
            _state.update { it.copy(messages = result.getOrDefault(emptyList())) }
        }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
    }

    /**
     * P203：@ 从单选改成多选。
     *
     * 点已选的人 = 取消；点没配 Key 的人 = 明确提示（不让他被选上）。
     */
    fun toggleTarget(id: String) {
        val member = _state.value.members.firstOrNull { it.id == id } ?: return
        val selected = id in _state.value.targetIds
        if (!selected && !member.configured) {
            _state.update {
                it.copy(banner = ChatBanner.Notice("${member.name} 还没配 API Key：去「设置 → 工具人」里填一个再 @ 他"))
            }
            return
        }
        _state.update {
            it.copy(
                targetIds = if (selected) it.targetIds - id else it.targetIds + id,
                banner = null,
            )
        }
    }

    /** P203：@ 全体成员（只选配了 Key 的；没配的会被跳过并说明）。 */
    fun selectAllTargets() {
        val members = _state.value.members
        val ids = members.filter { it.configured }.map { it.id }.toSet()
        val skipped = members.filterNot { it.configured }
        _state.update {
            it.copy(
                targetIds = ids,
                banner = if (skipped.isEmpty()) {
                    null
                } else {
                    ChatBanner.Notice(
                        skipped.joinToString("、") { m -> m.name } + " 还没配 API Key，没选上",
                    )
                },
            )
        }
    }

    /**
     * P205：引用某条消息。
     *
     * 引用谁就回谁：引用工具人的消息时自动把他加进 @；
     * 引用兰未晞或自己的消息时回到默认（兰未晞接）。
     */
    fun quoteMessage(message: GroupMessage) {
        val speaker = when {
            GroupSpeaker.isUser(message.speaker) -> "我"
            GroupSpeaker.isHost(message.speaker) -> hostName
            else -> _state.value.members.firstOrNull { it.id == message.speaker }?.name ?: "某人"
        }
        val preview = message.text.replace('\n', ' ').replace('\r', ' ').trim().take(60)
        if (preview.isBlank()) return
        val targetIds = when {
            GroupSpeaker.isUser(message.speaker) -> emptySet()
            GroupSpeaker.isHost(message.speaker) -> emptySet()
            else -> setOf(message.speaker)
        }
        _state.update {
            it.copy(
                quote = ChatQuote(message.id, speaker, preview),
                targetIds = targetIds,
                banner = null,
            )
        }
    }

    fun clearQuote() {
        _state.update { it.copy(quote = null) }
    }

    /** P205：点气泡里的引用卡片 → 滚到原消息并高亮。 */
    fun locateQuote(messageId: Long) {
        if (messageId <= 0) return
        _state.update { it.copy(highlightId = messageId) }
    }

    fun clearHighlight() {
        _state.update { it.copy(highlightId = 0) }
    }

    /** P203：清空 @，回到「不 @ 就是兰未晞接」。 */
    fun clearTargets() {
        _state.update { it.copy(targetIds = emptySet(), banner = null) }
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.sending || snapshot.draft.isBlank()) return
        val text = snapshot.draft
        // P205：引用只影响这一条的上下文
        val quote = snapshot.quote
        val quoteForModel = quote?.let { "${it.speaker}：${it.preview}" }.orEmpty()

        // 空 = 兰未晞接；非空 = 按成员列表顺序 @ 那些人
        val selected = if (snapshot.targetIds.isEmpty()) {
            snapshot.members.filter { it.isHost }
        } else {
            snapshot.members.filter { it.id in snapshot.targetIds }
        }
        val ready = selected.filter { it.configured }
        if (ready.isEmpty()) {
            val hint = if (selected.isEmpty()) {
                "群里没有可回复的成员"
            } else {
                selected.joinToString("、") { it.name } +
                    " 还没配 API Key：去「设置 → 工具人」里填一个再 @ 他"
            }
            _state.update { it.copy(banner = ChatBanner.Notice(hint)) }
            return
        }
        val skipped = selected.filterNot { it.configured }

        _state.update {
            it.copy(
                sending = true,
                draft = "",
                banner = null,
                quote = null,
                partialSpeakerId = ready.first().id,
                partialText = "",
                messages = it.messages + GroupMessage(
                    speaker = GroupSpeaker.USER,
                    text = text,
                    createdAt = System.currentTimeMillis(),
                    quoteId = quote?.messageId ?: 0,
                    quotePreview = quoteForModel,
                ),
            )
        }

        val job = scope.launch {
            val results = try {
                useCase.sendToMany(
                    groupId = groupId,
                    agents = membersProvider(),
                    userText = text,
                    targetAgentIds = ready.map { it.id },
                    onDelta = { speaker, partial ->
                        _state.update { it.copy(partialSpeakerId = speaker, partialText = partial) }
                    },
                    quoteId = quote?.messageId ?: 0,
                    quotePreview = quoteForModel,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AI-Chat", "group send failed: ${e.javaClass.name}: ${e.message}", e)
                null
            }

            if (results == null) {
                _state.update {
                    it.copy(
                        sending = false,
                        partialSpeakerId = null,
                        partialText = null,
                        // 没发出去就把引用放回来
                        quote = it.quote ?: quote,
                        banner = ChatBanner.Error("这次没发出去（异常），再试一次"),
                    )
                }
                loadHistory()
                return@launch
            }

            _state.update { it.copy(sending = false, partialSpeakerId = null, partialText = null) }
            val errors = buildList {
                results.filter { !it.completed }.mapNotNull { it.error }.forEach { add(it) }
                skipped.forEach { add("${it.name} 没配 API Key，这条没发给他") }
            }.distinct()
            _state.update {
                it.copy(
                    banner = if (errors.isEmpty()) null else ChatBanner.Error(errors.joinToString("；")),
                    quote = if (errors.isEmpty()) it.quote else (it.quote ?: quote),
                )
            }
            loadHistory()
        }
        sendJob = job
        job.invokeOnCompletion { if (sendJob === job) sendJob = null }
    }

    /** 取消本轮：已落库的用户消息保留，回复不写入。 */
    fun cancelSend() {
        val job = sendJob ?: return
        job.cancel()
        _state.update {
            it.copy(
                sending = false,
                partialSpeakerId = null,
                partialText = null,
                banner = ChatBanner.Notice("已取消，这轮回复没保存"),
            )
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 300

        /** 兰未晞的气泡色（固定，跟工具人的配色区分）。 */
        const val HOST_COLOR = "#E07A9B"
    }
}
