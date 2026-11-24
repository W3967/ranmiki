package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.PromptEntity
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

/** P133：创作提示词 DAO 验收（内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptDaoTest {

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

    private fun prompt(
        title: String,
        category: String = "ip",
        positive: String = "p",
        negative: String = "",
        pinned: Boolean = false,
        updatedAt: Long = 0L,
    ) = PromptEntity(
        title = title,
        category = category,
        positive = positive,
        negative = negative,
        pinned = pinned,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    @Test
    fun `pinned items come first`() = runBlocking {
        val dao = db.promptDao()
        val a = dao.insert(prompt("A", updatedAt = 3_000L))
        val b = dao.insert(prompt("B", pinned = true, updatedAt = 1_000L))
        val c = dao.insert(prompt("C", updatedAt = 2_000L))

        assertEquals(listOf(b, a, c), dao.listAll().map { it.id })
    }

    @Test
    fun `filter by category`() = runBlocking {
        val dao = db.promptDao()
        dao.insert(prompt("IP 开头钩子", category = "ip"))
        dao.insert(prompt("短视频口播", category = "video"))
        dao.insert(prompt("赛博少女", category = "image"))

        assertEquals(1, dao.listByCategory("ip").size)
        assertEquals(listOf("赛博少女"), dao.listByCategory("image").map { it.title })
        assertEquals(3, dao.listAll().size)
    }

    @Test
    fun `search covers title positive negative and memo`() = runBlocking {
        val dao = db.promptDao()
        dao.insert(prompt("角色出场", positive = "少女站在雨里", negative = "模糊", ))
        dao.insert(prompt("分镜", positive = "特写眼睛", negative = "多余手指"))
        dao.insert(
            prompt("氛围", positive = "霓虹灯", negative = "").copy(memo = "参考王家卫"),
        )

        assertEquals(listOf("角色出场"), dao.search("少女").map { it.title })
        assertEquals(listOf("分镜"), dao.search("手指").map { it.title })
        assertEquals(listOf("氛围"), dao.search("王家卫").map { it.title })
        assertTrue(dao.search("不存在的词").isEmpty())
    }

    @Test
    fun `pin toggles and reorders`() = runBlocking {
        val dao = db.promptDao()
        val a = dao.insert(prompt("A", updatedAt = 5_000L))
        val b = dao.insert(prompt("B", updatedAt = 1_000L))

        assertEquals(listOf(a, b), dao.listAll().map { it.id })
        dao.setPinned(b, true, 6_000L)
        assertEquals(listOf(b, a), dao.listAll().map { it.id })
        // 取消置顶后回到时间排序：B 刚被更新（7000 > A 的 5000），所以仍在前
        dao.setPinned(b, false, 7_000L)
        assertEquals(listOf(b, a), dao.listAll().map { it.id })
    }

    @Test
    fun `update and delete`() = runBlocking {
        val dao = db.promptDao()
        val id = dao.insert(prompt("旧标题"))
        dao.update(dao.byId(id)!!.copy(title = "新标题", positive = "新正向", updatedAt = 9_000L))

        val after = dao.byId(id)!!
        assertEquals("新标题", after.title)
        assertEquals("新正向", after.positive)
        assertEquals(9_000L, after.updatedAt)

        dao.deleteById(id)
        assertNull(dao.byId(id))
        assertEquals(0, dao.count())
    }
}