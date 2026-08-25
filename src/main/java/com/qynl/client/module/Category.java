package com.qynl.client.module;

public enum Category {
	COMBAT("Combat"),
	ASSIST("Assist"),
	RENDER("Render"),
	UTIL("Utility"),
	INFO("Info"),
	GUI("GUI");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}