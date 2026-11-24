package com.travelhelper.preference;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户偏好：一个用户一行，跨会话共享。
 *
 * <p>关键点：
 * <ul>
 *   <li>主键是稳定的 {@code user_id}（认证主体的 sub），不是 conversationId；</li>
 *   <li>{@code last_conversation_id} 只用于溯源，不参与查询；</li>
 *   <li>标量字段 null = 用户没说过；多值字段用子表，空集合 = 未提供；</li>
 *   <li>更新一律走 {@link #apply(PreferencePatch)}，只写补丁里出现的字段。</li>
 * </ul>
 */
@Entity
@Table(name = "user_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_preference_user", columnNames = "user_id"))
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 认证主体的稳定标识（如 JWT 的 sub）。 */
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "departure_city", length = 64)
    private String departureCity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "pace", length = 16)
    private TravelPace pace;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "companion_type", length = 16)
    private CompanionType companionType;

    @Column(name = "adult_count")
    private Integer adultCount;

    @Column(name = "child_count")
    private Integer childCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "budget_level", length = 16)
    private BudgetLevel budgetLevel;

    @Column(name = "budget_note", length = 200)
    private String budgetNote;

    @ElementCollection
    @CollectionTable(name = "user_preference_interest",
            joinColumns = @JoinColumn(name = "preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_up_interest", columnNames = {"preference_id", "interest"}))
    @Column(name = "interest", nullable = false, length = 32)
    private Set<String> interests = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_preference_dietary",
            joinColumns = @JoinColumn(name = "preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_up_dietary", columnNames = {"preference_id", "dietary"}))
    @Column(name = "dietary", nullable = false, length = 32)
    private Set<String> dietaryRestrictions = new LinkedHashSet<>();

    /** 最后在哪个会话里更新，仅用于排查问题。 */
    @Column(name = "last_conversation_id", length = 128)
    private String lastConversationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {
        // JPA 需要无参构造
    }

    public static UserPreference createFor(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("userId 过长（最多 128 个字符）");
        }
        UserPreference preference = new UserPreference();
        preference.userId = normalized;
        return preference;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /**
     * 合并补丁：只写 patch 里出现的字段。
     *
     * <ul>
     *   <li>标量：null = 用户没提，保持原值；要清空用 clearFields；</li>
     *   <li>集合：null = 不改；空列表 = 清空；非空 = 整体替换。</li>
     * </ul>
     */
    public void apply(PreferencePatch patch) {
        if (patch == null) {
            return;
        }
        Set<String> clear = patch.clearFields() == null ? Set.of() : patch.clearFields();

        // 1) 显式清空标量（同一字段随后又被赋值时，以赋值为准）
        if (clear.contains("departureCity")) {
            departureCity = null;
        }
        if (clear.contains("pace")) {
            pace = null;
        }
        if (clear.contains("companion")) {
            companionType = null;
            adultCount = null;
            childCount = null;
        }
        if (clear.contains("budget")) {
            budgetLevel = null;
            budgetNote = null;
        }

        // 2) 赋值：null 表示这一轮没提到
        if (patch.departureCity() != null) {
            departureCity = trimToNull(patch.departureCity(), 64);
        }
        if (patch.pace() != null) {
            pace = patch.pace();
        }
        if (patch.companionType() != null) {
            companionType = patch.companionType();
        }
        if (patch.adultCount() != null) {
            adultCount = nonNegative(patch.adultCount(), "adultCount");
        }
        if (patch.childCount() != null) {
            childCount = nonNegative(patch.childCount(), "childCount");
        }
        if (patch.budgetLevel() != null) {
            budgetLevel = patch.budgetLevel();
        }
        if (patch.budgetNote() != null) {
            budgetNote = trimToNull(patch.budgetNote(), 200);
        }

        // 3) 多值：null=不改；[]=清空；[a,b]=整体替换
        if (patch.interests() != null) {
            if (patch.interests().isEmpty()) {
                interests.clear();
            } else {
                replaceAll(interests, patch.interests(), 32);
            }
        }
        if (patch.dietary() != null) {
            if (patch.dietary().isEmpty()) {
                dietaryRestrictions.clear();
            } else {
                replaceAll(dietaryRestrictions, patch.dietary(), 32);
            }
        }

        // 关键：即使只改了 @ElementCollection，也要让主表变脏，
        // 否则 @Version / updated_at 可能不更新。
        updatedAt = Instant.now();
    }

    public void touchConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }
        String normalized = conversationId.trim();
        if (!normalized.isEmpty()) {
            lastConversationId = normalized.length() > 128
                    ? normalized.substring(0, 128)
                    : normalized;
        }
    }

    private static String trimToNull(String value, int maxLength) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static Integer nonNegative(Integer value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能为负数");
        }
        return value;
    }

    private static void replaceAll(Set<String> target, Collection<String> values, int maxLength) {
        target.clear();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            target.add(value.length() > maxLength ? value.substring(0, maxLength) : value);
        }
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public TravelPace getPace() {
        return pace;
    }

    public CompanionType getCompanionType() {
        return companionType;
    }

    public Integer getAdultCount() {
        return adultCount;
    }

    public Integer getChildCount() {
        return childCount;
    }

    public BudgetLevel getBudgetLevel() {
        return budgetLevel;
    }

    public String getBudgetNote() {
        return budgetNote;
    }

    public Set<String> getInterests() {
        return interests;
    }

    public Set<String> getDietaryRestrictions() {
        return dietaryRestrictions;
    }

    public String getLastConversationId() {
        return lastConversationId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
