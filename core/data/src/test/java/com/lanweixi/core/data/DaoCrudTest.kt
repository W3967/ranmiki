package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanweixi.core.data.entity.CharacterEntity
import com.lanweixi.core.data.entity.DiaryEntity
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.entity.FeedEntity
import com.lanweixi.core.data.entity.KvEntity
import com.lanweixi.core.data.entity.MemoryEntity
import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.entity.ModelEntity
import com.lanweixi.core.data.entity.VitalsSampleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T02 验收：DAO 增删查覆盖。
 * v2 起 notes 表随迁移加入；其余表结构未变。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoCrudTest {

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

    @Test
    fun messageCrud() = runBlocking {
        val dao = db.messageDao()
        val id = dao.insert(MessageEntity(role = "user", text = "在吗", status = "sent", createdAt = 1L, sessionId = "s1"))
        assertEquals(1, dao.count())

        val loaded = dao.getById(id)
        assertNotNull(loaded)
        assertEquals("在吗", loaded!!.text)

        dao.update(loaded.copy(text = "在吗？", status = "failed"))
        assertEquals("failed", dao.getById(id)!!.status)

        dao.insert(MessageEntity(role = "assistant", text = "在", status = "sent", createdAt = 2L, sessionId = "s1"))
        assertEquals(2, dao.listBySession("s1").size)
        assertEquals(0, dao.listBySession("s2").size)

        dao.deleteById(id)
        assertEquals(1, dao.count())
        dao.deleteBySession("s1")
        assertEquals(0, dao.count())
    }

    @Test
    fun memoryCrud() = runBlocking {
        val dao = db.memoryDao()
        val id = dao.insert(
            MemoryEntity(charId = "default", text = "他喜欢喝热茶", weight = 0.68f, source = "chat", createdAt = 10L, lastAccess = 10L, accessCount = 0)
        )
        assertEquals(1, dao.countByChar("default"))

        val memory = dao.getById(id)!!
        dao.update(memory.copy(accessCount = 1, weight = 0.7f))
        assertEquals(1, dao.getById(id)!!.accessCount)

        dao.deleteById(id)
        assertEquals(0, dao.countByChar("default"))
    }

    @Test
    fun characterCrud() = runBlocking {
        val dao = db.characterDao()
        dao.upsert(DefaultData.defaultCharacter())
        assertEquals(1, dao.count())
        assertEquals("兰未晞", dao.getById(DefaultData.DEFAULT_CHAR_ID)!!.name)

        val char = dao.getById(DefaultData.DEFAULT_CHAR_ID)!!
        dao.upsert(char.copy(affection = 80))
        assertEquals(80, dao.getById(DefaultData.DEFAULT_CHAR_ID)!!.affection)
        assertEquals(1, dao.list().size)

        dao.deleteById(DefaultData.DEFAULT_CHAR_ID)
        assertNull(dao.getById(DefaultData.DEFAULT_CHAR_ID))
    }

    @Test
    fun modelCrudAndActiveSwitch() = runBlocking {
        val dao = db.modelDao()
        dao.upsert(ModelEntity(id = "m1", name = "lite", dirUri = "file:///m1", schemaVer = 3, sizeBytes = 18L, checksum = "x", importedAt = 1L))
        dao.upsert(ModelEntity(id = "m2", name = "full", dirUri = "file:///m2", schemaVer = 3, sizeBytes = 64L, checksum = "y", importedAt = 2L))
        assertEquals(2, dao.count())

        dao.clearActive()
        dao.setActive("m2")
        assertEquals("m2", dao.getActive()!!.id)
        dao.clearActive()
        dao.setActive("m1")
        assertEquals("m1", dao.getActive()!!.id)

        dao.deleteById("m1")
        assertEquals(1, dao.count())
    }

    @Test
    fun diaryUniqueDayKey() = runBlocking {
        val dao = db.diaryDao()
        dao.upsert(DiaryEntity(dayKey = "2026-11-04", content = "第一版", mood = "平静", createdAt = 1L))
        dao.upsert(DiaryEntity(dayKey = "2026-11-04", content = "第二版", mood = "开心", createdAt = 2L))
        assertEquals(1, dao.count())
        assertEquals("第二版", dao.getByDay("2026-11-04")!!.content)
    }

    @Test
    fun feedEventVitalsCrud() = runBlocking {
        val feedDao = db.feedDao()
        feedDao.upsert(FeedEntity(id = "f1", source = "space", title = "动态", excerpt = "今天好热", importedAt = 1L))
        assertEquals(1, feedDao.unanalyzedCount())
        feedDao.markAnalyzed("f1")
        assertEquals(0, feedDao.unanalyzedCount())
        feedDao.deleteById("f1")
        assertEquals(0, feedDao.list().size)

        val eventDao = db.eventDao()
        eventDao.insert(EventEntity(type = "milestone", payload = "{}", at = 100L))
        assertEquals(1, eventDao.count())
        assertEquals(1, eventDao.recent(10).size)
        eventDao.deleteBefore(200L)
        assertEquals(0, eventDao.count())

        val vitalsDao = db.vitalsSampleDao()
        vitalsDao.insert(VitalsSampleEntity(at = 1000L, energy = 0.5f, cortisol = 0.2f, hunger = 0.1f, thirst = 0.3f))
        vitalsDao.insert(VitalsSampleEntity(at = 2000L, energy = 0.6f, cortisol = 0.2f, hunger = 0.1f, thirst = 0.3f))
        assertEquals(2, vitalsDao.listBetween(0L, 5000L).size)
        assertEquals(1, vitalsDao.listBetween(1500L, 5000L).size)
        vitalsDao.deleteBefore(1500L)
        assertEquals(1, vitalsDao.count())
    }

    @Test
    fun kvCrud() = runBlocking {
        val dao = db.kvDao()
        dao.put(KvEntity("k1", "v1"))
        assertEquals("v1", dao.get("k1"))
        dao.put(KvEntity("k1", "v2"))
        assertEquals("v2", dao.get("k1"))
        dao.delete("k1")
        assertNull(dao.get("k1"))
    }

    @Test
    fun characterEntityIsUsableAsDefault() {
        val default = DefaultData.defaultCharacter()
        assertEquals(CharacterEntity::class.java, default.javaClass)
        assertEquals(50, default.affection)
        assertEquals(50, default.trust)
        assertNull(default.modelId)
    }

    @Test
    fun `probeNoteDao`() = runBlocking {
        val id = db.noteDao().insert(
            com.lanweixi.core.data.entity.NoteEntity(
                title = "probe", content = "", createdAt = 1L, updatedAt = 1L,
            ),
        )
        assertEquals(1, db.noteDao().listActive().size)
        assertEquals(id, db.noteDao().listActive()[0].id)
    }
}
