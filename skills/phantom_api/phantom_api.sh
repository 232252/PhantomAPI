#!/bin/bash
# PhantomAPI CLI 工具 v4.0
# 用法: phantom_api.sh <device_ip> <command> [args]
# GitHub: https://github.com/232252/PhantomAPI

set -e

# 默认配置
DEFAULT_IP="192.168.110.140"
DEFAULT_TIMEOUT=5
MAX_RETRIES=3

# 参数解析
DEVICE_IP="${1:-$DEFAULT_IP}"
BASE_URL="http://${DEVICE_IP}:9999"
COMMAND="${2:-ping}"
shift 2 2>/dev/null || true

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_debug() { echo -e "${BLUE}[DEBUG]${NC} $1"; }

# API 请求函数 (带重试)
api_get() {
    local endpoint="$1"
    local retries=0
    local response=""
    
    while [ $retries -lt $MAX_RETRIES ]; do
        response=$(curl -s --connect-timeout $DEFAULT_TIMEOUT --max-time 10 "${BASE_URL}${endpoint}" 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$response" ]; then
            echo "$response"
            return 0
        fi
        retries=$((retries + 1))
        [ $retries -lt $MAX_RETRIES ] && sleep 0.5
    done
    
    log_error "请求失败: GET ${endpoint}"
    return 1
}

api_post() {
    local endpoint="$1"
    local data="$2"
    local retries=0
    local response=""
    
    while [ $retries -lt $MAX_RETRIES ]; do
        response=$(curl -s --connect-timeout $DEFAULT_TIMEOUT --max-time 10 \
            -X POST "${BASE_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$response" ]; then
            echo "$response"
            return 0
        fi
        retries=$((retries + 1))
        [ $retries -lt $MAX_RETRIES ] && sleep 0.5
    done
    
    log_error "请求失败: POST ${endpoint}"
    return 1
}

# JSON 格式化 (兼容无 jq 的情况)
json_format() {
    if command -v jq &>/dev/null; then
        jq .
    elif command -v python3 &>/dev/null; then
        python3 -m json.tool
    else
        cat
    fi
}

# 帮助信息
show_help() {
    echo -e "${CYAN}PhantomAPI CLI v4.0${NC}"
    echo ""
    echo "用法: $0 <device_ip> <command> [args]"
    echo ""
    echo -e "${YELLOW}系统命令:${NC}"
    echo "  ping                      测试连接"
    echo "  info                      设备信息"
    echo "  foreground                前台应用"
    echo "  packages [limit]          已安装应用 (默认显示前10个)"
    echo ""
    echo -e "${YELLOW}UI 命令:${NC}"
    echo "  tree                      获取 UI 树"
    echo "  find <text>               查找节点"
    echo "  tap <x> <y>               点击坐标"
    echo "  tap-wait <x> <y> <text>   点击并等待文本出现"
    echo "  swipe <sx> <sy> <ex> <ey> [duration]  滑动"
    echo "  back                      返回键"
    echo "  home                      主页键"
    echo "  wait <text> [timeout_ms]  等待文本出现"
    echo ""
    echo -e "${YELLOW}Web/WebView 命令:${NC}"
    echo "  web-detect                检测 WebView 状态"
    echo "  web-dom                   获取 WebView DOM"
    echo "  web-find <text>           查找 WebView 元素"
    echo "  web-click <x> <y>         坐标点击 (Web)"
    echo "  web-click-text <text>     文本点击 (Web)"
    echo ""
    echo -e "${YELLOW}Chrome CDP 命令:${NC}"
    echo "  cdp-pages                 获取 Chrome 页面列表"
    echo "  cdp-title                 获取 Chrome 页面标题"
    echo ""
    echo -e "${YELLOW}网络命令:${NC}"
    echo "  net-status                网络状态"
    echo "  net-wifi                  WiFi 信息"
    echo "  net-connections [limit]   网络连接 (默认20条)"
    echo "  net-traffic               流量统计"
    echo ""
    echo -e "${YELLOW}辅助命令:${NC}"
    echo "  scope-apps                需要启用作用域的应用"
    echo "  test                      完整功能测试"
    echo "  help                      显示帮助"
    echo ""
    echo -e "${CYAN}示例:${NC}"
    echo "  $0 192.168.110.140 ping"
    echo "  $0 192.168.110.140 tap 540 960"
    echo "  $0 192.168.110.140 web-click-text 登录"
    echo "  $0 192.168.110.140 test"
    echo ""
    echo "GitHub: https://github.com/232252/PhantomAPI"
}

# 命令处理
case "$COMMAND" in
    # === 系统命令 ===
    ping)
        log_info "测试连接..."
        api_get "/api/ping" | json_format
        ;;
    info)
        log_info "获取设备信息..."
        api_get "/api/sys/info" | json_format
        ;;
    foreground)
        log_info "获取前台应用..."
        api_get "/api/sys/foreground" | json_format
        ;;
    packages)
        LIMIT="${1:-10}"
        log_info "获取已安装应用 (前${LIMIT}个)..."
        api_get "/api/sys/packages" | if command -v jq &>/dev/null; then jq ".packages[:${LIMIT}]"; else json_format; fi
        ;;

    # === UI 命令 ===
    tree)
        log_info "获取 UI 树..."
        api_get "/api/ui/tree" | json_format
        ;;
    find)
        TEXT="${1:-设置}"
        log_info "查找节点: $TEXT"
        api_get "/api/ui/find?text=${TEXT}" | json_format
        ;;
    tap)
        X="${1:-540}"
        Y="${2:-960}"
        log_info "点击 ($X, $Y)"
        api_post "/api/ui/tap" "{\"x\":${X},\"y\":${Y}}" | json_format
        ;;
    tap-wait)
        X="${1:-540}"
        Y="${2:-960}"
        TEXT="${3:-确定}"
        TIMEOUT="${4:-5000}"
        log_info "点击 ($X, $Y) 并等待: $TEXT (${TIMEOUT}ms)"
        api_post "/api/ui/tap" "{\"x\":${X},\"y\":${Y},\"wait\":{\"condition\":{\"type\":\"text\",\"text\":\"${TEXT}\"},\"timeout_ms\":${TIMEOUT}}}" | json_format
        ;;
    swipe)
        SX="${1:-540}"
        SY="${2:-1500}"
        EX="${3:-540}"
        EY="${4:-500}"
        DUR="${5:-300}"
        log_info "滑动 ($SX,$SY) -> ($EX,$EY) [${DUR}ms]"
        api_post "/api/ui/swipe" "{\"startX\":${SX},\"startY\":${SY},\"endX\":${EX},\"endY\":${EY},\"duration\":${DUR}}" | json_format
        ;;
    back)
        log_info "返回键"
        api_post "/api/ui/back" "{}" | json_format
        ;;
    home)
        log_info "主页键"
        api_post "/api/ui/home" "{}" | json_format
        ;;
    wait)
        TEXT="${1:-确定}"
        TIMEOUT="${2:-5000}"
        log_info "等待: $TEXT (${TIMEOUT}ms)"
        api_post "/api/ui/wait" "{\"condition\":{\"type\":\"text\",\"text\":\"${TEXT}\"},\"timeout_ms\":${TIMEOUT}}" | json_format
        ;;

    # === Web/WebView 命令 ===
    web-detect)
        log_info "检测 WebView 状态..."
        api_get "/api/web/detect" | json_format
        ;;
    web-dom)
        log_info "获取 WebView DOM..."
        api_get "/api/web/dom" | json_format
        ;;
    web-find)
        TEXT="${1:-百度}"
        log_info "查找 WebView 元素: $TEXT"
        api_get "/api/web/find?text=${TEXT}" | json_format
        ;;
    web-click)
        X="${1:-540}"
        Y="${2:-960}"
        log_info "Web 坐标点击 ($X, $Y)"
        api_post "/api/web/click" "{\"x\":${X},\"y\":${Y}}" | json_format
        ;;
    web-click-text)
        TEXT="${1:-登录}"
        log_info "Web 文本点击: $TEXT"
        api_post "/api/web/click" "{\"text\":\"${TEXT}\"}" | json_format
        ;;

    # === Chrome CDP 命令 ===
    cdp-pages)
        log_info "获取 Chrome 页面列表..."
        api_get "/api/cdp/pages" | json_format
        ;;
    cdp-title)
        log_info "获取 Chrome 页面标题..."
        api_get "/api/cdp/title" | json_format
        ;;

    # === 网络命令 ===
    net-status)
        log_info "获取网络状态..."
        api_get "/api/net/status" | json_format
        ;;
    net-wifi)
        log_info "获取 WiFi 信息..."
        api_get "/api/net/wifi" | json_format
        ;;
    net-connections)
        LIMIT="${1:-20}"
        log_info "获取网络连接 (前${LIMIT}条)..."
        api_get "/api/net/connections" | if command -v jq &>/dev/null; then jq ".connections[:${LIMIT}]"; else json_format; fi
        ;;
    net-traffic)
        log_info "获取流量统计..."
        api_get "/api/net/traffic" | json_format
        ;;

    # === 辅助命令 ===
    scope-apps)
        log_info "获取需要启用作用域的应用..."
        api_get "/api/scope/apps" | json_format
        ;;

    # === 完整测试 ===
    test)
        echo -e "${CYAN}========== PhantomAPI 功能测试 ==========${NC}"
        echo ""
        
        echo -e "${YELLOW}[1/6] 测试连接...${NC}"
        PING_RESULT=$(api_get "/api/ping" 2>/dev/null)
        if echo "$PING_RESULT" | grep -q "ok\|success"; then
            echo -e "${GREEN}✓ 连接正常${NC}"
        else
            echo -e "${RED}✗ 连接失败${NC}"
            exit 1
        fi
        echo ""
        
        echo -e "${YELLOW}[2/6] 测试系统信息...${NC}"
        api_get "/api/sys/info" | json_format
        echo ""
        
        echo -e "${YELLOW}[3/6] 测试前台应用...${NC}"
        api_get "/api/sys/foreground" | json_format
        echo ""
        
        echo -e "${YELLOW}[4/6] 测试 UI 树...${NC}"
        TREE_SIZE=$(api_get "/api/ui/tree" | wc -c)
        echo -e "${GREEN}✓ UI 树大小: ${TREE_SIZE} bytes${NC}"
        echo ""
        
        echo -e "${YELLOW}[5/6] 测试 WebView 检测...${NC}"
        api_get "/api/web/detect" | json_format
        echo ""
        
        echo -e "${YELLOW}[6/6] 测试网络状态...${NC}"
        api_get "/api/net/status" | json_format
        echo ""
        
        echo -e "${GREEN}========== 测试完成 ==========${NC}"
        ;;

    # === 帮助 ===
    help|--help|-h)
        show_help
        ;;

    # === 默认 ===
    *)
        log_warn "未知命令: $COMMAND"
        echo ""
        show_help
        exit 1
        ;;
esac
