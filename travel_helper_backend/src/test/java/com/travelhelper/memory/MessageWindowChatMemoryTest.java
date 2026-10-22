package com.travelhelper.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 滑动窗口（MessageWindowChatMemory）的边界测试。
 * 直接测 ChatMemory 本身，不依赖 Spring 容器：窗口满后丢弃最旧消息、保留最新消息。
 */
class MessageWindowChatMemoryTest {

    private static final String CID = "window-1";

    @Test
    @DisplayName("超出窗口后，最旧的消息被丢弃")
    void dropsOldestWhenWindowExceeded() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(4).build();

        // 每轮 2 条（user + assistant），加了 3 轮共 6 条；窗口只有 4 条
        memory.add(CID, List.<Message>of(new UserMessage("1"), new AssistantMessage("a1")));
        memory.add(CID, List.<Message>of(new UserMessage("2"), new AssistantMessage("a2")));
        memory.add(CID, List.<Message>of(new UserMessage("3"), new AssistantMessage("a3")));

        List<String> kept = memory.get(CID).stream().map(Message::getText).toList();

        assertThat(kept).hasSize(4);                            // 只保留最近 4 条
        assertThat(kept).doesNotContain("1", "a1");             // 最旧一轮（user + assistant）被丢弃
        assertThat(kept).contains("2", "a2", "3", "a3");        // 最近两轮完整保留
    }

    @Test
    @DisplayName("恰好等于窗口大小时，全部保留")
    void keepsAllWhenExactlyAtWindowSize() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(4).build();

        memory.add(CID, List.<Message>of(new UserMessage("1"), new AssistantMessage("a1")));
        memory.add(CID, List.<Message>of(new UserMessage("2"), new AssistantMessage("a2")));

        List<String> kept = memory.get(CID).stream().map(Message::getText).toList();

        assertThat(kept).hasSize(4);
        assertThat(kept).containsExactlyInAnyOrder("1", "a1", "2", "a2");
    }
}