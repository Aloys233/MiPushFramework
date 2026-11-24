package com.xiaomi.xmsf.push.notification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Pure-Java safety and parsing policy for Xiaomi focus-notification payloads,
 * adapted to extract structured progress and status data for Android Live Updates.
 */
public final class FocusNotificationSafety {
    public static final String FOCUS_EXTRA_PREFIX = "miui.focus.";
    public static final int MAX_PARAMETER_BYTES = 3_072;
    public static final int PORTABLE_PROGRESS_MAX = 100;
    public static final int PORTABLE_MERCHANT_STAGE_PROGRESS = 10;
    public static final int PORTABLE_COURIER_STAGE_PROGRESS = 75;
    public static final int PORTABLE_DELIVERED_STAGE_PROGRESS = 100;

    private static final int MAX_FALLBACK_TITLE_CODE_POINTS = 160;
    private static final int MAX_FALLBACK_BODY_CODE_POINTS = 1_024;
    private static final int MAX_FALLBACK_URL_CODE_POINTS = 2_048;
    private static final int MAX_SEQUENCE_CODE_POINTS = 128;
    private static final int MAX_SCENE_CODE_POINTS = 64;
    private static final int MAX_SHORT_CRITICAL_CODE_POINTS = 10;
    private static final String FOOD_DELIVERY_SCENE = "foodDelivery";
    private static final String FOOD_DELIVERY_BUSINESS = "food_delivery";
    private static final String DEFAULT_TITLE = "MiPush notification";
    private static final String DEFAULT_BODY = "New notification";
    private static final String FOCUS_GROUP_MARKER = "#focus#";

    private static final String[] TERMINAL_KEYWORDS = {
            "已送达", "已完成", "已取消", "已结束", "已关闭", "已退款", "已签收",
            "delivered", "completed", "cancelled", "canceled", "finished"
    };

    private FocusNotificationSafety() {
    }

    public static ResolvedContent resolveReadableContent(
            String title,
            String body,
            String focusParameter,
            String fallbackTitle,
            String fallbackBody) {
        return resolveReadableContent(title, body, focusParameter, null, fallbackTitle, fallbackBody);
    }

    public static ResolvedContent resolveReadableContent(
            String title,
            String body,
            String focusParameter,
            String focusCustomParameter,
            String fallbackTitle,
            String fallbackBody) {
        PortableFocusData portable = parsePortableFocusData(focusParameter, focusCustomParameter);

        String resolvedTitle = hasText(title)
                ? title
                : firstText(portable.title(), portable.body(),
                sanitizeFallback(fallbackTitle, MAX_FALLBACK_TITLE_CODE_POINTS),
                DEFAULT_TITLE);
        String resolvedBody = hasText(body)
                ? body
                : firstText(portable.body(), portable.title(),
                sanitizeFallback(fallbackBody, MAX_FALLBACK_BODY_CODE_POINTS),
                DEFAULT_BODY);
        return new ResolvedContent(resolvedTitle, resolvedBody);
    }

    public static PortableFocusData parsePortableFocusData(String focusParameter) {
        return parsePortableFocusData(focusParameter, null);
    }

    public static PortableFocusData parsePortableFocusData(
            String focusParameter, String focusCustomParameter) {
        PortableFocusData primary = parseSingleFocusJson(focusParameter);
        PortableFocusData custom = parseSingleFocusJson(focusCustomParameter);
        if (primary == PortableFocusData.EMPTY) {
            return custom;
        }
        if (custom == PortableFocusData.EMPTY) {
            return primary;
        }
        return primary.mergeWithFallback(custom);
    }

