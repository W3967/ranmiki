package com.lanweixi.feature.chat

import com.lanweixi.domain.prompt.GroupSession
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P198：会话列表（单聊 + 群聊混排、过滤、排序、删除）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {

    private class FakeSource : SessionsSource {
        val chats = mutableListOf<SessionSummary>()
        val groups = mutableListOf<GroupSession>()
        val lastMessages = mutableMapOf<String, LastMessage>()
        val deleted = mutableListOf<String>()
        var createdId: String? = "s_new"
        var failCreate = false
        var failDelete = false

        override suspend fun listChats(): List<SessionSummary> = chats.toList()
        override suspend fun listGroups(): List<GroupSession> = groups.toList()
        override suspend fun lastMessage(sessionId: String): LastMessage? = lastMessages[sessionId]
        override suspend fun createChat(): String {
            if (failCreate) error("boom")
            return createdId!!
        }

        override suspend fun deleteChat(sessionId: String) {
            if (failDelete) error("boom")
            deleted += "chat:$sessionId"
        }

        override suspend fun deleteGroup(groupId: String) {
            deleted += "group:$groupId"
        }
    }

    private fun sessionsVm(source: FakeSource, scope: CoroutineScope) = SessionsViewModel(
        source = source,
        characterName = "兰未晞",
        characterAvatarUri = null,
        scope = scope,
    )

    @Test
    fun `group messages are not listed again as single chats`() = runTest {
        val source = FakeSource().apply {
            groups += GroupSession(id = "group_1", title = "讨论组", memberIds = listOf("a1"))
            // 群消息也写在同一张 messages 表里，sessionDigest 会把它当成一个单聊会话
            chats += SessionSummary(id = "group_1", messageCount = 4, preview = "hi", lastAt = 500)
            chats += SessionSummary(id = "default", messageCount = 2, preview = "你好", lastAt = 100)
        }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        val items = vm.state.value.items
        assertEquals(2, items.size)
        assertEquals(1, items.count { it.isGroup })
        assertEquals(1, items.count { !it.isGroup })
        assertEquals("group_1", items.first { it.isGroup }.id)
    }

    @Test
    fun `newest conversation comes first`() = runTest {
        val source = FakeSource().apply {
            groups += GroupSession(id = "group_1", title = "讨论组")
            chats += SessionSummary(id = "default", messageCount = 2, preview = "你好", lastAt = 100)
            lastMessages["group_1"] = LastMessage("刚说完", at = 900)
        }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertEquals(listOf("group_1", "default"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `group subtitle falls back to member line when it has no messages`() = runTest {
        val source = FakeSource().apply {
            groups += GroupSession(id = "group_1", title = "空群", memberIds = listOf("a1", "a2"))
        }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        val group = vm.state.value.items.single()
        assertEquals("兰未晞 + 2 个工具人", group.subtitle)
        assertEquals("空群", group.title)
    }

    @Test
    fun `single chat without messages shows placeholder`() = runTest {
        val source = FakeSource().apply {
            chats += SessionSummary(id = "s1", messageCount = 0, preview = "", lastAt = 0)
        }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        val item = vm.state.value.items.single()
        assertFalse(item.isGroup)
        assertEquals("兰未晞", item.title)
        assertEquals("还没有消息", item.subtitle)
    }

    @Test
    fun `create chat reports failure instead of navigating nowhere`() = runTest {
        val source = FakeSource().apply { failCreate = true }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        var navigatedTo: String? = null
        vm.createChat { navigatedTo = it }
        advanceUntilIdle()

        assertEquals(null, navigatedTo)
        assertTrue(vm.state.value.banner!!.contains("失败"))
    }

    @Test
    fun `create chat hands the new id to the caller`() = runTest {
        val source = FakeSource().apply { createdId = "s42" }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        var navigatedTo: String? = null
        vm.createChat { navigatedTo = it }
        advanceUntilIdle()

        assertEquals("s42", navigatedTo)
    }

    @Test
    fun `delete routes to chat or group accordingly`() = runTest {
        val source = FakeSource()
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        vm.delete(ConversationItem(id = "group_1", isGroup = true, title = "群", subtitle = "", lastAt = 0))
        vm.delete(ConversationItem(id = "default", isGroup = false, title = "兰未晞", subtitle = "", lastAt = 0))
        advanceUntilIdle()

        assertEquals(listOf("group:group_1", "chat:default"), source.deleted)
    }

    @Test
    fun `delete failure shows a banner`() = runTest {
        val source = FakeSource().apply { failDelete = true }
        val vm = sessionsVm(source, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        vm.delete(ConversationItem(id = "default", isGroup = false, title = "兰未晞", subtitle = "", lastAt = 0))
        advanceUntilIdle()

        assertTrue(vm.state.value.banner!!.contains("删除失败"))
    }

    @Test
    fun `list time shows clock today and date for older days`() {
        val now = ZonedDateTime.of(2026, 11, 24, 15, 30, 0, 0, ZoneId.systemDefault())
        val todayAt = now.withHour(9).withMinute(5).toInstant().toEpochMilli()
        val yesterdayAt = now.minusDays(1).withHour(20).toInstant().toEpochMilli()
        val earlierAt = now.minusDays(10).toInstant().toEpochMilli()

        assertEquals("09:05", listTime(todayAt, now))
        assertEquals("昨天", listTime(yesterdayAt, now))
        assertEquals("11-14", listTime(earlierAt, now))
        assertEquals("", listTime(0L, now))
    }
}
