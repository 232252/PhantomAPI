#!/bin/bash
# PhantomAPI 完整测试脚本

ADB="/tmp/platform-tools/adb"
IP="192.168.110.140"

echo "=========================================="
echo "  PhantomAPI 完整功能测试"
echo "  设备: $IP"
echo "=========================================="

# 1. 连接设备
echo ""
echo ">>> 1. 连接设备..."
$ADB connect $IP:5555 2>/dev/null
$ADB devices

# 2. 获取设备信息
echo ""
echo ">>> 2. 获取设备信息..."
echo "型号: $($ADB shell getprop ro.product.model)"
echo "品牌: $($ADB shell getprop ro.product.brand)"
echo "SDK: $($ADB shell getprop ro.build.version.sdk)"
echo "Android: $($ADB shell getprop ro.build.version.release)"
echo "分辨率: $($ADB shell wm size | grep Physical)"

# 3. 获取前台应用
echo ""
echo ">>> 3. 获取前台应用..."
$ADB shell "dumpsys activity top | grep ACTIVITY | head -1"

# 4. 获取 UI 树
echo ""
echo ">>> 4. 获取 UI 节点树..."
$ADB shell "uiautomator dump /sdcard/ui.xml 2>&1"
UI_XML=$($ADB shell "cat /sdcard/ui.xml")
echo "UI 树大小: $(echo "$UI_XML" | wc -c) 字节"

# 5. 查找特定文本
echo ""
echo ">>> 5. 查找文本节点..."
echo "$UI_XML" | grep -o 'text="[^"]*"' | sort -u | head -20

# 6. 测试 WebView 调试端口
echo ""
echo ">>> 6. 检测 WebView 调试端口..."
$ADB shell "cat /proc/net/unix | grep webview_devtools" || echo "未找到 WebView 调试端口"

# 7. 获取网络连接
echo ""
echo ">>> 7. 获取网络连接..."
$ADB shell "cat /proc/net/tcp | wc -l"
echo "TCP 连接数: $($ADB shell "cat /proc/net/tcp | wc -l")"

# 8. 测试点击
echo ""
echo ">>> 8. 测试点击功能..."
read -p "是否测试点击屏幕中心? (y/n): " test_tap
if [ "$test_tap" = "y" ]; then
    echo "点击 (540, 1200)..."
    $ADB shell "input tap 540 1200"
    sleep 1
    echo "点击完成"
fi

# 9. 测试返回
echo ""
echo ">>> 9. 测试返回键..."
read -p "是否测试返回键? (y/n): " test_back
if [ "$test_back" = "y" ]; then
    $ADB shell "input keyevent KEYCODE_BACK"
    echo "返回键已按下"
fi

# 10. 测试 Home
echo ""
echo ">>> 10. 测试 Home 键..."
read -p "是否测试 Home 键? (y/n): " test_home
if [ "$test_home" = "y" ]; then
    $ADB shell "input keyevent KEYCODE_HOME"
    echo "Home 键已按下"
fi

# 11. 安装测试
echo ""
echo ">>> 11. 检查已安装的 PhantomAPI..."
$ADB shell "pm list packages | grep phantom" || echo "未安装 PhantomAPI"

# 12. Root 检测
echo ""
echo ">>> 12. 检查 Root 权限..."
$ADB shell "su -c 'id'" 2>/dev/null && echo "已 Root" || echo "未 Root"

# 13. Magisk 检测
echo ""
echo ">>> 13. 检查 Magisk..."
$ADB shell "pm list packages | grep magisk" && echo "已安装 Magisk" || echo "未安装 Magisk"

# 14. LSPosed 检测
echo ""
echo ">>> 14. 检查 LSPosed..."
$ADB shell "pm list packages | grep lsposed" && echo "已安装 LSPosed" || echo "未安装 LSPosed"

echo ""
echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo ""
echo "API 端点 (如果服务已启动):"
echo "  http://$IP:9999/api/ping"
echo "  http://$IP:9999/api/sys/info"
echo "  http://$IP:9999/api/ui/tree"
echo "  http://$IP:9999/api/docs"
