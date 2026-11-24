package com.lanweixi.core.data.search

import com.lanweixi.core.data.repository.DocumentRepository
import com.lanweixi.core.data.repository.NoteRepository
import com.lanweixi.core.data.repository.PromptRepository
import com.lanweixi.core.data.repository.ReviewRepository
import com.lanweixi.core.data.repository.TaskRepository

/**
 * P188：全局搜索。
 *
 * 把散落在各仓储里的检索能力汇成一路，供插件（`search:index` 权限）与将来的搜索页共用。
 *
 * 有的仓储自带 `search`（笔记 / 提示词 / 文档，走 SQL LIKE），
 * 有的没有（任务 / 复盘，只能取回来在内存里过滤）——这里统一抹平。
 */
data class SearchHit(
    /** note / task / doc / prompt / review */
    val kind: String,
    val id: Long,
    val title: String,
    val snippet: String,
    val updatedAt: Long,
)

class GlobalSearch(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val documents: DocumentRepository,
    private val prompts: PromptRepository,
    private val reviews: ReviewRepository,
) {

    /**
     * 跨实体检索。[perKind] 限制每类返回条数，避免一次刷太多。
     *
     * 结果按更新时间倒序，跨类型混排——调用方不需要关心类型顺序。
     */
    suspend fun search(query: String, perKind: Int = 5): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val limit = perKind.coerceIn(1, 20)
        val out = mutableListOf<SearchHit>()

        runCatching {
            notes.search(q).take(limit).forEach {
                out += SearchHit("note", it.id, it.title, snippetOf(it.content), it.updatedAt)
            }
        }
        runCatching {
            tasks.listOpen()
                .filter { it.title.contains(q, ignoreCase = true) || it.detail.contains(q, ignoreCase = true) }
                .sortedByDescending { it.updatedAt }
                .take(limit)
                .forEach {
                    out += SearchHit("task", it.id, it.title, snippetOf(it.detail), it.updatedAt)
                }
        }
        runCatching {
            documents.search(q).take(limit).forEach {
                out += SearchHit("doc", it.id, it.title, it.fileName, it.updatedAt)
            }
        }
        runCatching {
            prompts.search(q).take(limit).forEach {
                out += SearchHit("prompt", it.id, it.title, snippetOf(it.positive), it.updatedAt)
            }
        }
        runCatching {
            reviews.listAll()
                .filter { it.summary.contains(q, ignoreCase = true) }
                .sortedByDescending { it.updatedAt }
                .take(limit)
                .forEach {
                    out += SearchHit("review", it.id, "复盘", snippetOf(it.summary), it.updatedAt)
                }
        }

        return out.sortedByDescending { it.updatedAt }
    }

    private fun snippetOf(text: String, max: Int = 60): String =
        text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(max)
}
