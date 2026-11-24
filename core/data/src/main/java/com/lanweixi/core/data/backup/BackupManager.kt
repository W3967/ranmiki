package com.lanweixi.core.data.backup

import androidx.room.withTransaction
import com.lanweixi.core.data.LanWeixiDatabase
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.entity.MessageEntity
import com.lanweixi.core.data.entity.NoteEntity
import com.lanweixi.core.data.entity.PromptEntity
import com.lanweixi.core.data.entity.ReviewEntity
import com.lanweixi.core.data.entity.SessionSummaryEntity
import com.lanweixi.core.data.entity.TaskEntity
import org.json.JSONArray
import org.json.JSONObject

/** 导入/导出的统计。 */
data class BackupStats(
    val notes: Int = 0,
    val tasks: Int = 0,
    val prompts: Int = 0,
    val documents: Int = 0,
    val reviews: Int = 0,
    /** P210：会话摘要（上下文压缩产物）。 */
    val summaries: Int = 0,
    /** P211：聊天消息（单聊 + 群聊）。 */
    val messages: Int = 0,
) {
    val total: Int get() = notes + tasks + prompts + documents + reviews + summaries + messages

    fun describe(): String =
        "笔记 " + notes + " · 任务 " + tasks + " · 提示词 " + prompts +
            " · 文档 " + documents + " · 复盘 " + reviews +
            " · 会话摘要 " + summaries + " · 聊天记录 " + messages
}

/**
 * P162：工作台数据备份。
 *
 * 只备**业务数据**（五张表的完整内容），不备文档分块索引——那是从原文
 * 重新导入即可重建的派生数据，带上会让文件膨胀好几倍。
 *
 * 格式是纯 JSON，可读、可手改、可跨版本导入。
 */
class BackupManager(private val db: LanWeixiDatabase) {

    companion object {
        /** P210：v2 加会话摘要；P211：v3 加聊天记录；导入仍然兼容 v1/v2。 */
        const val FORMAT_VERSION = 3
        const val APP_TAG = "lanweixi-workbench"
    }