    private static PortableFocusData parseSingleFocusJson(String focusParameter) {
        if (!isParameterWithinLimit(focusParameter)) {
            return PortableFocusData.EMPTY;
        }
        try {
            JsonElement parsed = JsonParser.parseString(focusParameter);
            if (parsed == null || !parsed.isJsonObject()) {
                return PortableFocusData.EMPTY;
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonObject paramV2 = object(root, "param_v2");
            JsonObject baseInfo = object(paramV2, "baseInfo");
            JsonObject progressInfo = object(paramV2, "progressInfo");
            JsonObject paramIsland = object(paramV2, "param_island");

            String scene = firstString(root, MAX_SCENE_CODE_POINTS, "scene");
            if (!hasText(scene)) {
                scene = firstString(paramV2, MAX_SCENE_CODE_POINTS, "scene");
            }
            String business = firstString(paramV2, MAX_SCENE_CODE_POINTS, "business");
            if (!hasText(business)) {
                business = firstString(root, MAX_SCENE_CODE_POINTS, "business");
            }

            String title = firstString(root, MAX_FALLBACK_TITLE_CODE_POINTS,
                    "title", "ticker");
            if (!hasText(title)) {
                title = firstString(baseInfo, MAX_FALLBACK_TITLE_CODE_POINTS,
                        "title");
            }
            if (!hasText(title)) {
                title = firstString(paramV2, MAX_FALLBACK_TITLE_CODE_POINTS,
                        "aodTitle");
            }
            if (!hasText(title)) {
                title = extractIslandTitle(paramIsland);
            }

            String body = firstString(root, MAX_FALLBACK_BODY_CODE_POINTS,
                    "content", "description");
            if (!hasText(body)) {
                body = firstString(baseInfo, MAX_FALLBACK_BODY_CODE_POINTS,
                        "content", "description", "subContent", "extra");
            }
            if (!hasText(body)) {
                body = firstString(paramV2, MAX_FALLBACK_BODY_CODE_POINTS,
                        "aodDetail", "subContent");
            }

            String url = firstString(root, MAX_FALLBACK_URL_CODE_POINTS,
                    "url", "intent_uri", "web_uri");
            if (!hasText(url)) {
                url = firstString(paramV2, MAX_FALLBACK_URL_CODE_POINTS,
                        "url", "intent_uri", "web_uri");
            }

            int progress = firstNonNegativeInt(root, PORTABLE_PROGRESS_MAX,
                    "progress");
            if (progress < 0) {
                progress = firstNonNegativeInt(progressInfo, PORTABLE_PROGRESS_MAX,
                        "progress");
            }

            int progressCount = firstNonNegativeInt(root, PORTABLE_PROGRESS_MAX,
                    "progressCount");
            if (progressCount < 0) {
                progressCount = firstNonNegativeInt(paramV2, PORTABLE_PROGRESS_MAX,
                        "progressCount");
            }

            Boolean updatable = firstBoolean(root, "updatable");
            if (updatable == null) {
                updatable = firstBoolean(paramV2, "updatable");
            }

            String sequence = firstScalarString(root, MAX_SEQUENCE_CODE_POINTS,
                    "sequence");
            if (!hasText(sequence)) {
                sequence = firstScalarString(paramV2, MAX_SEQUENCE_CODE_POINTS,
                        "sequence");
            }

            String accentColor = firstString(progressInfo, 32,
                    "colorProgress", "colorProgressEnd");
            if (!hasText(accentColor)) {
                accentColor = firstString(paramIsland, 32, "highlightColor");
            }
            if (!hasText(accentColor)) {
                accentColor = firstString(baseInfo, 32, "colorSubTitle");
            }
            if (!hasText(accentColor)) {
                accentColor = firstString(root, 32, "colorDesc", "colorDescDark");
            }

            String shortCriticalText = extractShortCriticalText(
                    root, paramV2, baseInfo, paramIsland, scene, business, progress, title, body);

            return new PortableFocusData(title, body, url, sequence, scene, business,
                    progress, progressCount, Boolean.TRUE.equals(updatable),
                    accentColor, shortCriticalText);
        } catch (Throwable ignored) {
            return PortableFocusData.EMPTY;
        }
    }

    private static String extractIslandTitle(JsonObject paramIsland) {
        if (paramIsland == null) {
            return null;
        }
        JsonObject bigIsland = object(paramIsland, "bigIslandArea");
        JsonObject fixedWidth = object(bigIsland, "fixedWidthDigitInfo");
        String text = firstString(fixedWidth, MAX_FALLBACK_TITLE_CODE_POINTS, "title", "content");
        if (hasText(text)) {
            return text;
        }
        JsonObject textInfo = object(bigIsland, "imageTextInfo");
        JsonObject innerText = object(textInfo, "textInfo");
        text = firstString(innerText, MAX_FALLBACK_TITLE_CODE_POINTS, "title");
        if (hasText(text)) {
            return text;
        }
        return firstString(paramIsland, MAX_FALLBACK_TITLE_CODE_POINTS, "title");
    }

    private static String extractShortCriticalText(
            JsonObject root,
            JsonObject paramV2,
            JsonObject baseInfo,
            JsonObject paramIsland,
            String scene,
            String business,
            int progress,
            String title,
            String body) {
        String candidate = null;
        if (paramIsland != null) {
            JsonObject smallIsland = object(paramIsland, "smallIslandArea");
            JsonObject combinePicInfo = object(smallIsland, "combinePicInfo");
            candidate = firstString(combinePicInfo, 32, "text", "title");
            if (!hasText(candidate)) {
                candidate = firstString(smallIsland, 32, "text", "title");
            }
            if (!hasText(candidate)) {
                JsonObject bigIsland = object(paramIsland, "bigIslandArea");
                JsonObject sameWidth = object(bigIsland, "sameWidthDigitInfo");
                candidate = firstString(sameWidth, 32, "digit", "content");
            }
        }
        if (!hasText(candidate)) {
            candidate = firstString(baseInfo, 32, "subTitle", "extra");
        }
        if (!hasText(candidate)) {
            candidate = firstString(root, 32, "status", "ticker");
        }
        if (!hasText(candidate) && isFoodDelivery(scene, business) && progress >= 0) {
            if (progress < PORTABLE_MERCHANT_STAGE_PROGRESS) {
                candidate = "待接单";
            } else if (progress < PORTABLE_COURIER_STAGE_PROGRESS) {
                candidate = "备餐中";
            } else if (progress < PORTABLE_DELIVERED_STAGE_PROGRESS) {
                candidate = "配送中";
            } else {
                candidate = "已送达";
            }
        }
        if (!hasText(candidate)) {
            candidate = hasText(title) ? title : body;
        }
        return shortenForChip(candidate);
    }

    private static String shortenForChip(String raw) {
        String clean = sanitizeFallback(raw, 32);
        if (!hasText(clean)) {
            return null;
        }
        int count = clean.codePointCount(0, clean.length());
        if (count <= MAX_SHORT_CRITICAL_CODE_POINTS) {
            return clean;
        }
        int end = clean.offsetByCodePoints(0, MAX_SHORT_CRITICAL_CODE_POINTS - 1);
        return clean.substring(0, end) + "…";
    }

    private static boolean isFoodDelivery(String scene, String business) {
        return FOOD_DELIVERY_SCENE.equals(scene)
                || (!hasText(scene) && FOOD_DELIVERY_BUSINESS.equals(business));
    }

    public static boolean isParameterWithinLimit(String parameter) {
        if (parameter == null || parameter.length() > MAX_PARAMETER_BYTES) {
            return false;
        }
        return parameter.getBytes(StandardCharsets.UTF_8).length <= MAX_PARAMETER_BYTES;
    }

    public static boolean isWellFormedParameter(String parameter) {
        if (!isParameterWithinLimit(parameter)) {
            return false;
        }
        try {
            JsonElement root = JsonParser.parseString(parameter);
            return root != null && root.isJsonObject();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isClickRoutePayload(String parameter) {
        if (!isWellFormedParameter(parameter)) {
            return false;
        }
        try {
            JsonElement parsed = JsonParser.parseString(parameter);
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject paramV2 = object(root, "param_v2");
            return (hasPositiveProtocol(root) || hasPositiveProtocol(paramV2))
                    && (hasSequence(root) || hasSequence(paramV2));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasPositiveProtocol(JsonObject object) {
        if (object == null) {
            return false;
        }
        JsonElement protocol = object.get("protocol");
        if (protocol == null || !protocol.isJsonPrimitive()) {
            return false;
        }
        try {
            return protocol.getAsInt() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasSequence(JsonObject object) {
        if (object == null) {
            return false;
        }
        JsonElement sequence = object.get("sequence");
        return sequence != null && sequence.isJsonPrimitive()
                && sequence.getAsString() != null
                && !sequence.getAsString().trim().isEmpty();
    }

    public static boolean isFocusExtraKey(String key) {
        return key != null && key.startsWith(FOCUS_EXTRA_PREFIX);
    }

    public static String stableFocusGroup(String packageName) {
        String prefix = hasText(packageName) ? packageName : "mipush";
        return prefix + "_" + FOCUS_GROUP_MARKER;
    }

    public static boolean isFocusGroup(String groupId) {
        return groupId != null && groupId.endsWith("_" + FOCUS_GROUP_MARKER);
    }

    public static boolean isTerminalText(String title, String body, String shortText) {
        String combined = ((title == null ? "" : title) + " "
                + (body == null ? "" : body) + " "
                + (shortText == null ? "" : shortText)).toLowerCase(java.util.Locale.ROOT);
        for (String keyword : TERMINAL_KEYWORDS) {
            if (combined.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T deliverWithSingleFallback(
            String packageName,
            String tag,
            int notificationId,
            boolean attemptFocus,
            Delivery<T> delivery) {
        if (!attemptFocus) {
            return invoke(delivery, packageName, tag, notificationId, false, null);
        }
        try {
            return delivery.deliver(packageName, tag, notificationId, true, null);
        } catch (Throwable focusFailure) {
            try {
                return delivery.deliver(packageName, tag, notificationId,
                        false, focusFailure);
            } catch (Throwable fallbackFailure) {
                if (fallbackFailure != focusFailure) {
                    try {
                        fallbackFailure.addSuppressed(focusFailure);
                    } catch (Throwable ignored) {
                    }
                }
                return rethrow(fallbackFailure);
            }
        }
    }

    private static <T> T invoke(
            Delivery<T> delivery,
            String packageName,
            String tag,
            int notificationId,
            boolean includeFocus,
            Throwable focusFailure) {
        try {
            return delivery.deliver(packageName, tag, notificationId,
                    includeFocus, focusFailure);
        } catch (Throwable failure) {
            return rethrow(failure);
        }
    }

    private static <T> T rethrow(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Notification delivery failed", failure);
    }

    private static String boundedString(JsonObject object, String name, int maxCodePoints) {
        if (object == null) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return sanitizeFallback(value.getAsString(), maxCodePoints);
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return null;
        }
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String firstString(
            JsonObject object, int maxCodePoints, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            String value = boundedString(object, name, maxCodePoints);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstScalarString(
            JsonObject object, int maxCodePoints, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                String result = sanitizeFallback(value.getAsString(), maxCodePoints);
                if (hasText(result)) {
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int firstNonNegativeInt(
            JsonObject object, int maximum, String... names) {
        if (object == null || names == null) {
            return -1;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                int parsed = value.getAsInt();
                if (parsed >= 0) {
                    return Math.min(maximum, parsed);
                }
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private static Boolean firstBoolean(JsonObject object, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                String raw = value.getAsString();
                if ("true".equalsIgnoreCase(raw)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(raw)) {
                    return Boolean.FALSE;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String sanitizeFallback(String value, int maxCodePoints) {
        if (!hasText(value)) {
            return null;
        }
        StringBuilder clean = new StringBuilder(Math.min(value.length(), maxCodePoints));
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isISOControl(codePoint)
                    || codePoint == '\n' || codePoint == '\t') {
                clean.appendCodePoint(codePoint);
            }
        }
        String result = clean.toString().trim();
        if (!hasText(result)) {
            return null;
        }
        int count = result.codePointCount(0, result.length());
        if (count <= maxCodePoints) {
            return result;
        }
        int end = result.offsetByCodePoints(0, maxCodePoints);
        return result.substring(0, end);
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("At least one safe fallback must be present");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    public interface Delivery<T> {
        T deliver(String packageName, String tag, int notificationId,
                  boolean includeFocusExtras, Throwable focusFailure) throws Throwable;
    }

    public static final class ResolvedContent {
        private final String title;
        private final String body;

        private ResolvedContent(String title, String body) {
            this.title = title;
            this.body = body;
        }

        public String title() {
            return title;
        }

        public String body() {
            return body;
        }
    }

    public static final class PortableFocusData {
        public static final PortableFocusData EMPTY = new PortableFocusData(
                null, null, null, null, null, null, -1, -1, false, null, null);

        private final String title;
        private final String body;
        private final String url;
        private final String sequence;
        private final String scene;
        private final String business;
        private final int progress;
        private final int progressCount;
        private final boolean updatable;
        private final String accentColor;
        private final String shortCriticalText;

        private PortableFocusData(
                String title,
                String body,
                String url,
                String sequence,
                String scene,
                String business,
                int progress,
                int progressCount,
                boolean updatable,
                String accentColor,
                String shortCriticalText) {
            this.title = title;
            this.body = body;
            this.url = url;
            this.sequence = sequence;
            this.scene = scene;
            this.business = business;
            this.progress = progress;
            this.progressCount = progressCount;
            this.updatable = updatable;
            this.accentColor = accentColor;
            this.shortCriticalText = shortCriticalText;
        }

        public static PortableFocusData forDetectedLiveUpdate(
                String title,
                String body,
                String scene,
                int progress,
                String accentColor,
                String shortCriticalText) {
            int normalizedProgress = Math.max(0, Math.min(PORTABLE_PROGRESS_MAX, progress));
            return new PortableFocusData(
                    title,
                    body,
                    null,
                    null,
                    scene,
                    scene,
                    normalizedProgress,
                    "foodDelivery".equalsIgnoreCase(scene) ? 3 : -1,
                    true,
                    accentColor,
                    shortCriticalText
            );
        }

        PortableFocusData mergeWithFallback(PortableFocusData fallback) {
            return new PortableFocusData(
                    hasText(title) ? title : fallback.title,
                    hasText(body) ? body : fallback.body,
                    hasText(url) ? url : fallback.url,
                    hasText(sequence) ? sequence : fallback.sequence,
                    hasText(scene) ? scene : fallback.scene,
                    hasText(business) ? business : fallback.business,
                    progress >= 0 ? progress : fallback.progress,
                    progressCount >= 0 ? progressCount : fallback.progressCount,
                    updatable || fallback.updatable,
                    hasText(accentColor) ? accentColor : fallback.accentColor,
                    hasText(shortCriticalText) ? shortCriticalText : fallback.shortCriticalText
            );
        }

        public boolean isEmpty() {
            return this == EMPTY
                    || (!hasText(title) && !hasText(body) && !hasProgress() && !hasText(shortCriticalText));
        }

        public String title() {
            return title;
        }

        public String body() {
            return body;
        }

        public String url() {
            return url;
        }

        public String sequence() {
            return sequence;
        }

        public String scene() {
            return scene;
        }

        public String business() {
            return business;
        }

        public int progress() {
            return progress;
        }

        public int progressCount() {
            return progressCount;
        }

        public boolean updatable() {
            return updatable;
        }

        public String accentColor() {
            return accentColor;
        }

        public String shortCriticalText() {
            return shortCriticalText;
        }

        public boolean hasProgress() {
            return progress >= 0;
        }

        public boolean isFoodDeliveryTimeline() {
            return isFoodDelivery(scene, business);
        }

        public boolean isTerminalState() {
            return progress >= PORTABLE_DELIVERED_STAGE_PROGRESS
                    || isTerminalText(title, body, shortCriticalText);
        }

        public int stageIndex() {
            if (!hasProgress() || progress < PORTABLE_MERCHANT_STAGE_PROGRESS) {
                return -1;
            }
            if (progress < PORTABLE_COURIER_STAGE_PROGRESS) {
                return 0;
            }
            if (progress < PORTABLE_DELIVERED_STAGE_PROGRESS) {
                return 1;
            }
            return 2;
        }
    }
}
