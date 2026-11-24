package com.lanweixi.core.data.draft

import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.entity.DraftStatus
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.TaskEntity
import com.lanweixi.core.data.repository.DraftRepository
import com.lanweixi.core.data.repository.NoteRepository
import com.lanweixi.core.data.repository.TaskRepository

/**
 * P206：审核一条草稿的结果。
 *
 * 用 sealed 而不是布尔：UI 要能说「已存成任务」还是「驳回」，
 * 也要能把失败原因原样摆给用户看。
 */
sealed interface ReviewOutcome {
    /** 写成了笔记，`noteId` 是笔记 id。 */
    data class NoteSaved(val draftId: Long, val noteId: Long) : ReviewOutcome

    /** 写成了任务，`taskId` 是任务 id。 */
    data class TaskSaved(val draftId: Long, val taskId: Long) : ReviewOutcome

    /** 用户驳回，什么都没写。 */
    data class Rejected(val draftId: Long) : ReviewOutcome

    /** P207：撤销了确认——正式记录已删除，草稿回到待审核。 */
    data class Undone(val draftId: Long) : ReviewOutcome

    /** 没处理成（草稿不存在 / 已处理过 / 写库失败）。 */
    data class Failed(val draftId: Long, val message: String) : ReviewOutcome
}

/**
 * 审核动作的最小接口。
 *
 * ViewModel 只依赖它，单测可以用假实现；真正的写库规则在 [DraftReviewer]。
 */
interface DraftReview {
    suspend fun approve(id: Long): ReviewOutcome

    suspend fun reject(id: Long): ReviewOutcome

    /** 批量确认；单条失败不中断，失败项也在返回里。 */
    suspend fun approveAll(): List<ReviewOutcome>

    /** P207：先改内容再确认（改的是草稿本身，确认后内容一致）。 */
    suspend fun approveEdited(
        id: Long,
        title: String,
        body: String,
        tags: String,
        dueAt: Long,
        priority: Int,
    ): ReviewOutcome

    /** P207：撤销已确认的草稿——删掉写进去的记录，草稿回到待审核。 */
    suspend fun undo(id: Long): ReviewOutcome
}

/**
 * P206：把草稿变成正式数据。
 *
 * 规则：
 * - 只有 pending 能被处理，已处理的再点一次返回 [ReviewOutcome.Failed]（防止连点两次插两条）；
 * - note 走 notes（正文原样，标题空白时用正文首行兜底）；
 * - task 走 tasks（dueAt / priority 来自草稿）；
 * - 驳回只改状态，不删记录——「AI 提过什么、被驳回了」本身就是有用的信号。
 *
 * P216：**多步写库都包在事务里**。以前「插笔记 → 标草稿已确认」是两次独立写：
 * 进程在中途被杀会留下孤儿笔记，草稿仍是 pending，用户再点确认就插第二条。
 * 现在用 [transaction]（生产是 Room `withTransaction`）把它们绑成原子操作；
 * 默认直通实现让纯 JVM 单测不需要数据库。
 */
