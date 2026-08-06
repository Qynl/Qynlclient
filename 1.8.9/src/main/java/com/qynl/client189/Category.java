package com.qynl.client189;

public enum Category {
    ASSIST("Assist"),
    RENDER("Render"),
    GUI("GUI");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
