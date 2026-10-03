package com.xiaomi.push.service;

import static android.app.Notification.GROUP_ALERT_SUMMARY;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static com.nihility.service.ForegroundHelper.CHANNEL_STATUS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.xiaomi.xmsf.R;

import java.util.Objects;

@RequiresApi(29/* Q */)
public class BackgroundActivityStartEnabler {

    public static @Nullable PendingIntent clonePendingIntentForBackgroundActivityStart(final PendingIntent pi) {
        if (pi == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+) 不再允许通过 Notification 的 mWhitelistToken 反射绕过 BAL，
            // 且借用 Token 在新系统中可能导致失效或系统抛出安全异常，直接返回原 PendingIntent
            return pi;
        }
        final Notification whitelistedN = sWhitelistedNotification;
        if (whitelistedN == null) return pi;
        whitelistedN.contentIntent = pi;
        final Parcel parcel = Parcel.obtain();
        try {
            whitelistedN.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final Notification n = Notification.CREATOR.createFromParcel(parcel);
            final PendingIntent whitelisted = n.contentIntent;
            n.contentIntent = null;
            return whitelisted != null ? whitelisted : pi;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to clone PendingIntent, fallback to original", t);
            return pi;
        } finally {
            parcel.recycle();
            whitelistedN.contentIntent = null;
        }
    }

    public static void initialize(final Context context) {
        try {
            final NotificationManager nm = Objects.requireNonNull(context.getSystemService(NotificationManager.class));
            String channelId = tryGetValidPushStatusChannelId(context, nm);
            if (channelId == null) return;
            notifyPushStatusInitializing(context, channelId, nm);
            scheduleCapture(nm, 5);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize BackgroundActivityStartEnabler", t);
        }
    }

    private static void notifyPushStatusInitializing(Context context, String channelId, NotificationManager nm) {
        final Notification n = new Notification.Builder(context, channelId).setTimeoutAfter(5_000)  // Must be long enough for all retries.
                .setContentTitle("Initializing...").setOngoing(true)        // To avoid being cancelled before capture
                .setGroup(TAG).setGroupAlertBehavior(GROUP_ALERT_SUMMARY)   // Effectively mute this notification
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).build();
        nm.notify(TAG, 0, n);
    }

    private static @Nullable String tryGetValidPushStatusChannelId(Context context, NotificationManager nm) {
        String channelId = CHANNEL_STATUS;
        for (int channelPostfix = 0; channelPostfix <= 16; ) {
            @Nullable NotificationChannel channel = nm.getNotificationChannel(channelId);
            if (channel == null) {
                if (CHANNEL_STATUS.equals(channelId)) {
                    // 原生 "status" 频道尚不存在，不在此创建原生频道，转为尝试临时频道，消除死循环
                    channelId = CHANNEL_STATUS + (++channelPostfix);
                    continue;
                }
                channel = new NotificationChannel(channelId, context.getString(R.string.notification_category_alive), IMPORTANCE_LOW);
                nm.createNotificationChannel(channel);
                return channelId;
            } else {
                if (channel.getImportance() > NotificationManager.IMPORTANCE_NONE) {
                    return channelId;
                }
                if (channelPostfix >= 16) {
                    Log.e(TAG, "Failed to obtain available notification channel.");
                    return null;
                }
                channelId = CHANNEL_STATUS + (++channelPostfix);   // If channel is disabled, try another temporary channel ID.
            }
        }
        return null;
    }

    private static void scheduleCapture(final NotificationManager nm, final int retries) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                final StatusBarNotification[] ns = nm.getActiveNotifications();
                findPushStatusInitializingNotification(nm, ns);
                if (pushStatusInitializingNotificationExists()) {
                    deleteTemporaryChannel(nm);
                } else if (retries == 0) {
                    Log.e(TAG, "Failed to capture active notification.");
                    nm.cancel(TAG, 0);      // In case it's there but unable to be captured.
                } else {
                    Log.i(TAG, "Wait to capture active notification.");
                    scheduleCapture(nm, retries - 1);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error in scheduleCapture", t);
            }
        }, 500);
    }

    private static void deleteTemporaryChannel(NotificationManager nm) {
        if (sWhitelistedNotification != null) {
            final String channelId = sWhitelistedNotification.getChannelId();
            if (channelId != null && !CHANNEL_STATUS.equals(channelId)) {
                try {
                    nm.deleteNotificationChannel(channelId);        // Delete channel if it is temporary
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean pushStatusInitializingNotificationExists() {
        return sWhitelistedNotification != null;
    }

    private static void findPushStatusInitializingNotification(NotificationManager nm, StatusBarNotification[] ns) {
        for (final StatusBarNotification n : ns)
            if (n.getId() == 0 && TAG.equals(n.getTag())) {
                sWhitelistedNotification = n.getNotification(); // Borrow mWhitelistToken from our foreground notification.
                nm.cancel(TAG, 0);
                break;
            }
    }

    private static @Nullable Notification sWhitelistedNotification;

    private static final String TAG = "MPF.BAFE";
}
