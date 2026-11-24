package com.xiaomi.xmsf.push.notification;

import static com.xiaomi.push.service.MyMIPushNotificationHelper.getNotificationTag;
import static com.xiaomi.push.service.MyNotificationIconHelper.KiB;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_SOUND;
import static top.trumeet.common.utils.NotificationAlertUtils.usesPackageResourceSound;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.IconCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.xiaomi.push.service.MyMIPushNotificationHelper;
import com.xiaomi.push.service.MyNotificationIconHelper;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.utils.Configurations;
import com.xiaomi.xmsf.push.utils.IconConfigurations;
import com.xiaomi.xmsf.utils.ColorUtil;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.ImgUtils;
import top.trumeet.common.utils.NotificationMetadata;
import top.trumeet.mipushframework.main.AdvancedSettingsPage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Trumeet
 * @date 2018/1/25
 */

public class NotificationController {
    private static final Logger logger = XLog.tag("NotificationController").build();

    private static final String NOTIFICATION_LARGE_ICON = "mipush_notification";
    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";
    private static final int NOTIFICATION_LARGE_ICON_DP = 64;
    private static final int MIN_NOTIFICATION_LARGE_ICON_PX = 64;
    private static final int MAX_NOTIFICATION_LARGE_ICON_PX = 256;

    public static final String CHANNEL_WARN = "warn";
    private static final int MOCK_LIVE_UPDATE_NOTIFICATION_ID = 0x4D4950;
    private static final AtomicInteger MOCK_LIVE_UPDATE_STAGE = new AtomicInteger(0);

    public static NotificationManagerEx getNotificationManagerEx() {
        return NotificationManagerEx.INSTANCE;
    }


