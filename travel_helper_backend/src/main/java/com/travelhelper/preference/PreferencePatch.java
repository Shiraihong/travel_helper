package com.travelhelper.preference;

import java.util.List;
import java.util.Set;

/**
 * 用户偏好的部分更新（PATCH）。
 *
 * <p>三态语义：
 * <ul>
 *   <li>标量 null = 用户没提，保持原值；要清空用 {@code clearFields}；</li>
 *   <li>{@code interests} / {@code dietary}：null = 不改，空列表 = 清空，非空 = 整体替换；</li>
 *   <li>{@code clearFields} 支持：departureCity / pace / companion / budget。</li>
 * </ul>
 */
public record PreferencePatch(
        String departureCity,
        TravelPace pace,
        CompanionType companionType,
        Integer adultCount,
        Integer childCount,
        BudgetLevel budgetLevel,
        String budgetNote,
        List<String> interests,
        List<String> dietary,
        Set<String> clearFields
) {

    /** 什么都不改。 */
    public static PreferencePatch none() {
        return new PreferencePatch(null, null, null, null, null,
                null, null, null, null, Set.of());
    }
}
