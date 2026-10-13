package com.travelhelper.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * ChatMemory 最小示例：演示同一 conversationId 共享上下文、不同 conversationId 互相隔离。
 * 用 mock 的 ChatModel 挡掉真实 LLM，不发起任何外部调用。
 */
class ChatMemoryExampleTest {

    private ChatMemory chatMemory;
    private ChatModel chatModel;
    private ChatClient chatClient;
    private ArgumentCaptor<Prompt> promptCaptor;

    @BeforeEach
    void setUp() {
        chatMemory = MessageWindowChatMemory.builder().build();
        chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));

        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    }

    /** 用指定 conversationId 走带记忆的 ChatClient 发一轮消息。 */
    private void ask(String conversationId, String message) {
        chatClient.prompt().user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    @Test
    @DisplayName("同一 conversationId 共享上下文")
    void sameConversationIdSharesContext() {
        ask("alice", "我叫小明，喜欢火锅");
        ask("alice", "我刚才说自己叫什么？");

        // 两轮各调一次模型，抓取两次请求的 Prompt
        verify(chatModel, times(2)).call(promptCaptor.capture());
        String secondPromptText = promptCaptor.getAllValues().get(1).getInstructions().toString();

        // 第二轮发给模型的上下文里，应同时包含第一轮的 user 消息和本轮消息
        assertThat(secondPromptText)
                .contains("我叫小明，喜欢火锅")
                .contains("我刚才说自己叫什么？");
    }

    @Test
    @DisplayName("不同 conversationId 互相隔离")
    void differentConversationIdsAreIsolated() {
        ask("alice", "我叫小明，喜欢火锅");
        ask("bob", "你好");

        verify(chatModel, times(2)).call(promptCaptor.capture());
        String bobPromptText = promptCaptor.getAllValues().get(1).getInstructions().toString();

        // bob 的上下文里不应出现 alice 说过的话
        assertThat(bobPromptText)
                .contains("你好")
                .doesNotContain("小明");
    }
}