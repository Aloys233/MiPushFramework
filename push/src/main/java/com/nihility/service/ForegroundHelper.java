package com.nihility.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import com.nihility.Global;
import com.xiaomi.xmsf.R;

public class ForegroundHelper {
    private static final String TAG = "ForegroundHelper";
    public static final String CHANNEL_STATUS = "status";
    public static final int NOTIFICATION_ALIVE_ID = 1;
    private final Service service;

    public ForegroundHelper(Service service) {
        this.service = service;
    }

    public void startForeground() {
        createNotificationGroupForPushStatus();
        if (Global.ConfigCenter().isStartForegroundService()) {
            showForegroundNotificationToKeepAlive();
        } else {
            stopForegroundNotification();
        }
    }

    public void stopForegroundNotification() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    void showForegroundNotificationToKeepAlive() {
        Notification notification = new NotificationCompat.Builder(service,
                CHANNEL_STATUS)
                .setContentTitle(service.getString(R.string.notification_alive))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int foregroundServiceType;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // remoteMessaging / specialUse 不受 Android 15+ 针对 dataSync 的 6 小时前台服务超时限制
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                } else {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                }
                service.startForeground(NOTIFICATION_ALIVE_ID, notification, foregroundServiceType);
            } else {
                service.startForeground(NOTIFICATION_ALIVE_ID, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start foreground service", t);
        }
    }

    void createNotificationGroupForPushStatus() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(service.getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String groupId = "status_group";
            NotificationChannelGroupCompat.Builder group =
                    new NotificationChannelGroupCompat.Builder(groupId)
                            .setName(CHANNEL_STATUS);
            manager.createNotificationChannelGroup(group.build());

            NotificationChannelCompat.Builder channel = new NotificationChannelCompat.Builder(
                    CHANNEL_STATUS, NotificationManager.IMPORTANCE_MIN)
                    .setName(service.getString(R.string.notification_category_alive)).setGroup(groupId);
            manager.createNotificationChannel(channel.build());
        }
    }
}