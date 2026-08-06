package com.qynl.client189;

import java.util.Arrays;
import java.util.List;

public final class Setting<T> {
    private final String key;
    private final String label;
    private final List<T> options;
    private final boolean numeric;
    private final double min, max, step;
    private final String unit;
    private T value;

    private Setting(String key, String label, T value, List<T> options, boolean numeric,
                    double min, double max, double step, String unit) {
        this.key = key; this.label = label; this.value = value;
        this.options = options; this.numeric = numeric;
        this.min = min; this.max = max; this.step = step; this.unit = unit;
    }

    @SafeVarargs
    public static <T> Setting<T> options(String key, String label, T value, T... options) {
        return new Setting<>(key, label, value, Arrays.asList(options), false, 0, 0, 0, "");
    }

    public static Setting<Double> range(String key, String label, double value, double min, double max, double step, String unit) {
        return new Setting<>(key, label, value, null, true, min, max, step, unit);
    }

    public static Setting<Double> range(String key, String label, double value, double min, double max, double step) {
        return range(key, label, value, min, max, step, "");
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public T getValue() { return value; }
    public double asDouble() { return ((Number) value).doubleValue(); }
    public String valueAsString() { return String.valueOf(value); }

    public String displayString() {
        if (numeric) {
            double v = ((Number) value).doubleValue();
            String num = v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
            return num + unit;
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public void cycle() {
        if (options != null && !options.isEmpty()) {
            int i = options.indexOf(value);
            value = options.get((i + 1) % options.size());
        } else if (numeric) {
            double v = ((Number) value).doubleValue() + step;
            if (v > max) v = min;
            value = (T) Double.valueOf(v);
        }
    }

    @SuppressWarnings("unchecked")
    public void setFromString(String s) {
        if (numeric) value = (T) Double.valueOf(s);
        else value = (T) s;
    }
}
