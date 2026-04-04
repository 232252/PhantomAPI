#!/bin/bash
# PhantomAPI Shell 版本 - 直接通过 ADB 执行

ADB="/tmp/platform-tools/adb"
DEVICE="192.168.110.140:5555"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[INFO]${NC} $1"; }
err() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查连接
check_device() {
    if ! $ADB devices | grep -q "$DEVICE"; then
        $ADB connect $DEVICE
    fi
}

# 获取设备信息
get_sys_info() {
    log "获取设备信息..."
    echo "Model: $($ADB shell getprop ro.product.model)"
    echo "Brand: $($ADB shell getprop ro.product.brand)"
    echo "SDK: $($ADB shell getprop ro.build.version.sdk)"
    echo "Android: $($ADB shell getprop ro.build.version.release)"
    echo "Screen: $($ADB shell wm size)"
}

# 获取前台应用
get_foreground() {
    log "获取前台应用..."
    $ADB shell "dumpsys activity activities | grep mResumedActivity | head -1"
}

# 获取 UI 树
get_ui_tree() {
    log "获取 UI 节点树..."
    $ADB shell "uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml"
}

# 点击坐标
tap() {
    local x=$1 y=$2
    log "点击坐标 ($x, $y)..."
    $ADB shell "input tap $x $y"
}

# 滑动
swipe() {
    local sx=$1 sy=$2 ex=$3 ey=$4
    log "滑动 ($sx, $sy) -> ($ex, $ey)..."
    $ADB shell "input swipe $sx $sy $ex $ey 300"
}

# 返回
back() {
    log "按返回键..."
    $ADB shell "input keyevent KEYCODE_BACK"
}

# Home
home() {
    log "按 Home 键..."
    $ADB shell "input keyevent KEYCODE_HOME"
}

# 查找并点击文本
find_and_click() {
    local text=$1
    log "查找并点击文本: $text"
    
    # dump UI
    $ADB shell "uiautomator dump /sdcard/ui.xml"
    
    # 查找包含文本的节点的坐标
    local xml=$($ADB shell "cat /sdcard/ui.xml")
    
    # 使用 grep 查找文本
    local node=$(echo "$xml" | grep -i "text=\"$text\"" | head -1)
    
    if [ -z "$node" ]; then
        err "未找到文本: $text"
        return 1
    fi
    
    # 提取 bounds
    local bounds=$(echo "$node" | grep -oP 'bounds="\[\d+,\d+\]\[\d+,\d+\]"')
    
    if [ -z "$bounds" ]; then
        err "无法提取坐标"
        return 1
    fi
    
    # 解析坐标
    local coords=$(echo "$bounds" | grep -oP '\d+')
    local x1=$(echo $coords | awk '{print $1}')
    local y1=$(echo $coords | awk '{print $2}')
    local x2=$(echo $coords | awk '{print $3}')
    local y2=$(echo $coords | awk '{print $4}')
    
    local cx=$(( (x1 + x2) / 2 ))
    local cy=$(( (y1 + y2) / 2 ))
    
    log "找到节点，坐标: ($cx, $cy)"
    tap $cx $cy
}

# 获取网络连接
get_connections() {
    log "获取网络连接..."
    $ADB shell "cat /proc/net/tcp | head -20"
}

# 获取 WebView sockets
get_webview_sockets() {
    log "获取 WebView 调试端口..."
    $ADB shell "cat /proc/net/unix | grep webview_devtools"
}

# 测试所有功能
test_all() {
    log "=== 开始测试 ==="
    
    echo ""
    get_sys_info
    
    echo ""
    get_foreground
    
    echo ""
    log "测试点击屏幕中心..."
    tap 540 960
    
    sleep 1
    
    echo ""
    log "测试返回键..."
    back
    
    echo ""
    log "=== 测试完成 ==="
}

# 交互式菜单
menu() {
    while true; do
        echo ""
        echo "=== PhantomAPI Shell 测试菜单 ==="
        echo "1. 获取设备信息"
        echo "2. 获取前台应用"
        echo "3. 获取 UI 树"
        echo "4. 点击坐标"
        echo "5. 滑动"
        echo "6. 返回键"
        echo "7. Home 键"
        echo "8. 查找并点击文本"
        echo "9. 获取网络连接"
        echo "10. 获取 WebView 调试端口"
        echo "0. 运行所有测试"
        echo "q. 退出"
        echo ""
        read -p "选择: " choice
        
        case $choice in
            1) get_sys_info ;;
            2) get_foreground ;;
            3) get_ui_tree ;;
            4) 
               read -p "输入 X 坐标: " x
               read -p "输入 Y 坐标: " y
               tap $x $y
               ;;
            5)
               read -p "起始 X: " sx
               read -p "起始 Y: " sy
               read -p "结束 X: " ex
               read -p "结束 Y: " ey
               swipe $sx $sy $ex $ey
               ;;
            6) back ;;
            7) home ;;
            8)
               read -p "输入要点击的文本: " text
               find_and_click "$text"
               ;;
            9) get_connections ;;
            10) get_webview_sockets ;;
            0) test_all ;;
            q|Q) exit 0 ;;
            *) err "无效选择" ;;
        esac
    done
}

# 主函数
main() {
    check_device
    
    case "$1" in
        "info") get_sys_info ;;
        "foreground") get_foreground ;;
        "tree") get_ui_tree ;;
        "tap") tap $2 $3 ;;
        "swipe") swipe $2 $3 $4 $5 ;;
        "back") back ;;
        "home") home ;;
        "find") find_and_click "$2" ;;
        "test") test_all ;;
        *) menu ;;
    esac
}

main "$@"
