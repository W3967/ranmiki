package com.lanweixi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** P206：工具痕迹的中文描述（用户不该看到 host_propose_task 这种名字）。 */
class ToolTrailLabelTest {

    @Test
    fun `read tools describe what was looked up`() {
        assertEquals("查了笔记", toolTrailLabel("host_search_notes"))
        assertEquals("查了任务", toolTrailLabel("host_list_tasks"))
        assertEquals("查了文档", toolTrailLabel("host_search_documents"))
        assertEquals("查了提示词", toolTrailLabel("host_list_prompts"))
    }

    @Test
    fun `write tools are not described as lookups`() {
        assertEquals("记了一条笔记", toolTrailLabel("host_propose_note"))
        assertEquals("记了一条任务", toolTrailLabel("host_propose_task"))
    }

    @Test
    fun `plugin tools fall back to their own name`() {
        assertEquals("weather.query", toolTrailLabel("weather.query"))
    }
}