    @TargetApi(Build.VERSION_CODES.N)
    private static void updateSummaryNotification(Context context, PushMetaInfo metaInfo, String packageName, String groupId) {
        if (groupId == null || FocusNotificationSafety.isFocusGroup(groupId)) {
            return;
        }
        if (!needGroupOfNotifications(packageName, groupId)) {
            getNotificationManagerEx().cancel(packageName, null, groupId.hashCode());
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                getExistsChannelId(context, metaInfo, packageName));
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        builder.setCategory(Notification.CATEGORY_EVENT)
                .setGroupSummary(true)
                .setGroup(groupId);
        notify(context, groupId.hashCode(), packageName, builder, metaInfo, false, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static boolean needGroupOfNotifications(String packageName, String groupId) {
        int notificationCntInGroup = getNotificationCountOfGroup(packageName, groupId);
        return notificationCntInGroup > 1;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static int getNotificationCountOfGroup(String packageName, String groupId) {
        StatusBarNotification[] activeNotifications =
                getNotificationManagerEx().getActiveNotifications(packageName);
        if (activeNotifications == null) {
            return 0;
        }

        int notificationCntInGroup = 0;
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (statusBarNotification != null
                    && statusBarNotification.getNotification() != null
                    && groupId.equals(statusBarNotification.getNotification().getGroup())
                    && (statusBarNotification.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                notificationCntInGroup++;
            }
        }
        return notificationCntInGroup;
    }

    public static void publish(Context context, PushMetaInfo metaInfo, int notificationId, String packageName, NotificationCompat.Builder notificationBuilder) {
        String channelId = getExistsChannelId(context, metaInfo, packageName);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || hasNoExplicitChannel(notificationBuilder)) {
            notificationBuilder.setChannelId(channelId);
        }

        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        applyAlertBehavior(metaInfo, packageName, notificationBuilder);
        notificationBuilder.setPriority(Notification.PRIORITY_HIGH);

        CustomConfiguration configuration = metaInfo == null ? null : XMPushUtils.getConfiguration(metaInfo);
        FocusNotificationSafety.PortableFocusData focusData = configuration == null
                ? FocusNotificationSafety.PortableFocusData.EMPTY
                : FocusNotificationSafety.parsePortableFocusData(
                        configuration.focusParam(null),
                        configuration.focusParamCustom(null));
        boolean hasFocusPayload = configuration != null && configuration.hasFocusPayload() && !focusData.isEmpty();
        if (!hasFocusPayload && metaInfo != null) {
            FocusNotificationSafety.PortableFocusData detectedFocus = LiveUpdateTextDetector.detectFromMetaInfo(metaInfo);
            if (detectedFocus != null && !detectedFocus.isEmpty()) {
                focusData = detectedFocus;
                hasFocusPayload = true;
            }
        }
        final FocusNotificationSafety.PortableFocusData resolvedFocusData = focusData;
        boolean attemptLiveUpdate = hasFocusPayload && LiveUpdateNotificationHelper.isSupported();

        if (hasFocusPayload) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isDebugChannel(notificationBuilder)) {
                if (NotificationChannelManager.ensureLiveUpdateChannel(context, packageName) != null) {
                    notificationBuilder.setChannelId(NotificationChannelManager.getLiveUpdateChannelId(packageName));
                }
            }
            // 隔离焦点通知分组，防止被普通通知折叠成 groupSummary 而破坏原生 Live Update 提升。
            try {
                String existingGroup = notificationBuilder.build().getGroup();
                if (TextUtils.isEmpty(existingGroup) || packageName.equals(existingGroup)) {
                    notificationBuilder.setGroup(FocusNotificationSafety.stableFocusGroup(packageName));
                }
            } catch (Throwable error) {
                logger.w("Unable to inspect focus notification group", error);
            }
        }

        Notification notification = FocusNotificationSafety.deliverWithSingleFallback(
                packageName,
                getNotificationTag(packageName),
                notificationId,
                attemptLiveUpdate,
                (deliveryPackage, deliveryTag, deliveryId, useLiveUpdate, focusFailure) -> {
                    if (focusFailure != null) {
                        logger.w("Live Update notification failed; falling back to standard notification", focusFailure);
                    }
                    return notify(context, deliveryId, deliveryPackage, notificationBuilder,
                            metaInfo, useLiveUpdate, useLiveUpdate ? resolvedFocusData : null);
                });

        updateSummaryNotification(context, metaInfo, packageName, notification.getGroup());
    }

    private static boolean hasNoExplicitChannel(NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        try {
            return TextUtils.isEmpty(builder.build().getChannelId());
        } catch (Throwable error) {
            return true;
        }
    }

    private static boolean isDebugChannel(NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        try {
            return NotificationChannelManager.DEBUG_CHANNEL_ID.equals(builder.build().getChannelId());
        } catch (Throwable error) {
            return false;
        }
    }

