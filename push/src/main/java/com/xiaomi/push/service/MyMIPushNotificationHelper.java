package com.xiaomi.push.service;

import static com.xiaomi.push.service.MIPushNotificationHelper.FROM_NOTIFICATION;
import static com.xiaomi.push.service.MIPushNotificationHelper.getTargetPackage;
import static com.xiaomi.push.service.MIPushNotificationHelper.isBusinessMessage;
import static com.xiaomi.push.service.MyNotificationIconHelper.MiB;
import static com.xiaomi.xmsf.push.notification.NotificationController.getBitmapFromUri;
import static com.xiaomi.xmsf.push.notification.NotificationController.getLargeIcon;
import static com.xiaomi.xmsf.push.notification.NotificationController.getNotificationManagerEx;
import static com.xiaomi.xmsf.push.notification.NotificationController.roundLargeIconIfConfigured;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.mipush.sdk.PushMessageProcessor;
import com.xiaomi.push.sdk.MyPushMessageHandler;
import com.xiaomi.xmpush.thrift.PushMessage;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.notification.FocusNotificationSafety;
import com.xiaomi.xmsf.push.notification.FocusOrderIdentityPolicy;
import com.xiaomi.xmsf.push.notification.LiveUpdateTextDetector;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.utils.Configurations;
import com.xiaomi.xmsf.push.utils.IconConfigurations;
import com.xiaomi.xmsf.push.utils.PackageConfig;
import com.xiaomi.xmsf.push.utils.RegSecUtils;
import com.xiaomi.xmsf.utils.ConfigCenter;
import com.xiaomi.xmsf.utils.ConvertUtils;

import org.apache.thrift.TBase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import top.trumeet.common.Constants;
import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.db.RegisteredApplicationDb;
import top.trumeet.mipush.provider.entities.RegisteredApplication;

/**
 * @author zts1993
 * @date 2018/2/8
 */

public class MyMIPushNotificationHelper {
    public static final String CLASS_NAME_PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler";
    private static Logger logger = XLog.tag("MyNotificationHelper").build();

    private static final int NOTIFICATION_BIG_STYLE_MIN_LEN = 25;

    private static final String GROUP_TYPE_MIPUSH_GROUP = "#group#";
    private static final String GROUP_TYPE_SAME_TITLE = "#title#";
    private static final String GROUP_TYPE_SAME_NOTIFICATION_ID = "#id#";
    private static final String GROUP_TYPE_PASS_THROUGH = "#pass_through#";

    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_LEFT = 1;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_MID = 2;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT = 3;
    private static final String NOTIFICATION_STYLE_BIG_PICTURE = "2";
    private static final String NOTIFICATION_STYLE_BIG_PICTURE_URI = "notification_bigPic_uri";
    private static final String NOTIFICATION_STYLE_BIG_TEXT = "1";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri";
    private static final String NOTIFICATION_STYLE_TYPE = "notification_style_type";


    private static boolean tryLoadConfigurations = false;

    private static final int NOTIFICATION_QUEUE_CAPACITY = 16;
    private static final AtomicInteger NOTIFICATION_THREAD_COUNT = new AtomicInteger(1);
    private static final ThreadPoolExecutor executorService = createNotificationExecutor();
    private static final KeyedSerialDispatcher<NotificationKey> notificationDispatcher =
            new KeyedSerialDispatcher<>(
                    executorService,
                    (key, failure) -> logger.e("Notification task failed for " + key, failure));
    private static final WakeScreenThrottle WAKE_SCREEN_THROTTLE =
            new WakeScreenThrottle(SystemClock::elapsedRealtime);

    private static ThreadPoolExecutor createNotificationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                3,
                3,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(NOTIFICATION_QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(() -> {
                        try {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        } catch (Throwable ignored) {
                        }
                        r.run();
                    }, "mipush-notification-" + NOTIFICATION_THREAD_COUNT.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * @see `MIPushNotificationHelper`#notifyPushMessage
     */
    public static void notifyPushMessage(Context context, byte[] decryptedContent) {
        XmPushActionContainer container = XMPushUtils.packToContainer(decryptedContent);
        String targetPackage = publishPackageName(container);
        if (!isTargetPackageAvailable(context, targetPackage)) {
            logger.i("Skip notification for unavailable target package " + targetPackage);
            return;
        }
        AppInfoUtils.AppNotificationOp notificationOp = AppInfoUtils.getAppNotificationOp(context, targetPackage, true);
        if (notificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
            logger.w("Do not notify because user block " + targetPackage + "'s notification");
        } else {
            loadConfigurationsOnce(context);
            handleNotificationByConfigurations(context, decryptedContent, targetPackage, container);
        }
    }

    static boolean isTargetPackageAvailable(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) return false;
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return info != null && info.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (Throwable error) {
            logger.w("Unable to verify target package " + packageName, error);
            return false;
        }
    }

    private static void handleNotificationByConfigurations(Context context, byte[] decryptedContent, String packageName, XmPushActionContainer container) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Set<String> operations = null;
        try {
            operations = Configurations.getInstance().handle(packageName, container);
        } catch (Exception e) {
            logger.e(e.getLocalizedMessage(), e);
        }

        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(operations);
        NotificationDispatchPipeline.dispatch(
                plan,
                () -> wakeScreen(appContext, packageName),
                () -> notificationDispatcher.execute(notificationKeyFor(container), () -> {
                    try {
                        doNotifyPushMessage(appContext, container, decryptedContent);
                    } catch (Exception e) {
                        logger.e(e.getLocalizedMessage(), e);
                    }
                }),
                () -> MyPushMessageHandler.startService(appContext, container, decryptedContent),
                (stage, exception) -> logger.e("Notification dispatch stage failed: " + stage, exception));
    }

