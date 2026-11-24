package com.travelhelper.preference;

/** 预算档位：只做提示词与大致筛选，不存精确金额。 */
public enum BudgetLevel {

    ECONOMY("经济"),
    COMFORT("舒适"),
    LUXURY("奢华");

    private final String label;

    BudgetLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
