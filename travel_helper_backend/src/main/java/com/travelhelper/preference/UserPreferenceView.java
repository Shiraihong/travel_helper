package com.travelhelper.preference;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/** 对外返回的偏好视图：不直接暴露实体，避免懒加载和字段变更影响 API。 */
public record UserPreferenceView(
        String userId,
        String departureCity,
        TravelPace pace,
        CompanionType companionType,
        Integer adultCount,
        Integer childCount,
        BudgetLevel budgetLevel,
        String budgetNote,
        Set<String> interests,
        Set<String> dietary,
        String lastConversationId,
        Instant updatedAt
) {

    public static UserPreferenceView from(UserPreference preference) {
        return new UserPreferenceView(
                preference.getUserId(),
                preference.getDepartureCity(),
                preference.getPace(),
                preference.getCompanionType(),
                preference.getAdultCount(),
                preference.getChildCount(),
                preference.getBudgetLevel(),
                preference.getBudgetNote(),
                new TreeSet<>(preference.getInterests()),
                new TreeSet<>(preference.getDietaryRestrictions()),
                preference.getLastConversationId(),
                preference.getUpdatedAt());
    }

    /** 用户还没有偏好记录时的空视图，前端无需处理 404。 */
    public static UserPreferenceView empty(String userId) {
        return new UserPreferenceView(userId, null, null, null, null, null,
                null, null, Set.of(), Set.of(), null, null);
    }
}
