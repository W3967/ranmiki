package com.lanweixi.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P206：v7 → v8 迁移验收（新增 drafts 表）。
 *
 * 在 v7 结构的库上跑生产用的 [Migrations.MIGRATION_7_8]，
 * 断言草稿表建出来、列与索引齐全，且旧表数据一根汗毛没动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftMigrationTest {

    private val dbName = "draft_migration_v7.db"

    private fun openV7(): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> =
        open("draft_migration_v7.db", 7)

    private fun openV8(): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> =
        open("draft_migration_v8.db", 8)

    private fun open(name: String, version: Int): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper to helper.writableDatabase
    }

    @Test
    fun `v7 to v8 creates drafts and keeps old rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        val (helper, db) = openV7()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `tags` TEXT NOT NULL, " +
                    "`archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO `notes` (`title`, `content`, `tags`, `archived`, `createdAt`, `updatedAt`) " +
                    "VALUES ('旧笔记', '正文', '', 0, 1, 1)",
            )

            Migrations.MIGRATION_7_8.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`drafts`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertEquals(
                setOf(
                    "id", "kind", "title", "body", "tags", "dueAt", "priority", "status",
                    "sourceSessionId", "sourceMessageId", "targetId", "createdAt", "reviewedAt",
                ),
                columns,
            )

            val indices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='drafts'").use { c ->
                while (c.moveToNext()) indices += c.getString(0)
            }
            assertTrue(indices.containsAll(setOf("index_drafts_status", "index_drafts_createdAt")))

            // 新建的草稿表是可插的，默认状态显式给值（迁移不写默认值，由仓储补）
            db.execSQL(
                "INSERT INTO `drafts` (`kind`, `title`, `body`, `tags`, `dueAt`, `priority`, `status`, " +
                    "`sourceSessionId`, `sourceMessageId`, `targetId`, `createdAt`, `reviewedAt`) " +
                    "VALUES ('note', '面试要点', '带简历', '', 0, 1, 'pending', 'default', 0, 0, 2, 0)",
            )
            db.query("SELECT `title`, `status` FROM `drafts`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("面试要点", c.getString(0))
                assertEquals("pending", c.getString(1))
            }

            // 旧表数据必须原样保留
            db.query("SELECT `title` FROM `notes`").use { c ->
                assertTrue("旧笔记应保留", c.moveToFirst())
                assertEquals("旧笔记", c.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `v8 to v9 adds sourceText with default empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "draft_migration_v8.db"
        context.deleteDatabase(name)
        val (helper, db) = openV8()
        try {
            // v8 的 drafts 表（没有 sourceText）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drafts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`tags` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `priority` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `sourceSessionId` TEXT NOT NULL, " +
                    "`sourceMessageId` INTEGER NOT NULL, `targetId` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO `drafts` (`kind`, `title`, `body`, `tags`, `dueAt`, `priority`, " +
                    "`status`, `sourceSessionId`, `sourceMessageId`, `targetId`, `createdAt`, `reviewedAt`) " +
                    "VALUES ('note', '旧草稿', '正文', '', 0, 1, 'pending', 'default', 0, 0, 1, 0)",
            )

            Migrations.MIGRATION_8_9.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`drafts`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertTrue("应有 sourceText 列", columns.contains("sourceText"))

            db.query("SELECT `title`, `sourceText` FROM `drafts`").use { c ->
                assertTrue("旧草稿应保留", c.moveToFirst())
                assertEquals("旧草稿", c.getString(0))
                assertEquals("旧行 sourceText 默认空", "", c.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `v9 to v10 creates session summaries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "draft_migration_v9.db"
        context.deleteDatabase(name)
        val (helper, db) = open(name, 9)
        try {
            // v9 库里已有的消息，迁移后必须还在
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`role` TEXT NOT NULL, `speaker` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, " +
                    "`quoteId` INTEGER NOT NULL, `quotePreview` TEXT NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO `messages` (`role`, `speaker`, `text`, `status`, `createdAt`, `sessionId`, " +
                    "`quoteId`, `quotePreview`) VALUES ('user', '', '旧消息', 'sent', 1, 'default', 0, '')",
            )

            Migrations.MIGRATION_9_10.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`session_summaries`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertEquals(
                setOf(
                    "id", "sessionId", "untilId", "content", "coveredCount", "createdAt", "updatedAt",
                ),
                columns,
            )

            val indices = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='session_summaries'").use { c ->
                while (c.moveToNext()) indices += c.getString(0)
            }
            assertTrue(indices.contains("index_session_summaries_sessionId_untilId"))

            db.execSQL(
                "INSERT INTO `session_summaries` (`sessionId`, `untilId`, `content`, `coveredCount`, " +
                    "`createdAt`, `updatedAt`) VALUES ('default', 40, '他明天面试', 32, 1, 1)",
            )
            db.query("SELECT `content`, `coveredCount` FROM `session_summaries`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("他明天面试", c.getString(0))
                assertEquals(32, c.getInt(1))
            }

            db.query("SELECT `text` FROM `messages`").use { c ->
                assertTrue("旧消息应保留", c.moveToFirst())
                assertEquals("旧消息", c.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
