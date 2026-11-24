package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

/** P135：文档与分块 DAO 验收（内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentDaoTest {

    private lateinit var db: LanWeixiDatabase
    private var now = 1_000L

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

    @Test
    fun `import writes document and chunks together`() = runBlocking {
        val dao = db.documentDao()
        val id = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "设计稿",
                fileName = "design.md",
                kind = "md",
                charCount = 12,
                chunkCount = 2,
                importedAt = now,
                updatedAt = now,
            ),
        )
        dao.insertChunks(
            listOf(
                com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 0, text = "第一块", charCount = 3),
                com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 1, text = "第二块", charCount = 3),
            ),
        )

        assertEquals(1, dao.count())
        assertEquals(listOf("第一块", "第二块"), dao.chunksOf(id).map { it.text })
        assertEquals(listOf("设计稿"), dao.listAll().map { it.title })
    }

    @Test
    fun `chunks are ordered by seq`() = runBlocking {
        val dao = db.documentDao()
        val id = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "顺序", fileName = "o.txt", kind = "txt",
                charCount = 0, chunkCount = 3, importedAt = now, updatedAt = now,
            ),
        )
        dao.insertChunks(
            listOf(
                com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 2, text = "C", charCount = 1),
                com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 0, text = "A", charCount = 1),
                com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 1, text = "B", charCount = 1),
            ),
        )
        assertEquals(listOf("A", "B", "C"), dao.chunksOf(id).map { it.text })
    }

    @Test
    fun `search hits title and chunk text`() = runBlocking {
        val dao = db.documentDao()
        val a = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "Kotlin 笔记", fileName = "k.md", kind = "md",
                charCount = 0, chunkCount = 1, importedAt = 2_000L, updatedAt = 2_000L,
            ),
        )
        dao.insertChunks(
            listOf(com.lanweixi.core.data.entity.DocChunkEntity(docId = a, seq = 0, text = "协程是轻量线程", charCount = 7)),
        )
        val b = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "菜谱", fileName = "cook.txt", kind = "txt",
                charCount = 0, chunkCount = 1, importedAt = 1_000L, updatedAt = 1_000L,
            ),
        )
        dao.insertChunks(
            listOf(com.lanweixi.core.data.entity.DocChunkEntity(docId = b, seq = 0, text = "番茄炒蛋", charCount = 4)),
        )

        assertEquals(listOf("Kotlin 笔记"), dao.search("Kotlin").map { it.title })
        assertEquals(listOf("Kotlin 笔记"), dao.search("轻量线程").map { it.title })
        assertEquals(listOf("菜谱"), dao.search("番茄").map { it.title })
        assertTrue(dao.search("不存在的词").isEmpty())
        assertEquals("协程是轻量线程", dao.firstHitChunk(a, "线程")?.text)
    }

    @Test
    fun `delete removes chunks too`() = runBlocking {
        val dao = db.documentDao()
        val id = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = "删我", fileName = "d.txt", kind = "txt",
                charCount = 0, chunkCount = 1, importedAt = now, updatedAt = now,
            ),
        )
        dao.insertChunks(
            listOf(com.lanweixi.core.data.entity.DocChunkEntity(docId = id, seq = 0, text = "x", charCount = 1)),
        )
        dao.deleteChunksOf(id)
        dao.deleteById(id)

        assertNull(dao.byId(id))
        assertTrue(dao.chunksOf(id).isEmpty())
        assertEquals(0, dao.count())
    }
}