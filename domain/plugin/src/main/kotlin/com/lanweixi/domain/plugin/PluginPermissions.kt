package com.lanweixi.domain.plugin

/**
 * 宿主认可的权限 id 白名单（Schema v1）。
 *
 * 文档约定：**不在枚举内的 permissionId 直接拒绝**——插件不能自带"未知权限"绕过校验。
 * 后续版本可以扩展这张表，扩展时要同步 schema 文档。
 */
object PluginPermissions {

    /** 核心库表级权限。 */
    const val CORE_DB_READ_TASK = "core_db:read:task"
    const val CORE_DB_WRITE_TASK = "core_db:write:task"
    const val CORE_DB_READ_NOTE = "core_db:read:note"
    const val CORE_DB_WRITE_NOTE = "core_db:write:note"
    const val CORE_DB_READ_CREATION_PROMPT = "core_db:read:creation_prompt"
    const val CORE_DB_WRITE_CREATION_PROMPT = "core_db:write:creation_prompt"
    const val CORE_DB_READ_DOCUMENT_INDEX = "core_db:read:document_index"
    const val CORE_DB_WRITE_DOCUMENT_INDEX = "core_db:write:document_index"

    /** 能力类权限。 */
    const val NETWORK = "network"
        const val DOCUMENT_READ_ALL = "document:read_all"
    const val SEARCH_INDEX = "search:index"
    const val LOG_SUBSCRIBE_HOST = "log:subscribe_host"
    const val UI_CUSTOM_THEME = "ui:custom_theme"
    const val HOTKEY_REGISTER = "hotkey:register"

    /** 全部已知权限。 */
    val KNOWN: Set<String> = setOf(
        CORE_DB_READ_TASK, CORE_DB_WRITE_TASK,
        CORE_DB_READ_NOTE, CORE_DB_WRITE_NOTE,
        CORE_DB_READ_CREATION_PROMPT, CORE_DB_WRITE_CREATION_PROMPT,
        CORE_DB_READ_DOCUMENT_INDEX, CORE_DB_WRITE_DOCUMENT_INDEX,
        NETWORK, DOCUMENT_READ_ALL, SEARCH_INDEX,
        LOG_SUBSCRIBE_HOST, UI_CUSTOM_THEME, HOTKEY_REGISTER,
    )

    fun isKnown(permissionId: String): Boolean = permissionId.trim() in KNOWN

    /**
     * P217：会让插件产生副作用、或读到用户全文的权限。
     *
     * 申请了这些权限的插件，**它声明的所有工具都强制走确认弹窗**，
     * 不再只信 manifest 里的 `sensitive` 自声明（插件把自己写成 false 就能绕过确认）。
     */
    private val FORCE_CONFIRM: Set<String> = setOf(
        NETWORK,
        CORE_DB_WRITE_TASK,
        CORE_DB_WRITE_NOTE,
        CORE_DB_WRITE_CREATION_PROMPT,
        CORE_DB_WRITE_DOCUMENT_INDEX,
        DOCUMENT_READ_ALL,
        SEARCH_INDEX,
        LOG_SUBSCRIBE_HOST,
    )

    /** P217：这个权限是否要求工具确认。 */
    fun requiresConfirmation(permissionId: String): Boolean =
        permissionId.trim() in FORCE_CONFIRM

    /** 权限分类（给授权弹窗分组展示用）。 */
    fun category(permissionId: String): String = when {
        permissionId.startsWith("core_db:") -> "数据访问"
        permissionId == NETWORK -> "网络"
        permissionId == DOCUMENT_READ_ALL -> "文档"
        permissionId == SEARCH_INDEX -> "搜索"
        permissionId == LOG_SUBSCRIBE_HOST -> "日志"
        permissionId == UI_CUSTOM_THEME -> "界面"
        permissionId == HOTKEY_REGISTER -> "快捷键"
        else -> "其他"
    }
}

/**
 * P217：插件是否需要工具确认。
 *
 * 只要申请了任一「有副作用 / 读全文」的能力，插件里的工具都按敏感处理；
 * 纯只读（笔记列表、任务列表）仍然不弹，避免每次查台账都打断用户。
 */
fun PluginManifest.requiresToolConfirmation(): Boolean =
    permissions.any { PluginPermissions.requiresConfirmation(it.permissionId) }

