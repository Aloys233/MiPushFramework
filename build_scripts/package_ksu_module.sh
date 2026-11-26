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
description=将 MiPushFramework (com.xiaomi.xmsf) 作为系统特权应用 (priv-app) 挂载并自动刷新包缓存，支持 Android 11+ 包可见性限制与离线推送通道。
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

# 生成 service.sh：等待系统完全启动后再配置电池优化白名单，确保首次安装也生效
cat << 'EOF' > "$MODULE_DIR/service.sh"
#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
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

# 不再在 customize.sh 里调用 pm install：
# 1) 直接传入 $MODPATH (/data/adb/modules_update/...) 易受 SELinux 限制失败；
# 2) pm install 会把 APK 写入 /data/app 形成 UPDATED_SYSTEM_APP，后续刷模块时反过来压住 /system/priv-app 新版。
# 若检测到旧的 /data/app 覆盖层，则通过 pm uninstall-system-updates 移除（保留应用数据）。
if command -v pm >/dev/null 2>&1; then
    PKG_DUMP=$(dumpsys package com.xiaomi.xmsf 2>/dev/null || true)
    if echo "$PKG_DUMP" | grep -q "UPDATED_SYSTEM_APP"; then
        ui_print "- 检测到 /data/app 存在覆盖更新，正在移除以启用模块新版(保留数据)..."
        pm uninstall-system-updates com.xiaomi.xmsf 2>&1 | while read -r line; do
            ui_print "  $line"
        done
    elif echo "$PKG_DUMP" | grep -q "codePath=/data/app/"; then
        ui_print "! 注意：检测到已作为普通用户应用安装 com.xiaomi.xmsf"
        ui_print "  若模块版本号更高且签名一致，重启后将自动接管为系统特权应用"
        ui_print "  若同版本重装或签名不一致，请先卸载旧版后再重启"
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
