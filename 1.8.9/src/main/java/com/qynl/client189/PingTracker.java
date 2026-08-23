package com.qynl.client189;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * PingTracker — measures the real round-trip time from the keep-alive stream.
 *
 * <p>Most servers fake or skip tab-list latency (1 ms, 0 ms), so this tracks
 * the actual keep-alive exchange instead: the client responds to every server
 * keep-alive, and the gap between that response and the next server keep-alive
 * minus the server's own sending interval is the RTT. The server interval is
 * learned from arrival gaps, so it works whatever interval the server uses.
 * Samples are median filtered; outliers outside 20–600 ms are discarded.</p>
 *
 * <p>Fed by {@code KeepAliveMixin}, read by {@code PhantomModule} and
 * {@code HindsightModule}.</p>
 */
public final class PingTracker {
    private static long lastS2cTime = -1;
    private static long lastC2sTime = -1;
    private static final Deque<Long> arrivalGaps = new ArrayDeque<>();
    private static final Deque<Integer> rttSamples = new ArrayDeque<>();
    private static long arrivalGapMedian = 2000;
    private static int pingMs = -1;

    private PingTracker() {
    }

    public static void onKeepAliveReceived() {
        long now = System.currentTimeMillis();
        if (lastS2cTime > 0) {
            long gap = now - lastS2cTime;
            if (gap > 500 && gap < 10000) {
                arrivalGaps.addLast(gap);
                if (arrivalGaps.size() > 5) arrivalGaps.pollFirst();
                long med = medianLong(arrivalGaps);
                if (med > 0) arrivalGapMedian = med;
            }
        }
        lastS2cTime = now;
        if (lastC2sTime > 0) {
            int est = (int) (now - lastC2sTime - arrivalGapMedian);
            if (est >= 20 && est <= 600) {
                rttSamples.addLast(est);
                if (rttSamples.size() > 5) rttSamples.pollFirst();
                pingMs = medianInt(rttSamples);
            }
        }
    }

    public static void onKeepAliveSent() {
        lastC2sTime = System.currentTimeMillis();
    }

    /** Measured RTT in ms, or -1 until the first valid sample. */
    public static int getPingMs() {
        return pingMs;
    }

    public static boolean hasPing() {
        return pingMs > 0;
    }

    private static long medianLong(Deque<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static int medianInt(Deque<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
