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
 * P132：v1 -> v2 迁移验收。
 *
 * 在一个空的原生 SQLite 库上执行**生产用的** [Migrations.MIGRATION_1_2]，
 * 断言 notes 表与索引被正确建出（防 SQL 拼写漂移）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteMigrationTest {

    private fun openTemporary(): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_probe.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper to helper.writableDatabase
    }

    @Test
    fun `migration creates notes table with all columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("migration_probe.db")
        val (helper, db) = openTemporary()
        try {
            Migrations.MIGRATION_1_2.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`notes`)").use { c ->
                while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertEquals(
                setOf("id", "title", "content", "tags", "archived", "createdAt", "updatedAt"),
                columns,
            )

            val indices = mutableSetOf<String>()
            db.query("PRAGMA index_list(`notes`)").use { c ->
                while (c.moveToNext()) indices += c.getString(c.getColumnIndexOrThrow("name"))
            }
            assertTrue("should build updatedAt index, got=" + indices, indices.any { it.contains("updatedAt") })
            assertTrue("should build archived index, got=" + indices, indices.any { it.contains("archived") })
        } finally {
            helper.close()
            context.deleteDatabase("migration_probe.db")
        }
    }

    @Test
    fun `migration is idempotent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("migration_probe.db")
        val (helper, db) = openTemporary()
        try {
            Migrations.MIGRATION_1_2.migrate(db)
            Migrations.MIGRATION_1_2.migrate(db)
            db.query("SELECT COUNT(*) FROM `notes`").use { assertTrue(it.moveToFirst()) }
        } finally {
            helper.close()
            context.deleteDatabase("migration_probe.db")
        }
    }

    @Test
    fun `migration chain is contiguous and reaches current version`() {
        val chain = Migrations.ALL.toList()
        assertEquals(1, chain.first().startVersion)
        for (i in 0 until chain.size - 1) {
            assertEquals("迁移必须首尾相接", chain[i].endVersion, chain[i + 1].startVersion)
        }
        assertEquals(LanWeixiDatabase.VERSION, chain.last().endVersion)
        assertEquals(LanWeixiDatabase.VERSION - 1, chain.size)
    }
}