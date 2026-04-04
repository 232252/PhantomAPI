#!/system/bin/sh
# PhantomAPI - service.sh
# 在系统启动完成后执行

MODDIR="${0%/*}"
LOGFILE="/data/adb/PhantomAPI.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE"
}

log "=== PhantomAPI service ==="

# 等待系统启动完成
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

log "系统启动完成"

# 等待 PackageManager 就绪
sleep 5

# 检查服务状态
check_service() {
    local pkg="com.phantom.api"
    local pid=$(pidof $pkg 2>/dev/null)
    if [ -n "$pid" ]; then
        log "服务运行中: PID=$pid"
        return 0
    else
        log "服务未运行，尝试启动"
        am start-foreground-service -n $pkg/.service.PhantomHttpService 2>&1 >> "$LOGFILE"
        return 1
    fi
}

# 启动服务
log "启动 PhantomAPI 服务"

# 检查是否是系统应用
CHECK_RESULT=$(dumpsys package com.phantom.api 2>/dev/null | grep -E "userId=|flags=" | head -2)
log "包状态: $CHECK_RESULT"

# 启动主 Activity
am start -n com.phantom.api/.MainActivity 2>&1 >> "$LOGFILE"

# 等待服务启动
sleep 3

# 检查 HTTP 端口
if netstat -tuln 2>/dev/null | grep -q ":9999"; then
    log "HTTP 服务已启动: 端口 9999"
else
    log "警告: HTTP 服务未启动"
fi

# 检查无障碍服务
A11Y_ENABLED=$(settings get secure enabled_accessibility_services 2>/dev/null)
if echo "$A11Y_ENABLED" | grep -q "com.phantom.api"; then
    log "无障碍服务已启用"
else
    log "警告: 无障碍服务未启用，请手动开启"
fi

log "service 完成"