    @NonNull
    public static String getExistsChannelId(Context context, PushMetaInfo metaInfo, String packageName) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String channelId = custom.borrowChannelId(null);
        if (TextUtils.isEmpty(channelId) ||
                getNotificationManagerEx().getNotificationChannel(packageName, channelId) == null) {
            // Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName);
            channelId = NotificationChannelManager.getChannelId(metaInfo, packageName);
        }
        return channelId;
    }

    private static Notification notify(
            Context context, int notificationId, String packageName,
            NotificationCompat.Builder notificationBuilder, PushMetaInfo metaInfo,
            boolean asLiveUpdate,
            @Nullable FocusNotificationSafety.PortableFocusData focusData) {
        // Make the behavior consistent with official MIUI
        Bundle extras = new Bundle();
        extras.putString("target_package", packageName);
        notificationBuilder.addExtras(extras);

        // Set small icon
        processIcon(context, packageName, notificationBuilder);

        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        applyOfficialMetadata(context, packageName, notificationBuilder, configuration);

        String iconUri = configuration.notificationLargeIconUri(null);
        Bitmap largeIcon = getLargeIcon(context, metaInfo, iconUri);
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon);
        }

        String subText = configuration.subText(null);
        buildExtraSubText(context, packageName, notificationBuilder, subText);

        applyLargeIconFallback(context, packageName, metaInfo, notificationBuilder);

        FocusNotificationSafety.ResolvedContent resolvedContent = ensureReadableStandardContent(
                context, packageName, notificationBuilder, metaInfo, configuration);

        if (!asLiveUpdate) {
            String focusParam = configuration.focusParam(null);
            if (focusParam != null) {
                Bundle focusBundle = new Bundle();
                focusBundle.putString("miui.focus.param", focusParam);

                Bundle picsBundle = new Bundle();
                for (String key : configuration.keys()) {
                    if (key.startsWith("miui.focus.pic_")) {
                        String url = configuration.get(key, null);
                        focusBundle.putString(key, url);
                        if (!TextUtils.isEmpty(url)) {
                            Bitmap pic = getBitmapFromUri(context, url, 200 * KiB);
                            if (pic != null) {
                                picsBundle.putParcelable(key, pic);
                            }
                        }
                    }
                }
                if (!picsBundle.isEmpty()) {
                    focusBundle.putBundle("miui.focus.pics", picsBundle);
                }
                notificationBuilder.addExtras(focusBundle);
            }
        }

        notificationBuilder.setAutoCancel(true);
        Notification notification = notificationBuilder.build();
        if (asLiveUpdate && focusData != null && LiveUpdateNotificationHelper.isSupported()) {
            notification = LiveUpdateNotificationHelper.buildLiveUpdateNotification(
                    context, notification, focusData, resolvedContent);
        }
        getNotificationManagerEx().notify(
                packageName, getNotificationTag(packageName), notificationId, notification);
        return notification;
    }

    private static FocusNotificationSafety.ResolvedContent ensureReadableStandardContent(
            Context context,
            String packageName,
            NotificationCompat.Builder notificationBuilder,
            @Nullable PushMetaInfo metaInfo,
            @Nullable CustomConfiguration configuration) {
        String existingTitle = null;
        String existingBody = null;
        try {
            Notification preview = notificationBuilder.build();
            if (preview.extras != null) {
                CharSequence title = preview.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence body = preview.extras.getCharSequence(Notification.EXTRA_TEXT);
                existingTitle = title == null ? null : title.toString();
                existingBody = body == null ? null : body.toString();
            }
        } catch (Throwable error) {
            logger.w("Unable to inspect notification content", error);
        }

        String metaTitle = metaInfo == null ? null : metaInfo.getTitle();
        String metaBody = metaInfo == null ? null : metaInfo.getDescription();
        String focusParameter = configuration == null ? null : configuration.focusParam(null);
        String focusCustomParameter = configuration == null ? null : configuration.focusParamCustom(null);
        String fallbackTitle = packageName;
        try {
            CharSequence appName = Global.ApplicationNameCache().getAppName(context, packageName);
            if (appName != null && appName.length() > 0) {
                fallbackTitle = appName.toString();
            }
        } catch (Throwable ignored) {
        }

        FocusNotificationSafety.ResolvedContent resolved = FocusNotificationSafety.resolveReadableContent(
                !TextUtils.isEmpty(existingTitle) ? existingTitle : metaTitle,
                !TextUtils.isEmpty(existingBody) ? existingBody : metaBody,
                focusParameter,
                focusCustomParameter,
                fallbackTitle,
                "New notification");
        if (TextUtils.isEmpty(existingTitle)) {
            notificationBuilder.setContentTitle(resolved.title());
        }
        if (TextUtils.isEmpty(existingBody)) {
            notificationBuilder.setContentText(resolved.body());
        }
        return resolved;
    }

    @Nullable
    public static Bitmap getLargeIcon(Context context, PushMetaInfo metaInfo, String iconUri) {
        Bitmap largeIcon = Global.IconCache().getBitmap(context, iconUri,
                (context1, iconUri1) -> getBitmapFromUri(context1, iconUri1, 200 * KiB));
        return prepareLargeIconForNotification(context, metaInfo, largeIcon);
    }

    @Nullable
    public static Bitmap prepareLargeIconForNotification(
            Context context, @Nullable PushMetaInfo metaInfo, @Nullable Bitmap largeIcon) {
        if (largeIcon == null || largeIcon.isRecycled()) {
            return null;
        }
        try {
            int maxDimension = resolveNotificationLargeIconSize(context);
            int width = largeIcon.getWidth();
            int height = largeIcon.getHeight();
            Bitmap boundedIcon = largeIcon;
            int sourceMax = Math.max(width, height);
            if (sourceMax > maxDimension) {
                float scale = (float) maxDimension / sourceMax;
                int boundedWidth = Math.max(1, Math.round(width * scale));
                int boundedHeight = Math.max(1, Math.round(height * scale));
                boundedIcon = Bitmap.createScaledBitmap(largeIcon, boundedWidth, boundedHeight, true);
            }
            return roundLargeIconIfConfigured(metaInfo, boundedIcon);
        } catch (Throwable error) {
            logger.w("Unable to prepare notification large icon", error);
            return null;
        }
    }

    private static int resolveNotificationLargeIconSize(@Nullable Context context) {
        float density = 1f;
        if (context != null && context.getResources() != null
                && context.getResources().getDisplayMetrics() != null) {
            density = context.getResources().getDisplayMetrics().density;
        }
        int size = Math.round(NOTIFICATION_LARGE_ICON_DP * density);
        return Math.min(MAX_NOTIFICATION_LARGE_ICON_PX, Math.max(MIN_NOTIFICATION_LARGE_ICON_PX, size));
    }

    private static void applyAlertBehavior(
            @Nullable PushMetaInfo metaInfo,
            String packageName,
            NotificationCompat.Builder notificationBuilder) {
        int notifyType = metaInfo == null ? 0 : metaInfo.getNotifyType();
        String soundUri = metaInfo == null
                ? null
                : XMPushUtils.getConfiguration(metaInfo).soundUri(null);
        if (usesPackageResourceSound(notifyType, soundUri, packageName)) {
            notificationBuilder.setDefaults(notifyType & ~NOTIFY_TYPE_SOUND);
            notificationBuilder.setSound(Uri.parse(soundUri));
        } else {
            notificationBuilder.setDefaults(notifyType);
        }
    }

    private static void applyOfficialMetadata(
            Context context,
            String packageName,
            NotificationCompat.Builder builder,
            CustomConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        NotificationMetadata metadata = NotificationMetadata.from(configuration);

        String smallIconUri = configuration.notificationSmallIconUri(null);
        if (!TextUtils.isEmpty(smallIconUri) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Bitmap icon = getBitmapFromUri(context, smallIconUri, 200 * KiB);
            if (icon != null) {
                builder.setSmallIcon(IconCompat.createWithBitmap(icon));
            }
        }

        String smallIconColor = configuration.notificationSmallIconColor(null);
        if (!TextUtils.isEmpty(smallIconColor)) {
            try {
                builder.setColor(Color.parseColor(smallIconColor));
            } catch (IllegalArgumentException ignored) {
                try {
                    builder.setColor(Integer.parseInt(smallIconColor));
                } catch (NumberFormatException ignoredToo) {
                }
            }
        }

        if (metadata.timeoutSeconds != null && metadata.timeoutSeconds > 0) {
            builder.setTimeoutAfter(metadata.timeoutSeconds * 1000L);
        }
        if (metadata.backgroundColor != null) {
            builder.setColor(metadata.backgroundColor);
            if (metadata.ongoing == null) builder.setOngoing(true);
            if (metadata.colorized == null) builder.setColorized(true);
        }
        if (metadata.ongoing != null) builder.setOngoing(metadata.ongoing);
        if (metadata.colorized != null) builder.setColorized(metadata.colorized);
        if (metadata.visibility != null) builder.setVisibility(metadata.visibility);
        if (metadata.category != null) builder.setCategory(metadata.category);
        if (metadata.enableKeyguard != null) {
            builder.getExtras().putBoolean("miui.enableKeyguard", metadata.enableKeyguard);
        }
        if (metadata.enableFloat != null) {
            builder.getExtras().putBoolean("miui.enableFloat", metadata.enableFloat);
        }
        builder.getExtras().putString("mipush_target_package", packageName);
        builder.getExtras().putString("xmsf_target_package", packageName);
    }

    private static void applyLargeIconFallback(
            Context context,
            String packageName,
            @Nullable PushMetaInfo metaInfo,
            NotificationCompat.Builder builder) {
        if (context == null || builder == null || TextUtils.isEmpty(packageName) || hasLargeIcon(builder)) {
            return;
        }
        try {
            CustomConfiguration configuration = metaInfo == null ? null : XMPushUtils.getConfiguration(metaInfo);
            if (configuration != null) {
                String[] configuredIcons = {
                        configuration.notificationLargeIconUri(null),
                        configuration.conversationSenderIcon(null),
                        configuration.conversationIcon(null)
                };
                for (String iconUri : configuredIcons) {
                    if (TextUtils.isEmpty(iconUri)) continue;
                    Bitmap configuredIcon = getLargeIcon(context, metaInfo, iconUri);
                    if (configuredIcon != null) {
                        builder.setLargeIcon(configuredIcon);
                        return;
                    }
                }
            }
            Bitmap applicationIcon = Global.IconCache().getRawIconBitmap(context, packageName);
            if (applicationIcon != null && !applicationIcon.isRecycled()) {
                Bitmap boundedIcon = prepareLargeIconForNotification(context, metaInfo, applicationIcon);
                if (boundedIcon != null) {
                    builder.setLargeIcon(boundedIcon);
                }
            }
        } catch (Throwable error) {
            logger.w("Unable to resolve notification large-icon fallback", error);
        }
    }

    private static boolean hasLargeIcon(NotificationCompat.Builder builder) {
        try {
            return builder.build().getLargeIcon() != null;
        } catch (Throwable error) {
            return false;
        }
    }

    public static Bitmap roundLargeIconIfConfigured(PushMetaInfo metaInfo, Bitmap largeIcon) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        if (custom.roundLargeIcon(false)) {
            largeIcon = ImgUtils.trimImgToCircle(largeIcon, Color.TRANSPARENT);
        }
        return largeIcon;
    }

    @Nullable
    public static Bitmap getBitmapFromUri(Context context, String iconUri, int maxDownloadBytes) {
        Bitmap bitmap = null;
        if (iconUri != null) {
            if (iconUri.startsWith("http")) {
                MyNotificationIconHelper.GetIconResult result =
                        MyNotificationIconHelper.getIconFromUrl(context, iconUri, maxDownloadBytes);
                if (result != null) {
                    bitmap = result.bitmap;
                }
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, iconUri);
            }
        }
        return bitmap;
    }

    public static void cancelPackageNotifications(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) return;
        try {
            StatusBarNotification[] active = getNotificationManagerEx().getActiveNotifications(packageName);
            if (active == null) return;
            for (StatusBarNotification sbn : active) {
                if (sbn != null) {
                    getNotificationManagerEx().cancel(packageName, sbn.getTag(), sbn.getId());
                }
            }
        } catch (Throwable error) {
            logger.w("Unable to clear notifications for removed package " + packageName, error);
        }
    }

    public static void cancel(Context context, XmPushActionContainer container,
                              int notificationId, String notificationGroup, boolean clearGroup) {
        String packageName = MyMIPushNotificationHelper.getNotificationTargetPackage(container);
        String tag = getNotificationTag(container);
        getNotificationManagerEx().cancel(packageName, tag, notificationId);
        Integer orderNotificationId = FocusOrderIdentityPolicy.resolveNotificationId(
                packageName, container == null ? null : container.getMetaInfo());
        if (orderNotificationId != null && orderNotificationId != notificationId) {
            getNotificationManagerEx().cancel(packageName, tag, orderNotificationId);
        }

        if (clearGroup) {
            if (notificationGroup != null) {
                getNotificationManagerEx().cancel(packageName,
                        getNotificationTag(container), notificationGroup.hashCode());
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationGroup != null) {
                XmPushActionContainer copy = container.deepCopy();
                try {
                    Configurations.getInstance().handle(packageName, copy);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                updateSummaryNotification(context, copy.metaInfo, packageName, notificationGroup);
            }
        }
    }


    /**
     * @param ctx context
     * @param pkg packageName
     * @return 0 if not processed
     */
    public static int getIconColor(final Context ctx, final String pkg) {
        return Global.IconCache().getAppColor(ctx, pkg, (ctx1, iconBitmap) -> {
            if (iconBitmap == null) {
                return Notification.COLOR_DEFAULT;
            }
            int color = ColorUtil.getIconColor(iconBitmap);
            if (color != Notification.COLOR_DEFAULT) {
                final float[] hsl = new float[3];
                ColorUtils.colorToHSL(color, hsl);
                hsl[1] = 0.94f;
                hsl[2] = Math.min(hsl[2] * 0.6f, 0.31f);
                return ColorUtils.HSLToColor(hsl);
            } else {
                return Notification.COLOR_DEFAULT;
            }
        });
    }


    public static void processIcon(Context context, String packageName, NotificationCompat.Builder notificationBuilder) {
        notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);

        // refer: https://dev.mi.com/console/doc/detail?pId=2625#_5_0
        Context pkgContext = null;
        try {
            pkgContext = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        int largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON);
        int smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON);

        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(pkgContext.getResources(), largeIconId));
        }

        applyIconAccent(notificationBuilder, getIconColor(context, packageName));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IconConfigurations.IconConfig iconConfig = Global.IconConfigurations().get(packageName);
            if (iconConfig != null && iconConfig.isEnabled && iconConfig.isEnabledAll) {
                Bitmap iconBitmap = iconConfig.bitmap();
                if (iconBitmap != null) {
                    notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap));
                    applyIconAccent(notificationBuilder, iconConfig.color());
                    return;
                }
            }

            if (smallIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, smallIconId));
                return;
            }
            if (largeIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, largeIconId));
                return;
            }

            Bitmap iconBitmap = iconConfig == null ? null : iconConfig.bitmap();
            if (iconBitmap != null && iconConfig.isEnabled) {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap));
                applyIconAccent(notificationBuilder, iconConfig.color());
                return;
            }

            IconCompat iconCache = Global.IconCache().getIconCache(context, packageName, (ctx, b) -> IconCompat.createWithBitmap(b));
            if (iconCache != null) {
                notificationBuilder.setSmallIcon(iconCache);
                return;
            }
        }
    }

    static void applyIconAccent(NotificationCompat.Builder builder, int color) {
        try {
            if (NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    builder.build()) == null) {
                builder.setColor(color);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static void buildExtraSubText(Context context, String packageName, NotificationCompat.Builder localBuilder, CharSequence text) {
        if ("".equals(text)) {
            localBuilder.setSubText(null);
            return;
        }
        if (text == null) {
            text = Global.ApplicationNameCache().getAppName(context, packageName);
        }
        int color = localBuilder.getColor();
        if (color == Notification.COLOR_DEFAULT) {
            localBuilder.setSubText(text);
            return;
        }
        CharSequence subText = ColorUtil.createColorSubtext(text, color);
        localBuilder.setSubText(subText);
    }

    private static int getIconId(Context context, String packageName, String resourceName) {
        return context.getResources().getIdentifier(resourceName, "drawable", packageName);
    }


    public static void test(Context context, String packageName, String title, String description) {
        NotificationChannelManager.registerChannelIfNeeded(context, new PushMetaInfo(), packageName);

        int id = (int) (System.currentTimeMillis() / 1000L);

        NotificationCompat.Builder localBuilder = new NotificationCompat.Builder(context);

        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.bigText(description);
        style.setBigContentTitle(title);
        style.setSummaryText(description);
        localBuilder.setStyle(style);
        localBuilder.setWhen(System.currentTimeMillis());
        localBuilder.setShowWhen(true);

        Intent notifyIntent = new Intent(context, AdvancedSettingsPage.class);
        // Set the Activity to start in a new, empty task
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Create the PendingIntent
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                context, 0, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        localBuilder.setContentIntent(notifyPendingIntent);

        NotificationController.publish(context, new PushMetaInfo(), id, packageName, localBuilder);
    }

    public static void testLiveUpdate(Context context, String packageName) {
        NotificationChannelManager.registerDebugChannelIfNeeded(context, packageName);

        int[] progresses = {15, 55, 85, 100};
        String[] titles = {
                "商家已接单，正在备餐",
                "骑手已取餐，正在飞奔赶来",
                "骑手即将到达，请留意电话",
                "外卖已送达，祝您用餐愉快"
        };
        String[] bodies = {
                "预计 15 分钟后出餐 · MiPush 实时通知测试",
                "距您 1.2 km，预计 8 分钟送达",
                "距您 150 m，马上送达",
                "已放入指定取餐点，可左滑关闭"
        };
        String[] statuses = {"备餐中", "配送中", "快到了", "已送达"};

        int stage = MOCK_LIVE_UPDATE_STAGE.getAndUpdate(i -> (i + 1) % progresses.length);
        int progress = progresses[stage];
        String title = titles[stage];
        String body = bodies[stage];
        String status = statuses[stage];

        PushMetaInfo metaInfo = new PushMetaInfo();
        Map<String, String> extras = new HashMap<>();
        try {
            JSONObject paramV2 = new JSONObject();
            paramV2.put("scene", "foodDelivery");
            paramV2.put("updatable", true);
            JSONObject baseInfo = new JSONObject();
            baseInfo.put("title", title);
            baseInfo.put("content", body);
            baseInfo.put("subTitle", status);
            paramV2.put("baseInfo", baseInfo);
            JSONObject progressInfo = new JSONObject();
            progressInfo.put("progress", progress);
            progressInfo.put("colorProgress", "#FF6200");
            paramV2.put("progressInfo", progressInfo);

            JSONObject json = new JSONObject();
            json.put("ticker", status);
            json.put("title", title);
            json.put("description", body);
            json.put("scene", "foodDelivery");
            json.put("progress", progress);
            json.put("updatable", true);
            json.put("param_v2", paramV2);
            extras.put("miui.focus.param", json.toString());
        } catch (JSONException e) {
            logger.e("Failed to construct mock focus JSON", e);
        }
        metaInfo.setExtra(extras);
        metaInfo.setTitle(title);
        metaInfo.setDescription(body);

        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? NotificationChannelManager.DEBUG_CHANNEL_ID
                : getExistsChannelId(context, metaInfo, packageName);
        NotificationCompat.Builder localBuilder = new NotificationCompat.Builder(context, channelId);
        localBuilder.setContentTitle(title);
        localBuilder.setContentText(body);
        localBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        localBuilder.setWhen(System.currentTimeMillis());
        localBuilder.setShowWhen(true);

        Intent notifyIntent = new Intent(context, AdvancedSettingsPage.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                context, 0, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        localBuilder.setContentIntent(notifyPendingIntent);

        NotificationController.publish(
                context, metaInfo, MOCK_LIVE_UPDATE_NOTIFICATION_ID, packageName, localBuilder);
    }

}
