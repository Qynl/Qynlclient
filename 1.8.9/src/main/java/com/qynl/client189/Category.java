package com.qynl.client189;

public enum Category {
    COMBAT("Combat"),
    RENDER("Render"),
    UTILITY("Utility"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
