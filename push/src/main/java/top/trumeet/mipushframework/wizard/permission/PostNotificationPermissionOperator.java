package top.trumeet.mipushframework.wizard.permission;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

public class PostNotificationPermissionOperator implements PermissionOperator {
    private final Context context;
    private boolean hasRequestedPermission = false;

    public PostNotificationPermissionOperator(Context context) {
        this.context = context;
    }

    @Override
    public boolean isPermissionGranted() {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    @Override
    public void requestPermissionSilently() {
        // 通知权限不支持静默申请
    }

    @Override
    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context instanceof Activity) {
            Activity activity = (Activity) context;
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.POST_NOTIFICATIONS)
                    && hasRequestedPermission) {
                openNotificationSettings();
            } else {
                hasRequestedPermission = true;
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1001
                );
            }
        } else {
            openNotificationSettings();
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", context.getPackageName());
            intent.putExtra("app_uid", context.getApplicationInfo().uid);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Intent appDetailIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetailIntent.setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof Activity)) {
                appDetailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(appDetailIntent);
        }
    }
}
