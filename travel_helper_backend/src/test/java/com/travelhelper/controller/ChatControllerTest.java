package com.travelhelper.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatClient 单元测试：用 {@code @MockitoBean} mock 掉 ChatModel，跑真实 ChatClient 链路而不调用大模型 API。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 关键：把真实 ChatModel（OpenAI/DeepSeek）替换成 mock，杜绝真实 API 调用。
    // 注意 Spring Boot 3.4+ 里 @MockBean 已弃用，推荐 @MockitoBean。
    @MockitoBean
    private ChatModel chatModel;

    @Test
    void chatEndpointReturnsNonEmpty() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("这是被 mock 出来的回复")))));

        mockMvc.perform(get("/api/chat").param("message", "你好"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).isNotBlank();
                });
    }

    @Test
    void travelQuestionReturnsAnswerAndRegistersSearchTool() throws Exception {
        // 用 mock 的 ChatModel 返回固定回复，避免真的调 DeepSeek API
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("（mock）东京3日游建议：Day1 浅草/上野，Day2 涩谷/原宿，Day3 台场")))));

        mockMvc.perform(get("/api/chat")
                        .param("message", "东京3天怎么玩")
                        .param("role", "travel-advisor"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).isNotBlank();
                });

        // 可选的「工具调用」断言。注意：ChatModel 被 mock 后，真正的两轮工具执行循环（在 ChatModel 内部）
        // 不会真的跑起来，所以无法断言「日志里真的调用了 WebSearchTools」；
        // 单测边界上只能断言「search 工具已注册、随 Prompt 一起交给了模型」。
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());

        ChatOptions options = captor.getValue().getOptions();
        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);

        boolean searchToolSent = ((ToolCallingChatOptions) options).getToolCallbacks().stream()
                .anyMatch(tc -> "search".equals(tc.getToolDefinition().name()));
        assertThat(searchToolSent).isTrue();
    }

    @Test
    void conversationRemembersWithinSameConversationId() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("ok")))));

        // 独立 conversationId，避免和别的测试 / 无 ID 请求共用同一条历史
        String conversationId = "memtest-" + System.nanoTime();

        mockMvc.perform(get("/api/chat")
                        .param("message", "我叫小明，喜欢火锅")
                        .param("conversationId", conversationId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/chat")
                        .param("message", "我刚才说自己叫什么")
                        .param("conversationId", conversationId))
                .andExpect(status().isOk());

        // 两轮各调一次模型，抓取发给模型的 Prompt
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());

        // 第二轮上下文里应同时包含第一轮和本轮的用户消息
        String secondPromptText = captor.getAllValues().get(1).getInstructions().toString();
        assertThat(secondPromptText)
                .contains("我叫小明")
                .contains("我刚才说自己叫什么");
    }

    @Test
    void toolCallingIsTriggered() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("ok")))));

        // 挂载工具并发起一次对话。ChatModel 已被 mock，所以真正发生的是一次 call。
        ChatClient.builder(chatModel)
                .defaultTools(new ClockTools())
                .build()
                .prompt().user("现在几点")
                .call().content();

        // 「工具调用被触发」在单测边界上的体现：工具被注册进请求选项并随 Prompt 一起交给模型，
        // 模型（真实实现）据此才会返回 tool_calls 并执行。
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());

        ChatOptions options = captor.getValue().getOptions();
        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);

        boolean clockToolSent = ((ToolCallingChatOptions) options).getToolCallbacks().stream()
                .anyMatch(tc -> "getCurrentTime".equals(tc.getToolDefinition().name()));
        assertThat(clockToolSent).isTrue();
    }

    /** 测试用本地工具，避免依赖项目中已删除的真实工具类。 */
    public static class ClockTools {
        @Tool(description = "获取当前的日期和时间")
        public String getCurrentTime() {
            return "2026-10-07 12:00:00";
        }
    }
}