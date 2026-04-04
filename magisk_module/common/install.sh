#!/system/bin/sh
# PhantomAPI - 安装脚本

MODDIR="${0%/*}"

# 输出信息
ui_print "========================================"
ui_print "  PhantomAPI - Android Control Service "
ui_print "========================================"
ui_print ""
ui_print "正在安装..."

# 检查 Android 版本
SDK=$(getprop ro.build.version.sdk)
ui_print "Android SDK: $SDK"

if [ "$SDK" -lt 26 ]; then
    ui_print "错误: 需要 Android 8.0+"
    exit 1
fi

# 检查是否有 root
if [ ! -f "/system/bin/su" ] && [ ! -f "/system/xbin/su" ]; then
    ui_print "警告: 未检测到 su，部分功能可能受限"
fi

ui_print ""
ui_print "安装完成！"
ui_print ""
ui_print "使用步骤:"
ui_print "1. 重启设备"
ui_print "2. 启用无障碍服务"
ui_print "3. 访问 http://<设备IP>:9999"
ui_print ""
ui_print "========================================"
