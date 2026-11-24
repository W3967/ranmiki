package com.lanweixi.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T02 验收中的"迁移测试"替代项。
 *
 * v1 → v2 新增 notes 表（DataModule.MIGRATION_1_2），旧数据保留。
 * 这里验证：
 * 1) 数据库版本与 DB 类声明一致（v2）；
 * 2) 十张表全部建出；
 * 3) 空库首启写入默认角色，且重复执行幂等。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseOpeningTest {

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
    fun `database version matches declaration`() {
        assertEquals(LanWeixiDatabase.VERSION, db.openHelper.writableDatabase.version)
    }

    @Test
    fun `all tables exist`() {
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'"
        )
        val names = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) names += it.getString(0)
        }
        val expected = setOf(
            "messages", "memories", "characters", "models",
            "vitals_samples", "diary", "feeds", "events", "kv", "notes", "prompts", "tasks",
            "documents", "doc_chunks", "reviews", "drafts", "session_summaries",
        )
        // room_master_table 是 Room 自己的 schema 校验表，不属于业务表
        assertEquals(expected, names - "room_master_table")
    }

    @Test
    fun `empty database seeds default character idempotently`() = runBlocking {
        assertEquals(0, db.characterDao().count())

        DatabaseInitializer.seedIfNeeded(db)
        val characters = db.characterDao().list()
        assertEquals(1, characters.size)
        assertEquals(DefaultData.DEFAULT_CHAR_ID, characters[0].id)
        assertEquals(DefaultData.DEFAULT_CHAR_NAME, characters[0].name)
        assertNotNull(db.kvDao().get(DatabaseInitializer.KEY_SCHEMA_READY))

        DatabaseInitializer.seedIfNeeded(db)
        assertEquals("seeding must be idempotent", 1, db.characterDao().count())
    }
}
