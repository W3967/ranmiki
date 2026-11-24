package com.lanweixi.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 方案表 4：messages —— status 区分 pending / sent / failed。 */
@Entity(
    tableName = "messages",
    indices = [Index("sessionId"), Index("createdAt"), Index("sessionId", "createdAt")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val speaker: String = "",
    val text: String,
    val status: String,
    val createdAt: Long,
    val sessionId: String,
    /** P204：被引用消息 id（0 = 无引用），只用于点引用回到原消息。 */
    val quoteId: Long = 0,
    /** P204：引用快照「名字：前 60 字…」，渲染与进上下文都用它（原消息没了也能显示）。 */
    val quotePreview: String = "",
)

/** 方案表 4：memories —— 承载打分与衰减，weight 为浮点。 */
@Entity(tableName = "memories", indices = [Index("charId"), Index("createdAt")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val charId: String,
    val text: String,
    val weight: Float,
    val source: String,
    val createdAt: Long,
    val lastAccess: Long,
    val accessCount: Int,
)

/** 方案表 4：characters —— 关系数值不再硬编码，modelId 允许为空。 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val affection: Int,
    val trust: Int,
    val stage: String,
    val modelId: String? = null,
    val avatar: String? = null,
    val voiceId: String? = null,
)

/** 方案表 4：models —— 用户导入的 Live2D 模型。 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dirUri: String,
    val schemaVer: Int,
    val sizeBytes: Long,
    val checksum: String,
    val previewPath: String? = null,
    val importedAt: Long,
    val isActive: Boolean = false,
)

/** 方案表 4：vitals_samples —— 按天聚合后可归档。 */
@Entity(tableName = "vitals_samples", indices = [Index("at")])
data class VitalsSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val energy: Float,
    val cortisol: Float,
    val hunger: Float,
    val thirst: Float,
)

/** 方案表 4：diary —— dayKey 唯一约束，防重复生成。 */
@Entity(tableName = "diary", indices = [Index(value = ["dayKey"], unique = true)])
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val content: String,
    val mood: String,
    val createdAt: Long,
)

/** 方案表 4：feeds —— analyzed 标记是否已被引用进对话。 */
@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val excerpt: String,
    val importedAt: Long,
    val analyzed: Boolean = false,
)

