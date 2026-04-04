#!/bin/bash
# PhantomAPI 测试脚本

# 设备 IP
DEVICE_IP="${1:-192.168.110.140}"
PORT="9999"
BASE_URL="http://$DEVICE_IP:$PORT"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

# 发送 HTTP 请求
http_get() {
    curl -s -w "\n[HTTP Status: %{http_code}]" "$1"
}

http_post() {
    curl -s -w "\n[HTTP Status: %{http_code}]" -X POST -H "Content-Type: application/json" -d "$2" "$1"
}

# 测试服务是否运行
test_ping() {
    log_test "测试服务连接..."
    echo "GET $BASE_URL/api/ping"
    http_get "$BASE_URL/api/ping"
    echo ""
}

# 测试设备信息
test_sys_info() {
    log_test "获取设备信息..."
    echo "GET $BASE_URL/api/sys/info"
    http_get "$BASE_URL/api/sys/info"
    echo ""
}

# 测试前台应用
test_foreground() {
    log_test "获取前台应用..."
    echo "GET $BASE_URL/api/sys/foreground"
    http_get "$BASE_URL/api/sys/foreground"
    echo ""
}

# 测试已安装应用
test_packages() {
    log_test "获取已安装应用列表..."
    echo "GET $BASE_URL/api/sys/packages"
    http_get "$BASE_URL/api/sys/packages" | head -100
    echo ""
}

# 测试节点树
test_ui_tree() {
    log_test "获取 UI 节点树..."
    echo "GET $BASE_URL/api/ui/tree"
    http_get "$BASE_URL/api/ui/tree" | head -200
    echo ""
}

# 测试坐标点击
test_tap() {
    log_test "测试坐标点击 (中心点)..."
    echo "POST $BASE_URL/api/ui/tap"
    http_post "$BASE_URL/api/ui/tap" '{"x":540,"y":960}'
    echo ""
}

# 测试滑动
test_swipe() {
    log_test "测试滑动..."
    echo "POST $BASE_URL/api/ui/swipe"
    http_post "$BASE_URL/api/ui/swipe" '{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":300}'
    echo ""
}

# 测试返回键
test_back() {
    log_test "测试返回键..."
    echo "GET $BASE_URL/api/ui/back"
    http_get "$BASE_URL/api/ui/back"
    echo ""
}

# 测试 Home 键
test_home() {
    log_test "测试 Home 键..."
    echo "GET $BASE_URL/api/ui/home"
    http_get "$BASE_URL/api/ui/home"
    echo ""
}

# 测试 WebView 检测
test_web_detect() {
    log_test "检测 WebView..."
    echo "GET $BASE_URL/api/web/detect"
    http_get "$BASE_URL/api/web/detect"
    echo ""
}

# 测试 WebView Socket 列表
test_web_sockets() {
    log_test "获取 WebView Socket 列表..."
    echo "GET $BASE_URL/api/web/sockets"
    http_get "$BASE_URL/api/web/sockets"
    echo ""
}

# 测试网络连接
test_net_connections() {
    log_test "获取网络连接..."
    echo "GET $BASE_URL/api/net/connections"
    http_get "$BASE_URL/api/net/connections" | head -100
    echo ""
}

# 测试流量统计
test_net_traffic() {
    log_test "获取流量统计..."
    echo "GET $BASE_URL/api/net/traffic"
    http_get "$BASE_URL/api/net/traffic"
    echo ""
}

# 测试查找节点
test_find() {
    log_test "查找节点..."
    echo "GET $BASE_URL/api/ui/find?text=设置"
    http_get "$BASE_URL/api/ui/find?text=设置"
    echo ""
}

# 运行所有测试
run_all_tests() {
    log_info "开始运行所有测试..."
    log_info "目标设备: $DEVICE_IP:$PORT"
    echo "======================================"
    
    # 基础测试
    test_ping
    echo "======================================"
    
    # 系统域测试
    test_sys_info
    echo "======================================"
    
    test_foreground
    echo "======================================"
    
    test_packages
    echo "======================================"
    
    # UI 域测试
    test_ui_tree
    echo "======================================"
    
    test_find
    echo "======================================"
    
    # Web 域测试
    test_web_detect
    echo "======================================"
    
    test_web_sockets
    echo "======================================"
    
    # 网络域测试
    test_net_connections
    echo "======================================"
    
    test_net_traffic
    echo "======================================"
    
    log_info "测试完成"
}

# 交互式菜单
interactive_menu() {
    while true; do
        echo ""
        echo "========== PhantomAPI 测试菜单 =========="
        echo "目标设备: $DEVICE_IP:$PORT"
        echo ""
        echo "系统域:"
        echo "  1. Ping 测试"
        echo "  2. 获取设备信息"
        echo "  3. 获取前台应用"
        echo "  4. 获取已安装应用"
        echo ""
        echo "UI 域:"
        echo "  5. 获取节点树"
        echo "  6. 查找节点"
        echo "  7. 坐标点击"
        echo "  8. 滑动"
        echo "  9. 返回键"
        echo "  10. Home 键"
        echo ""
        echo "Web 域:"
        echo "  11. 检测 WebView"
        echo "  12. WebView Socket 列表"
        echo ""
        echo "网络域:"
        echo "  13. 网络连接"
        echo "  14. 流量统计"
        echo ""
        echo "  0. 运行所有测试"
        echo "  q. 退出"
        echo ""
        read -p "请选择: " choice
        
        case "$choice" in
            1) test_ping ;;
            2) test_sys_info ;;
            3) test_foreground ;;
            4) test_packages ;;
            5) test_ui_tree ;;
            6) 
                read -p "输入搜索文本: " text
                http_get "$BASE_URL/api/ui/find?text=$text"
                ;;
            7)
                read -p "输入 X 坐标: " x
                read -p "输入 Y 坐标: " y
                http_post "$BASE_URL/api/ui/tap" "{\"x\":$x,\"y\":$y}"
                ;;
            8) test_swipe ;;
            9) test_back ;;
            10) test_home ;;
            11) test_web_detect ;;
            12) test_web_sockets ;;
            13) test_net_connections ;;
            14) test_net_traffic ;;
            0) run_all_tests ;;
            q|Q) 
                log_info "退出测试"
                exit 0 
                ;;
            *) log_warn "无效选择" ;;
        esac
    done
}

# 主函数
main() {
    if [ "$1" = "-i" ] || [ "$1" = "--interactive" ]; then
        interactive_menu
    else
        run_all_tests
    fi
}

main "$@"
