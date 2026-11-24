package com.xiaomi.xmsf.push.notification;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaomi.xmpush.thrift.PushMetaInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Derives a stable notification id from order-keyed focus payloads
 * (e.g. orderId+scene or JD's package-prefixed numeric notifyId) so status updates
 * update the same live card/chip in place instead of stacking duplicates.
 */
public final class FocusOrderIdentityPolicy {
    private static final String FOCUS_PARAM = "miui.focus.param";
    private static final String FOCUS_PARAM_CUSTOM = "miui.focus.param.custom";

    private FocusOrderIdentityPolicy() {
    }

    @Nullable
    public static Integer resolveNotificationId(
            @Nullable String packageName,
            @Nullable PushMetaInfo metaInfo) {
        if (TextUtils.isEmpty(packageName) || metaInfo == null) {
            return null;
        }
        Map<String, String> extra = metaInfo.getExtra();
        if (extra == null) {
            return null;
        }
        String key = orderKey(extra.get(FOCUS_PARAM), packageName);
        if (key == null) {
            key = orderKey(extra.get(FOCUS_PARAM_CUSTOM), packageName);
        }
        if (key == null) {
            return null;
        }
        return (packageName.hashCode() / 10) * 10 + key.hashCode();
    }

    @Nullable
    static String orderKey(@Nullable String focusParam, @Nullable String packageName) {
        if (!FocusNotificationSafety.isWellFormedParameter(focusParam)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(focusParam);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            List<JsonObject> scopes = sessionScopes(root);

            for (JsonObject scope : scopes) {
                String orderId = scalarString(scope, "orderId");
                if (!TextUtils.isEmpty(orderId)) {
                    String scene = scalarString(scope, "scene");
                    return TextUtils.isEmpty(scene) ? orderId : scene + "|" + orderId;
                }
            }
            if (!TextUtils.isEmpty(packageName)) {
                for (JsonObject scope : scopes) {
                    String notifyId = scalarString(scope, "notifyId");
                    if (notifyId != null && notifyId.startsWith(packageName)) {
                        String suffix = notifyId.substring(packageName.length());
                        if (!suffix.isEmpty() && isAllDigits(suffix)) {
                            return suffix;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<JsonObject> sessionScopes(JsonObject root) {
        List<JsonObject> scopes = new ArrayList<>(3);
        scopes.add(root);
        addIfObject(scopes, root, "param_v2");
        addIfObject(scopes, root, "param_v1");
        return scopes;
    }

    private static void addIfObject(List<JsonObject> target, JsonObject parent, String key) {
        JsonElement elem = parent.get(key);
        if (elem != null && elem.isJsonObject()) {
            target.add(elem.getAsJsonObject());
        }
    }

    @Nullable
    private static String scalarString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        if (elem == null || !elem.isJsonPrimitive()) {
            return null;
        }
        try {
            String value = elem.getAsString();
            return TextUtils.isEmpty(value) || value.trim().isEmpty() ? null : value.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
