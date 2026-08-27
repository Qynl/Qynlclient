package com.qynl.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Live "did it fire?" feed for the HUD.
 *
 * <p>Combat features report events here ("WTap", "Trigger", ...) and the HUD
 * draws short glass chips for them. A feature that works is visible; a
 * feature that never fires is silent — which instantly separates a broken
 * build (old jar, not enabled) from a real bug.</p>
 *
 * <p>Identical labels arriving within 600&nbsp;ms are merged into one chip
 * whose lifetime refreshes — a 12-CPS triggerbot shows one steady chip
 * instead of twelve blinking ones.</p>
 */
public final class FeatureFeed {

    public static final class Chip {
        public final String label;
        public long bornMs;

        Chip(String label, long bornMs) {
            this.label = label;
            this.bornMs = bornMs;
        }
    }

    private static final Deque<Chip> CHIPS = new ArrayDeque<>();
    private static final int MAX_CHIPS = 6;
    private static final long CHIP_TTL_MS = 1400;

    /** Reports a feature event. Cheap; safe from any thread. */
    public static void report(String label) {
        if (label == null || label.isEmpty()) return;
        long now = System.currentTimeMillis();
        synchronized (CHIPS) {
            // Merge into an existing identical chip while it is alive.
            for (Chip c : CHIPS) {
                if (c.label.equals(label) && now - c.bornMs <= CHIP_TTL_MS) {
                    c.bornMs = now;
                    return;
                }
            }
            CHIPS.addLast(new Chip(label, now));
            while (CHIPS.size() > MAX_CHIPS) {
                CHIPS.removeFirst();
            }
        }
    }

    /** Chips still alive for drawing, oldest first. */
    public static List<Chip> live() {
        long now = System.currentTimeMillis();
        List<Chip> out = new ArrayList<>();
        synchronized (CHIPS) {
            CHIPS.removeIf(c -> now - c.bornMs > CHIP_TTL_MS);
            out.addAll(CHIPS);
        }
        return out;
    }

    private FeatureFeed() {
    }
}
