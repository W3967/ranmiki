package com.lanweixi.feature.chat
import com.lanweixi.domain.prompt.PromptBuilder
import com.lanweixi.domain.prompt.ChatConfigStore
import com.lanweixi.domain.prompt.ChatConfigProvider
import com.lanweixi.domain.prompt.ChatConfig


/** 测试用配置端口：可固定、可随时改值，用于验证 send() 每次读取最新配置。 */
class FixedConfigProvider(var config: ChatConfig) : ChatConfigProvider {
    var reads = 0
        private set

    override suspend fun current(): ChatConfig {
        reads++
        return config
    }
}
