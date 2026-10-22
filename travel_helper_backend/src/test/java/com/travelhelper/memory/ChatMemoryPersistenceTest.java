package com.travelhelper.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 持久化测试：多轮记忆落在 JDBC（文件 H2）里，重建 DataSource / 重建 ChatMemory 后仍能恢复。
 * 不启动 Spring 容器，直接造「文件库 + JdbcChatMemoryRepository」，用全新的 DataSource 模拟一次「重启」。
 */
class ChatMemoryPersistenceTest {

    @Test
    void fileBasedMemorySurvivesDataSourceReopen() throws Exception {
        Path dir = Files.createTempDirectory("charmem");
        String url = "jdbc:h2:file:" + dir.resolve("memorydb").toAbsolutePath().toString().replace('\\', '/');
        String conversationId = "persist-1";

        // 第一次「启动」：建库、建表、写一轮记忆
        DataSource first = new DriverManagerDataSource(url, "sa", "");
        initSchema(first);
        ChatMemory firstMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(JdbcChatMemoryRepository.builder()
                        .dataSource(first)
                        .transactionManager(new DataSourceTransactionManager(first))
                        .build())
                .maxMessages(20)
                .build();
        firstMemory.add(conversationId,
                List.<Message>of(new UserMessage("我叫小明，喜欢火锅"), new AssistantMessage("好的小明")));

        // 第二次「启动」（模拟重启）：全新的 DataSource + 全新的 ChatMemory 实例，读同一个文件
        DataSource second = new DriverManagerDataSource(url, "sa", "");
        ChatMemory secondMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(JdbcChatMemoryRepository.builder()
                        .dataSource(second)
                        .transactionManager(new DataSourceTransactionManager(second))
                        .build())
                .maxMessages(20)
                .build();

        List<String> restored = secondMemory.get(conversationId).stream()
                .map(Message::getText)
                .toList();

        assertThat(restored).hasSize(2);
        assertThat(restored).contains("我叫小明，喜欢火锅", "好的小明");
    }

    /** 运行 Spring AI 自带的 H2 建表脚本（只对「第一次启动」执行；文件已持久化时无需重复建表）。 */
    private static void initSchema(DataSource dataSource) {
        new ResourceDatabasePopulator(
                new ClassPathResource("org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql"))
                .execute(dataSource);
    }
}