package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P142：会话摘要（首条用户消息当话题标签）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDigestTest {

    private lateinit var db: LanWeixiDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LanWeixiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun msg(sessionId: String, role: String, text: String, at: Long) = MessageEntity(
        sessionId = sessionId,
        role = role,
        text = text,
        createdAt = at,
        status = "sent",
    )

    @Test
    fun `preview is the first user message of each session`() = runBlocking {
        val dao = db.messageDao()
        dao.insert(msg("s1", "user", "帮我查任务", 100))
        dao.insert(msg("s1", "assistant", "好的", 101))
        dao.insert(msg("s1", "user", "再查一次", 102))
        dao.insert(msg("s2", "user", "写个提示词", 200))
        dao.insert(msg("s2", "assistant", "给", 201))

        val digests = dao.sessionDigest()

        assertEquals(2, digests.size)
        // 按最后活动时间倒序
        assertEquals("s2", digests[0].sessionId)
        assertEquals("写个提示词", digests[0].preview)
        assertEquals(2, digests[0].messageCount)

        assertEquals("s1", digests[1].sessionId)
        assertEquals("帮我查任务", digests[1].preview)
        assertEquals(3, digests[1].messageCount)
        assertEquals(102L, digests[1].lastAt)
    }

    @Test
    fun `session without user message falls back to null preview`() = runBlocking {
        val dao = db.messageDao()
        dao.insert(msg("only-assistant", "assistant", "系统开场白", 50))

        val digest = dao.sessionDigest().first()
        assertEquals("only-assistant", digest.sessionId)
        assertTrue("没有用户消息时 preview 应为空", digest.preview.isNullOrBlank())
        assertEquals(1, digest.messageCount)
    }

    @Test
    fun `empty database yields no digests`() = runBlocking {
        assertTrue(db.messageDao().sessionDigest().isEmpty())
    }
}