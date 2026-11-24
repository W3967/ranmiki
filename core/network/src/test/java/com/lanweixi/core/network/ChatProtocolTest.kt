package com.lanweixi.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** P209：协议判定与 Anthropic 端点拼接。 */
class ChatProtocolTest {

    @Test
    fun `anthropic base urls pick the anthropic protocol`() {
        assertEquals(ChatProtocol.ANTHROPIC, protocolOf("https://api.anthropic.com"))
        assertEquals(ChatProtocol.ANTHROPIC, protocolOf("https://api.anthropic.com/v1"))
        assertEquals(ChatProtocol.ANTHROPIC, protocolOf("https://my-proxy.example.com/anthropic"))
    }

    @Test
    fun `everything else stays openai compatible`() {
        assertEquals(ChatProtocol.OPENAI, protocolOf("https://api.deepseek.com"))
        assertEquals(ChatProtocol.OPENAI, protocolOf("https://api.openai.com/v1"))
        assertEquals(ChatProtocol.OPENAI, protocolOf(""))
    }

    @Test
    fun `messages url fills the missing path segment`() {
        assertEquals("https://api.anthropic.com/v1/messages", messagesUrl("https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com/v1/messages", messagesUrl("https://api.anthropic.com/"))
        assertEquals("https://api.anthropic.com/v1/messages", messagesUrl("https://api.anthropic.com/v1"))
        assertEquals("https://api.anthropic.com/v1/messages", messagesUrl("https://api.anthropic.com/v1/messages"))
        assertEquals("https://api.anthropic.com/v1/messages", messagesUrl(""))
    }

    @Test
    fun `openai endpoint is untouched`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            chatCompletionsUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.moonshot.cn/v1/chat/completions",
            chatCompletionsUrl("https://api.moonshot.cn/v1"),
        )
    }
}
