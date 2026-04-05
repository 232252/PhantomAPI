#!/bin/bash
# PhantomAPI CLI 工具 v3.0
# 用法: phantom_api.sh <device_ip> <command> [args]

set -e

DEVICE_IP="${1:-192.168.110.140}"
BASE_URL="http://${DEVICE_IP}:9999"
COMMAND="${2:-ping}"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

api_get() { curl -s "${BASE_URL}$1"; }
api_post() { curl -s -X POST "${BASE_URL}$1" -H "Content-Type: application/json" -d "$2"; }

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
        log_info "点击 ($X, $Y)"
        api_post "/api/ui/tap" "{\"x\":${X},\"y\":${Y}}" | jq .
        ;;
    swipe)
        SX="${3:-540}"; SY="${4:-1500}"; EX="${5:-540}"; EY="${6:-500}"; DUR="${7:-300}"
        log_info "滑动 ($SX,$SY) -> ($EX,$EY)"
        api_post "/api/ui/swipe" "{\"startX\":${SX},\"startY\":${SY},\"endX\":${EX},\"endY\":${EY},\"duration\":${DUR}}" | jq .
        ;;
    back)
        log_info "返回键"
        api_post "/api/ui/back" "{}" | jq .
        ;;
    wait)
        TEXT="${3:-确定}"
        TIMEOUT="${4:-5000}"
        log_info "等待: $TEXT (${TIMEOUT}ms)"
        api_post "/api/ui/wait" "{\"condition\":{\"type\":\"text\",\"text\":\"${TEXT}\"},\"timeout_ms\":${TIMEOUT}}" | jq .
        ;;
    web-detect)
        api_get "/api/web/detect" | jq .
        ;;
    web-dom)
        api_get "/api/web/dom" | jq .
        ;;
    web-find)
        TEXT="${3:-百度}"
        log_info "查找元素: $TEXT"
        api_get "/api/web/find?text=${TEXT}" | jq .
        ;;
    web-click)
        # 支持坐标或文本
        if [[ "$3" =~ ^[0-9]+$ ]]; then
            X="${3:-540}"; Y="${4:-960}"
            log_info "坐标点击 ($X, $Y)"
            api_post "/api/web/click" "{\"x\":${X},\"y\":${Y}}" | jq .
        else
            TEXT="${3:-登录}"
            log_info "文本点击: $TEXT"
            api_post "/api/web/click" "{\"text\":\"${TEXT}\"}" | jq .
        fi
        ;;
    cdp-pages)
        log_info "获取 Chrome 页面列表"
        api_get "/api/cdp/pages" | jq .
        ;;
    cdp-title)
        log_info "获取 Chrome 标题"
        api_get "/api/cdp/title" | jq .
        ;;
    scope-apps)
        log_info "获取需要启用作用域的应用"
        api_get "/api/scope/apps" | jq .
        ;;
    net-status)
        api_get "/api/net/status" | jq .
        ;;
    net-connections)
        api_get "/api/net/connections" | jq '.connections[:20]'
        ;;
    *)
        echo "PhantomAPI CLI v3.0"
        echo ""
        echo "用法: $0 <device_ip> <command> [args]"
        echo ""
        echo "系统命令:"
        echo "  ping              - 测试连接"
        echo "  info              - 设备信息"
        echo "  foreground        - 前台应用"
        echo "  packages          - 已安装应用"
        echo ""
        echo "UI 命令:"
        echo "  tree              - UI 树"
        echo "  find <text>       - 查找节点"
        echo "  tap <x> <y>       - 点击坐标"
        echo "  swipe <sx> <sy> <ex> <ey> [duration] - 滑动"
        echo "  back              - 返回键"
        echo ""
        echo "WebView 命令:"
        echo "  web-detect        - 检测 WebView 状态"
        echo "  web-dom           - 获取 DOM"
        echo "  web-find <text>   - 查找元素"
        echo "  web-click <x> <y> - 坐标点击"
        echo "  web-click <text>  - 文本点击"
        echo ""
        echo "Chrome CDP 命令:"
        echo "  cdp-pages         - 页面列表"
        echo "  cdp-title         - 当前标题"
        echo ""
        echo "辅助命令:"
        echo "  scope-apps        - 需要启用作用域的应用"
        echo ""
        echo "网络命令:"
        echo "  net-status        - 网络状态"
        echo "  net-connections   - 连接拓扑"
        ;;
esac
