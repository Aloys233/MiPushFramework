package com.xiaomi.xmsf.push.notification;

import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts Xiaomi focus notification metadata into Android 16+ (API 36+)
 * native Live Updates (Promoted Ongoing notifications with ProgressStyle).
 */
@RequiresApi(36)
public final class LiveUpdateNotificationHelper {

    private static final int DEFAULT_ACCENT_COLOR = Color.rgb(255, 98, 0);
    private static final int INACTIVE_SEGMENT_COLOR = Color.argb(90, 140, 140, 140);

    private LiveUpdateNotificationHelper() {
    }

    @NonNull
    public static Notification buildLiveUpdateNotification(
            @NonNull Context context,
            @NonNull Notification baseNotification,
            @NonNull FocusNotificationSafety.PortableFocusData focus,
            @NonNull FocusNotificationSafety.ResolvedContent content) {
        Notification.Builder builder = Notification.Builder.recoverBuilder(context, baseNotification);

        // Live Updates 要求非空标题、禁止 colorized、禁止 groupSummary、禁止自定义 RemoteViews。
        builder.setContentTitle(content.title());
        builder.setContentText(content.body());
        builder.setColorized(false);
        builder.setGroupSummary(false);
        builder.setCustomContentView(null);
        builder.setCustomBigContentView(null);
        builder.setCustomHeadsUpContentView(null);

        Bundle cleanedExtras = baseNotification.extras == null
                ? new Bundle() : new Bundle(baseNotification.extras);
        stripFocusKeys(cleanedExtras);
        boolean terminal = focus.isTerminalState();
        cleanedExtras.putBoolean("android.requestPromotedOngoing", !terminal);
        builder.setExtras(cleanedExtras);

        builder.setOngoing(!terminal);
        builder.setAutoCancel(terminal);
        try {
            builder.setRequestPromotedOngoing(!terminal);
        } catch (LinkageError ignored) {
            // API 36.0 设备没有该方法，保留 extras 中的兼容字段
        }

        if (focus.updatable() || focus.hasProgress()) {
            builder.setOnlyAlertOnce(true);
        }

        try {
            if (!terminal && !TextUtils.isEmpty(focus.shortCriticalText())) {
                builder.setShortCriticalText(focus.shortCriticalText());
            } else {
                builder.setShortCriticalText(null);
            }
        } catch (LinkageError ignored) {
        }

        if (focus.hasProgress()) {
            builder.setStyle(buildProgressStyle(focus, content));
            builder.setCategory(Notification.CATEGORY_PROGRESS);
        } else {
            Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle()
                    .setBigContentTitle(content.title())
                    .bigText(content.body());
            builder.setStyle(bigTextStyle);
        }

        return builder.build();
    }

    private static Notification.ProgressStyle buildProgressStyle(
            @NonNull FocusNotificationSafety.PortableFocusData focus,
            @NonNull FocusNotificationSafety.ResolvedContent content) {
        int progress = Math.max(0, Math.min(FocusNotificationSafety.PORTABLE_PROGRESS_MAX, focus.progress()));
        int accent = resolveAccentColor(focus);
        int semanticStyle = resolveSemanticStyle(focus, content, progress);

        Notification.ProgressStyle style = new Notification.ProgressStyle()
                .setProgress(progress)
                .setStyledByProgress(true);

        if (focus.isFoodDeliveryTimeline()) {
            // 三段总长 = 100，分别对应备餐(0..35)、配送(35..75)、送达(75..100)
            List<Notification.ProgressStyle.Segment> segments = Arrays.asList(
                    applySegmentSemanticStyle(
                            new Notification.ProgressStyle.Segment(35)
                                    .setColor(progress >= 10 ? accent : INACTIVE_SEGMENT_COLOR),
                            progress >= 10 ? semanticStyle : null),
                    applySegmentSemanticStyle(
                            new Notification.ProgressStyle.Segment(40)
                                    .setColor(progress >= 35 ? accent : INACTIVE_SEGMENT_COLOR),
                            progress >= 35 ? semanticStyle : null),
                    applySegmentSemanticStyle(
                            new Notification.ProgressStyle.Segment(25)
                                    .setColor(progress >= 75 ? accent : INACTIVE_SEGMENT_COLOR),
                            progress >= 75 ? semanticStyle : null)
            );
            List<Notification.ProgressStyle.Point> points = Arrays.asList(
                    applyPointSemanticStyle(
                            new Notification.ProgressStyle.Point(35)
                                    .setColor(progress >= 35 ? accent : INACTIVE_SEGMENT_COLOR),
                            progress >= 35 ? semanticStyle : null),
                    applyPointSemanticStyle(
                            new Notification.ProgressStyle.Point(75)
                                    .setColor(progress >= 75 ? accent : INACTIVE_SEGMENT_COLOR),
                            progress >= 75 ? semanticStyle : null)
            );
            style.setProgressSegments(segments);
            style.setProgressPoints(points);
        } else {
            List<Notification.ProgressStyle.Segment> segments = Arrays.asList(
                    applySegmentSemanticStyle(
                            new Notification.ProgressStyle.Segment(FocusNotificationSafety.PORTABLE_PROGRESS_MAX)
                                    .setColor(accent),
                            semanticStyle)
            );
            style.setProgressSegments(segments);
        }

        return style;
    }

    private static int resolveSemanticStyle(
            @NonNull FocusNotificationSafety.PortableFocusData focus,
            @NonNull FocusNotificationSafety.ResolvedContent content,
            int progress) {
        String combined = (content.title() + " " + content.body() + " "
                + (focus.shortCriticalText() == null ? "" : focus.shortCriticalText())).toLowerCase();
        if (combined.contains("取消") || combined.contains("退款") || combined.contains("cancel")) {
            return Notification.SEMANTIC_STYLE_DANGER;
        }
        if (focus.isTerminalState() && progress >= FocusNotificationSafety.PORTABLE_PROGRESS_MAX) {
            return Notification.SEMANTIC_STYLE_SAFE;
        }
        if (focus.isFoodDeliveryTimeline() && progress >= 75) {
            return Notification.SEMANTIC_STYLE_CAUTION;
        }
        return Notification.SEMANTIC_STYLE_INFO;
    }

    private static Notification.ProgressStyle.Segment applySegmentSemanticStyle(
            @NonNull Notification.ProgressStyle.Segment segment,
            @Nullable Integer semanticStyle) {
        if (semanticStyle != null) {
            try {
                segment.setSemanticStyle(semanticStyle);
            } catch (LinkageError ignored) {
            }
        }
        return segment;
    }

    private static Notification.ProgressStyle.Point applyPointSemanticStyle(
            @NonNull Notification.ProgressStyle.Point point,
            @Nullable Integer semanticStyle) {
        if (semanticStyle != null) {
            try {
                point.setSemanticStyle(semanticStyle);
            } catch (LinkageError ignored) {
            }
        }
        return point;
    }

    private static int resolveAccentColor(@NonNull FocusNotificationSafety.PortableFocusData focus) {
        String raw = focus.accentColor();
        if (!TextUtils.isEmpty(raw)) {
            try {
                return Color.parseColor(raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return DEFAULT_ACCENT_COLOR;
    }

    private static void stripFocusKeys(@Nullable Bundle extras) {
        if (extras == null) {
            return;
        }
        for (String key : new ArrayList<>(extras.keySet())) {
            try {
                if (FocusNotificationSafety.isFocusExtraKey(key)) {
                    extras.remove(key);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 36;
    }
}
