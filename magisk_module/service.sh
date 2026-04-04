#!/system/bin/sh
# PhantomAPI - service.sh
# 系统启动后执行

MODDIR=${0%/*}

log() {
    echo "PhantomAPI: $1" >> /cache/phantomapi.log
}

log "service.sh 开始执行"

# 等待系统启动完成
sleep 10

# 检查服务是否已启动
check_service() {
    pgrep -f "com.phantom.api" > /dev/null 2>&1
}

# 启动服务
start_service() {
    if ! check_service; then
        log "启动 PhantomAPI 服务..."
        am start-foreground-service -n com.phantom.api/.service.PhantomHttpService
    fi
}

# 循环检查并启动
for i in 1 2 3 4 5; do
    sleep 5
    if check_service; then
        log "服务已运行"
        break
    else
        start_service
    fi
done

log "service.sh 执行完成"
