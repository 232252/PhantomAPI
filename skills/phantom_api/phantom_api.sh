#!/bin/bash
# PhantomAPI CLI 工具
# 用法: phantom_api.sh <device_ip> <command> [args]

set -e

DEVICE_IP="${1:-192.168.110.140}"
BASE_URL="http://${DEVICE_IP}:9999"
COMMAND="${2:-ping}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

# API 调用函数
api_get() {
    local endpoint="$1"
    curl -s "${BASE_URL}${endpoint}"
}

api_post() {
    local endpoint="$1"
    local data="$2"
    curl -s -X POST "${BASE_URL}${endpoint}" \
        -H "Content-Type: application/json" \
        -d "$data"
}

case "$COMMAND" in
    ping)
        api_get "/api/ping" | jq .
        ;;
    
    info)
        api_get "/api/sys/info" | jq .
        ;;
    
    foreground)
        api_get "/api/sys/foreground" | jq .
        ;;
    
    packages)
        api_get "/api/sys/packages" | jq '.packages[:10]'
        ;;
    
    tree)
        api_get "/api/ui/tree" | jq .
        ;;
    
    find)
        TEXT="${3:-设置}"
        api_get "/api/ui/find?text=${TEXT}" | jq .
        ;;
    
    tap)
        X="${3:-540}"
        Y="${4:-960}"
        log_info "点击坐标 ($X, $Y)"
        api_post "/api/ui/tap" "{\"x\":${X},\"y\":${Y}}" | jq .
        ;;
    
    swipe)
        SX="${3:-540}"
        SY="${4:-1500}"
        EX="${5:-540}"
        EY="${6:-500}"
        DUR="${7:-300}"
        log_info "滑动 ($SX,$SY) -> ($EX,$EY)"
        api_post "/api/ui/swipe" "{\"startX\":${SX},\"startY\":${SY},\"endX\":${EX},\"endY\":${EY},\"duration\":${DUR}}" | jq .
        ;;
    
    back)
        log_info "返回键"
        api_post "/api/ui/back" | jq .
        ;;
    
    web-debug)
        api_get "/api/web/debug" | jq .
        ;;
    
    web-exec)
        SCRIPT="${3:-console.log('test')}"
        api_post "/api/web/execute" "{\"script\":\"${SCRIPT}\"}" | jq .
        ;;
    
    net-status)
        api_get "/api/net/status" | jq .
        ;;
    
    wifi)
        api_get "/api/net/wifi" | jq .
        ;;
    
    *)
        echo "PhantomAPI CLI 工具"
        echo ""
        echo "用法: $0 <device_ip> <command> [args]"
        echo ""
        echo "命令:"
        echo "  ping              - 测试连接"
        echo "  info              - 设备信息"
        echo "  foreground        - 前台应用"
        echo "  packages          - 已安装应用"
        echo "  tree              - UI 树"
        echo "  find <text>       - 查找节点"
        echo "  tap <x> <y>       - 点击坐标"
        echo "  swipe <sx> <sy> <ex> <ey> [duration] - 滑动"
        echo "  back              - 返回键"
        echo "  web-debug         - WebView 调试状态"
        echo "  web-exec <script> - 执行 JS"
        echo "  net-status        - 网络状态"
        echo "  wifi              - WiFi 信息"
        ;;
esac
