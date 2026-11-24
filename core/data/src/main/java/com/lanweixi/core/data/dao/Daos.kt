package com.lanweixi.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import com.lanweixi.core.data.entity.DocChunkEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.SessionSummaryEntity
import com.lanweixi.core.data.entity.VitalsSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun listBySession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("SELECT sessionId, COUNT(*) AS cnt FROM messages GROUP BY sessionId ORDER BY MAX(createdAt) DESC")
    suspend fun sessionCounts(): List<SessionCount>

    /** P105：所有会话的最近消息（盲评用）。 */
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentAll(limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    /** P211：备份用——全量导出与整体替换。 */
    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun listAllForBackup(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    /**
     * P142：会话摘要——给「会话列表」用。
     *
     * 只靠 id 分不出话题，这里把**首条用户消息**当话题标签，
     * 再带最后活动时间，用户才能判断哪个会话该重开。
     */
    @Query(
        "SELECT m.sessionId AS sessionId, COUNT(*) AS messageCount, MAX(m.createdAt) AS lastAt, " +
            "(SELECT text FROM messages m2 WHERE m2.sessionId = m.sessionId AND m2.role = 'user' " +
            "ORDER BY m2.id ASC LIMIT 1) AS preview " +
            "FROM messages m GROUP BY m.sessionId ORDER BY lastAt DESC"
    )
    suspend fun sessionDigest(): List<SessionDigest>
}

/** P37：会话聚合（消息数），供会话列表使用。 */
data class SessionCount(
    val sessionId: String,
    val cnt: Int,
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Insert
    suspend fun insertAll(memories: List<MemoryEntity>): List<Long>

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE charId = :charId ORDER BY createdAt DESC")
    suspend fun listByChar(charId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE charId = :charId ORDER BY createdAt DESC")
    fun observeByChar(charId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: Long): MemoryEntity?

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM memories WHERE charId = :charId")
    suspend fun countByChar(charId: String): Int
}

@Dao
interface CharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity)

    @Query("SELECT * FROM characters")
    suspend fun list(): List<CharacterEntity>

    @Query("SELECT * FROM characters")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    suspend fun list(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ModelEntity?

    @Query("UPDATE models SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE models SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM models")
    suspend fun count(): Int
}

@Dao
interface VitalsSampleDao {
    @Insert
    suspend fun insert(sample: VitalsSampleEntity): Long

    @Query("SELECT * FROM vitals_samples WHERE at BETWEEN :from AND :to ORDER BY at ASC")
    suspend fun listBetween(from: Long, to: Long): List<VitalsSampleEntity>

    @Query("DELETE FROM vitals_samples WHERE at < :before")
    suspend fun deleteBefore(before: Long)

    @Query("SELECT COUNT(*) FROM vitals_samples")
    suspend fun count(): Int
}

@Dao
interface DiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(diary: DiaryEntity)

    @Query("SELECT * FROM diary WHERE dayKey = :dayKey LIMIT 1")
    suspend fun getByDay(dayKey: String): DiaryEntity?

    @Query("SELECT * FROM diary ORDER BY createdAt DESC")
    suspend fun list(): List<DiaryEntity>

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM diary")
    suspend fun count(): Int
}

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feed: FeedEntity)

    @Query("SELECT * FROM feeds ORDER BY importedAt DESC")
    suspend fun list(): List<FeedEntity>

    @Query("UPDATE feeds SET analyzed = 1 WHERE id = :id")
    suspend fun markAnalyzed(id: String)

    @Query("SELECT COUNT(*) FROM feeds WHERE analyzed = 0")
    suspend fun unanalyzedCount(): Int

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT * FROM events ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EventEntity>

    /** P211：按类型与时间窗取事件（用量汇总用）。 */
    @Query("SELECT * FROM events WHERE type = :type AND at BETWEEN :from AND :to ORDER BY at ASC")
    suspend fun listByTypeBetween(type: String, from: Long, to: Long): List<EventEntity>

    @Query("DELETE FROM events WHERE at < :before")
    suspend fun deleteBefore(before: Long)

    /** P217：只保留最近 [keep] 条（events 只增不删的兜底）。 */
    @Query(
        "DELETE FROM events WHERE id NOT IN " +
            "(SELECT id FROM events ORDER BY at DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimToLatest(keep: Int)

    /**
     * P217：历史事件里的用户原文脱敏（幂等）。
     *
     * 旧版本的 `conversation` payload 存的是用户消息前 40 字、`task_done` 存任务标题。
     * 这些现在都改成 `chars=N`；存量数据在启动清理时顺手改写，避免原文一直留在库里。
     */
    @Query(
        "UPDATE events SET payload = 'chars=' || length(payload) " +
            "WHERE type = 'conversation' AND payload NOT LIKE 'chars=%'"
    )
    suspend fun redactConversationPayloads()

    @Query(
        "UPDATE events SET payload = 'chars=' || length(payload) " +
            "WHERE type = 'task_done' AND payload NOT LIKE 'chars=%'"
    )
    suspend fun redactTaskDonePayloads()

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}

