package com.lanweixi.core.data

import android.content.Context
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
import com.lanweixi.core.data.dao.ReviewDao
import com.lanweixi.core.data.dao.VitalsSampleDao
import com.lanweixi.core.data.repository.CharacterRepository
import com.lanweixi.core.data.repository.DiaryRepository
import com.lanweixi.core.data.repository.EventRepository
import com.lanweixi.core.data.repository.FeedRepository
import com.lanweixi.core.data.repository.KvRepository
import com.lanweixi.core.data.repository.MemoryRepository
import com.lanweixi.core.data.repository.MessageRepository
import com.lanweixi.core.data.repository.ModelRepository
import com.lanweixi.core.data.repository.RepositoryContainer
import com.lanweixi.core.data.repository.RoomCharacterRepository
import com.lanweixi.core.data.repository.RoomDiaryRepository
import com.lanweixi.core.data.repository.RoomEventRepository
import com.lanweixi.core.data.repository.RoomFeedRepository
import com.lanweixi.core.data.repository.RoomKvRepository
import com.lanweixi.core.data.repository.RoomMemoryRepository
import com.lanweixi.core.data.repository.RoomMessageRepository
import com.lanweixi.core.data.repository.RoomModelRepository
import com.lanweixi.core.data.repository.RoomVitalsRepository
import com.lanweixi.core.data.repository.VitalsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * T02 收尾：LanWeixiDatabase 的唯一来源。
 *
 * 版本与迁移见 [LanWeixiDatabase.VERSION] 与 [Migrations]；
 * 由 SingletonComponent 持有单例——调用方不得自行 new/建库。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LanWeixiDatabase =
        androidx.room.Room.databaseBuilder(
            context,
            LanWeixiDatabase::class.java,
            LanWeixiDatabase.NAME,
        ).addMigrations(*Migrations.ALL).build()

    @Provides
    fun provideNoteDao(db: LanWeixiDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideNoteRepository(dao: NoteDao): com.lanweixi.core.data.repository.NoteRepository =
        com.lanweixi.core.data.repository.RoomNoteRepository(dao)

    @Provides
    fun providePromptDao(db: LanWeixiDatabase): PromptDao = db.promptDao()

    @Provides
    fun providePromptRepository(dao: PromptDao): com.lanweixi.core.data.repository.PromptRepository =
        com.lanweixi.core.data.repository.RoomPromptRepository(dao)

    @Provides
    fun provideTaskDao(db: LanWeixiDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideTaskRepository(dao: TaskDao): com.lanweixi.core.data.repository.TaskRepository =
        com.lanweixi.core.data.repository.RoomTaskRepository(dao)

    @Provides
    fun provideDocumentDao(db: LanWeixiDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideDocumentRepository(dao: DocumentDao): com.lanweixi.core.data.repository.DocumentRepository =
        com.lanweixi.core.data.repository.RoomDocumentRepository(dao)

    @Provides
    fun provideReviewDao(db: LanWeixiDatabase): ReviewDao = db.reviewDao()

    @Provides
    fun provideReviewRepository(dao: ReviewDao): com.lanweixi.core.data.repository.ReviewRepository =
        com.lanweixi.core.data.repository.RoomReviewRepository(dao)

    /** P206：草稿（待审核）仓储。 */
    @Provides
    fun provideDraftDao(db: LanWeixiDatabase): com.lanweixi.core.data.dao.DraftDao = db.draftDao()

    @Provides
    fun provideDraftRepository(
        dao: com.lanweixi.core.data.dao.DraftDao,
    ): com.lanweixi.core.data.repository.DraftRepository =
        com.lanweixi.core.data.repository.RoomDraftRepository(dao)

    /** P210：会话摘要。 */
    @Provides
    fun provideSummaryDao(db: LanWeixiDatabase): com.lanweixi.core.data.dao.SummaryDao =
        db.summaryDao()

    @Provides
    fun provideSummaryRepository(
        dao: com.lanweixi.core.data.dao.SummaryDao,
    ): com.lanweixi.core.data.repository.SessionSummaryRepository =
        com.lanweixi.core.data.repository.RoomSessionSummaryRepository(dao)

    @Provides
    fun provideRepositoryContainer(db: LanWeixiDatabase): RepositoryContainer =
        RepositoryContainer(db)

    // ── Repository 绑定（接口 → Room 实现） ──────────────────
    @Provides
    fun provideMessageRepository(dao: MessageDao): MessageRepository = RoomMessageRepository(dao)

    @Provides
    fun provideMemoryRepository(dao: MemoryDao): MemoryRepository = RoomMemoryRepository(dao)

    @Provides
    fun provideCharacterRepository(dao: CharacterDao): CharacterRepository = RoomCharacterRepository(dao)

    @Provides
    fun provideModelRepository(dao: ModelDao): ModelRepository = RoomModelRepository(dao)

    @Provides
    fun provideVitalsRepository(dao: VitalsSampleDao): VitalsRepository = RoomVitalsRepository(dao)

    @Provides
    fun provideDiaryRepository(dao: DiaryDao): DiaryRepository = RoomDiaryRepository(dao)

    @Provides
    fun provideFeedRepository(dao: FeedDao): FeedRepository = RoomFeedRepository(dao)

    @Provides
    fun provideEventRepository(dao: EventDao): EventRepository = RoomEventRepository(dao)

    @Provides
    fun provideKvRepository(dao: KvDao): KvRepository = RoomKvRepository(dao)

    // ── P06b：引擎运行态（kv 承载，单例）──────────────────────

    @Provides
    fun provideMessageDao(db: LanWeixiDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: LanWeixiDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideCharacterDao(db: LanWeixiDatabase): CharacterDao = db.characterDao()

    @Provides
    fun provideModelDao(db: LanWeixiDatabase): ModelDao = db.modelDao()

    @Provides
    fun provideVitalsSampleDao(db: LanWeixiDatabase): VitalsSampleDao = db.vitalsSampleDao()

    @Provides
    fun provideDiaryDao(db: LanWeixiDatabase): DiaryDao = db.diaryDao()

    @Provides
    fun provideFeedDao(db: LanWeixiDatabase): FeedDao = db.feedDao()

    @Provides
    fun provideEventDao(db: LanWeixiDatabase): EventDao = db.eventDao()

    @Provides
    fun provideKvDao(db: LanWeixiDatabase): KvDao = db.kvDao()
}
