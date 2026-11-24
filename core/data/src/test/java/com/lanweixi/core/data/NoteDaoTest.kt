package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.NoteEntity
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

/** P132：工作台笔记 DAO 验收（内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDaoTest {

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

    private fun note(title: String, content: String = "", tags: String = "") =
        NoteEntity(title = title, content = content, tags = tags, createdAt = 0L, updatedAt = 0L)

    @Test
    fun `insert assigns id and sorts by updatedAt desc`() = runBlocking {
        val dao = db.noteDao()
        val a = dao.insert(note("A").copy(updatedAt = 1_000L))
        val b = dao.insert(note("B").copy(updatedAt = 2_000L))
        val c = dao.insert(note("C").copy(updatedAt = 3_000L))

        assertTrue(a > 0 && b > a && c > b)
        assertEquals(listOf(c, b, a), dao.listActive().map { it.id })
    }

    @Test
    fun `update edits fields`() = runBlocking {
        val dao = db.noteDao()
        val id = dao.insert(note("原标题", "旧正文"))
        val row = dao.byId(id)!!
        dao.update(row.copy(title = "新标题", content = "新正文", updatedAt = 9_000L))

        val after = dao.byId(id)!!
        assertEquals("新标题", after.title)
        assertEquals("新正文", after.content)
        assertEquals(9_000L, after.updatedAt)
    }

    @Test
    fun `search matches title content and tags`() = runBlocking {
        val dao = db.noteDao()
        dao.insert(note("Kotlin 协程", "挂起函数", "语言"))
        dao.insert(note("Compose 布局", "Modifier", "android"))
        dao.insert(note("做饭", "番茄炒蛋", "生活"))

        assertEquals(listOf("Kotlin 协程"), dao.search("协程").map { it.title })
        assertEquals(listOf("Compose 布局"), dao.search("Modifier").map { it.title })
        assertEquals(listOf("Compose 布局"), dao.search("android").map { it.title })
        assertTrue(dao.search("不存在的词").isEmpty())
    }

    @Test
    fun `archive hides from active and search`() = runBlocking {
        val dao = db.noteDao()
        val id = dao.insert(note("待归档", "x"))
        dao.setArchived(id, true, 2_000L)

        assertTrue(dao.listActive().isEmpty())
        assertEquals(listOf(id), dao.listArchived().map { it.id })
        assertTrue(dao.search("待归档").isEmpty())
        assertEquals(0, dao.countActive())

        dao.setArchived(id, false, 3_000L)
        assertEquals(listOf(id), dao.listActive().map { it.id })
        assertEquals(1, dao.countActive())
    }

    @Test
    fun `delete removes row`() = runBlocking {
        val dao = db.noteDao()
        val id = dao.insert(note("删我"))
        dao.deleteById(id)
        assertNull(dao.byId(id))
        assertEquals(0, dao.countActive())
    }
}