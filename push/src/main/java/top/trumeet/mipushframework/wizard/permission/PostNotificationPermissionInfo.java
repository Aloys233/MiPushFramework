package top.trumeet.mipushframework.wizard.permission;

import android.content.Context;

import androidx.annotation.NonNull;

import com.xiaomi.xmsf.R;

public class PostNotificationPermissionInfo implements PermissionInfo {
    private final Context context;

    public PostNotificationPermissionInfo(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public PermissionOperator getPermissionOperator() {
        return new PostNotificationPermissionOperator(context);
    }

    @NonNull
    @Override
    public String getPermissionTitle() {
        return context.getString(R.string.wizard_title_post_notification_permission);
    }

    @NonNull
    @Override
    public String getPermissionDescription() {
        return context.getString(R.string.wizard_title_post_notification_permission_text);
    }
}
