package com.lanweixi.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lanweixi.core.data.dao.CharacterDao
import com.lanweixi.core.data.dao.DiaryDao
import com.lanweixi.core.data.dao.EventDao
import com.lanweixi.core.data.dao.FeedDao
import com.lanweixi.core.data.dao.KvDao
import com.lanweixi.core.data.dao.MemoryDao
import com.lanweixi.core.data.dao.MessageDao
import com.lanweixi.core.data.dao.ModelDao
import com.lanweixi.core.data.dao.NoteDao
import com.lanweixi.core.data.dao.PromptDao
import com.lanweixi.core.data.dao.TaskDao
import com.lanweixi.core.data.dao.DocumentDao
import com.lanweixi.core.data.dao.DraftDao
import com.lanweixi.core.data.dao.ReviewDao
import com.lanweixi.core.data.dao.SummaryDao
import com.lanweixi.core.data.dao.VitalsSampleDao
import com.lanweixi.core.data.entity.CharacterEntity
import com.lanweixi.core.data.entity.DiaryEntity
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.entity.FeedEntity
import com.lanweixi.core.data.entity.KvEntity
import com.lanweixi.core.data.entity.MemoryEntity
import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.entity.ModelEntity
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.entity.DocChunkEntity
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.SessionSummaryEntity
import com.lanweixi.core.data.entity.VitalsSampleEntity

/**
 * schema 通过 KSP 导出到 schemas/。
 * v1 → v2 增加 notes 表（见 DataModule.MIGRATION_1_2），旧数据保留。
 */
@Database(
    entities = [
        MessageEntity::class,
        MemoryEntity::class,
        CharacterEntity::class,
        ModelEntity::class,
        VitalsSampleEntity::class,
        DiaryEntity::class,
        FeedEntity::class,
        EventEntity::class,
        KvEntity::class,
        NoteEntity::class,
        PromptEntity::class,
        TaskEntity::class,
        DocumentEntity::class,
        DocChunkEntity::class,
        ReviewEntity::class,
        DraftEntity::class,
        SessionSummaryEntity::class,
    ],
    version = LanWeixiDatabase.VERSION,
    exportSchema = true,
)
abstract class LanWeixiDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun characterDao(): CharacterDao
    abstract fun modelDao(): ModelDao
    abstract fun vitalsSampleDao(): VitalsSampleDao
    abstract fun diaryDao(): DiaryDao
    abstract fun feedDao(): FeedDao
    abstract fun eventDao(): EventDao
    abstract fun kvDao(): KvDao

    abstract fun noteDao(): NoteDao

    abstract fun promptDao(): PromptDao

    abstract fun taskDao(): TaskDao

    abstract fun documentDao(): DocumentDao

    abstract fun reviewDao(): ReviewDao

    abstract fun draftDao(): DraftDao

    abstract fun summaryDao(): SummaryDao

    companion object {
        /** v1：聊天/记忆/角色/模型/状态/日记/动态/事件/kv；v2：notes；v7：messages 加引用两列；v8：drafts；v9：drafts 加 sourceText；v10：session_summaries。 */
        const val VERSION = 10
        const val NAME = "lanweixi.db"
    }
}
