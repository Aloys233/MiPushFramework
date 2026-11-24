package com.xiaomi.push.service;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Throttles per-package wake-lock acquisitions so bursts of notifications
 * do not keep waking the screen repeatedly.
 */
final class WakeScreenThrottle {
    static final long DEFAULT_COOLDOWN_MILLIS = 3_000L;

    private final LongSupplier clock;
    private final long cooldownMillis;
    private final Map<String, Long> lastWakeByPackage = new HashMap<>();

    WakeScreenThrottle(LongSupplier clock) {
        this(clock, DEFAULT_COOLDOWN_MILLIS);
    }

    WakeScreenThrottle(LongSupplier clock, long cooldownMillis) {
        this.clock = clock;
        this.cooldownMillis = cooldownMillis;
    }

    synchronized boolean tryAcquire(String packageName) {
        String key = TextUtils.isEmpty(packageName) ? "" : packageName;
        long now = clock.getAsLong();
        Long previous = lastWakeByPackage.get(key);
        if (previous != null && now - previous >= 0L && now - previous < cooldownMillis) {
            return false;
        }
        lastWakeByPackage.put(key, now);
        return true;
    }
}
