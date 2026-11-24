package com.lanweixi.core.data.repository

import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.dao.CharacterDao
import com.lanweixi.core.data.dao.DiaryDao
import com.lanweixi.core.data.dao.EventDao
import com.lanweixi.core.data.dao.FeedDao
import com.lanweixi.core.data.dao.KvDao
import com.lanweixi.core.data.dao.MemoryDao
import com.lanweixi.core.data.dao.MessageDao
import com.lanweixi.core.data.dao.ModelDao
import com.lanweixi.core.data.dao.VitalsSampleDao
import com.lanweixi.core.data.entity.CharacterEntity
import com.lanweixi.core.data.entity.DiaryEntity
import com.lanweixi.core.data.entity.EventEntity
import com.lanweixi.core.data.entity.FeedEntity
import com.lanweixi.core.data.entity.KvEntity
import com.lanweixi.core.data.entity.MemoryEntity
import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.entity.ModelEntity
import com.lanweixi.core.data.entity.VitalsSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 方案 3.2：UI 不直接引用 DAO，只能经 Repository 取数。
 * 接口集中在此，Room 实现紧随其后；后续可替换实现而不动调用方。
 */
interface MessageRepository {
    suspend fun append(message: MessageEntity): Long
    suspend fun update(message: MessageEntity)
    suspend fun history(sessionId: String): List<MessageEntity>
    /** P105：所有会话的最近消息（盲评用）。 */
    suspend fun recentAll(limit: Int): List<MessageEntity>
    fun observe(sessionId: String): Flow<List<MessageEntity>>
    suspend fun deleteSession(sessionId: String)
}

interface MemoryRepository {
    suspend fun remember(memory: MemoryEntity): Long
    suspend fun byCharacter(charId: String): List<MemoryEntity>
    suspend fun update(memory: MemoryEntity)
    suspend fun forget(id: Long)
    suspend fun count(charId: String): Int
}

interface CharacterRepository {
    suspend fun save(character: CharacterEntity)
    suspend fun list(): List<CharacterEntity>
    suspend fun get(id: String): CharacterEntity?
}

interface ModelRepository {
    suspend fun register(model: ModelEntity)
    suspend fun list(): List<ModelEntity>
    suspend fun active(): ModelEntity?
    suspend fun activate(id: String)
    suspend fun remove(id: String)
}

interface VitalsRepository {
    suspend fun record(sample: VitalsSampleEntity): Long
    suspend fun between(from: Long, to: Long): List<VitalsSampleEntity>
    suspend fun trimBefore(before: Long)
}

interface DiaryRepository {
    suspend fun save(diary: DiaryEntity)
    suspend fun byDay(dayKey: String): DiaryEntity?
    suspend fun list(): List<DiaryEntity>
}

interface FeedRepository {
    suspend fun save(feed: FeedEntity)
    suspend fun list(): List<FeedEntity>
    suspend fun markAnalyzed(id: String)
    suspend fun pendingCount(): Int
}

interface EventRepository {
    suspend fun record(event: EventEntity): Long
    suspend fun recent(limit: Int): List<EventEntity>

    /** P211：按类型与时间窗取（用量汇总）。 */
    suspend fun listByTypeBetween(type: String, from: Long, to: Long): List<EventEntity>

    /**
     * P217：清理旧事件（events 只增不删的兜底）。
     *
     * 先删 [before] 之前的，再只保留最近 [keepLatest] 条。
     */
    suspend fun trim(before: Long, keepLatest: Int)

    /** P217：把历史事件里的用户原文改成 `chars=N`（幂等）。 */
    suspend fun redactLegacyPayloads()
}

interface KvRepository {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}


interface NoteRepository {
    suspend fun listActive(): List<com.lanweixi.core.data.entity.NoteEntity>
    suspend fun listArchived(): List<com.lanweixi.core.data.entity.NoteEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.NoteEntity?
    suspend fun search(query: String): List<com.lanweixi.core.data.entity.NoteEntity>
    suspend fun save(note: com.lanweixi.core.data.entity.NoteEntity): Long
    suspend fun delete(note: com.lanweixi.core.data.entity.NoteEntity)
    suspend fun deleteById(id: Long)
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun countActive(): Int
}


