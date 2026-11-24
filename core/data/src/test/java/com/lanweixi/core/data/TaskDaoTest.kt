package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.TaskEntity
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

/** P134：任务清单 DAO 验收（内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDaoTest {

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

    private fun task(
        title: String,
        dueAt: Long = 0L,
        priority: Int = 1,
        createdAt: Long = 0L,
    ) = TaskEntity(
        title = title,
        dueAt = dueAt,
        priority = priority,
        createdAt = createdAt,
        updatedAt = 0L,
    )

    @Test
    fun `open list puts dated tasks first then by due then priority`() = runBlocking {
        val dao = db.taskDao()
        val noDueHigh = dao.insert(task("无截止·高", priority = 2, createdAt = 5L))
        val dueLate = dao.insert(task("后天", dueAt = 2_000L, priority = 1))
        val dueSoon = dao.insert(task("明天", dueAt = 1_000L, priority = 0))
        val noDueLow = dao.insert(task("无截止·低", priority = 0, createdAt = 9L))

        assertEquals(
            listOf(dueSoon, dueLate, noDueHigh, noDueLow),
            dao.listOpen().map { it.id },
        )
    }

    @Test
    fun `completing moves task to done list and stamps doneAt`() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insert(task("写周报"))

        dao.setDone(id, true, 5_000L, 5_000L)
        assertTrue(dao.listOpen().isEmpty())
        assertEquals(listOf(id), dao.listDone().map { it.id })
        assertEquals(5_000L, dao.byId(id)!!.doneAt)
        assertEquals(0, dao.countOpen())

        dao.setDone(id, false, 0L, 9_000L)
        assertEquals(listOf(id), dao.listOpen().map { it.id })
        assertEquals(0L, dao.byId(id)!!.doneAt)
        assertEquals(1, dao.countOpen())
    }

    @Test
    fun `update edits fields`() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insert(task("旧标题"))
        dao.update(dao.byId(id)!!.copy(title = "新标题", detail = "备注", priority = 2, updatedAt = 9_000L))

        val after = dao.byId(id)!!
        assertEquals("新标题", after.title)
        assertEquals("备注", after.detail)
        assertEquals(2, after.priority)
        assertEquals(9_000L, after.updatedAt)
    }

    @Test
    fun `delete removes task`() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insert(task("删我"))
        dao.deleteById(id)
        assertNull(dao.byId(id))
        assertEquals(0, dao.countOpen())
    }

    @Test
    fun `noteRef defaults to zero so it is opt in`() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insert(task("独立任务"))
        assertEquals(0L, dao.byId(id)!!.noteRef)
    }
}