package com.qynl.client.module;

import java.util.List;

/**
 * A single user-tweakable option of a module. Two kinds exist:
 * <ul>
 *   <li>{@link #options(String, String, Object, Object...)} — pick one of several values (e.g. mode).</li>
 *   <li>{@link #range(String, String, double, double, double, double)} — a number you can step up/down.</li>
 * </ul>
 * Values are saved to the config as strings and restored on startup.
 */
public final class Setting<T> {
	private final String key;
	private final String label;
	private final List<T> options;
	private final boolean numeric;
	private final double min;
	private final double max;
	private final double step;
	private final String unit;
	private T value;

	private Setting(String key, String label, T value, List<T> options, boolean numeric,
					double min, double max, double step, String unit) {
		this.key = key;
		this.label = label;
		this.value = value;
		this.options = options;
		this.numeric = numeric;
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
	}

	public static <T> Setting<T> options(String key, String label, T value, T... options) {
		return new Setting<>(key, label, value, List.of(options), false, 0, 0, 0, "");
	}

	public static Setting<Double> range(String key, String label, double value, double min, double max, double step) {
		return range(key, label, value, min, max, step, "");
	}

	public static Setting<Double> range(String key, String label, double value, double min, double max, double step, String unit) {
		return new Setting<>(key, label, value, null, true, min, max, step, unit);
	}

	public static Setting<String> text(String key, String label, String value) {
		return new Setting<>(key, label, value, null, false, 0, 0, 0, "");
	}

	public String getKey() {
		return key;
	}

	public String getLabel() {
		return label;
	}

	public T getValue() {
		return value;
	}

	public double asDouble() {
		return ((Number) value).doubleValue();
	}

	/** Raw string form used when saving to the config. */
	public String valueAsString() {
		return String.valueOf(value);
	}

	/** Human-readable form shown in the settings screen. */
	public String displayString() {
		if (numeric) {
			double v = ((Number) value).doubleValue();
			String num = v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
			return num + unit;
		}
		if (value instanceof Boolean b) {
			return b ? "On" : "Off";
		}
		return String.valueOf(value);
	}

	/** Move to the next option / step the number up (wrapping back to the start). */
	public void cycle() {
		if (options != null && !options.isEmpty()) {
			int i = options.indexOf(value);
			value = options.get((i + 1) % options.size());
		} else if (numeric) {
			double v = ((Number) value).doubleValue() + step;
			if (v > max) {
				v = min;
			}
			value = (T) Double.valueOf(v);
		}
	}

	@SuppressWarnings("unchecked")
	public void setFromString(String s) {
		if (s == null) {
			return;
		}
		if (numeric) {
			try {
				double parsed = Double.parseDouble(s.trim());
				if (!Double.isFinite(parsed)) {
					return;
				}
				value = (T) Double.valueOf(Math.max(min, Math.min(max, parsed)));
			} catch (NumberFormatException ignored) {
				// Keep the current value when a config or GUI edit is invalid.
			}
		} else if (value instanceof Boolean) {
			value = (T) Boolean.valueOf(s);
		} else {
			value = (T) s;
		}
	}
}
