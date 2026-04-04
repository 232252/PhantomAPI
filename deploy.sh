#!/bin/bash
# PhantomAPI 安装部署脚本

# 设备 IP
DEVICE_IP="${1:-192.168.110.140}"
ADB="adb"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# 检查 ADB
check_adb() {
    if ! command -v adb &> /dev/null; then
        # 尝试使用本地的 ADB
        if [ -f "$HOME/android-sdk/platform-tools/adb" ]; then
            ADB="$HOME/android-sdk/platform-tools/adb"
        else
            log_error "未找到 adb，请安装 Android SDK Platform Tools"
            exit 1
        fi
    fi
    log_info "使用 ADB: $ADB"
}

# 连接设备
connect_device() {
    log_info "连接设备: $DEVICE_IP:5555"
    
    # 断开现有连接
    $ADB disconnect $DEVICE_IP:5555 2>/dev/null
    
    # 连接设备
    $ADB connect $DEVICE_IP:5555
    
    # 检查连接状态
    if ! $ADB devices | grep -q "$DEVICE_IP"; then
        log_error "设备连接失败"
        exit 1
    fi
    
    log_info "设备已连接"
    $ADB devices
}

# 检查 Root
check_root() {
    log_info "检查 Root 权限..."
    
    ROOT_STATUS=$($ADB shell "su -c 'echo root_test' 2>/dev/null")
    
    if [ "$ROOT_STATUS" = "root_test" ]; then
        log_info "设备已 Root"
        return 0
    else
        log_warn "设备未 Root 或未授权"
        return 1
    fi
}

# 安装 APK
install_apk() {
    APK_FILE="$1"
    
    if [ ! -f "$APK_FILE" ]; then
        log_error "APK 文件不存在: $APK_FILE"
        exit 1
    fi
    
    log_info "安装 APK: $APK_FILE"
    
    # 卸载旧版本
    $ADB uninstall com.phantom.api 2>/dev/null
    
    # 安装新版本
    $ADB install -r "$APK_FILE"
    
    if [ $? -eq 0 ]; then
        log_info "APK 安装成功"
    else
        log_error "APK 安装失败"
        exit 1
    fi
}

# 安装到系统分区
install_to_system() {
    APK_FILE="$1"
    
    log_info "安装到系统分区..."
    
    # 重新挂载系统分区
    $ADB shell "su -c 'mount -o rw,remount /system'"
    
    # 创建目录
    $ADB shell "su -c 'mkdir -p /system/priv-app/PhantomAPI'"
    
    # 推送 APK
    $ADB push "$APK_FILE" /sdcard/PhantomAPI.apk
    $ADB shell "su -c 'cp /sdcard/PhantomAPI.apk /system/priv-app/PhantomAPI/PhantomAPI.apk'"
    
    # 设置权限
    $ADB shell "su -c 'chmod 644 /system/priv-app/PhantomAPI/PhantomAPI.apk'"
    $ADB shell "su -c 'chown root:root /system/priv-app/PhantomAPI/PhantomAPI.apk'"
    
    # 重新挂载为只读
    $ADB shell "su -c 'mount -o ro,remount /system'"
    
    log_info "系统分区安装完成，需要重启设备"
}

# 安装 Magisk 模块
install_magisk_module() {
    MODULE_ZIP="$1"
    
    if [ ! -f "$MODULE_ZIP" ]; then
        log_error "Magisk 模块文件不存在: $MODULE_ZIP"
        exit 1
    fi
    
    log_info "安装 Magisk 模块: $MODULE_ZIP"
    
    # 推送模块到设备
    $ADB push "$MODULE_ZIP" /sdcard/PhantomAPI.zip
    
    log_info "模块已推送到设备，请在 Magisk Manager 中手动安装"
    log_info "文件路径: /sdcard/PhantomAPI.zip"
}

# 启动服务
start_service() {
    log_info "启动服务..."
    
    # 启动 Activity
    $ADB shell "am start -n com.phantom.api/.MainActivity"
    
    sleep 2
    
    # 启动 HTTP 服务
    $ADB shell "am startservice -n com.phantom.api/.service.PhantomHttpService"
    
    log_info "服务已启动"
}

# 打开无障碍设置
open_accessibility() {
    log_info "打开无障碍设置..."
    $ADB shell "am start -a android.settings.ACCESSIBILITY_SETTINGS"
}

