package com.travelhelper.preference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 偏好表的合并语义、跨会话读取、用户隔离。用内存 H2，不碰开发库。 */
@DataJpaTest
@Import({UserPreferenceService.class, PreferencePromptRenderer.class})
class UserPreferenceServiceTest {

    @Autowired
    private UserPreferenceService service;

    @Autowired
    private UserPreferenceRepository repository;

    @Test
    @DisplayName("PATCH 只改提到的字段，不抹掉其他偏好")
    void partialPatchKeepsOtherFields() {
        service.merge("u1", "c1", patch("北京", TravelPace.RELAXED, CompanionType.FAMILY, 2, 1,
                BudgetLevel.COMFORT, "不含机票", List.of("美食", "博物馆"), List.of("不吃辣"), Set.of()));

        // 第二轮只改预算档位
        service.merge("u1", "c2", patch(null, null, null, null, null,
                BudgetLevel.LUXURY, null, null, null, Set.of()));

        UserPreferenceView view = service.findView("u1").orElseThrow();
        assertThat(view.departureCity()).isEqualTo("北京");
        assertThat(view.pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(view.companionType()).isEqualTo(CompanionType.FAMILY);
        assertThat(view.adultCount()).isEqualTo(2);
        assertThat(view.childCount()).isEqualTo(1);
        assertThat(view.budgetLevel()).isEqualTo(BudgetLevel.LUXURY);
        assertThat(view.budgetNote()).isEqualTo("不含机票");
        assertThat(view.interests()).containsExactlyInAnyOrder("美食", "博物馆");
        assertThat(view.dietary()).contains("不吃辣");
        assertThat(view.lastConversationId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("多值字段传空列表表示清空")
    void emptyListClearsMultiValueFields() {
        service.merge("u2", null, patch(null, null, null, null, null, null, null,
                List.of("美食"), List.of("素食"), Set.of()));

        service.merge("u2", null, patch(null, null, null, null, null, null, null,
                List.of(), List.of(), Set.of()));

        UserPreferenceView view = service.findView("u2").orElseThrow();
        assertThat(view.interests()).isEmpty();
        assertThat(view.dietary()).isEmpty();
    }

    @Test
    @DisplayName("clearFields 可以显式清空标量")
    void clearFieldsClearsScalars() {
        service.merge("u3", null, patch("上海", TravelPace.PACKED, CompanionType.COUPLE, 2, 0,
                BudgetLevel.LUXURY, "豪华", null, null, Set.of()));

        service.merge("u3", null, patch(null, null, null, null, null, null, null, null, null,
                Set.of("departureCity", "pace", "companion", "budget")));

        UserPreferenceView view = service.findView("u3").orElseThrow();
        assertThat(view.departureCity()).isNull();
        assertThat(view.pace()).isNull();
        assertThat(view.companionType()).isNull();
        assertThat(view.adultCount()).isNull();
        assertThat(view.childCount()).isNull();
        assertThat(view.budgetLevel()).isNull();
        assertThat(view.budgetNote()).isNull();
    }

    @Test
    @DisplayName("同一用户换会话仍能读到偏好")
    void preferenceIsStableAcrossConversations() {
        service.merge("u4", "conv-a", patch("广州", null, null, null, null, null, null, null, null, Set.of()));

        UserPreferenceView view = service.merge("u4", "conv-b",
                patch(null, null, null, null, null, null, null, null, null, Set.of()));

        assertThat(view.departureCity()).isEqualTo("广州");
        assertThat(view.lastConversationId()).isEqualTo("conv-b");
    }

    @Test
    @DisplayName("不同用户互相隔离")
    void usersAreIsolated() {
        service.merge("alice", null, patch("北京", null, null, null, null, null, null, null, null, Set.of()));
        service.merge("bob", null, patch("上海", null, null, null, null, null, null, null, null, Set.of()));

        assertThat(service.findView("alice").orElseThrow().departureCity()).isEqualTo("北京");
        assertThat(service.findView("bob").orElseThrow().departureCity()).isEqualTo("上海");
    }

    @Test
    @DisplayName("删除后偏好不存在")
    void deleteRemovesPreference() {
        service.merge("u5", null, patch("北京", null, null, null, null, null, null, null, null, Set.of()));

        service.delete("u5");

        assertThat(service.findView("u5")).isEmpty();
    }

    @Test
    @DisplayName("只改子集合也会让主表版本号递增")
    void collectionOnlyChangeStillBumpsVersion() {
        service.merge("u6", null, patch(null, null, null, null, null, null, null,
                List.of("美食"), null, Set.of()));
        long first = repository.findByUserId("u6").orElseThrow().getVersion();

        service.merge("u6", null, patch(null, null, null, null, null, null, null,
                List.of("购物"), null, Set.of()));
        long second = repository.findByUserId("u6").orElseThrow().getVersion();

        assertThat(second).isGreaterThan(first);
    }

    private static PreferencePatch patch(String city, TravelPace pace, CompanionType companion,
                                         Integer adults, Integer children, BudgetLevel budget,
                                         String note, List<String> interests, List<String> dietary,
                                         Set<String> clearFields) {
        return new PreferencePatch(city, pace, companion, adults, children, budget, note,
                interests, dietary, clearFields);
    }
}
