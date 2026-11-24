package com.lanweixi.domain.plugin

/**
 * P165：插件界面描述节点。
 *
 * 插件是 Lua 沙箱，**跑不了 Compose**。所以界面这条路反过来走：
 * 插件在 Lua 里组装一棵描述树，宿主把它渲染成原生 Compose。
 *
 * 宿主只认下面这几种节点，插件不能引入任意组件——这是安全边界，
 * 也是保证视觉统一的手段（所有节点都用慕夏主题渲染）。
 *
 * 插件侧写法（Lua）：
 * ```lua
 * function page_main(args)
 *   return {
 *     { type = "title",  text = "工作台概览" },
 *     { type = "text",   text = "待办 3 项" },
 *     { type = "button", text = "刷新", action = "page_main" },
 *   }
 * end
 * ```
 *
 * @property action 按钮点击后要调用的 Lua 函数名（同插件内）
 */
sealed interface PluginUiNode {

    /** 大标题。 */
    data class Title(val text: String) : PluginUiNode

    /** 正文段落。 */
    data class Text(val text: String) : PluginUiNode

    /** 分组卡片：标题 + 正文。 */
    data class Card(val title: String, val body: String) : PluginUiNode

    /** 无序列表。 */
    data class BulletList(val items: List<String>) : PluginUiNode

    /** 可点击按钮。 */
    data class Button(val text: String, val action: String) : PluginUiNode

    /** 分隔线。 */
    data object Divider : PluginUiNode

    /** 提示条（危险 / 警告类文字）。 */
    data class Notice(val text: String) : PluginUiNode
}

/** 一次渲染的结果：节点树 + 可能的错误说明。 */
data class PluginUiPage(
    val nodes: List<PluginUiNode> = emptyList(),
    val error: String? = null,
)
