package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.repository.RoomDraftRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P206：drafts 表（v8）与仓储的行为验收。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftDaoTest {

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

    private fun noteDraft(title: String, createdAt: Long = 1L) = DraftEntity(
        kind = DraftKind.NOTE,
        title = title,
        body = "正文",
        createdAt = createdAt,
    )

    @Test
    fun `pending lists newest first`() = runBlocking {
        val dao = db.draftDao()
        val a = dao.insert(noteDraft("A", createdAt = 100L))
        val b = dao.insert(noteDraft("B", createdAt = 300L))
        val c = dao.insert(noteDraft("C", createdAt = 200L))

        assertTrue(a > 0 && b > a && c > b)
        assertEquals(listOf(b, c, a), dao.listPending().map { it.id })
        assertEquals(3, dao.countPending())
    }

    @Test
    fun `markReviewed moves row out of pending`() = runBlocking {
        val dao = db.draftDao()
        val id = dao.insert(noteDraft("待审"))

        dao.markReviewed(id, DraftStatus.APPROVED, targetId = 42L, now = 5_000L)

        assertTrue(dao.listPending().isEmpty())
        assertEquals(0, dao.countPending())
        val row = dao.byId(id)!!
        assertEquals(DraftStatus.APPROVED, row.status)
        assertEquals(42L, row.targetId)
        assertEquals(5_000L, row.reviewedAt)
        assertEquals(listOf(id), dao.listReviewed(10).map { it.id })
    }

    @Test
    fun `reviewed sorts by review time desc and honors limit`() = runBlocking {
        val dao = db.draftDao()
        val a = dao.insert(noteDraft("A"))
        val b = dao.insert(noteDraft("B"))
        val c = dao.insert(noteDraft("C"))
        dao.markReviewed(a, DraftStatus.REJECTED, 0L, now = 100L)
        dao.markReviewed(b, DraftStatus.APPROVED, 7L, now = 300L)
        dao.markReviewed(c, DraftStatus.REJECTED, 0L, now = 200L)

        assertEquals(listOf(b, c, a), dao.listReviewed(10).map { it.id })
        assertEquals(listOf(b, c), dao.listReviewed(2).map { it.id })
    }

    @Test
    fun `delete removes row`() = runBlocking {
        val dao = db.draftDao()
        val id = dao.insert(noteDraft("删我"))
        dao.deleteById(id)

        assertNull(dao.byId(id))
        assertEquals(0, dao.countPending())
    }

    @Test
    fun `repository fills timestamps and status`() = runBlocking {
        var now = 1_000L
        val repo = RoomDraftRepository(db.draftDao(), clock = { ++now })
        val id = repo.propose(
            DraftEntity(
                kind = DraftKind.TASK,
                title = "交报告",
                dueAt = 9_000L,
                priority = 2,
                createdAt = 0L,
            ),
        )

        val row = repo.byId(id)!!
        assertEquals(DraftStatus.PENDING, row.status)
        assertEquals(1_001L, row.createdAt)
        assertEquals(9_000L, row.dueAt)
        assertEquals(2, row.priority)
        assertEquals(0L, row.reviewedAt)

        repo.markApproved(id, targetId = 8L)
        val after = repo.byId(id)!!
        assertEquals(DraftStatus.APPROVED, after.status)
        assertEquals(8L, after.targetId)
        assertEquals(1_002L, after.reviewedAt)
        assertEquals(0, repo.countPending())
    }
}
