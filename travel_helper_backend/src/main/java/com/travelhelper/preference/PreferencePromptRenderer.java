package com.travelhelper.preference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 把偏好实体渲染成给模型看的「数据块」。
 *
 * <p>只输出已知字段；null / 空集合一律跳过，避免把「未提供」当成事实注入。
 * 集合排序后再拼接，保证同一份数据的 prompt 稳定、便于对比和缓存。
 */
@Component
public class PreferencePromptRenderer {

    public String render(UserPreference preference) {
        List<String> lines = new ArrayList<>();

        if (preference.getDepartureCity() != null) {
            lines.add("出发地：" + preference.getDepartureCity());
        }
        if (preference.getBudgetLevel() != null || preference.getBudgetNote() != null) {
            lines.add("预算：" + budgetText(preference));
        }
        if (preference.getPace() != null) {
            lines.add("节奏：" + preference.getPace().label());
        }
        String companions = companionText(preference);
        if (companions != null) {
            lines.add("同行：" + companions);
        }
        if (!preference.getDietaryRestrictions().isEmpty()) {
            lines.add("忌口：" + joinSorted(preference.getDietaryRestrictions()));
        }
        if (!preference.getInterests().isEmpty()) {
            lines.add("兴趣：" + joinSorted(preference.getInterests()));
        }

        return lines.isEmpty() ? "未提供" : String.join("\n", lines);
    }

    private String budgetText(UserPreference preference) {
        String level = preference.getBudgetLevel() == null
                ? null
                : preference.getBudgetLevel().label();
        String note = preference.getBudgetNote();
        if (level != null && note != null) {
            return level + "（" + note + "）";
        }
        return level != null ? level : note;
    }

    private String companionText(UserPreference preference) {
        String type = preference.getCompanionType() == null
                ? null
                : preference.getCompanionType().label();
        Integer adults = preference.getAdultCount();
        Integer children = preference.getChildCount();

        if (type == null && adults == null && children == null) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        if (type != null) {
            text.append(type);
        }
        if (adults != null || children != null) {
            List<String> counts = new ArrayList<>();
            if (adults != null) {
                counts.add(adults + " 成人");
            }
            if (children != null) {
                counts.add(children + " 儿童");
            }
            if (text.length() > 0) {
                text.append("（").append(String.join(" / ", counts)).append("）");
            } else {
                text.append(String.join(" / ", counts));
            }
        }
        return text.toString();
    }

    private String joinSorted(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining("、"));
    }
}
