package com.travelhelper.preference;

/** 同行人类型。人数单独用 adultCount / childCount 表达。 */
public enum CompanionType {

    SOLO("独自"),
    COUPLE("情侣"),
    FAMILY("家庭"),
    FRIENDS("朋友"),
    COLLEAGUES("同事");

    private final String label;

    CompanionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
