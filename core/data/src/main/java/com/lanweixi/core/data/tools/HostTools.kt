package com.lanweixi.core.data.tools

import com.lanweixi.core.data.entity.DraftEntity
import com.lanweixi.core.data.entity.DraftKind
import com.lanweixi.core.data.repository.DocumentRepository
import com.lanweixi.core.data.repository.DraftRepository
import com.lanweixi.core.data.repository.NoteRepository
import com.lanweixi.core.data.repository.PromptRepository
import com.lanweixi.core.data.repository.TaskRepository
import com.lanweixi.domain.tools.ToolCall
import com.lanweixi.domain.tools.ToolExecutor
import com.lanweixi.domain.tools.ToolResult
import com.lanweixi.domain.tools.ToolSource
import com.lanweixi.domain.tools.ToolSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * P138：宿主内置工具。
 *
 * 这几个是真的能执行——模型调用后立刻查本地库并回填，
 * 把工作台五件套从「孤岛」接进对话。
 *
 * 只有读工具是「直接查」。P206 的两个写入工具（[ProposeNote] / [ProposeTask]）
 * 写的是**草稿表**，不碰正式数据：用户还要在待审核页点确认。所以它们也不标 sensitive——
 * 敏感确认留给真正生效的动作，否则「记一下」要连弹两次框。
 */
