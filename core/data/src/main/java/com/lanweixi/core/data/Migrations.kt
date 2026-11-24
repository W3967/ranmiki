package com.lanweixi.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 已发布的 schema 迁移。
 *
 * 规则：**只增不改**。每次升 [LanWeixiDatabase.VERSION] 都在这里补一条，
 * 并同步更新 `schemas/<version>.json`（KSP 导出）。
 */
object Migrations {

    /** v1 → v2：新增 workbench 的 `notes` 表。旧表与旧数据原样保留。 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`tags` TEXT NOT NULL, " +
                    "`archived` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
        }
    }

    /** v2 → v3：新增创作提示词表 `prompts`。 */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prompts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`positive` TEXT NOT NULL, " +
                    "`negative` TEXT NOT NULL, " +
                    "`params` TEXT NOT NULL, " +
                    "`memo` TEXT NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prompts_category` ON `prompts` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prompts_updatedAt` ON `prompts` (`updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prompts_pinned` ON `prompts` (`pinned`)")
        }
    }

    /** v3 → v4：新增任务表 `tasks`。 */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`detail` TEXT NOT NULL, " +
                    "`dueAt` INTEGER NOT NULL, " +
                    "`priority` INTEGER NOT NULL, " +
                    "`done` INTEGER NOT NULL, " +
                    "`doneAt` INTEGER NOT NULL, " +
                    "`noteRef` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_done` ON `tasks` (`done`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_priority` ON `tasks` (`priority`)")
        }
    }

    /** v4 → v5：新增 documents 与 doc_chunks 两张表。 */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `documents` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`charCount` INTEGER NOT NULL, " +
                    "`chunkCount` INTEGER NOT NULL, " +
                    "`importedAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_importedAt` ON `documents` (`importedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_kind` ON `documents` (`kind`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `doc_chunks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`docId` INTEGER NOT NULL, " +
                    "`seq` INTEGER NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`charCount` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doc_chunks_docId` ON `doc_chunks` (`docId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doc_chunks_docId_seq` ON `doc_chunks` (`docId`, `seq`)")
        }
    }

    /** v5 → v6：新增复盘表 `reviews`。 */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reviews` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`periodType` TEXT NOT NULL, " +
                    "`periodStart` INTEGER NOT NULL, " +
                    "`periodEnd` INTEGER NOT NULL, " +
                    "`summary` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_periodStart` ON `reviews` (`periodStart`)")
        }
    }

    /** v6 → v7：messages 加引用两列（quoteId 只用来定位，quotePreview 是落库快照）。 */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `quoteId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `quotePreview` TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v7 → v8：新增 AI 草稿表 `drafts`（待审核队列）。
     *
     * 独立表的好处：现有笔记 / 任务查询一行都不用改，驳回的草稿也不会
     * 在笔记搜索里留下痕迹。
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drafts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`body` TEXT NOT NULL, " +
                    "`tags` TEXT NOT NULL, " +
                    "`dueAt` INTEGER NOT NULL, " +
                    "`priority` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`sourceSessionId` TEXT NOT NULL, " +
                    "`sourceMessageId` INTEGER NOT NULL, " +
                    "`targetId` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drafts_status` ON `drafts` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drafts_createdAt` ON `drafts` (`createdAt`)")
        }
    }

    /**
     * v8 → v9：drafts 加 `sourceText`（触发草稿的用户原话节选）。
     *
     * 工具执行时当前消息还没落库，没有消息 id 可用；存文本才能让
     * 「点来源跳回会话」找到那条消息。
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `drafts` ADD COLUMN `sourceText` TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v9 → v10：新增 `session_summaries`（上下文压缩后的会话摘要）。
     *
     * 每个会话一条；`untilId` 记录覆盖到哪条消息，避免重复压缩。
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_summaries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sessionId` TEXT NOT NULL, " +
                    "`untilId` INTEGER NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`coveredCount` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_summaries_sessionId_untilId` " +
                    "ON `session_summaries` (`sessionId`, `untilId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_summaries_sessionId_createdAt` " +
                    "ON `session_summaries` (`sessionId`, `createdAt`)",
            )
        }
    }

    /** 全部迁移，按顺序喂给 Room 构建器。 */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
    )
}
