package com.travelhelper.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置：内存实现，按 conversationId 隔离。
 * 每个会话保留的最大消息条数由 travel-helper.memory.max-messages 配置。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    ChatMemory chatMemory(ChatMemoryProperties properties) {
        return MessageWindowChatMemory.builder()
                .maxMessages(properties.getMaxMessages())
                .build();
    }
}