interface PromptRepository {
    suspend fun listAll(): List<com.lanweixi.core.data.entity.PromptEntity>
    suspend fun listByCategory(category: String): List<com.lanweixi.core.data.entity.PromptEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.PromptEntity?
    suspend fun search(query: String): List<com.lanweixi.core.data.entity.PromptEntity>
    suspend fun save(prompt: com.lanweixi.core.data.entity.PromptEntity): Long
    suspend fun deleteById(id: Long)
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun count(): Int
}


interface TaskRepository {
    suspend fun listOpen(): List<com.lanweixi.core.data.entity.TaskEntity>
    suspend fun listDone(): List<com.lanweixi.core.data.entity.TaskEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.TaskEntity?
    suspend fun save(task: com.lanweixi.core.data.entity.TaskEntity): Long
    suspend fun setDone(id: Long, done: Boolean)
    suspend fun deleteById(id: Long)
    suspend fun countOpen(): Int
}


interface DocumentRepository {
    suspend fun listAll(): List<com.lanweixi.core.data.entity.DocumentEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.DocumentEntity?
    suspend fun chunksOf(docId: Long): List<com.lanweixi.core.data.entity.DocChunkEntity>
    suspend fun search(query: String): List<com.lanweixi.core.data.entity.DocumentEntity>
    suspend fun firstHit(docId: Long, query: String): com.lanweixi.core.data.entity.DocChunkEntity?
    suspend fun import(
        title: String,
        fileName: String,
        kind: String,
        chunks: List<String>,
    ): Long
    suspend fun deleteById(id: Long)
    suspend fun count(): Int
}


interface ReviewRepository {
    suspend fun listAll(): List<com.lanweixi.core.data.entity.ReviewEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.ReviewEntity?
    suspend fun snapshot(periodStart: Long, periodEnd: Long): com.lanweixi.core.data.dao.ReviewSnapshot
    suspend fun save(review: com.lanweixi.core.data.entity.ReviewEntity): Long
    suspend fun deleteById(id: Long)
}


/**
 * P206：AI 草稿（待审核）。
 *
 * [propose] 只负责落库，不碰正式表；变成笔记/任务由 DraftReviewer 做。
 */
interface DraftRepository {
    suspend fun listPending(): List<com.lanweixi.core.data.entity.DraftEntity>
    suspend fun listReviewed(limit: Int = 30): List<com.lanweixi.core.data.entity.DraftEntity>
    suspend fun byId(id: Long): com.lanweixi.core.data.entity.DraftEntity?
    suspend fun propose(draft: com.lanweixi.core.data.entity.DraftEntity): Long
    suspend fun markApproved(id: Long, targetId: Long)
    suspend fun markRejected(id: Long)

    /** P207：撤销确认，草稿回待审核。 */
    suspend fun markPending(id: Long)

    /** P207：确认前修改内容（标题 / 正文 / 标签 / 截止 / 优先级）。 */
    suspend fun updateContent(
        id: Long,
        title: String,
        body: String,
        tags: String,
        dueAt: Long,
        priority: Int,
    )

    suspend fun countPending(): Int
    suspend fun deleteById(id: Long)
}


/**
 * P210：会话摘要（上下文压缩产物）。
 *
 * 每个会话只存一条：`save` 时已有就覆盖（滚动摘要），没有就新建。
 */
interface SessionSummaryRepository {
    suspend fun latest(sessionId: String): com.lanweixi.core.data.entity.SessionSummaryEntity?
    suspend fun listAll(): List<com.lanweixi.core.data.entity.SessionSummaryEntity>
    suspend fun count(): Int
    suspend fun save(
        sessionId: String,
        untilId: Long,
        content: String,
        coveredCount: Int,
    ): Long
    suspend fun deleteBySession(sessionId: String)
}

class RoomMessageRepository(private val dao: MessageDao) : MessageRepository {
    override suspend fun append(message: MessageEntity) = dao.insert(message)
    override suspend fun update(message: MessageEntity) = dao.update(message)
    override suspend fun history(sessionId: String) = dao.listBySession(sessionId)
    override suspend fun recentAll(limit: Int) = dao.recentAll(limit)
    override fun observe(sessionId: String) = dao.observeBySession(sessionId)
    override suspend fun deleteSession(sessionId: String) = dao.deleteBySession(sessionId)
}