/** 方案表 4：events —— 里程碑与近期事件的数据源。 */
@Entity(tableName = "events", indices = [Index("at")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val at: Long,
)

/** 方案表 4：kv —— 仅保留确实无结构的配置。 */
@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * 工作台笔记（生产力底座的原子数据）。
 *
 * 后续的任务 / 文档索引 / 创作提示词 / 复盘都会引用 [id]。
 * `tags` 用逗号分隔的纯文本——够用且不引入关系表。
 */
@Entity(tableName = "notes", indices = [Index("updatedAt"), Index("archived")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tags: String = "",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 创作提示词（IP 脚本 / 短视频 / AI 绘图）。
 *
 * `category` 用固定字符串（ip / video / image / other），不建枚举表——
 * 分类是产品概念，改了不该动 schema。
 */
@Entity(tableName = "prompts", indices = [Index("category"), Index("updatedAt"), Index("pinned")])
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "ip",
    val positive: String,
    val negative: String = "",
    /** 模型 / 尺寸 / 采样等，写成一坨自由文本。 */
    val params: String = "",
    val memo: String = "",
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 工作台任务。
 *
 * `dueAt == 0` 表示不设截止；`priority` 0 低 / 1 中 / 2 高；
 * `noteRef` 预留为「任务挂在哪篇笔记下」，本期 UI 不暴露，先留列避免以后再加迁移。
 */
@Entity(tableName = "tasks", indices = [Index("done"), Index("dueAt"), Index("priority")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val dueAt: Long = 0L,
    val priority: Int = 1,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val noteRef: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 导入的文档（正文已抽成纯文本，按块存到 [DocChunkEntity]）。 */
@Entity(tableName = "documents", indices = [Index("importedAt"), Index("kind")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,
    val kind: String,
    val charCount: Int,
    val chunkCount: Int,
    val importedAt: Long,
    val updatedAt: Long,
)

/**
 * 文档分块。
 *
 * 单独成表而不是把全文塞进一行，是为了以后接检索（每块独立打分）和给插件 `search:index` 用。
 */
@Entity(tableName = "doc_chunks", indices = [Index("docId"), Index("docId", "seq")])
data class DocChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val seq: Int,
    val text: String,
    val charCount: Int,
)

/**
 * 复盘记录。
 *
 * 周期内的产出不存快照，而是每次打开时按时间范围现查——
 * 数据一直在变，存快照会把「后来补的笔记」永久漏掉。
 */
@Entity(tableName = "reviews", indices = [Index("periodStart")])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** week / month */
    val periodType: String,
    val periodStart: Long,
    val periodEnd: Long,
    val summary: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * P206：AI 产出的草稿（待审核队列）。
 *
 * 独立成表、不往 notes / tasks 上挂 status 列，原因：
 * 笔记与任务已经有搜索 / 标签 / 导出 / 全局搜索五处入口，
 * 混进未审核的草稿就得处处过滤，漏一处就是脏数据。
 *
 * `kind` 决定确认后写进哪张表：note → notes（[body] 是正文），
 * task → tasks（[body] 是详情，[dueAt] / [priority] 生效）。
 */
@Entity(tableName = "drafts", indices = [Index("status"), Index("createdAt")])
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [DraftKind.NOTE] / [DraftKind.TASK] */
    val kind: String,
    val title: String,
    val body: String = "",
    /** 标签（逗号分隔），仅 note 使用。 */
    val tags: String = "",
    /** 截止时间戳（毫秒，0 = 不设），仅 task 使用。 */
    val dueAt: Long = 0L,
    /** 0 低 / 1 中 / 2 高，仅 task 使用。 */
    val priority: Int = 1,
    /** [DraftStatus.PENDING] / [DraftStatus.APPROVED] / [DraftStatus.REJECTED] */
    val status: String = DraftStatus.PENDING,
    /** 从哪个会话来的（空 = 未知）；P207 用它跳回原消息。 */
    val sourceSessionId: String = "",
    /** 原消息 id（0 = 未知）。 */
    val sourceMessageId: Long = 0L,
    /**
     * P207：触发这条草稿的用户原话（节选）。
     *
     * 工具执行时当前消息还没落库，拿不到 id；存文本是为了「点来源跳回会话」时
     * 能按内容找到那条消息并高亮。空 = 来源不明。
     */
    val sourceText: String = "",
    /** 确认后写进 notes / tasks 的 id（0 = 还没写）。 */
    val targetId: Long = 0L,
    val createdAt: Long,
    /** 审核时间（0 = 还没审）。 */
    val reviewedAt: Long = 0L,
)

object DraftKind {
    const val NOTE = "note"
    const val TASK = "task"
}

object DraftStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
}

/**
 * P210：会话摘要（上下文压缩的产物）。
 *
 * 每个会话只留**一条**：每次压缩都是「旧摘要 + 新一段对话」重新压，
 * 长度受控，存储也简单。`untilId` 是覆盖到的最后一条消息 id，
 * 下次从更大的 id 开始压，避免重复进摘要。
 */
@Entity(
    tableName = "session_summaries",
    indices = [Index("sessionId", "untilId"), Index("sessionId", "createdAt")],
)
data class SessionSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 单聊是会话 id，群聊是群 id。 */
    val sessionId: String,
    /** 摘要覆盖到的最后一条消息 id（含）。 */
    val untilId: Long = 0L,
    val content: String,
    /** 覆盖过的消息总条数（展示与导出用）。 */
    val coveredCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