# 测试服务
test_service() {
    log_info "测试服务..."
    
    sleep 2
    
    # 测试 ping
    RESPONSE=$(curl -s "http://$DEVICE_IP:9999/api/ping" 2>/dev/null)
    
    if echo "$RESPONSE" | grep -q "ok"; then
        log_info "服务运行正常"
        echo "$RESPONSE"
    else
        log_warn "服务可能未正常运行"
    fi
}

# 查看日志
view_logs() {
    log_info "查看服务日志..."
    $ADB shell "logcat -s PhantomAPI:* PhantomHttpService:* PhantomA11y:* -v time"
}

# 授予必要权限
grant_permissions() {
    log_info "授予权限..."
    
    $ADB shell "pm grant com.phantom.api android.permission.READ_EXTERNAL_STORAGE"
    $ADB shell "pm grant com.phantom.api android.permission.WRITE_EXTERNAL_STORAGE"
    
    log_info "权限已授予"
}

# 主菜单
main_menu() {
    while true; do
        echo ""
        echo "========== PhantomAPI 部署菜单 =========="
        echo "目标设备: $DEVICE_IP"
        echo ""
        echo "  1. 连接设备"
        echo "  2. 检查 Root"
        echo "  3. 安装 APK (普通安装)"
        echo "  4. 安装 APK (系统分区)"
        echo "  5. 安装 Magisk 模块"
        echo "  6. 启动服务"
        echo "  7. 打开无障碍设置"
        echo "  8. 授予权限"
        echo "  9. 测试服务"
        echo "  10. 查看日志"
        echo "  11. 一键部署 (普通安装)"
        echo "  12. 一键部署 (Magisk 模块)"
        echo ""
        echo "  q. 退出"
        echo ""
        read -p "请选择: " choice
        
        case "$choice" in
            1) connect_device ;;
            2) check_root ;;
            3)
                read -p "输入 APK 文件路径: " apk
                install_apk "$apk"
                ;;
            4)
                read -p "输入 APK 文件路径: " apk
                install_to_system "$apk"
                ;;
            5)
                read -p "输入 Magisk 模块路径: " module
                install_magisk_module "$module"
                ;;
            6) start_service ;;
            7) open_accessibility ;;
            8) grant_permissions ;;
            9) test_service ;;
            10) view_logs ;;
            11)
                connect_device
                LATEST_APK=$(find ./dist -name "*.apk" -type f | head -n 1)
                if [ -n "$LATEST_APK" ]; then
                    install_apk "$LATEST_APK"
                    grant_permissions
                    start_service
                    sleep 3
                    open_accessibility
                else
                    log_error "未找到 APK 文件，请先构建"
                fi
                ;;
            12)
                connect_device
                check_root
                LATEST_MODULE=$(find ./dist -name "*.zip" -type f | head -n 1)
                if [ -n "$LATEST_MODULE" ]; then
                    install_magisk_module "$LATEST_MODULE"
                else
                    log_error "未找到 Magisk 模块，请先构建"
                fi
                ;;
            q|Q)
                log_info "退出"
                exit 0
                ;;
            *)
                log_warn "无效选择"
                ;;
        esac
    done
}

# 一键部署
quick_deploy() {
    log_info "开始一键部署..."
    
    # 连接设备
    connect_device
    
    # 查找 APK
    LATEST_APK=$(find ./dist -name "*.apk" -type f 2>/dev/null | head -n 1)
    if [ -z "$LATEST_APK" ]; then
        # 如果没有在 dist 目录，尝试查找 app/build
        LATEST_APK=$(find ./app/build/outputs/apk -name "*.apk" -type f 2>/dev/null | head -n 1)
    fi
    
    if [ -z "$LATEST_APK" ]; then
        log_error "未找到 APK 文件"
        exit 1
    fi
    
    log_info "使用 APK: $LATEST_APK"
    
    # 安装
    install_apk "$LATEST_APK"
    
    # 授权
    grant_permissions
    
    # 启动
    start_service
    
    # 打开设置
    sleep 2
    open_accessibility
    
    # 测试
    sleep 3
    test_service
    
    log_info "部署完成"
    log_info "请在设备上启用 PhantomAPI 无障碍服务"
    log_info "API 地址: http://$DEVICE_IP:9999"
}

# 主函数
main() {
    check_adb
    
    if [ "$1" = "-m" ] || [ "$1" = "--menu" ]; then
        main_menu
    elif [ "$1" = "-d" ] || [ "$1" = "--deploy" ]; then
        quick_deploy
    else
        main_menu
    fi
}

main "$@"