class RoomMemoryRepository(private val dao: MemoryDao) : MemoryRepository {
    override suspend fun remember(memory: MemoryEntity) = dao.insert(memory)
    override suspend fun byCharacter(charId: String) = dao.listByChar(charId)
    override suspend fun update(memory: MemoryEntity) = dao.update(memory)
    override suspend fun forget(id: Long) = dao.deleteById(id)
    override suspend fun count(charId: String) = dao.countByChar(charId)
}

class RoomCharacterRepository(private val dao: CharacterDao) : CharacterRepository {
    override suspend fun save(character: CharacterEntity) = dao.upsert(character)
    override suspend fun list() = dao.list()
    override suspend fun get(id: String) = dao.getById(id)
}

class RoomModelRepository(private val dao: ModelDao) : ModelRepository {
    override suspend fun register(model: ModelEntity) = dao.upsert(model)
    override suspend fun list() = dao.list()
    override suspend fun active() = dao.getActive()
    override suspend fun activate(id: String) {
        dao.clearActive()
        dao.setActive(id)
    }
    override suspend fun remove(id: String) = dao.deleteById(id)
}

class RoomVitalsRepository(private val dao: VitalsSampleDao) : VitalsRepository {
    override suspend fun record(sample: VitalsSampleEntity) = dao.insert(sample)
    override suspend fun between(from: Long, to: Long) = dao.listBetween(from, to)
    override suspend fun trimBefore(before: Long) = dao.deleteBefore(before)
}

class RoomDiaryRepository(private val dao: DiaryDao) : DiaryRepository {
    override suspend fun save(diary: DiaryEntity) = dao.upsert(diary)
    override suspend fun byDay(dayKey: String) = dao.getByDay(dayKey)
    override suspend fun list() = dao.list()
}

class RoomFeedRepository(private val dao: FeedDao) : FeedRepository {
    override suspend fun save(feed: FeedEntity) = dao.upsert(feed)
    override suspend fun list() = dao.list()
    override suspend fun markAnalyzed(id: String) = dao.markAnalyzed(id)
    override suspend fun pendingCount() = dao.unanalyzedCount()
}

class RoomEventRepository(private val dao: EventDao) : EventRepository {
    override suspend fun record(event: EventEntity) = dao.insert(event)
    override suspend fun recent(limit: Int) = dao.recent(limit)
    override suspend fun listByTypeBetween(type: String, from: Long, to: Long) =
        dao.listByTypeBetween(type, from, to)

    override suspend fun trim(before: Long, keepLatest: Int) {
        dao.deleteBefore(before)
        dao.trimToLatest(keepLatest)
    }

    override suspend fun redactLegacyPayloads() {
        dao.redactConversationPayloads()
        dao.redactTaskDonePayloads()
    }
}

class RoomKvRepository(private val dao: KvDao) : KvRepository {
    override suspend fun put(key: String, value: String) = dao.put(KvEntity(key, value))
    override suspend fun get(key: String) = dao.get(key)
    override suspend fun remove(key: String) = dao.delete(key)
}


class RoomNoteRepository(
    private val dao: com.lanweixi.core.data.dao.NoteDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : NoteRepository {
    override suspend fun listActive() = dao.listActive()
    override suspend fun listArchived() = dao.listArchived()
    override suspend fun byId(id: Long) = dao.byId(id)
    override suspend fun search(query: String) = dao.search(query)
    override suspend fun save(note: com.lanweixi.core.data.entity.NoteEntity): Long {
        val now = clock()
        return if (note.id == 0L) {
            dao.insert(note.copy(createdAt = note.createdAt.takeIf { it > 0 } ?: now, updatedAt = now))
        } else {
            dao.update(note.copy(updatedAt = now))
            note.id
        }
    }
    override suspend fun delete(note: com.lanweixi.core.data.entity.NoteEntity) = dao.delete(note)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, clock())
    override suspend fun countActive() = dao.countActive()
}


