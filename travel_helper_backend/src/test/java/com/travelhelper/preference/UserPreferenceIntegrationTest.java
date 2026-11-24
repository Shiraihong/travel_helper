package com.travelhelper.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 偏好接口 + 聊天注入的集成测试。
 * 用内存 H2，mock 掉 ChatModel，不发起真实模型调用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:preference-it;DB_CLOSE_DELAY=-1",
        "travel-helper.auth.dev-user-id=dev-user"
})
class UserPreferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserPreferenceRepository repository;

    @Autowired
    private UserPreferenceService service;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("没有记录时返回空视图，而不是 404")
    void returnsEmptyViewForNewUser() throws Exception {
        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("dev-user"))
                .andExpect(jsonPath("$.departureCity").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.interests").isEmpty());
    }

    @Test
    @DisplayName("PATCH 合并更新，GET 读取，DELETE 清空")
    void patchGetAndDelete() throws Exception {
        mockMvc.perform(patch("/api/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departureCity": "北京",
                                  "pace": "RELAXED",
                                  "companionType": "FAMILY",
                                  "adultCount": 2,
                                  "childCount": 1,
                                  "budgetLevel": "COMFORT",
                                  "budgetNote": "不含机票",
                                  "interests": ["美食"],
                                  "dietary": ["不吃辣"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("dev-user"))
                .andExpect(jsonPath("$.departureCity").value("北京"))
                .andExpect(jsonPath("$.budgetLevel").value("COMFORT"))
                .andExpect(jsonPath("$.interests", org.hamcrest.Matchers.hasItem("美食")));

        // 只改预算，其他字段必须保留
        mockMvc.perform(patch("/api/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetLevel\":\"LUXURY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureCity").value("北京"))
                .andExpect(jsonPath("$.budgetLevel").value("LUXURY"))
                .andExpect(jsonPath("$.interests", org.hamcrest.Matchers.hasItem("美食")));

        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureCity").value("北京"));

        mockMvc.perform(delete("/api/preferences"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("dev-user"))
                .andExpect(jsonPath("$.interests").isEmpty());
    }

    @Test
    @DisplayName("已保存的偏好会被注入到聊天 Prompt")
    void savedPreferenceIsInjectedIntoChatPrompt() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("ok")))));

        service.merge("dev-user", "conv-1", new PreferencePatch(
                "北京", TravelPace.RELAXED, CompanionType.FAMILY, 2, 1,
                BudgetLevel.COMFORT, "不含机票",
                List.of("美食"), List.of("不吃辣"), null));

        mockMvc.perform(get("/api/chat").param("message", "帮我规划行程"))
                .andExpect(status().isOk());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String promptText = captor.getValue().getInstructions().toString();

        assertThat(promptText)
                .contains("出发地：北京")
                .contains("预算：舒适（不含机票）")
                .contains("节奏：轻松")
                .contains("同行：家庭（2 成人 / 1 儿童）")
                .contains("兴趣：美食");
    }
}