class HostTools(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val documents: DocumentRepository,
    private val prompts: PromptRepository,
    private val drafts: DraftRepository,
    /** P206：草稿来源会话；执行时现取（引擎切会话后不会写错）。 */
    private val sessionId: suspend () -> String = { "" },
) {

    fun executors(): List<ToolExecutor> = listOf(
        SearchNotes(notes),
        ListTasks(tasks),
        SearchDocuments(documents),
        ListPrompts(prompts),
        ProposeNote(drafts, sessionId),
        ProposeTask(drafts, sessionId),
    )

    private abstract class BaseTool : ToolExecutor {
        protected fun ok(call: ToolCall, text: String) =
            ToolResult(callId = call.id, name = call.name, content = text)

        protected fun fail(call: ToolCall, message: String) =
            ToolResult(callId = call.id, name = call.name, content = message, isError = true)

        protected fun arg(call: ToolCall, key: String): String =
            runCatching { JSONObject(call.arguments.ifBlank { "{}" }).optString(key) }.getOrDefault("")

        protected fun boolArg(call: ToolCall, key: String, default: Boolean = false): Boolean =
            runCatching { JSONObject(call.arguments.ifBlank { "{}" }).optBoolean(key, default) }
                .getOrDefault(default)

        /** 数值参数；模型可能给字符串（"0" / "明天" 之类），给不出来就走默认值。 */
        protected fun longArg(call: ToolCall, key: String, default: Long = 0L): Long {
            val json = runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }.getOrNull() ?: return default
            json.optLong(key, default).let { if (it != 0L) return it }
            return runCatching { json.optString(key).toLong() }.getOrDefault(default)
        }

        /**
         * P206：时间戳归一化。
         *
         * 模型经常把秒当毫秒给（或者反过来），差三个数量级。
         * 1e9 ~ 4e9 只可能是秒（2001–2096），乘 1000；其余按毫秒处理。
         */
        protected fun normalizeMillis(raw: Long): Long = normalizeEpochMillis(raw)

        protected fun formatDue(at: Long): String =
            if (at <= 0L) "不设截止" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
    }

    private class SearchNotes(private val notes: NoteRepository) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_search_notes",
            description = "搜索或列出用户的工作台笔记。query 留空表示列出最近的笔记（想看总数时用空 query）。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "关键词，留空表示列出最近笔记"),
                ),
                "required" to listOf("query"),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val query = arg(call, "query")
            val rows = runCatching {
                if (query.isBlank()) notes.listActive().take(8) else notes.search(query).take(8)
            }.getOrElse { return fail(call, "读取笔记失败：" + (it.message ?: "未知原因")) }
            if (rows.isEmpty()) return ok(call, "没有匹配的笔记")
            val text = rows.joinToString("\n") { note ->
                "- " + note.title.ifBlank { "无标题" } + "：" + note.content.replace('\n', ' ').take(120)
            }
            return ok(call, "共 " + rows.size + " 条笔记：\n" + text)
        }
    }

    private class ListTasks(private val tasks: TaskRepository) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_list_tasks",
            description = "列出用户的任务及数量，默认只看未完成；想知道有几个任务时调这个。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "includeDone" to mapOf("type" to "boolean", "description" to "是否包含已完成"),
                ),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val includeDone = boolArg(call, "includeDone")
            val open = runCatching { tasks.listOpen().take(15) }
                .getOrElse { return fail(call, "读取任务失败：" + (it.message ?: "未知原因")) }
            val done = if (includeDone) {
                runCatching { tasks.listDone().take(10) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            if (open.isEmpty() && done.isEmpty()) return ok(call, "没有任务")
            val sb = StringBuilder()
            if (open.isNotEmpty()) {
                sb.append("未完成 ").append(open.size).append(" 项：\n")
                open.forEach { t ->
                    sb.append("- ").append(t.title)
                    sb.append("（优先级 ").append(priorityLabel(t.priority)).append("）")
                    if (t.dueAt > 0L) sb.append(" 截止 ").append(t.dueAt)
                    sb.append('\n')
                }
            }
            if (done.isNotEmpty()) {
                sb.append("已完成 ").append(done.size).append(" 项：\n")
                done.forEach { sb.append("- ").append(it.title).append('\n') }
            }
            return ok(call, sb.toString().trim())
        }

        private fun priorityLabel(p: Int) = when (p) {
            2 -> "高"
            0 -> "低"
            else -> "中"
        }
    }

    private class SearchDocuments(private val documents: DocumentRepository) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_search_documents",
            description = "在用户导入的文档里搜索关键词，返回命中的文档名与片段。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("query" to mapOf("type" to "string", "description" to "关键词")),
                "required" to listOf("query"),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val query = arg(call, "query")
            if (query.isBlank()) return ok(call, "请给出要搜索的关键词")
            val docs = runCatching { documents.search(query).take(5) }
                .getOrElse { return fail(call, "搜索文档失败：" + (it.message ?: "未知原因")) }
            if (docs.isEmpty()) return ok(call, "没有匹配的文档")
            val sb = StringBuilder("命中 " + docs.size + " 篇：\n")
            docs.forEach { doc ->
                sb.append("- ").append(doc.title).append("（").append(doc.kind).append("）")
                val hit = runCatching { documents.firstHit(doc.id, query)?.text }.getOrNull()
                if (!hit.isNullOrBlank()) sb.append("：").append(hit.replace('\n', ' ').take(120))
                sb.append('\n')
            }
            return ok(call, sb.toString().trim())
        }
    }

    private class ListPrompts(private val prompts: PromptRepository) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_list_prompts",
            description = "列出用户保存的创作提示词（IP / 短视频 / 绘图）及数量。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "category" to mapOf("type" to "string", "description" to "ip / video / image / other，留空表示全部"),
                ),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val category = arg(call, "category")
            val rows = runCatching {
                if (category.isBlank()) prompts.listAll().take(10) else prompts.listByCategory(category).take(10)
            }.getOrElse { return fail(call, "读取提示词失败：" + (it.message ?: "未知原因")) }
            if (rows.isEmpty()) return ok(call, "没有提示词")
            return ok(
                call,
                rows.joinToString("\n") { p ->
                    "- " + p.title + "（" + p.category + "）：" + p.positive.replace('\n', ' ').take(100)
                },
            )
        }
    }

    /**
     * P206：写一条笔记草稿。
     *
     * 描述里那句「正文允许分点 / Markdown」很关键：系统提示词的说话方式要求
     * 「不要列表、不要标题」，模型会把这套风格带进工具参数，写出来像聊天记录。
     * 明确说「这是数据不是回复」才能拿到结构化的笔记。
     */
    private class ProposeNote(
        private val drafts: DraftRepository,
        private val sessionId: suspend () -> String,
    ) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_propose_note",
            description = "把一条笔记放进「待审核」，用户在待审核页确认后才会真正存进笔记本。" +
                "只在用户明确让你记下来（「记一下」「记下来」「帮我记住」，英文 remember this / note this 也算）" +
                "或确认了某个结论时调用，不要自己主动写。" +
                "正文可以用分点、列表、Markdown——这是要存的数据，不受聊天说话方式的限制。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string", "description" to "笔记标题，尽量短"),
                    "content" to mapOf("type" to "string", "description" to "笔记正文，可以分段"),
                    "tags" to mapOf("type" to "string", "description" to "标签，逗号分隔，没有就不传"),
                ),
                "required" to listOf("title", "content"),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val title = arg(call, "title").trim()
            val content = arg(call, "content").trim()
            if (title.isBlank() && content.isBlank()) {
                return fail(call, "笔记不能是空的：标题或正文至少给一个")
            }
            if (overLimit(drafts)) {
                return fail(call, "待审核里已经堆了 " + MAX_PENDING + " 条，先让用户处理完再记")
            }
            val noteTitle = title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
                .take(MAX_TITLE_CHARS)
            val id = runCatching {
                drafts.propose(
                    DraftEntity(
                        kind = DraftKind.NOTE,
                        title = noteTitle,
                        body = content,
                        tags = arg(call, "tags").trim().take(MAX_TAGS_CHARS),
                        sourceSessionId = sourceSession(sessionId),
                        sourceText = call.context.trim().take(MAX_SOURCE_CHARS),
                        createdAt = 0L,
                    ),
                )
            }.getOrElse { return fail(call, "写入草稿失败：" + (it.message ?: "未知原因")) }
            return ok(
                call,
                "已放进待审核（笔记草稿 #" + id + "）。" +
                    "告诉用户去「更多 → 待审核」里点确认，确认后才会出现在笔记里。",
            )
        }
    }

    /**
     * P206：写一条任务草稿。
     *
     * 截止时间收「人话」（today / tomorrow / 2026-11-25 / 2026-11-25 15:00），
     * **不让模型自己算毫秒**——实测它把「明天下午三点」算成了三天后。
     * 旧参数 dueAt（时间戳）保留兼容。
     */
    private class ProposeTask(
        private val drafts: DraftRepository,
        private val sessionId: suspend () -> String,
        private val clock: () -> Long = { System.currentTimeMillis() },
    ) : BaseTool() {
        override val spec = ToolSpec(
            name = "host_propose_task",
            description = "把一条任务放进「待审核」，用户在待审核页确认后才会真正存进任务清单。" +
                "只在用户明确让你记一个待办时调用（「记一下」「记下来」，英文 remember this / note this 也算），" +
                "不要自己主动加任务。" +
                "详情可以用分点——这是要存的数据，不受聊天说话方式的限制。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string", "description" to "任务标题，尽量短"),
                    "detail" to mapOf("type" to "string", "description" to "详情，可以没有"),
                    "due" to mapOf(
                        "type" to "string",
                        "description" to "截止时间，用下面几种写法之一，没有截止就留空：" +
                            "today（今天）/ tomorrow（明天）/ 2026-11-25（某天）/ 2026-11-25 15:00（某天几点）。" +
                            "用户说「明天下午三点」就写 tomorrow 15:00。不要自己换算成时间戳。",
                    ),
                    "priority" to mapOf(
                        "type" to "integer",
                        "description" to "优先级：0 低 / 1 中 / 2 高，拿不准填 1",
                    ),
                ),
                "required" to listOf("title"),
            ),
            source = ToolSource.HOST,
        )

        override suspend fun execute(call: ToolCall): ToolResult {
            val title = arg(call, "title").trim()
            if (title.isBlank()) return fail(call, "任务得有标题")
            if (overLimit(drafts)) {
                return fail(call, "待审核里已经堆了 " + MAX_PENDING + " 条，先让用户处理完再加")
            }
            val dueAt = dueAtOf(call)
            val priority = longArg(call, "priority", 1L).toInt().coerceIn(0, 2)
            val id = runCatching {
                drafts.propose(
                    DraftEntity(
                        kind = DraftKind.TASK,
                        title = title.take(MAX_TITLE_CHARS),
                        body = arg(call, "detail").trim(),
                        dueAt = dueAt,
                        priority = priority,
                        sourceSessionId = sourceSession(sessionId),
                        sourceText = call.context.trim().take(MAX_SOURCE_CHARS),
                        createdAt = 0L,
                    ),
                )
            }.getOrElse { return fail(call, "写入草稿失败：" + (it.message ?: "未知原因")) }
            return ok(
                call,
                "已放进待审核（任务草稿 #" + id + "，截止 " + formatDue(dueAt) + "）。" +
                    "告诉用户去「更多 → 待审核」里点确认，确认后才会出现在任务清单里。",
            )
        }

        /** 优先读人话 due；没有再看旧的时间戳参数。 */
        private fun dueAtOf(call: ToolCall): Long {
            val human = arg(call, "due").trim()
            if (human.isNotEmpty()) {
                val parsed = DueTimeParser.parse(human, clock())
                if (parsed > 0L) return parsed
            }
            return normalizeMillis(longArg(call, "dueAt", 0L))
        }
    }

    private companion object {
        /** 待审核上限：防止模型反复写把队列堆死。 */
        const val MAX_PENDING = 20

        const val MAX_TITLE_CHARS = 80
        const val MAX_TAGS_CHARS = 120

        /** P207：来源原话节选长度（够聊天页按内容找到那条消息）。 */
        const val MAX_SOURCE_CHARS = 200

        suspend fun overLimit(drafts: DraftRepository): Boolean =
            runCatching { drafts.countPending() >= MAX_PENDING }.getOrDefault(false)

        suspend fun sourceSession(sessionId: suspend () -> String): String =
            runCatching { sessionId() }.getOrDefault("").take(64)
    }
}