class RoomPromptRepository(
    private val dao: com.lanweixi.core.data.dao.PromptDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PromptRepository {
    override suspend fun listAll() = dao.listAll()
    override suspend fun listByCategory(category: String) = dao.listByCategory(category)
    override suspend fun byId(id: Long) = dao.byId(id)
    override suspend fun search(query: String) = dao.search(query)

    override suspend fun save(prompt: com.lanweixi.core.data.entity.PromptEntity): Long {
        val now = clock()
        return if (prompt.id == 0L) {
            dao.insert(prompt.copy(createdAt = prompt.createdAt.takeIf { it > 0 } ?: now, updatedAt = now))
        } else {
            dao.update(prompt.copy(updatedAt = now))
            prompt.id
        }
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned, clock())
    override suspend fun count() = dao.count()
}


class RoomTaskRepository(
    private val dao: com.lanweixi.core.data.dao.TaskDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TaskRepository {
    override suspend fun listOpen() = dao.listOpen()
    override suspend fun listDone() = dao.listDone()
    override suspend fun byId(id: Long) = dao.byId(id)

    override suspend fun save(task: com.lanweixi.core.data.entity.TaskEntity): Long {
        val now = clock()
        return if (task.id == 0L) {
            dao.insert(task.copy(createdAt = task.createdAt.takeIf { it > 0 } ?: now, updatedAt = now))
        } else {
            dao.update(task.copy(updatedAt = now))
            task.id
        }
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        val now = clock()
        dao.setDone(id, done, if (done) now else 0L, now)
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun countOpen() = dao.countOpen()
}


class RoomDocumentRepository(
    private val dao: com.lanweixi.core.data.dao.DocumentDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DocumentRepository {
    override suspend fun listAll() = dao.listAll()
    override suspend fun byId(id: Long) = dao.byId(id)
    override suspend fun chunksOf(docId: Long) = dao.chunksOf(docId)
    override suspend fun search(query: String) = dao.search(query)
    override suspend fun firstHit(docId: Long, query: String) = dao.firstHitChunk(docId, query)
    override suspend fun count() = dao.count()

    /** 文档与分块必须一起落库，所以先插文档拿到 id 再批量插块。 */
    override suspend fun import(
        title: String,
        fileName: String,
        kind: String,
        chunks: List<String>,
    ): Long {
        val now = clock()
        val docId = dao.insert(
            com.lanweixi.core.data.entity.DocumentEntity(
                title = title,
                fileName = fileName,
                kind = kind,
                charCount = chunks.sumOf { it.length },
                chunkCount = chunks.size,
                importedAt = now,
                updatedAt = now,
            ),
        )
        if (chunks.isNotEmpty()) {
            dao.insertChunks(
                chunks.mapIndexed { index, text ->
                    com.lanweixi.core.data.entity.DocChunkEntity(
                        docId = docId,
                        seq = index,
                        text = text,
                        charCount = text.length,
                    )
                },
            )
        }
        return docId
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteChunksOf(id)
        dao.deleteById(id)
    }
}


class RoomReviewRepository(
    private val dao: com.lanweixi.core.data.dao.ReviewDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ReviewRepository {
    override suspend fun listAll() = dao.listAll()
    override suspend fun byId(id: Long) = dao.byId(id)

    override suspend fun snapshot(periodStart: Long, periodEnd: Long) =
        com.lanweixi.core.data.dao.ReviewSnapshot(
            periodStart = periodStart,
            periodEnd = periodEnd,
            notes = dao.notesCreated(periodStart, periodEnd),
            tasksCreated = dao.tasksCreated(periodStart, periodEnd),
            tasksDone = dao.tasksDone(periodStart, periodEnd),
            prompts = dao.promptsAdded(periodStart, periodEnd),
            documents = dao.documentsAdded(periodStart, periodEnd),
            draftsProposed = dao.draftsProposedCount(periodStart, periodEnd),
            draftsApproved = dao.draftsApprovedCount(periodStart, periodEnd),
            draftsRejected = dao.draftsRejectedCount(periodStart, periodEnd),
        )

    override suspend fun save(review: com.lanweixi.core.data.entity.ReviewEntity): Long {
        val now = clock()
        return if (review.id == 0L) {
            dao.insert(review.copy(createdAt = review.createdAt.takeIf { it > 0 } ?: now, updatedAt = now))
        } else {
            dao.update(review.copy(updatedAt = now))
            review.id
        }
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

/**
 * P206：草稿仓储。
 *
 * [propose] 补时间戳与默认状态；状态流转只允许 pending → approved / rejected，
 * 已处理的草稿再次提交由 DraftReviewer 拦下（这里不管，免得仓储藏业务判断）。
 */
class RoomDraftRepository(
    private val dao: com.lanweixi.core.data.dao.DraftDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DraftRepository {
    override suspend fun listPending() = dao.listPending()
    override suspend fun listReviewed(limit: Int) = dao.listReviewed(limit)
    override suspend fun byId(id: Long) = dao.byId(id)
    override suspend fun countPending() = dao.countPending()
    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun propose(draft: com.lanweixi.core.data.entity.DraftEntity): Long {
        val now = clock()
        return dao.insert(
            draft.copy(
                status = com.lanweixi.core.data.entity.DraftStatus.PENDING,
                createdAt = draft.createdAt.takeIf { it > 0 } ?: now,
                reviewedAt = 0L,
                targetId = 0L,
            ),
        )
    }

    override suspend fun markApproved(id: Long, targetId: Long) =
        dao.markReviewed(id, com.lanweixi.core.data.entity.DraftStatus.APPROVED, targetId, clock())

    override suspend fun markRejected(id: Long) =
        dao.markReviewed(id, com.lanweixi.core.data.entity.DraftStatus.REJECTED, 0L, clock())

    override suspend fun markPending(id: Long) = dao.markPending(id)

    override suspend fun updateContent(
        id: Long,
        title: String,
        body: String,
        tags: String,
        dueAt: Long,
        priority: Int,
    ) = dao.updateContent(id, title, body, tags, dueAt, priority)
}

/** P210：会话摘要仓储（每会话一条，滚动覆盖）。 */
class RoomSessionSummaryRepository(
    private val dao: com.lanweixi.core.data.dao.SummaryDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SessionSummaryRepository {
    override suspend fun latest(sessionId: String) = dao.latest(sessionId)
    override suspend fun listAll() = dao.listAll()
    override suspend fun count() = dao.count()
    override suspend fun deleteBySession(sessionId: String) = dao.deleteBySession(sessionId)

    override suspend fun save(
        sessionId: String,
        untilId: Long,
        content: String,
        coveredCount: Int,
    ): Long {
        val now = clock()
        val existing = dao.latest(sessionId)
        return if (existing == null) {
            dao.insert(
                com.lanweixi.core.data.entity.SessionSummaryEntity(
                    sessionId = sessionId,
                    untilId = untilId,
                    content = content,
                    coveredCount = coveredCount,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    untilId = untilId,
                    content = content,
                    coveredCount = coveredCount,
                    updatedAt = now,
                ),
            )
            existing.id
        }
    }
}

/** 把数据库装配成一组 Repository，app 层只需持有这个容器。 */
class RepositoryContainer(db: LanWeixiDatabase) {
    val messages: MessageRepository = RoomMessageRepository(db.messageDao())
    val memories: MemoryRepository = RoomMemoryRepository(db.memoryDao())
    val characters: CharacterRepository = RoomCharacterRepository(db.characterDao())
    val models: ModelRepository = RoomModelRepository(db.modelDao())
    val vitals: VitalsRepository = RoomVitalsRepository(db.vitalsSampleDao())
    val diary: DiaryRepository = RoomDiaryRepository(db.diaryDao())
    val feeds: FeedRepository = RoomFeedRepository(db.feedDao())
    val events: EventRepository = RoomEventRepository(db.eventDao())
    val kv: KvRepository = RoomKvRepository(db.kvDao())

    val notes: NoteRepository = RoomNoteRepository(db.noteDao())
    val prompts: PromptRepository = RoomPromptRepository(db.promptDao())
    val tasks: TaskRepository = RoomTaskRepository(db.taskDao())
    val documents: DocumentRepository = RoomDocumentRepository(db.documentDao())
    val reviews: ReviewRepository = RoomReviewRepository(db.reviewDao())
    val drafts: DraftRepository = RoomDraftRepository(db.draftDao())
    val summaries: SessionSummaryRepository = RoomSessionSummaryRepository(db.summaryDao())
}
