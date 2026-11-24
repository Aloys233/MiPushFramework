package top.trumeet.common.utils;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Sanitised subset of the notification metadata contract.
 */
public final class NotificationMetadata {
    public static final int MAX_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;

    @Nullable public final Integer timeoutSeconds;
    @Nullable public final Boolean enableKeyguard;
    @Nullable public final Boolean enableFloat;
    @Nullable public final Boolean ongoing;
    @Nullable public final Boolean colorized;
    @Nullable public final Integer backgroundColor;
    @Nullable public final Integer visibility;
    @Nullable public final String category;

    private NotificationMetadata(Builder b) {
        timeoutSeconds = b.timeoutSeconds;
        enableKeyguard = b.enableKeyguard;
        enableFloat = b.enableFloat;
        ongoing = b.ongoing;
        colorized = b.colorized;
        backgroundColor = b.backgroundColor;
        visibility = b.visibility;
        category = b.category;
    }

    public static NotificationMetadata from(@Nullable CustomConfiguration configuration) {
        Builder b = new Builder();
        if (configuration == null) return b.build();
        b.timeoutSeconds = intValue(configuration, "timeout", 0, MAX_TIMEOUT_SECONDS);
        if (b.timeoutSeconds == null) {
            b.timeoutSeconds = intValue(configuration, "notification_timeout", 0, MAX_TIMEOUT_SECONDS);
        }
        b.enableKeyguard = boolValue(configuration, "enable_keyguard");
        b.enableFloat = boolValue(configuration, "enable_float");
        b.ongoing = boolValue(configuration, "notification_ongoing");
        b.colorized = boolValue(configuration, "notification_colorized");
        String background = first(configuration, "background_color", "notification_background_color");
        b.backgroundColor = colorValue(background);
        b.visibility = visibility(configuration);
        b.category = category(configuration);
        return b.build();
    }

    @Nullable
    private static Boolean boolValue(CustomConfiguration c, String key) {
        if (!c.keys().contains(key)) return null;
        String value = c.get(key, null);
        if (value == null) return Boolean.FALSE;
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }

    @Nullable
    private static Integer intValue(CustomConfiguration c, String key, int min, int max) {
        String value = c.get(key, null);
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static String first(CustomConfiguration c, String first, String second) {
        String value = c.get(first, null);
        return value == null ? c.get(second, null) : value;
    }

    @Nullable
    private static Integer visibility(CustomConfiguration c) {
        String value = first(c, "notification_visibility", "visibility");
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("public".equals(normalized)) return 1;
        if ("private".equals(normalized)) return 0;
        if ("secret".equals(normalized)) return -1;
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed >= -1 && parsed <= 1 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static String category(CustomConfiguration c) {
        String value = first(c, "notification_category", "category");
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "call": case "navigation": case "message": case "email":
            case "event": case "alarm": case "progress": case "promo":
            case "recommendation": case "service": case "social":
            case "status": case "system": case "transport": case "err":
            case "reminder": case "workout": case "location":
            case "stopwatch": case "missed_call":
                return normalized;
            default: return null;
        }
    }

    @Nullable
    private static Integer colorValue(@Nullable String value) {
        if (value == null) return null;
        String v = value.trim();
        try {
            if (v.matches("#[0-9a-fA-F]{6}")) {
                return (int) (0xff000000L | Long.parseLong(v.substring(1), 16));
            }
            if (v.matches("#[0-9a-fA-F]{8}")) {
                return (int) Long.parseLong(v.substring(1), 16);
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class Builder {
        Integer timeoutSeconds, visibility;
        Boolean enableKeyguard, enableFloat, ongoing, colorized;
        Integer backgroundColor;
        String category;
        NotificationMetadata build() { return new NotificationMetadata(this); }
    }
}
