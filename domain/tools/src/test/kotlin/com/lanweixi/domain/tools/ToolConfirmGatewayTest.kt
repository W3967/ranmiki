package com.lanweixi.domain.tools

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P139：敏感工具确认通道。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolConfirmGatewayTest {

    private val spec = ToolSpec(name = "host_delete_note", description = "删一条笔记", sensitive = true)
    private val call = ToolCall(id = "call_1", name = "host_delete_note", arguments = "{\"id\":3}")

    @Test
    fun `allow resumes with true`() = runTest {
        val gateway = ToolConfirmGateway()
        var result: Boolean? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { result = gateway.ask(spec, call) }

        assertNotNull("应有一条待确认", gateway.pending.value)
        assertEquals("host_delete_note", gateway.pending.value?.toolName)
        assertEquals("{\"id\":3}", gateway.pending.value?.arguments)

        gateway.answer(true)
        job.join()

        assertEquals(true, result)
        assertNull("答完应清空", gateway.pending.value)
    }

    @Test
    fun `reject resumes with false`() = runTest {
        val gateway = ToolConfirmGateway()
        var result: Boolean? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { result = gateway.ask(spec, call) }

        assertNotNull(gateway.pending.value)
        gateway.answer(false)
        job.join()

        assertEquals(false, result)
        assertNull(gateway.pending.value)
    }

    @Test
    fun `timeout counts as refusal`() = runTest {
        val gateway = ToolConfirmGateway()
        var result: Boolean? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { result = gateway.ask(spec, call) }

        assertNotNull(gateway.pending.value)
        advanceTimeBy(ToolConfirmGateway.TIMEOUT_MS + 1_000)
        job.join()

        assertEquals("超时按拒绝处理", false, result)
        assertNull(gateway.pending.value)
    }

    @Test
    fun `second ask is refused while one is pending`() = runTest {
        val gateway = ToolConfirmGateway()
        var first: Boolean? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { first = gateway.ask(spec, call) }
        assertNotNull(gateway.pending.value)

        val second = gateway.ask(spec, ToolCall("call_2", "host_delete_note", "{}"))
        assertFalse("排队等第二个弹窗比直接拒绝更糟", second)

        gateway.answer(true)
        job.join()
        assertEquals(true, first)
    }

    @Test
    fun `cancel resumes with false`() = runTest {
        val gateway = ToolConfirmGateway()
        var result: Boolean? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { result = gateway.ask(spec, call) }

        assertNotNull(gateway.pending.value)
        gateway.cancel()
        job.join()

        assertEquals(false, result)
        assertNull(gateway.pending.value)
    }

    @Test
    fun `answer without pending is a no-op`() {
        val gateway = ToolConfirmGateway()
        gateway.answer(true)
        assertNull(gateway.pending.value)
    }

    @Test
    fun `pending request carries description and call id`() = runTest {
        val gateway = ToolConfirmGateway()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { gateway.ask(spec, call) }
        assertTrue(gateway.pending.value!!.description.contains("删一条笔记"))
        assertEquals("call_1", gateway.pending.value!!.callId)
        gateway.answer(false)
        job.join()
    }
}