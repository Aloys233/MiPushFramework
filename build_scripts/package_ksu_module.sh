#!/bin/bash
set -e

TAG="${1:-v1.1.1}"
CLEAN_TAG="${TAG#v}"
APK_FILE=$(find push/build/outputs/apk/normal/release/ -maxdepth 1 -name "*${CLEAN_TAG}*.apk" 2>/dev/null | head -n 1)
if [ -z "$APK_FILE" ]; then
    APK_FILE=$(ls -t push/build/outputs/apk/normal/release/*.apk 2>/dev/null | head -n 1)
fi

if [ -z "$APK_FILE" ]; then
    echo "Error: APK file not found under push/build/outputs/apk/normal/release/!"
    exit 1
fi
echo "Found APK: $APK_FILE"

MODULE_DIR="build/ksu_module"
rm -rf "$MODULE_DIR"
mkdir -p "$MODULE_DIR/system/priv-app/com.xiaomi.xmsf"
mkdir -p "$MODULE_DIR/system/etc/permissions"

# 复制 APK 到 priv-app
cp "$APK_FILE" "$MODULE_DIR/system/priv-app/com.xiaomi.xmsf/com.xiaomi.xmsf.apk"

# 生成 privapp-permissions 白名单 xml
# Android 9+ 在 ro.control_privapp_permissions=enforce 下会强制校验 priv-app 申请的所有
# signature|privileged 权限，缺失会导致 system_server 启动时抛 IllegalStateException 卡二屏。
cat << 'EOF' > "$MODULE_DIR/system/etc/permissions/privapp-permissions-com.xiaomi.xmsf.xml"
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.xiaomi.xmsf">
        <permission name="android.permission.UPDATE_APP_OPS_STATS"/>
        <permission name="android.permission.PACKAGE_USAGE_STATS"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS"/>
    </privapp-permissions>
</permissions>
EOF

# 获取版本号
REV_COUNT=$(git rev-list --count HEAD 2>/dev/null || echo "100")

# 生成 module.prop
cat << EOF > "$MODULE_DIR/module.prop"
id=mipush_framework_system
name=MiPush Framework (System App)
version=${TAG}
versionCode=${REV_COUNT}
author=Aloys23
description=将 MiPushFramework (com.xiaomi.xmsf) 挂载为系统特权应用 (priv-app) 并流式安装至 /data/app 规避 KernelSU 默认 umount 闪退，同时自动刷新包缓存。
EOF

# 生成 post-fs-data.sh：在 PMS 启动前一次性清理 package_cache 与旧 dalvik-cache
# 解决 KernelSU/EROFS 模块 APK 固定时间戳导致重启后命中旧包缓存、版本号不刷新的问题
cat << 'EOF' > "$MODULE_DIR/post-fs-data.sh"
#!/system/bin/sh
MODDIR=${0%/*}

if [ -f "$MODDIR/.clear_cache" ]; then
    rm -rf /data/system/package_cache/* 2>/dev/null || true
    rm -rf /data/dalvik-cache/arm/*com.xiaomi.xmsf* \
           /data/dalvik-cache/arm64/*com.xiaomi.xmsf* \
           /data/dalvik-cache/x86_64/*com.xiaomi.xmsf* 2>/dev/null || true
    rm -f "$MODDIR/.clear_cache"
fi
EOF

# 生成 service.sh：开机后兜底补装（防止 Recovery 刷机或被回退到纯 /system/priv-app 后触发 KSU umount 闪退）并配置电池优化白名单
cat << 'EOF' > "$MODULE_DIR/service.sh"
#!/system/bin/sh
MODDIR=${0%/*}
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

APK_PATH="$MODDIR/system/priv-app/com.xiaomi.xmsf/com.xiaomi.xmsf.apk"
CUR_PATH=$(pm path com.xiaomi.xmsf 2>/dev/null || true)

# 若刷入时 pm 不可用/失败（存在 .need_install），或当前仅运行在 /system/priv-app 下
# （KernelSU 默认对非 Root 应用 umount /system/priv-app，会导致进程找不到自身 APK 秒闪退），
# 则流式安装到 /data/app 形成 UPDATED_SYSTEM_APP，兼顾 PRIVILEGED 特权与免 umount 闪退。
if [ -f "$APK_PATH" ] && { [ -f "$MODDIR/.need_install" ] || echo "$CUR_PATH" | grep -q "/system/priv-app/"; }; then
    APK_SIZE=$(wc -c < "$APK_PATH")
    if pm install -r -d -S "$APK_SIZE" < "$APK_PATH" >/dev/null 2>&1; then
        rm -f "$MODDIR/.need_install"
    fi
fi

dumpsys deviceidle whitelist +com.xiaomi.xmsf 2>/dev/null || true
EOF

# 生成 customize.sh
cat << 'EOF' > "$MODULE_DIR/customize.sh"
SKIPUNZIP=0

ui_print "****************************************"
ui_print "  MiPush Framework (System App) 模块   "
ui_print "****************************************"

ui_print "- 正在配置系统特权应用权限..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/etc" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755

# 必须通过流式安装 (pm install -r -d -S <size> < apk) 同步写入 /data/app：
# 1) KernelSU 默认对非 Root 应用执行 umount /system/priv-app，仅放系统分区会导致打开秒闪退；
#    同步写入 /data/app 后，既通过 /system/priv-app 获得 SYSTEM + PRIVILEGED 权限，又通过 /data/app 避开 umount；
# 2) 旧版直接传 $MODPATH (/data/adb/modules_update/...) 给 pm install 易被 SELinux 拦截导致版本不更新，
#    改用 stdin 流式传输由 root 读取文件喂给 PMS，彻底绕过 /data/adb 权限限制。
APK_PATH="$MODPATH/system/priv-app/com.xiaomi.xmsf/com.xiaomi.xmsf.apk"
if [ -f "$APK_PATH" ]; then
    if command -v pm >/dev/null 2>&1; then
        APK_SIZE=$(wc -c < "$APK_PATH")
        ui_print "- 正在流式安装更新应用 (${APK_SIZE} bytes)..."
        INSTALL_OUT=$(pm install -r -d -S "$APK_SIZE" < "$APK_PATH" 2>&1)
        INSTALL_RC=$?
        echo "$INSTALL_OUT" | while IFS= read -r line; do
            ui_print "  $line"
        done
        if [ "$INSTALL_RC" -ne 0 ] || ! echo "$INSTALL_OUT" | grep -q "Success"; then
            ui_print "! 即时安装未成功，已标记在开机后自动补装"
            touch "$MODPATH/.need_install"
        fi
    else
        ui_print "- 当前环境无 pm，已标记在开机后自动补装"
        touch "$MODPATH/.need_install"
    fi
fi

ui_print "- 正在清理系统包缓存并标记重启刷新..."
touch "$MODPATH/.clear_cache"
rm -rf /data/system/package_cache/* 2>/dev/null || true

ui_print "****************************************"
ui_print " 安装成功！"
ui_print " 重启手机后将自动生效为最新系统特权应用喵~"
ui_print "****************************************"
EOF

ZIP_NAME="MiPushFramework-${TAG}-ksu-module.zip"
rm -f "$ZIP_NAME"
(cd "$MODULE_DIR" && zip -r "../../$ZIP_NAME" ./*)
echo "Successfully packaged: $ZIP_NAME"

if [ -n "$GITHUB_ENV" ]; then
    echo "KSU_MODULE_ZIP=$ZIP_NAME" >> "$GITHUB_ENV"
fi