class DraftReviewer(
    private val drafts: DraftRepository,
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    /** P216：`transaction { ... }` 内所有写要么全成功、要么全回滚。 */
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
) : DraftReview {

    override suspend fun approve(id: Long): ReviewOutcome =
        inTransaction(id) { approveInTransaction(id) }

    override suspend fun reject(id: Long): ReviewOutcome =
        inTransaction(id) { rejectInTransaction(id) }

    override suspend fun approveAll(): List<ReviewOutcome> =
        drafts.listPending().map { approve(it.id) }

    override suspend fun approveEdited(
        id: Long,
        title: String,
        body: String,
        tags: String,
        dueAt: Long,
        priority: Int,
    ): ReviewOutcome = inTransaction(id) {
        val draft = drafts.byId(id)
            ?: return@inTransaction ReviewOutcome.Failed(id, "这条草稿已经不存在了")
        if (draft.status != DraftStatus.PENDING) {
            return@inTransaction ReviewOutcome.Failed(id, "这条已经处理过了")
        }
        // 改内容和确认必须同一事务：只改了内容没确认会留下「草稿变了、记录没变」的中间态
        drafts.updateContent(
            id = id,
            title = title.trim().take(TITLE_MAX_CHARS),
            body = body,
            tags = tags.trim(),
            dueAt = dueAt.coerceAtLeast(0L),
            priority = priority.coerceIn(0, 2),
        )
        approveInTransaction(id)
    }

    override suspend fun undo(id: Long): ReviewOutcome = inTransaction(id) {
        val draft = drafts.byId(id)
            ?: return@inTransaction ReviewOutcome.Failed(id, "这条草稿已经不存在了")
        if (draft.status != DraftStatus.APPROVED) {
            return@inTransaction ReviewOutcome.Failed(id, "只有已确认的草稿能撤销")
        }
        when (draft.kind) {
            DraftKind.NOTE -> if (draft.targetId > 0L) notes.deleteById(draft.targetId)
            DraftKind.TASK -> if (draft.targetId > 0L) tasks.deleteById(draft.targetId)
            else -> return@inTransaction ReviewOutcome.Failed(id, "不认识的草稿类型：" + draft.kind)
        }
        drafts.markPending(id)
        ReviewOutcome.Undone(id)
    }

    /** 真正写正式数据的那一步（调用方已在事务里）。 */
    private suspend fun approveInTransaction(id: Long): ReviewOutcome {
        val draft = drafts.byId(id)
            ?: return ReviewOutcome.Failed(id, "这条草稿已经不存在了")
        if (draft.status != DraftStatus.PENDING) {
            return ReviewOutcome.Failed(id, "这条已经处理过了")
        }
        return when (draft.kind) {
            DraftKind.NOTE -> {
                val noteId = notes.save(
                    NoteEntity(
                        title = titleOf(draft),
                        content = draft.body,
                        tags = draft.tags.trim(),
                        archived = false,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )
                drafts.markApproved(id, noteId)
                ReviewOutcome.NoteSaved(draft.id, noteId)
            }

            DraftKind.TASK -> {
                val taskId = tasks.save(
                    TaskEntity(
                        title = titleOf(draft),
                        detail = draft.body,
                        dueAt = draft.dueAt.coerceAtLeast(0L),
                        priority = draft.priority.coerceIn(0, 2),
                        done = false,
                        doneAt = 0L,
                        noteRef = 0L,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )
                drafts.markApproved(id, taskId)
                ReviewOutcome.TaskSaved(draft.id, taskId)
            }

            else -> ReviewOutcome.Failed(id, "不认识的草稿类型：" + draft.kind)
        }
    }

    private suspend fun rejectInTransaction(id: Long): ReviewOutcome {
        val draft = drafts.byId(id)
            ?: return ReviewOutcome.Failed(id, "这条草稿已经不存在了")
        if (draft.status != DraftStatus.PENDING) {
            return ReviewOutcome.Failed(id, "这条已经处理过了")
        }
        drafts.markRejected(id)
        return ReviewOutcome.Rejected(id)
    }

    /** 事务包装：写库异常一律变成可展示的失败结果（事务已回滚）。 */
    private suspend fun inTransaction(
        id: Long,
        block: suspend () -> ReviewOutcome,
    ): ReviewOutcome = try {
        var outcome: ReviewOutcome? = null
        transaction { outcome = block() }
        outcome ?: ReviewOutcome.Failed(id, "写入未完成")
    } catch (e: Exception) {
        ReviewOutcome.Failed(id, e.message ?: "写入失败")
    }

    /** 标题空白时用正文首行；再空给个占位，别让列表里出现空白行。 */
    private fun titleOf(draft: DraftEntity): String {
        val explicit = draft.title.trim()
        if (explicit.isNotEmpty()) return explicit.take(TITLE_MAX_CHARS)
        val firstLine = draft.body.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        return firstLine.take(TITLE_MAX_CHARS).ifBlank { "无标题" }
    }

    private companion object {
        const val TITLE_MAX_CHARS = 80
    }
}
