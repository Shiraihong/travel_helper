package com.travelhelper.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置：底层用 {@link ChatMemoryRepository}（JDBC 落库，走 H2 / Neon），
 * 外层用 {@link MessageWindowChatMemory} 做滑动窗口，按 conversationId 隔离。
 * 每个会话保留的最大消息条数由 travel-helper.memory.max-messages 配置。
 *
 * 注意：JDBC 的 ChatMemoryRepository 由 Spring AI 自动配置提供（JdbcChatMemoryRepository），
 * 这里只负责把「窗口大小」和「持久化仓库」组合起来。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    ChatMemory chatMemory(ChatMemoryProperties properties, ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(properties.getMaxMessages())
                .build();
    }
}