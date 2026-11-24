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
 * P204：v6 → v7 迁移验收（messages 加引用两列）。
 *
 * 在旧版 messages 表上执行生产用的 [Migrations.MIGRATION_6_7]，
 * 断言列被加出来、旧行拿到默认值（0 / 空串）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageQuoteMigrationTest {

    private fun openTemporary(): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("message_quote_migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper to helper.writableDatabase
    }

    @Test
    fun `v6 to v7 adds quote columns and keeps old rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("message_quote_migration.db")
        val (helper, db) = openTemporary()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`speaker` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`sessionId` TEXT NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO `messages` (`role`, `speaker`, `text`, `status`, `createdAt`, `sessionId`) " +
                    "VALUES ('user', '', '旧消息', 'sent', 1, 'default')",
            )

            Migrations.MIGRATION_6_7.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`messages`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertEquals(
                setOf(
                    "id", "role", "speaker", "text", "status", "createdAt", "sessionId",
                    "quoteId", "quotePreview",
                ),
                columns,
            )

            db.query("SELECT `text`, `quoteId`, `quotePreview` FROM `messages`").use { c ->
                assertTrue("旧行应保留", c.moveToFirst())
                assertEquals("旧消息", c.getString(0))
                assertEquals("旧行 quoteId 默认 0", 0L, c.getLong(1))
                assertEquals("旧行 quotePreview 默认空", "", c.getString(2))
            }
        } finally {
            helper.close()
            context.deleteDatabase("message_quote_migration.db")
        }
    }
}
