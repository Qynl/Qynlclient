package com.qynl.client.module;

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