    /** 导出全部业务数据为 JSON 文本。 */
    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("notes", JSONArray().apply {
            db.noteDao().listAllForBackup().forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("tags", n.tags)
                    put("archived", n.archived)
                    put("createdAt", n.createdAt)
                    put("updatedAt", n.updatedAt)
                })
            }
        })

        root.put("tasks", JSONArray().apply {
            db.taskDao().listAllForBackup().forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("detail", t.detail)
                    put("dueAt", t.dueAt)
                    put("priority", t.priority)
                    put("done", t.done)
                    put("doneAt", t.doneAt)
                    put("noteRef", t.noteRef)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                })
            }
        })

        root.put("prompts", JSONArray().apply {
            db.promptDao().listAll().forEach { pr ->
                put(JSONObject().apply {
                    put("id", pr.id)
                    put("title", pr.title)
                    put("category", pr.category)
                    put("positive", pr.positive)
                    put("negative", pr.negative)
                    put("params", pr.params)
                    put("memo", pr.memo)
                    put("pinned", pr.pinned)
                    put("createdAt", pr.createdAt)
                    put("updatedAt", pr.updatedAt)
                })
            }
        })

        root.put("documents", JSONArray().apply {
            db.documentDao().listAll().forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("title", d.title)
                    put("fileName", d.fileName)
                    put("kind", d.kind)
                    put("charCount", d.charCount)
                    put("chunkCount", d.chunkCount)
                    put("importedAt", d.importedAt)
                    put("updatedAt", d.updatedAt)
                })
            }
        })

        root.put("reviews", JSONArray().apply {
            db.reviewDao().listAll().forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("periodType", r.periodType)
                    put("periodStart", r.periodStart)
                    put("periodEnd", r.periodEnd)
                    put("summary", r.summary)
                    put("createdAt", r.createdAt)
                    put("updatedAt", r.updatedAt)
                })
            }
        })

        // P210：会话摘要（上下文压缩的产物，丢了就等于忘了前面聊过什么）
        root.put("sessionSummaries", JSONArray().apply {
            db.summaryDao().listAll().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("sessionId", s.sessionId)
                    put("untilId", s.untilId)
                    put("content", s.content)
                    put("coveredCount", s.coveredCount)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                })
            }
        })

        // P211：聊天记录（单聊 + 群聊都在 messages 里，靠 sessionId 区分）
        root.put("messages", JSONArray().apply {
            db.messageDao().listAllForBackup().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("role", m.role)
                    put("speaker", m.speaker)
                    put("text", m.text)
                    put("status", m.status)
                    put("createdAt", m.createdAt)
                    put("sessionId", m.sessionId)
                    put("quoteId", m.quoteId)
                    put("quotePreview", m.quotePreview)
                })
            }
        })

        return root.toString(2)
    }

    /**
     * 从 JSON 恢复。
     *
     * **全量替换**：先清空五张表再导入。调用方必须在 UI 上明确警告。
     * 格式不对会在动数据之前就抛错，不会留下半截状态。
     */
    suspend fun importJson(json: String): BackupStats {
        val root = JSONObject(json)
        val app = root.optString("app", "")
        if (app != APP_TAG) {
            error("不是兰未晞工作台的备份文件")
        }
        val version = root.optInt("version", 0)
        if (version > FORMAT_VERSION) {
            error("备份版本 " + version + " 比当前应用新，请先升级应用")
        }

        // 先全部解析成对象，确认没问题再动数据库
        val notes = root.optJSONArray("notes").toList { o ->
            NoteEntity(
                id = o.optLong("id"),
                title = o.optString("title"),
                content = o.optString("content"),
                tags = o.optString("tags"),
                archived = o.optBoolean("archived"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        val tasks = root.optJSONArray("tasks").toList { o ->
            TaskEntity(
                id = o.optLong("id"),
                title = o.optString("title"),
                detail = o.optString("detail"),
                dueAt = o.optLong("dueAt"),
                priority = o.optInt("priority", 1),
                done = o.optBoolean("done"),
                doneAt = o.optLong("doneAt"),
                noteRef = o.optLong("noteRef"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        val prompts = root.optJSONArray("prompts").toList { o ->
            PromptEntity(
                id = o.optLong("id"),
                title = o.optString("title"),
                category = o.optString("category", "ip"),
                positive = o.optString("positive"),
                negative = o.optString("negative"),
                params = o.optString("params"),
                memo = o.optString("memo"),
                pinned = o.optBoolean("pinned"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        val documents = root.optJSONArray("documents").toList { o ->
            DocumentEntity(
                id = o.optLong("id"),
                title = o.optString("title"),
                fileName = o.optString("fileName"),
                kind = o.optString("kind", "txt"),
                charCount = o.optInt("charCount"),
                chunkCount = o.optInt("chunkCount"),
                importedAt = o.optLong("importedAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        val reviews = root.optJSONArray("reviews").toList { o ->
            ReviewEntity(
                id = o.optLong("id"),
                periodType = o.optString("periodType", "week"),
                periodStart = o.optLong("periodStart"),
                periodEnd = o.optLong("periodEnd"),
                summary = o.optString("summary"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        // P210：v1 备份没有这一段，取空即可
        val summaries = root.optJSONArray("sessionSummaries").toList { o ->
            SessionSummaryEntity(
                id = o.optLong("id"),
                sessionId = o.optString("sessionId"),
                untilId = o.optLong("untilId"),
                content = o.optString("content"),
                coveredCount = o.optInt("coveredCount"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
        // P211：v3 才带聊天记录，更早的备份取空
        val messages = root.optJSONArray("messages").toList { o ->
            MessageEntity(
                id = o.optLong("id"),
                role = o.optString("role"),
                speaker = o.optString("speaker"),
                text = o.optString("text"),
                status = o.optString("status", "sent"),
                createdAt = o.optLong("createdAt"),
                sessionId = o.optString("sessionId"),
                quoteId = o.optLong("quoteId"),
                quotePreview = o.optString("quotePreview"),
            )
        }

        db.withTransaction {
            db.noteDao().deleteAll()
            db.taskDao().deleteAll()
            db.promptDao().deleteAll()
            // P216：documents 整表替换，但 doc_chunks 是另一张表。
            // 以前只删 documents，旧分块全留着，搜索时会挂在同 id 的新文档上
            // （备份不导出分块，导入后本来就没有新分块）。
            db.documentDao().deleteAllChunks()
            db.documentDao().deleteAll()
            db.reviewDao().deleteAll()

            // P215：旧备份（v1/v2）里没有聊天记录与摘要这两段。
            // 以前不管有没有都先 deleteAll，结果用新版应用导入旧备份 = 聊天记录和摘要被静默清空。
            // 现在只在备份确实带这一段时才替换；不带就保留现在的。
            val hasSummaries = root.has("sessionSummaries")
            val hasMessages = root.has("messages")
            if (hasSummaries) db.summaryDao().deleteAll()
            if (hasMessages) db.messageDao().deleteAll()

            notes.forEach { db.noteDao().insert(it) }
            tasks.forEach { db.taskDao().insert(it) }
            prompts.forEach { db.promptDao().insert(it) }
            documents.forEach { db.documentDao().insert(it) }
            reviews.forEach { db.reviewDao().insert(it) }
            if (hasSummaries) summaries.forEach { db.summaryDao().insert(it) }
            if (hasMessages) messages.forEach { db.messageDao().insert(it) }
        }

        return BackupStats(
            notes = notes.size,
            tasks = tasks.size,
            prompts = prompts.size,
            documents = documents.size,
            reviews = reviews.size,
            summaries = summaries.size,
            messages = messages.size,
        )
    }

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            optJSONObject(i)?.let { out.add(mapper(it)) }
        }
        return out
    }
}
