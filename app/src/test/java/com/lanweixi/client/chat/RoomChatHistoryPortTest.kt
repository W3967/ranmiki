package com.lanweixi.client.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.repository.RoomMessageRepository
import com.lanweixi.core.network.ChatError
import com.lanweixi.core.network.ChatStreamCallback
import com.lanweixi.core.network.SseEvent
import com.lanweixi.domain.prompt.PromptBuilder
import com.lanweixi.domain.prompt.ChatConfig
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.feature.chat.ConversationEngine
import com.lanweixi.feature.chat.StreamRunner
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P06c.1：Room 适配器 + 对话用例联动测试（内存 Room）。
 * 验收点：Done 后 messages 表恰好 2 行且字段齐全；429/401/abort 后 0 行。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomChatHistoryPortTest {

    private lateinit var db: LanWeixiDatabase
    private lateinit var port: RoomChatHistoryPort

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LanWeixiDatabase::class.java,
        ).allowMainThreadQueries().build()
        port = RoomChatHistoryPort(RoomMessageRepository(db.messageDao()), StaticSession())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun engine(runner: StreamRunner, dispatcher: CoroutineDispatcher): ConversationEngine {
        return ConversationEngine(
            stream = runner,
            promptBuilder = PromptBuilder(),
            history = port,
            configProvider = object : ChatConfigProvider {
                override suspend fun current(): ChatConfig = ChatConfig(
                    baseUrl = "http://127.0.0.1/mock",
                    apiKey = "sk-test",
                    persona = "你是兰未晞。",
                )
            },
            dispatcher = dispatcher,
        )
    }

    private fun runner(block: (ChatStreamCallback) -> Unit) = StreamRunner { _, cb ->
        block(cb)
        null
    }

    @Test
    fun `done writes exactly two rows with full fields`() = runTest {
        val engine = engine(
            runner { cb ->
                cb.onEvent(SseEvent.Delta("你"))
                cb.onEvent(SseEvent.Done("你好", null, null, null))
                cb.onClosed()
            },
            UnconfinedTestDispatcher(testScheduler),
        )
        val outcome = engine.send("在吗")
        assertTrue(outcome.completed)

        val rows = db.messageDao().listBySession("default")
        assertEquals(2, rows.size)

        val user = rows[0]
        assertEquals("user", user.role)
        assertEquals("在吗", user.text)
        assertEquals("sent", user.status)
        assertEquals("default", user.sessionId)
        assertEquals("", user.speaker)
        assertTrue(user.createdAt > 0)

        val assistant = rows[1]
        assertEquals("assistant", assistant.role)
        assertEquals("你好", assistant.text)
        assertEquals("sent", assistant.status)
    }

    @Test
    fun `abort writes zero rows`() = runTest {
        val engine = engine(
            runner { cb -> cb.onError(ChatError.Network(IOException("unexpected end of stream"))) },
            UnconfinedTestDispatcher(testScheduler),
        )
        val outcome = engine.send("在吗")
        assertFalse(outcome.completed)
        assertTrue(db.messageDao().listBySession("default").isEmpty())
    }

    @Test
    fun `429 writes zero rows`() = runTest {
        val engine = engine(
            runner { cb -> cb.onError(ChatError.Busy(429, "请求过于频繁")) },
            UnconfinedTestDispatcher(testScheduler),
        )
        engine.send("在吗")
        assertTrue(db.messageDao().listBySession("default").isEmpty())
    }

    @Test
    fun `401 writes zero rows`() = runTest {
        val engine = engine(
            runner { cb -> cb.onError(ChatError.InvalidKey(401, "bad key")) },
            UnconfinedTestDispatcher(testScheduler),
        )
        engine.send("在吗")
        assertTrue(db.messageDao().listBySession("default").isEmpty())
    }

    private class StaticSession : com.lanweixi.feature.chat.SessionPort {
        override suspend fun current(): String = "default"
        override suspend fun switch(id: String) = Unit
        override suspend fun list() = emptyList<com.lanweixi.feature.chat.SessionSummary>()
        override suspend fun delete(id: String) = Unit
        override suspend fun createNew(): String = "default"
    }

}
