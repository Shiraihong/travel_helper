package com.travelhelper.preference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 提示词渲染：只输出已知字段，集合稳定排序。 */
class PreferencePromptRendererTest {

    private final PreferencePromptRenderer renderer = new PreferencePromptRenderer();

    @Test
    void emptyPreferenceRendersNotProvided() {
        UserPreference preference = UserPreference.createFor("u");

        assertThat(renderer.render(preference)).isEqualTo("未提供");
    }

    @Test
    void rendersKnownFieldsOnlyAndSortsCollections() {
        UserPreference preference = UserPreference.createFor("u");
        preference.apply(new PreferencePatch(
                "北京", TravelPace.RELAXED, CompanionType.FAMILY, 2, 1,
                BudgetLevel.COMFORT, "不含机票",
                List.of("美食", "博物馆"), List.of("海鲜过敏", "不吃辣"), Set.of()));

        String text = renderer.render(preference);

        assertThat(text)
                .contains("出发地：北京")
                .contains("预算：舒适（不含机票）")
                .contains("节奏：轻松")
                .contains("同行：家庭（2 成人 / 1 儿童）")
                .contains("忌口：不吃辣、海鲜过敏")
                .contains("兴趣：博物馆、美食");
    }

    @Test
    void skipsUnknownFields() {
        UserPreference preference = UserPreference.createFor("u");
        preference.apply(new PreferencePatch(null, null, null, null, null,
                null, "人均 3000", null, null, Set.of()));

        assertThat(renderer.render(preference)).isEqualTo("预算：人均 3000");
    }
}