    private static NotificationKey notificationKeyFor(XmPushActionContainer container) {
        String packageName = publishPackageName(container);
        return new NotificationKey(
                packageName,
                getNotificationTag(packageName),
                getNotificationId(container));
    }

    private static String publishPackageName(@Nullable XmPushActionContainer container) {
        if (container == null) {
            return "";
        }
        if ("com.xiaomi.xmsf".equals(container.getPackageName())
                && container.getMetaInfo() != null
                && container.getMetaInfo().getExtra() != null) {
            String wrappedTarget = container.getMetaInfo().getExtra().get("miui_package_name");
            if (wrappedTarget != null && !wrappedTarget.trim().isEmpty()) {
                return wrappedTarget.trim();
            }
        }
        try {
            String targetPackage = getTargetPackage(container);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                return targetPackage;
            }
        } catch (Throwable error) {
            logger.w("Unable to derive notification publish package", error);
        }
        if (container.getPackageName() != null && !container.getPackageName().isEmpty()) {
            return container.getPackageName();
        }
        return "";
    }

    public static String getNotificationTargetPackage(@Nullable XmPushActionContainer container) {
        return publishPackageName(container);
    }

    private static void loadConfigurationsOnce(Context context) {
        if (!tryLoadConfigurations) {
            tryLoadConfigurations = true;
            try {
                ConfigCenter configCenter = Global.ConfigCenter();
                Uri configurationDirectory = configCenter.getConfigurationDirectory(context);
                loadConfigurations(context, configurationDirectory);
            } catch (Exception e) {
                Utils.makeText(context, e.toString(), Toast.LENGTH_LONG);
            }
        }
    }

    private static void loadConfigurations(Context context, Uri configurationDirectory) {
        Configurations configurations = Configurations.getInstance();
        if (configurations.init(context, configurationDirectory)) {
            IconConfigurations iconConfigurations = Global.IconConfigurations();
            iconConfigurations.init(context, configurationDirectory);
        }
    }

    private static void wakeScreen(Context context, String sourcePackage) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || powerManager.isInteractive()) {
            return;
        }
        if (!WAKE_SCREEN_THROTTLE.tryAcquire(sourcePackage)) {
            return;
        }
        PowerManager.WakeLock fullWakeLock = powerManager.newWakeLock((
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP
        ), "xmsf: configurations of " + sourcePackage);
        fullWakeLock.acquire(5_000L);
    }

    private static Notification findActiveNotification(String packageName, int notificationId) {
        StatusBarNotification[] notifications = getNotificationManagerEx().getActiveNotifications(packageName);
        if (notifications == null) {
            return null;
        }
        for (StatusBarNotification notification : notifications) {
            if (notification != null && notification.getId() == notificationId) {
                return notification.getNotification();
            }
        }
        return null;
    }

    private static NotificationCompat.Builder addToExistingMessageNotification(
            Context context, String packageName, int notificationId,
            NotificationCompat.MessagingStyle.Message message) {
        try {
            Notification activeNotification = findActiveNotification(packageName, notificationId);
            if (activeNotification != null) {
                NotificationCompat.MessagingStyle activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification); // todo: try remove
                if (activeStyle != null) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, activeNotification);
                    activeStyle.addMessage(message);
                    builder.setStyle(activeStyle); // todo: try remove
                    return builder;
                }
            }
            return null;
        } catch (Exception e) {
            logger.e(e.getLocalizedMessage(), e);
            return null;
        }
    }

    private static void doNotifyPushMessage(Context context, XmPushActionContainer container, byte[] decryptedContent) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        logPushMessage(metaInfo);

        NotificationInfo result = getNotificationFor(context, container, decryptedContent);

        NotificationController.publish(context, metaInfo, result.notificationId,
                publishPackageName(container), result.notificationBuilder);
    }

    private static void logPushMessage(PushMetaInfo metaInfo) {
        String title = metaInfo.getTitle();
        String description = metaInfo.getDescription();

        logger.i("title:" + title + "  description:" + description);
    }

    @NonNull
    private static NotificationInfo getNotificationFor(Context context, XmPushActionContainer container, byte[] decryptedContent) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        String packageName = publishPackageName(container);

        Context pkgCtx = getPackageContext(context, packageName);
        NotificationCompat.MessagingStyle.Message message = createMessage(context, container, pkgCtx);
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        boolean useMessagingStyle = message != null && custom.useMessagingStyle(false);

        int notificationId = getNotificationId(container);

        NotificationCompat.Builder notificationBuilder;

        if (useMessagingStyle) {
            notificationBuilder = messagingStyleNotificationBuilder(context, container, notificationId, message, pkgCtx);
        } else {
            notificationBuilder = normalStyleNotificationBuilder(context, packageName, container.getMetaInfo());
        }

        if (metaInfo.getExtra() != null) {
            setNotificationStyleAction(notificationBuilder, context, packageName, metaInfo.getExtra());
        }
        addDebugAction(context, container, decryptedContent, metaInfo, packageName, notificationBuilder);

        notificationBuilder.setWhen(metaInfo.getMessageTs());
        notificationBuilder.setShowWhen(custom.notificationShowWhen(true));

        String group = getGroupName(context, container);
        notificationBuilder.setGroup(group);

        Intent intentExtra = new Intent();
        intentExtra.putExtra(Constants.INTENT_NOTIFICATION_ID, notificationId);
        intentExtra.putExtra(Constants.INTENT_NOTIFICATION_GROUP, notificationBuilder.build().getGroup());

        ClickPendingIntent clickPendingIntent = getClickedPendingIntent(
                context, container, decryptedContent, notificationId, intentExtra.getExtras());

        if (clickPendingIntent != null) {
            notificationBuilder.setContentIntent(clickPendingIntent.pendingIntent);
            if (!clickPendingIntent.activity) {
                carryPendingIntentForTemporarilyWhitelisted(context, container, notificationBuilder);
            }
        }
        return new NotificationInfo(notificationId, notificationBuilder);
    }

    private static class NotificationInfo {
        public final int notificationId;
        public final NotificationCompat.Builder notificationBuilder;

        public NotificationInfo(int notificationId, NotificationCompat.Builder notificationBuilder) {
            this.notificationId = notificationId;
            this.notificationBuilder = notificationBuilder;
        }
    }

    static final class NotificationKey {
        final String packageName;
        final String tag;
        final int id;

        NotificationKey(String packageName, String tag, int id) {
            this.packageName = packageName == null ? "" : packageName;
            this.tag = tag == null ? "" : tag;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NotificationKey)) return false;
            NotificationKey that = (NotificationKey) other;
            return id == that.id
                    && packageName.equals(that.packageName)
                    && tag.equals(that.tag);
        }

        @Override
        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + tag.hashCode();
            result = 31 * result + id;
            return result;
        }

        @Override
        public String toString() {
            return packageName + "/" + tag + "/" + id;
        }
    }

    private static Context getPackageContext(Context context, String packageName) {
        Context pkgCtx = context;
        if (NotificationManagerEx.isHooked) {
            try {
                pkgCtx = context.createPackageContext(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return pkgCtx;
    }

    @NonNull
    private static NotificationCompat.Builder normalStyleNotificationBuilder(
            Context context, String packageName, PushMetaInfo metaInfo) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String fallbackTitle = packageName;
        try {
            CharSequence appName = Global.ApplicationNameCache().getAppName(context, packageName);
            if (appName != null && appName.length() > 0) {
                fallbackTitle = appName.toString();
            }
        } catch (Throwable ignored) {
        }
        FocusNotificationSafety.ResolvedContent resolved = FocusNotificationSafety.resolveReadableContent(
                metaInfo.getTitle(),
                metaInfo.getDescription(),
                configuration.focusParam(null),
                configuration.focusParamCustom(null),
                fallbackTitle,
                "New notification");
        String title = resolved.title();
        String description = resolved.body();

        Bitmap bigPic = getBigPic(context, metaInfo);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        if (bigPic != null) {
            NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();
            style.bigPicture(bigPic);
            style.setBigContentTitle(title);
            String imageDescription = configuration.imageDescription(null);
            if (!TextUtils.isEmpty(imageDescription)) {
                style.setContentDescription(imageDescription);
            }
            notificationBuilder.setStyle(style);
        } else if (description.length() > NOTIFICATION_BIG_STYLE_MIN_LEN || configuration.hasFocusPayload()) {
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
            style.bigText(description);
            style.setBigContentTitle(title);
            notificationBuilder.setStyle(style);
        }

        String[] titleAndDesp = determineTitleAndDespByDIP(context, metaInfo);
        FocusNotificationSafety.ResolvedContent dipResolved = FocusNotificationSafety.resolveReadableContent(
                titleAndDesp[0],
                titleAndDesp[1],
                configuration.focusParam(null),
                configuration.focusParamCustom(null),
                title,
                description);
        notificationBuilder.setContentTitle(dipResolved.title());
        notificationBuilder.setContentText(dipResolved.body());
        return notificationBuilder;
    }

    private static Bitmap getBigPic(Context context, PushMetaInfo metaInfo) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String bigPicUri = configuration.notificationBigPicUri(null);
        return Global.IconCache().getBitmap(context, bigPicUri,
                (context1, iconUri) -> getBitmapFromUri(
                        context1, iconUri, 1 * MiB));
    }

    @NonNull
    private static NotificationCompat.Builder messagingStyleNotificationBuilder(
            Context context, XmPushActionContainer container, int notificationId, NotificationCompat.MessagingStyle.Message message, Context pkgCtx) {
        String packageName = container.getPackageName();
        NotificationCompat.Builder messagingBuilder = addToExistingMessageNotification(context, packageName, notificationId, message);
        if (messagingBuilder != null) {
            return messagingBuilder;
        }

        return createMessageStyleNotificationBuilder(context, container, message, pkgCtx, packageName);
    }

    @NonNull
    private static NotificationCompat.Builder createMessageStyleNotificationBuilder(Context context, XmPushActionContainer container, NotificationCompat.MessagingStyle.Message message, Context pkgCtx, String packageName) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        Person group = getGroupFor(context, metaInfo).build();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        attachMessagingStyle(message, group, metaInfo, notificationBuilder);
        addShortcutToEnableMessagingStyle(context, container, pkgCtx, packageName, group, notificationBuilder);
        return notificationBuilder;
    }

    private static void attachMessagingStyle(NotificationCompat.MessagingStyle.Message message, Person group, PushMetaInfo metaInfo, NotificationCompat.Builder notificationBuilder) {
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(group);
        style.setConversationTitle(group.getName());
        style.setGroupConversation(isGroupConversation(metaInfo));
        style.addMessage(message);
        notificationBuilder.setStyle(style);
    }

    private static void addShortcutToEnableMessagingStyle(Context context, XmPushActionContainer container, Context pkgCtx, String packageName, Person group, NotificationCompat.Builder notificationBuilder) {
        // if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        try {
            String key = group.getKey() != null ? group.getKey() : group.getName().toString();
            Intent intent = getIntentForMessagingStyle(context, container, packageName);
            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(pkgCtx, key)
                    .setIntent(intent)
                    .setLongLived(true)
                    .setShortLabel(group.getName())
                    .setIcon(group.getIcon())
                    .build();

            ShortcutManagerCompat.pushDynamicShortcut(pkgCtx, shortcut);
            notificationBuilder.setShortcutInfo(shortcut);
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private static Intent getIntentForMessagingStyle(Context context, XmPushActionContainer container, String packageName) {
        Intent intent = getSdkIntent(context, container);
        if (intent == null) {
            PackageManager packageManager = context.getPackageManager();
            intent = packageManager.getLaunchIntentForPackage(packageName);
        }
        return intent;
    }

    @Nullable
    private static NotificationCompat.MessagingStyle.Message createMessage(Context context, XmPushActionContainer container, Context pkgCtx) {
        PushMetaInfo metaInfo = container.metaInfo;
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String senderMessage = custom.conversationMessage(null);
        if (senderMessage == null) {
            return null;
        }
        return createMessage(context, pkgCtx, metaInfo, senderMessage);
    }

    @NonNull
    private static NotificationCompat.MessagingStyle.Message createMessage(Context context, Context pkgCtx, PushMetaInfo metaInfo, String senderMessage) {
        boolean atLeastP = pkgCtx != null &&
                pkgCtx.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P;

        Person person = null;
        if (isGroupConversation(metaInfo) || atLeastP) {
            person = getPerson(context, metaInfo).build();
        }
        return new NotificationCompat.MessagingStyle.Message(
                senderMessage, metaInfo.getMessageTs(), person);
    }

    private static boolean isGroupConversation(PushMetaInfo metaInfo) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        return custom.conversationTitle(null) != null;
    }

    @NonNull
    private static Person.Builder getGroupFor(Context context, PushMetaInfo metaInfo) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String conversation = custom.conversationTitle(null);
        String conversationId = custom.conversationId(null);
        String conversationIcon = custom.conversationIcon(null);

        Person.Builder personBuilder = isGroupConversation(metaInfo) ?
                new Person.Builder() :
                getPerson(context, metaInfo);
        if (conversation != null) {
            personBuilder.setName(conversation);
        } else if (personBuilder.build().getName() == null) {
            personBuilder.setName(metaInfo.getTitle());
        }
        if (conversationId != null) {
            personBuilder.setKey(conversationId);
        }
        Bitmap largeIcon = getLargeIcon(context, metaInfo, conversationIcon);
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon));
        }
        return personBuilder;
    }

    @NonNull
    private static Person.Builder getPerson(Context context, PushMetaInfo metaInfo) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String sender = custom.conversationSender(null);
        String senderId = custom.conversationSenderId(null);
        String senderIcon = custom.conversationSenderIcon(null);
        String textIcon = custom.textIcon(null);

        Person.Builder personBuilder = new Person.Builder().setName(sender);
        personBuilder.setImportant(custom.conversationImportant(false));
        if (senderId != null) {
            personBuilder.setKey(senderId);
        }
        Bitmap largeIcon = getLargeIcon(context, metaInfo, senderIcon);
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon));
        } else if (textIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(
                    roundLargeIconIfConfigured(metaInfo,
                            ImageUtils.INSTANCE.textToBitmap(textIcon, 72, 0xFF003E6F, Color.WHITE)
                    )));

        }
        return personBuilder;
    }

    public static int getPendingIntentFlag(int baseFlag, boolean mutable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlag | (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        }
        return baseFlag;
    }

    private static void carryPendingIntentForTemporarilyWhitelisted(Context xmPushService, XmPushActionContainer buildContainer, NotificationCompat.Builder localBuilder) {
        PushMetaInfo metaInfo = buildContainer.getMetaInfo();
        // Also carry along the target PendingIntent, whose target will get temporarily whitelisted for background-activity-start upon sent.
        final Intent targetIntent = buildTargetIntentWithoutExtras(publishPackageName(buildContainer), metaInfo);
        final PendingIntent pi = PendingIntent.getService(xmPushService, 0, targetIntent, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
        localBuilder.getExtras().putParcelable("mipush.target", pi);
    }

    public static int getNotificationId(XmPushActionContainer container) {
        final PushMetaInfo metaInfo = container == null ? null : container.getMetaInfo();
        if (metaInfo == null) {
            return 0;
        }
        String packageName = publishPackageName(container);
        Integer orderNotificationId = FocusOrderIdentityPolicy.resolveNotificationId(packageName, metaInfo);
        if (orderNotificationId != null) {
            return orderNotificationId;
        }
        String id = metaInfo.isSetNotifyId() ? String.valueOf(metaInfo.getNotifyId()) : metaInfo.getId();
        String idWithPackage = packageName + "_" + id;
        return idWithPackage.hashCode();
    }

    static int getClickRequestCode(
            XmPushActionContainer container,
            int notificationId,
            @Nullable Intent routeIntent) {
        PushMetaInfo metaInfo = container == null ? null : container.getMetaInfo();
        StringBuilder identity = new StringBuilder()
                .append(publishPackageName(container)).append('|')
                .append(notificationId).append('|')
                .append(metaInfo == null ? "" : metaInfo.getId()).append('|')
                .append(metaInfo == null ? 0L : metaInfo.getMessageTs());
        if (routeIntent != null) {
            identity.append('|').append(routeIntent.getAction())
                    .append('|').append(routeIntent.getDataString())
                    .append('|').append(routeIntent.getComponent())
                    .append('|').append(routeIntent.getFlags())
                    .append('|').append(routeIntent.getCategories());
        }
        return identity.toString().hashCode();
    }

    public static String getNotificationTag(String packageName) {
        return "mipush_" + packageName;
    }

    public static String getNotificationTag(XmPushActionContainer container) {
        return getNotificationTag(publishPackageName(container));
    }

    private static String getGroupName(Context xmPushService, XmPushActionContainer buildContainer) {
        PushMetaInfo metaInfo = buildContainer.getMetaInfo();
        String packageName = publishPackageName(buildContainer);
        RegisteredApplication application = RegisteredApplicationDb.getRegisteredApplication(packageName);

        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String group = configuration.notificationGroup(null);
        if (group != null) {
            group = packageName + "_" + GROUP_TYPE_MIPUSH_GROUP + "_" + group;
        } else if (metaInfo.passThrough == 1) {
            group = packageName + "_" + GROUP_TYPE_PASS_THROUGH;
        } else if (configuration.hasFocusPayload() || LiveUpdateTextDetector.detectFromMetaInfo(metaInfo) != null) {
            group = FocusNotificationSafety.stableFocusGroup(packageName);
        } else {
            group = packageName;
        }
        return group;
    }

    private static void addDebugAction(Context xmPushService, XmPushActionContainer buildContainer, byte[] var1, PushMetaInfo metaInfo, String packageName, NotificationCompat.Builder localBuilder) {
        if (Global.ConfigCenter().isDebugMode()) {
            int i = R.drawable.ic_notifications_black_24dp;

            PendingIntent pendingIntentOpenActivity = openActivityPendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentOpenActivity != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Open App", pendingIntentOpenActivity));
            }

            PendingIntent pendingIntentJump = startServicePendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentJump != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Jump", pendingIntentJump));
            }

            Intent sdkIntentJump = getSdkIntent(xmPushService, buildContainer);
            if (sdkIntentJump != null) {
                PendingIntent pendingIntent = PendingIntent.getActivity(xmPushService, 0, sdkIntentJump, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
                localBuilder.addAction(new NotificationCompat.Action(i, "SDK Intent", pendingIntent));
            }
        }
    }

    public static Intent buildTargetIntentWithoutExtras(final String pkg, final PushMetaInfo metaInfo) {
        return new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).addCategory(String.valueOf(metaInfo.getNotifyId()))
                .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER);
    }

    private static PendingIntent openActivityPendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        String packageName = paramXmPushActionContainer.getPackageName();
        PackageManager packageManager = paramContext.getPackageManager();
        Intent localIntent1 = packageManager.getLaunchIntentForPackage(packageName);
        if (localIntent1 != null) {
            localIntent1.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
            return PendingIntent.getActivity(paramContext, 0, localIntent1, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
        }
        return null;
    }

    private static ClickPendingIntent getClickedPendingIntent(
            Context context, XmPushActionContainer container, byte[] decryptedContent,
            int notificationId, Bundle extra) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        if (metaInfo == null) {
            return null;
        }
        String targetPackage = publishPackageName(container);

        //Jump web
        String urlJump = null;
        if (!TextUtils.isEmpty(metaInfo.url)) {
            urlJump = metaInfo.url;
        } else if (metaInfo.getExtra() != null) {
            urlJump = metaInfo.getExtra().get(PushConstants.EXTRA_PARAM_WEB_URI);
        }

        if (!TextUtils.isEmpty(urlJump)) {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(urlJump));
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            return ClickPendingIntent.activity(PendingIntent.getActivity(
                    context, getClickRequestCode(container, notificationId, intent), intent,
                    getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false)));
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.xiaomi.xmsf",
                isBusinessMessage(container) ?
                        "com.xiaomi.mipush.sdk.PushMessageHandler" :
                        "com.xiaomi.push.sdk.MyPushMessageHandler"));
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent);
        intent.putExtra(FROM_NOTIFICATION, true);
        intent.putExtras(extra);
        intent.addCategory(String.valueOf(metaInfo.getNotifyId()));

        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        boolean liveUpdateLike = configuration.hasFocusPayload()
                || LiveUpdateTextDetector.detectFromMetaInfo(metaInfo) != null;
        boolean useActivity = configuration.useClickedActivity(false)
                || (liveUpdateLike && !configuration.keys().contains("use_clicked_activity"));
        Intent activityIntent = getSdkIntent(context, container);
        if (useActivity && activityIntent != null && !isActivityExported(context, activityIntent)) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null && isActivityExported(context, launchIntent)) {
                activityIntent = launchIntent;
            } else {
                activityIntent = null;
            }
        }
        if (!useActivity || activityIntent == null) {
            return ClickPendingIntent.service(PendingIntent.getService(
                    context, getClickRequestCode(container, notificationId, null), intent,
                    getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false)));
        }
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra("mipush_serviceIntent", intent);
        activityIntent.putExtras(intent);
        return ClickPendingIntent.activity(PendingIntent.getActivity(
                context, getClickRequestCode(container, notificationId, activityIntent), activityIntent,
                getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false)));
    }

    private static final class ClickPendingIntent {
        final PendingIntent pendingIntent;
        final boolean activity;

        private ClickPendingIntent(PendingIntent pendingIntent, boolean activity) {
            this.pendingIntent = pendingIntent;
            this.activity = activity;
        }

        static ClickPendingIntent activity(PendingIntent pendingIntent) {
            return new ClickPendingIntent(pendingIntent, true);
        }

        static ClickPendingIntent service(PendingIntent pendingIntent) {
            return new ClickPendingIntent(pendingIntent, false);
        }
    }

    /**
     * @see PushMessageProcessor#getNotificationMessageIntent
     */
    public static Intent getSdkIntent(Context context, XmPushActionContainer container) {
        String pkgName = publishPackageName(container);
        PushMetaInfo paramPushMetaInfo = container.getMetaInfo();
        if (paramPushMetaInfo == null) {
            return null;
        }
        Map<String, String> extra = paramPushMetaInfo.getExtra();
        Intent payloadIntent = getPayloadRouteIntent(context, container);
        Intent focusIntent = getFocusRouteIntent(context, pkgName, paramPushMetaInfo);
        if (extra == null || !extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return focusIntent != null ? focusIntent : payloadIntent;
        }

        Intent intent = null;

        String typeId = extra.get(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            } catch (Exception e2) {
                logger.e("Cause: " + e2.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(typeId)) {

            if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
                String intentStr = extra.get(PushConstants.EXTRA_PARAM_INTENT_URI);
                if (intentStr != null) {
                    try {
                        intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME);
                        intent.setPackage(pkgName);
                    } catch (URISyntaxException e3) {
                        logger.e("Cause: " + e3.getMessage());
                    }
                }
            } else {
                if (extra.containsKey(PushConstants.EXTRA_PARAM_CLASS_NAME)) {
                    String className = (String) extra.get(PushConstants.EXTRA_PARAM_CLASS_NAME);
                    intent = new Intent();
                    intent.setComponent(new ComponentName(pkgName, className));
                    try {
                        if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_FLAG)) {
                            intent.setFlags(Integer.parseInt(extra.get(PushConstants.EXTRA_PARAM_INTENT_FLAG)));
                        }
                    } catch (NumberFormatException e4) {
                        logger.e("Cause by intent_flag: " + e4.getMessage());
                    }

                }
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(typeId)) {
            String uri = extra.get(PushConstants.EXTRA_PARAM_WEB_URI);

            MalformedURLException e;

            if (uri != null) {
                String tmp = uri.trim();
                if (!(tmp.startsWith("http://") || tmp.startsWith("https://"))) {
                    tmp = "http://" + tmp;
                }
                try {
                    String protocol = new URL(tmp).getProtocol();
                    if (!"http".equals(protocol)) {
                        if (!"https".equals(protocol)) {
                            //why ?
                        }
                    }
                    Intent intent2 = new Intent("android.intent.action.VIEW");
                    intent2.setData(Uri.parse(tmp));
                    intent = intent2;
                } catch (MalformedURLException e6) {
                    e = e6;
                    logger.e("Cause: " + e.getMessage());
                    return null;
                }
            }
        }


        if (intent == null || PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            if (focusIntent != null) {
                intent = focusIntent;
            } else if (payloadIntent != null) {
                intent = payloadIntent;
            }
        } else if (!isActivityExported(context, intent)) {
            if (focusIntent != null && isActivityExported(context, focusIntent)) {
                intent = focusIntent;
            } else if (payloadIntent != null && isActivityExported(context, payloadIntent)) {
                intent = payloadIntent;
            }
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                if (inFetchIntentBlackList(pkgName)) {
                    return null;
                }

                return intent;
            }
        }

        return null;
    }

    private static final int PAYLOAD_ROUTE_MAX_DEPTH = 8;
    private static final int PAYLOAD_ROUTE_MAX_NODES = 256;
    private static final int PAYLOAD_ROUTE_MAX_LENGTH = 16 * 1024;
    private static final int PAYLOAD_DOCUMENT_MAX_LENGTH = 64 * 1024;

    @Nullable
    private static Intent getPayloadRouteIntent(Context context, XmPushActionContainer container) {
        try {
            String pkgName = publishPackageName(container);
            String regSec = RegSecUtils.getRegSec(container);
            if (TextUtils.isEmpty(regSec) || TextUtils.isEmpty(pkgName)) {
                return null;
            }
            TBase messageBody = ConvertUtils.getResponseMessageBodyFromContainer(container, regSec);
            if (!(messageBody instanceof XmPushActionSendMessage)) {
                return null;
            }
            PushMessage message = ((XmPushActionSendMessage) messageBody).getMessage();
            if (message == null || TextUtils.isEmpty(message.getPayload())) {
                return null;
            }
            String payload = message.getPayload().trim();
            if (payload.length() > PAYLOAD_DOCUMENT_MAX_LENGTH) {
                return null;
            }
            int[] nodeCount = new int[]{0};
            if (payload.startsWith("{")) {
                return findPayloadRoute(context, pkgName, new JSONObject(payload), 0, nodeCount);
            }
            if (payload.startsWith("[")) {
                return findPayloadRoute(context, pkgName, new JSONArray(payload), 0, nodeCount);
            }
            return resolvePayloadRoute(context, pkgName, payload);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Intent findPayloadRoute(
            Context context, String packageName, Object value, int depth, int[] nodeCount) {
        if (value == null || depth > PAYLOAD_ROUTE_MAX_DEPTH || nodeCount[0]++ >= PAYLOAD_ROUTE_MAX_NODES) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (child instanceof String && isPayloadRouteKey(key)) {
                    Intent candidate = resolvePayloadRoute(context, packageName, (String) child);
                    if (candidate != null) {
                        return candidate;
                    }
                }
                Intent nested = findPayloadRoute(context, packageName, child, depth + 1, nodeCount);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Intent nested = findPayloadRoute(context, packageName, array.opt(i), depth + 1, nodeCount);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (value instanceof String) {
            String candidate = ((String) value).trim();
            if (candidate.startsWith("{") || candidate.startsWith("[")) {
                try {
                    Object nested = candidate.startsWith("{")
                            ? new JSONObject(candidate) : new JSONArray(candidate);
                    return findPayloadRoute(context, packageName, nested, depth + 1, nodeCount);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isPayloadRouteKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("url")
                || normalized.equals("uri")
                || normalized.equals("scheme")
                || normalized.equals("jump_scheme")
                || normalized.equals("intent_uri")
                || normalized.equals("deep_link")
                || normalized.equals("deeplink")
                || normalized.equals("link")
                || normalized.endsWith("_url")
                || normalized.endsWith("_uri");
    }

    @Nullable
    private static Intent resolvePayloadRoute(Context context, String packageName, String value) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(value)) {
            return null;
        }
        String route = value.trim();
        if (route.length() == 0 || route.length() > PAYLOAD_ROUTE_MAX_LENGTH
                || route.indexOf('\n') >= 0 || route.indexOf('\r') >= 0
                || route.endsWith("?") || route.endsWith("&")) {
            return null;
        }
        try {
            Intent intent;
            if (route.startsWith("intent:")) {
                intent = Intent.parseUri(route, Intent.URI_INTENT_SCHEME);
            } else {
                Uri uri = Uri.parse(route);
                if (TextUtils.isEmpty(uri.getScheme())) {
                    return null;
                }
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.exported
                    && packageName.equals(resolved.activityInfo.packageName)) {
                intent.setComponent(new ComponentName(
                        resolved.activityInfo.packageName, resolved.activityInfo.name));
                return intent;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * tmp black list
     *
     * @param pkg package name
     * @return is in black list
     */
    private static boolean inFetchIntentBlackList(String pkg) {
        if (pkg.contains("youku")) {
            return true;
        }

        return false;
    }


    private static PendingIntent startServicePendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        if (paramPushMetaInfo == null) {
            return null;
        }

        Intent localIntent;
        if (isBusinessMessage(paramXmPushActionContainer)) {
            localIntent = new Intent();
            localIntent.setComponent(new ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler"));
        } else {
            localIntent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
            localIntent.setComponent(new ComponentName(paramXmPushActionContainer.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler"));
        }
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, paramArrayOfByte);
        localIntent.putExtra(FROM_NOTIFICATION, true);
        localIntent.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(paramContext, 0, localIntent, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
        } else {
            return PendingIntent.getService(paramContext, 0, localIntent, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
        }
    }

    /**
     * @see MIPushNotificationHelper#determineTitleAndDespByDIP
     */
    private static String[] determineTitleAndDespByDIP(Context paramContext, PushMetaInfo paramPushMetaInfo) {

        try {
            return JavaCalls.callStaticMethodOrThrow(MIPushNotificationHelper.class, "determineTitleAndDespByDIP", paramContext, paramPushMetaInfo);
        } catch (Exception e) {
            logger.e(e.getMessage(), e);
            return new String[]{paramPushMetaInfo.getTitle(), paramPushMetaInfo.getDescription()};
        }
    }

    // from sdk 3.7.2
    @TargetApi(16)
    private static NotificationCompat.Builder setNotificationStyleAction(NotificationCompat.Builder builder, Context context, String pkgName, Map<String, String> metaExtra) {
        PendingIntent stylePendingIntent = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_LEFT, metaExtra);
        if (stylePendingIntent != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_LEFT_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_LEFT_NAME), stylePendingIntent);
        }
        PendingIntent stylePendingIntent2 = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_MID, metaExtra);
        if (stylePendingIntent2 != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_MID_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_MID_NAME), stylePendingIntent2);
        }
        PendingIntent stylePendingIntent3 = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT, metaExtra);
        if (stylePendingIntent3 != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_RIGHT_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_RIGHT_NAME), stylePendingIntent3);
        }
        return builder;
    }

    private static PendingIntent getStylePendingIntent(Context context, String pkgName, int place, Map<String, String> metaExtra) {
        Intent intent;
        if (metaExtra == null || (intent = getPendingIntentFromExtra(context, pkgName, place, metaExtra)) == null) {
            return null;
        }
        return PendingIntent.getActivity(context, 0, intent, getPendingIntentFlag(PendingIntent.FLAG_UPDATE_CURRENT, false));
    }

    private static Intent getPendingIntentFromExtra(Context context, String pkgName, int place, Map<String, String> extra) {
        String str;
        String webUriKey;
        String intentUriKey;
        String intentClassKey;
        if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
            str = NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT;
        } else {
            str = place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT ? NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT : NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT;
        }
        String typeId = extra.get(str);
        if (TextUtils.isEmpty(typeId)) {
            return null;
        }
        Intent intent = null;
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            } catch (Exception e) {
                logger.e("Cause: " + e.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(typeId)) {
            if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                intentUriKey = NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI;
            } else {
                intentUriKey = place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT ? NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI : NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI;
            }
            if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                intentClassKey = NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS;
            } else {
                intentClassKey = place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT ? NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS : NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS;
            }
            if (extra.containsKey(intentUriKey)) {
                String intentStr = extra.get(intentUriKey);
                if (intentStr != null) {
                    try {
                        intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME);
                        intent.setPackage(pkgName);
                    } catch (URISyntaxException e2) {
                        logger.e("Cause: " + e2.getMessage());
                    }
                }
            } else if (extra.containsKey(intentClassKey)) {
                String className = extra.get(intentClassKey);
                intent = new Intent();
                intent.setComponent(new ComponentName(pkgName, className));
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(typeId)) {
            if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                webUriKey = NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI;
            } else {
                webUriKey = place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT ? NOTIFICATION_STYLE_BUTTON_MID_WEB_URI : NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI;
            }
            String uri = extra.get(webUriKey);
            if (!TextUtils.isEmpty(uri)) {
                String tmp = uri.trim();
                if (!tmp.startsWith("http://") && !tmp.startsWith("https://")) {
                    tmp = "http://" + tmp;
                }
                try {
                    URL url = new URL(tmp);
                    String protocol = url.getProtocol();
                    if ("http".equals(protocol) || "https".equals(protocol)) {
                        intent = new Intent("android.intent.action.VIEW");
                        intent.setData(Uri.parse(tmp));
                    }
                } catch (MalformedURLException e3) {
                    logger.e("Cause: " + e3.getMessage());
                }
            }
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ResolveInfo rinfo = context.getPackageManager().resolveActivity(intent, Intent.FLAG_ACTIVITY_NO_ANIMATION);
                if (rinfo != null) {
                    return intent;
                }
            } catch (Exception e4) {
                logger.e("Cause: " + e4.getMessage());
            }
        }
        return null;
    }

    @Nullable
    private static Intent getFocusRouteIntent(
            Context context, String pkgName, PushMetaInfo metaInfo) {
        if (context == null || TextUtils.isEmpty(pkgName) || metaInfo == null) {
            return null;
        }
        try {
            CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
            String param = configuration.focusParam(null);
            String customParam = configuration.focusParamCustom(null);
            if (!FocusNotificationSafety.isClickRoutePayload(param)
                    && !FocusNotificationSafety.isClickRoutePayload(customParam)) {
                return null;
            }
            FocusNotificationSafety.PortableFocusData focus =
                    FocusNotificationSafety.parsePortableFocusData(param, customParam);
            String route = focus.url();
            if (TextUtils.isEmpty(route)) {
                return null;
            }
            route = route.trim();
            Intent candidate = route.startsWith("intent:")
                    ? Intent.parseUri(route, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(route));
            candidate.setPackage(pkgName);
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.exported
                    && pkgName.equals(resolved.activityInfo.packageName)) {
                candidate.setComponent(new ComponentName(
                        resolved.activityInfo.packageName, resolved.activityInfo.name));
                return candidate;
            }
        } catch (Throwable error) {
            logger.w("Unable to resolve focus click route for " + pkgName, error);
        }
        return null;
    }

    private static boolean isActivityExported(Context context, @Nullable Intent activityIntent) {
        if (context == null || activityIntent == null) {
            return false;
        }
        try {
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(activityIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.exported;
        } catch (Throwable ignored) {
            return false;
        }
    }


}
