package com.xiaomi.xmsf.push.notification;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xiaomi.xmpush.thrift.PushMetaInfo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback detector that turns ordinary delivery/ride-hailing/logistics text notifications
 * into Android Live Updates when the server did not include miui.focus.* extras.
 */
public final class LiveUpdateTextDetector {
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");

    private static final String[] MARKETING_EXCLUSIONS = {
            "优惠券", "满减", "红包", "活动", "立减", "秒杀", "抽奖", "会员", "促销", "推荐", "直播", "广告"
    };

    private LiveUpdateTextDetector() {
    }

    @Nullable
    public static FocusNotificationSafety.PortableFocusData detectFromMetaInfo(@Nullable PushMetaInfo metaInfo) {
        if (metaInfo == null) {
            return null;
        }
        String title = metaInfo.getTitle();
        String body = metaInfo.getDescription();
        String combined = ((title == null ? "" : title) + " " + (body == null ? "" : body)).trim();
        if (combined.isEmpty()) {
            return null;
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        for (String exclusion : MARKETING_EXCLUSIONS) {
            if (lower.contains(exclusion)) {
                return null;
            }
        }

        // 1. 外卖/配送场景
        if (containsAny(lower, "骑手", "配送", "备餐", "出餐", "取餐", "送达", "外卖", "小哥")) {
            int progress = inferDeliveryProgress(lower);
            if (progress >= 0) {
                String chip = deliveryChip(progress);
                return FocusNotificationSafety.PortableFocusData.forDetectedLiveUpdate(
                        safeText(title, chip),
                        safeText(body, title),
                        "foodDelivery",
                        progress,
                        "#FF6200",
                        chip);
            }
        }

        // 2. 网约车/出行场景
        if (containsAny(lower, "司机已接单", "正在赶来", "距您", "到达上车点", "行程中", "司机已到达", "网约车")) {
            int progress = inferRideProgress(lower);
            String chip = progress >= 100 ? "已到达" : (progress >= 60 ? "快到了" : "司机赶来");
            return FocusNotificationSafety.PortableFocusData.forDetectedLiveUpdate(
                    safeText(title, chip),
                    safeText(body, title),
                    "rideHailing",
                    progress,
                    "#1A73E8",
                    chip);
        }

        // 3. 快递物流派送场景
        if (containsAny(lower, "正在派送", "派送中", "快递员", "已签收", "已放入快递柜", "驿站待取")) {
            int progress = containsAny(lower, "已签收", "已取件") ? 100
                    : (containsAny(lower, "快递柜", "驿站") ? 90 : 75);
            String chip = progress >= 100 ? "已签收" : (progress >= 90 ? "待取件" : "派送中");
            return FocusNotificationSafety.PortableFocusData.forDetectedLiveUpdate(
                    safeText(title, chip),
                    safeText(body, title),
                    "logistics",
                    progress,
                    "#0F9D58",
                    chip);
        }

        return null;
    }

    private static int inferDeliveryProgress(String lower) {
        if (containsAny(lower, "已送达", "已放", "已签收", "已取消", "已完成")) {
            return 100;
        }
        if (containsAny(lower, "即将到达", "快到了", "楼下", "马上送达")) {
            return 90;
        }
        if (containsAny(lower, "正在配送", "配送中", "已取餐", "正赶往", "赶来", "距离您", "距您")) {
            return 65;
        }
        if (containsAny(lower, "备餐", "出餐", "制作中", "商家已接单", "正在制作")) {
            return 25;
        }
        Matcher m = PERCENT_PATTERN.matcher(lower);
        if (m.find()) {
            try {
                int p = Integer.parseInt(m.group(1));
                if (p >= 0 && p <= 100) return p;
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static int inferRideProgress(String lower) {
        if (containsAny(lower, "已到达", "已上车", "行程结束", "已完成", "已取消")) {
            return 100;
        }
        if (containsAny(lower, "即将到达", "到达上车点")) {
            return 85;
        }
        if (containsAny(lower, "正在赶来", "距您")) {
            return 50;
        }
        return 25;
    }

    private static String deliveryChip(int progress) {
        if (progress >= 100) return "已送达";
        if (progress >= 85) return "快到了";
        if (progress >= 35) return "配送中";
        return "备餐中";
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String safeText(@Nullable String primary, @Nullable String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }
}
