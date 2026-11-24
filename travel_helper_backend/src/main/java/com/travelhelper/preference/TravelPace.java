package com.travelhelper.preference;

/** 行程节奏：轻松 / 适中 / 紧凑。 */
public enum TravelPace {

    RELAXED("轻松"),
    BALANCED("适中"),
    PACKED("紧凑");

    private final String label;

    TravelPace(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
