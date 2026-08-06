package com.qynl.client.module;

public enum Category {
	ASSIST("Assist"),
	RENDER("Render"),
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