@Dao
interface KvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: KvEntity)

    @Query("SELECT value FROM kv WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM kv WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM kv")
    suspend fun count(): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY updatedAt DESC")
    suspend fun listActive(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updatedAt DESC")
    suspend fun listArchived(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Query(
        "SELECT * FROM notes WHERE archived = 0 AND (" +
            "title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'" +
            ") ORDER BY updatedAt DESC"
    )
    suspend fun search(query: String): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun listAllForBackup(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notes WHERE archived = 0")
    suspend fun countActive(): Int
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY pinned DESC, updatedAt DESC")
    suspend fun listAll(): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE category = :category ORDER BY pinned DESC, updatedAt DESC")
    suspend fun listByCategory(category: String): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun byId(id: Long): PromptEntity?

    @Query(
        "SELECT * FROM prompts WHERE " +
            "title LIKE '%' || :query || '%' OR positive LIKE '%' || :query || '%' OR " +
            "negative LIKE '%' || :query || '%' OR memo LIKE '%' || :query || '%' " +
            "ORDER BY pinned DESC, updatedAt DESC"
    )
    suspend fun search(query: String): List<PromptEntity>

    @Insert
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE prompts SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int
}

@Dao
interface TaskDao {
    /** 待办优先：有截止的按截止日近的在前，无截止的按优先级，最后按创建时间。 */
    @Query(
        "SELECT * FROM tasks WHERE done = 0 ORDER BY " +
            "CASE WHEN dueAt = 0 THEN 1 ELSE 0 END ASC, dueAt ASC, priority DESC, createdAt DESC"
    )
    suspend fun listOpen(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 1 ORDER BY doneAt DESC")
    suspend fun listDone(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET done = :done, doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long, now: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun listAllForBackup(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tasks WHERE done = 0")
    suspend fun countOpen(): Int
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    suspend fun listAll(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    /** P216：备份导入会整表替换 documents，旧分块必须一起清（否则旧分块挂到新文档上）。 */
    @Query("DELETE FROM doc_chunks")
    suspend fun deleteAllChunks()

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Insert
    suspend fun insertChunks(chunks: List<DocChunkEntity>)

    @Query("SELECT * FROM doc_chunks WHERE docId = :docId ORDER BY seq ASC")
    suspend fun chunksOf(docId: Long): List<DocChunkEntity>

    @Query("DELETE FROM doc_chunks WHERE docId = :docId")
    suspend fun deleteChunksOf(docId: Long)

    /** 标题或任意分块命中即算命中。 */
    @Query(
        "SELECT DISTINCT d.* FROM documents d LEFT JOIN doc_chunks c ON c.docId = d.id " +
            "WHERE d.title LIKE '%' || :query || '%' OR d.fileName LIKE '%' || :query || '%' " +
            "OR c.text LIKE '%' || :query || '%' ORDER BY d.importedAt DESC"
    )
    suspend fun search(query: String): List<DocumentEntity>

    /** 返回第一条命中的分块，用于列表里展示上下文。 */
    @Query("SELECT * FROM doc_chunks WHERE docId = :docId AND text LIKE '%' || :query || '%' ORDER BY seq ASC LIMIT 1")
    suspend fun firstHitChunk(docId: Long, query: String): DocChunkEntity?
}

/**
 * 复盘用的跨表汇总。
 *
 * 全部按「区间内」查，区间由调用方算好（本周 / 上周 / 本月）。
 */
@Dao
interface ReviewDao {
    @Query("DELETE FROM reviews")
    suspend fun deleteAll()

    @Query("SELECT * FROM reviews ORDER BY periodStart DESC")
    suspend fun listAll(): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun byId(id: Long): ReviewEntity?

    @Insert
    suspend fun insert(review: ReviewEntity): Long

    @Update
    suspend fun update(review: ReviewEntity)

    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE createdAt BETWEEN :from AND :to")
    suspend fun notesCreatedCount(from: Long, to: Long): Int

    @Query("SELECT * FROM notes WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt ASC")
    suspend fun notesCreated(from: Long, to: Long): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE createdAt BETWEEN :from AND :to")
    suspend fun tasksCreatedCount(from: Long, to: Long): Int

    @Query("SELECT * FROM tasks WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt ASC")
    suspend fun tasksCreated(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 1 AND doneAt BETWEEN :from AND :to ORDER BY doneAt ASC")
    suspend fun tasksDone(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM prompts WHERE createdAt BETWEEN :from AND :to")
    suspend fun promptsAddedCount(from: Long, to: Long): Int

    @Query("SELECT * FROM prompts WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt ASC")
    suspend fun promptsAdded(from: Long, to: Long): List<PromptEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE importedAt BETWEEN :from AND :to")
    suspend fun documentsAddedCount(from: Long, to: Long): Int

    /** P208：这段时间 AI 提了几条草稿、采纳了几条。 */
    @Query("SELECT COUNT(*) FROM drafts WHERE createdAt BETWEEN :from AND :to")
    suspend fun draftsProposedCount(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM drafts WHERE status = 'approved' AND createdAt BETWEEN :from AND :to")
    suspend fun draftsApprovedCount(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM drafts WHERE status = 'rejected' AND createdAt BETWEEN :from AND :to")
    suspend fun draftsRejectedCount(from: Long, to: Long): Int

    @Query("SELECT * FROM documents WHERE importedAt BETWEEN :from AND :to ORDER BY importedAt ASC")
    suspend fun documentsAdded(from: Long, to: Long): List<DocumentEntity>
}

/** 一次性汇总，供复盘页展示。 */
data class ReviewSnapshot(
    val periodStart: Long,
    val periodEnd: Long,
    val notes: List<NoteEntity>,
    val tasksCreated: List<TaskEntity>,
    val tasksDone: List<TaskEntity>,
    val prompts: List<PromptEntity>,
    val documents: List<DocumentEntity>,
    /** P208：AI 提议 / 采纳 / 驳回的草稿数。 */
    val draftsProposed: Int = 0,
    val draftsApproved: Int = 0,
    val draftsRejected: Int = 0,
) {
    val isEmpty: Boolean
        get() = notes.isEmpty() && tasksCreated.isEmpty() && tasksDone.isEmpty() &&
            prompts.isEmpty() && documents.isEmpty() && draftsProposed == 0
}

/** P142：会话列表的一行。 */
data class SessionDigest(
    val sessionId: String,
    val messageCount: Int,
    val lastAt: Long,
    val preview: String?,
)

/**
 * P206：待审核草稿。
 *
 * 只按状态查询，不做模糊搜索——草稿是短期待办，用户一次只会看十几条。
 * [markReviewed] 一次写完 status / targetId / reviewedAt，避免三次 UPDATE 的中间态。
 */
@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE status = 'pending' ORDER BY createdAt DESC, id DESC")
    suspend fun listPending(): List<DraftEntity>

    @Query(
        "SELECT * FROM drafts WHERE status != 'pending' " +
            "ORDER BY reviewedAt DESC, id DESC LIMIT :limit",
    )
    suspend fun listReviewed(limit: Int): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun byId(id: Long): DraftEntity?

    @Query("SELECT COUNT(*) FROM drafts WHERE status = 'pending'")
    suspend fun countPending(): Int

    @Insert
    suspend fun insert(draft: DraftEntity): Long

    @Update
    suspend fun update(draft: DraftEntity)

    @Query("UPDATE drafts SET status = :status, targetId = :targetId, reviewedAt = :now WHERE id = :id")
    suspend fun markReviewed(id: Long, status: String, targetId: Long, now: Long)

    /** P207：撤销确认——回到待审核，targetId 清空。 */
    @Query("UPDATE drafts SET status = 'pending', targetId = 0, reviewedAt = 0 WHERE id = :id")
    suspend fun markPending(id: Long)

    /** P207：确认前「修改」——只改内容字段，不动状态。 */
    @Query(
        "UPDATE drafts SET title = :title, body = :body, tags = :tags, " +
            "dueAt = :dueAt, priority = :priority WHERE id = :id",
    )
    suspend fun updateContent(
        id: Long,
        title: String,
        body: String,
        tags: String,
        dueAt: Long,
        priority: Int,
    )

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * P210：会话摘要。
 *
 * 每个会话只有一条（滚动覆盖），所以「最新一条」就是当前全部摘要。
 */
@Dao
interface SummaryDao {
    @Query(
        "SELECT * FROM session_summaries WHERE sessionId = :sessionId " +
            "ORDER BY untilId DESC LIMIT 1",
    )
    suspend fun latest(sessionId: String): SessionSummaryEntity?

    @Query("SELECT * FROM session_summaries ORDER BY sessionId ASC")
    suspend fun listAll(): List<SessionSummaryEntity>

    @Query("SELECT COUNT(*) FROM session_summaries")
    suspend fun count(): Int

    @Insert
    suspend fun insert(row: SessionSummaryEntity): Long

    @Update
    suspend fun update(row: SessionSummaryEntity)

    @Query("DELETE FROM session_summaries WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM session_summaries")
    suspend fun deleteAll()
}
