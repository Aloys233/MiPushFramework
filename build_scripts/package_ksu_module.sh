#!/bin/bash
set -e

TAG="${1:-v1.0.0}"
APK_FILE=$(find push/build/outputs/apk/normal/release/ -name "*.apk" 2>/dev/null | head -n 1)

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
cat << 'EOF' > "$MODULE_DIR/system/etc/permissions/privapp-permissions-com.xiaomi.xmsf.xml"
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.xiaomi.xmsf">
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
description=将 MiPushFramework (com.xiaomi.xmsf) 作为系统特权应用 (priv-app) 挂载并自动安装，支持 Android 11+ 包可见性限制与离线推送通道。
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

APK_PATH="$MODPATH/system/priv-app/com.xiaomi.xmsf/com.xiaomi.xmsf.apk"
if [ -f "$APK_PATH" ]; then
    ui_print "- 正在自动安装应用 (pm install)..."
    pm install -r -d "$APK_PATH" 2>&1 | while read -r line; do
        ui_print "  $line"
    done
fi

ui_print "- 正在配置电池优化白名单..."
dumpsys deviceidle whitelist +com.xiaomi.xmsf 2>/dev/null || true

ui_print "****************************************"
ui_print " 安装成功！"
ui_print " 重启手机后将完全生效为系统特权应用喵~"
ui_print "****************************************"
EOF

ZIP_NAME="MiPushFramework-${TAG}-ksu-module.zip"
(cd "$MODULE_DIR" && zip -r "../../$ZIP_NAME" ./*)
echo "Successfully packaged: $ZIP_NAME"

if [ -n "$GITHUB_ENV" ]; then
    echo "KSU_MODULE_ZIP=$ZIP_NAME" >> "$GITHUB_ENV"
fi
