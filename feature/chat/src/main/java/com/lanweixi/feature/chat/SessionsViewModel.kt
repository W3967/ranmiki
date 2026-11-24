package com.lanweixi.feature.chat

import androidx.lifecycle.ViewModel
import com.lanweixi.domain.prompt.GroupSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P198：会话列表主页的 ViewModel。
 *
 * 把单聊（[SessionSummary]）和群聊（[GroupSession]）拼成一个列表：
 *  - 单聊的 `sessionId` 和群 id 存在同一张消息表里，所以**必须先按群 id 过滤**，
 *    否则同一场群聊会在列表里出现两次（一次当单聊、一次当群）；
 *  - 排序按最后活动时间，没有消息的排最后。
 */
class SessionsViewModel(
    private val source: SessionsSource,
    private val characterName: String,
    private val characterAvatarUri: String?,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(loading = true, banner = null) }

            val groups = runCatching { source.listGroups() }.getOrDefault(emptyList())
            val groupIds = groups.map { it.id }.toSet()
            val chats = runCatching { source.listChats() }.getOrDefault(emptyList())
                .filterNot { it.id in groupIds }

            val chatItems = chats.map { chat ->
                ConversationItem(
                    id = chat.id,
                    isGroup = false,
                    title = characterName,
                    subtitle = chat.preview.ifBlank { "还没有消息" },
                    lastAt = chat.lastAt,
                    avatarUri = characterAvatarUri,
                )
            }

            val groupItems = groups.map { group ->
                val last = runCatching { source.lastMessage(group.id) }.getOrNull()
                ConversationItem(
                    id = group.id,
                    isGroup = true,
                    title = group.title,
                    subtitle = last?.text?.takeIf { it.isNotBlank() } ?: memberLine(group),
                    lastAt = last?.at ?: 0L,
                    avatarUri = null,
                )
            }

            val items = (chatItems + groupItems).sortedByDescending { it.lastAt }
            _state.update { it.copy(items = items, loading = false) }
        }
    }

    /** 新建单聊并切过去；[onCreated] 拿到 id 后由 UI 导航。 */
    fun createChat(onCreated: (String) -> Unit) {
        scope.launch {
            val id = runCatching { source.createChat() }.getOrNull()
            if (id.isNullOrBlank()) {
                _state.update { it.copy(banner = "新建会话失败，再试一次") }
            } else {
                onCreated(id)
            }
        }
    }

    fun delete(item: ConversationItem) {
        scope.launch {
            val result = runCatching {
                if (item.isGroup) source.deleteGroup(item.id) else source.deleteChat(item.id)
            }
            if (result.isFailure) {
                _state.update { it.copy(banner = "删除失败，再试一次") }
                return@launch
            }
            refresh()
        }
    }

    private fun memberLine(group: GroupSession): String =
        if (group.memberIds.isEmpty()) "只有兰未晞" else "兰未晞 + ${group.memberIds.size} 个工具人"
}
