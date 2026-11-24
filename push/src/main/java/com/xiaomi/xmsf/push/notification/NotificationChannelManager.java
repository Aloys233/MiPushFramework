package com.xiaomi.xmsf.push.notification;

import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_LIGHTS;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_SOUND;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_VIBRATE;
import static top.trumeet.common.utils.NotificationAlertUtils.usesPackageResourceSound;
import static top.trumeet.common.utils.NotificationUtils.getChannelIdByPkg;
import static top.trumeet.common.utils.NotificationUtils.getGroupIdByPkg;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmsf.R;

import java.util.Arrays;
import java.util.List;

import top.trumeet.common.utils.CustomConfiguration;

public class NotificationChannelManager {

    public static final String DEBUG_CHANNEL_ID = "mipush_debug_test_v2";
    private static final String LIVE_UPDATE_CHANNEL_SUFFIX = "_mipush_live_v1";

    public static NotificationManagerEx getNotificationManagerEx() {
        return NotificationManagerEx.INSTANCE;
    }

    @TargetApi(26)
    private static NotificationChannelGroup createGroupWithPackage(@NonNull String packageName,
                                                                   @NonNull CharSequence appName) {
        return new NotificationChannelGroup(getGroupIdByPkg(packageName), appName);
    }

    private static NotificationChannel createChannelWithPackage(@NonNull PushMetaInfo metaInfo,
                                                                @NonNull String packageName) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String channelName = configuration.channelName("未分类");
        String channelDescription = configuration.channelDescription(null);
        String sound = configuration.soundUri(null);

        NotificationChannel channel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int importance = configuration.channelImportance(NotificationManager.IMPORTANCE_DEFAULT);
            int notifyType = metaInfo.getNotifyType();
            channel = new NotificationChannel(getChannelId(metaInfo, packageName), channelName, importance);
            channel.setDescription(channelDescription);
            channel.enableVibration((notifyType & NOTIFY_TYPE_VIBRATE) != 0);
            channel.enableLights((notifyType & NOTIFY_TYPE_LIGHTS) != 0);
            if ((notifyType & NOTIFY_TYPE_SOUND) == 0) {
                channel.setSound(null, null);
            } else if (usesPackageResourceSound(notifyType, sound, packageName)) {
                AudioAttributes attr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(Uri.parse(sound), attr);
            }
        }
        return channel;
    }

    public static String getChannelId(@NonNull PushMetaInfo metaInfo,
                                      @NonNull String packageName) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        return getChannelIdByPkg(packageName) + "_" + configuration.channelId("");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean isNotificationChannelEnabled(@Nullable NotificationChannel channel){
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    public static boolean isNotificationChannelEnabled(@NonNull String packageName, @Nullable String channelId){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!TextUtils.isEmpty(channelId)) {
                NotificationChannel channel =
                        NotificationManagerEx.INSTANCE.getNotificationChannel(packageName, channelId);
                return isNotificationChannelEnabled(channel);
            }
            return false;
        } else {
            return NotificationManagerEx.INSTANCE.areNotificationsEnabled(packageName);
        }
    }

    public static NotificationChannel registerChannelIfNeeded(Context context, PushMetaInfo metaInfo, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        CharSequence appName = Global.ApplicationNameCache().getAppName(context, packageName);
        if (appName == null) {
            return null;
        }

        return createNotificationChannel(metaInfo, packageName, appName);

    }

    public static void registerDebugChannelIfNeeded(Context context, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel existing =
                getNotificationManagerEx().getNotificationChannel(packageName, DEBUG_CHANNEL_ID);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                DEBUG_CHANNEL_ID, "MiPush Framework test", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Manual notification replay");
        channel.enableVibration(true);
        channel.enableLights(true);
        getNotificationManagerEx().createNotificationChannels(
                packageName, Arrays.asList(channel));
    }

    @Nullable
    public static NotificationChannel ensureLiveUpdateChannel(Context context, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context == null
                || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            String channelId = getLiveUpdateChannelId(packageName);
            NotificationChannel existing =
                    getNotificationManagerEx().getNotificationChannel(packageName, channelId);
            if (existing != null) {
                return existing;
            }
            CharSequence appName = null;
            try {
                appName = Global.ApplicationNameCache().getAppName(context, packageName);
            } catch (Throwable ignored) {
            }
            if (TextUtils.isEmpty(appName)) {
                appName = packageName;
            }
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_live_update_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(
                    context.getString(R.string.notification_live_update_channel_description));
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationChannelGroup group = createGroupWithPackage(packageName, appName);
            if (ensureChannelGroupRegistered(packageName, group)) {
                channel.setGroup(group.getId());
            }

            createChannelSafely(packageName, channel);
            return getNotificationManagerEx().getNotificationChannel(packageName, channelId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getLiveUpdateChannelId(String packageName) {
        return getChannelIdByPkg(packageName) + LIVE_UPDATE_CHANNEL_SUFFIX;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static boolean ensureChannelGroupRegistered(@NonNull String packageName,
                                                        @NonNull NotificationChannelGroup group) {
        try {
            getNotificationManagerEx().createNotificationChannelGroups(
                    packageName, Arrays.asList(group));
            List<NotificationChannelGroup> groups =
                    getNotificationManagerEx().getNotificationChannelGroups(packageName);
            if (groups != null) {
                for (NotificationChannelGroup existingGroup : groups) {
                    if (existingGroup != null && TextUtils.equals(group.getId(), existingGroup.getId())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createChannelSafely(@NonNull String packageName,
                                            @NonNull NotificationChannel channel) {
        try {
            getNotificationManagerEx().createNotificationChannels(
                    packageName, Arrays.asList(channel));
        } catch (IllegalArgumentException e) {
            if (!TextUtils.isEmpty(channel.getGroup())) {
                channel.setGroup(null);
                getNotificationManagerEx().createNotificationChannels(
                        packageName, Arrays.asList(channel));
            } else {
                throw e;
            }
        }
    }

    private static NotificationChannel createNotificationChannel(PushMetaInfo metaInfo, String packageName, CharSequence appName) {
        NotificationChannel notificationChannel = createChannelWithPackage(metaInfo, packageName);
        if (notificationChannel == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return notificationChannel;
        }

        NotificationChannelGroup notificationChannelGroup = createGroupWithPackage(packageName, appName);
        if (ensureChannelGroupRegistered(packageName, notificationChannelGroup)) {
            notificationChannel.setGroup(notificationChannelGroup.getId());
        }

        createChannelSafely(packageName, notificationChannel);
        return notificationChannel;
    }

}
