package com.qynl.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Friends system — names that are never targeted by combat assists
 * and render green in name-related modules.
 */
public final class Friends {
    private static final Set<String> names = new LinkedHashSet<>();

    private Friends() {}

    public static boolean isFriend(String name) {
        return names.contains(name.toLowerCase());
    }

    public static Set<String> getFriends() {
        return Collections.unmodifiableSet(names);
    }

    public static void add(String name) {
        names.add(name.toLowerCase().trim());
        save();
    }

    public static void remove(String name) {
        names.remove(name.toLowerCase().trim());
        save();
    }

    public static void load(String raw) {
        names.clear();
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty()) names.add(trimmed);
        }
    }

    public static String serialize() {
        return String.join(",", names);
    }

    private static void save() {
        QynlClient qynl = QynlClient.getInstance();
        if (qynl != null && qynl.getConfig() != null) {
            qynl.getConfig().save();
        }
    }
}