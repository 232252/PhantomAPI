#!/system/bin/sh
# PhantomAPI - uninstall.sh

log() {
    echo "PhantomAPI: $1" >> /cache/phantomapi.log
}

log "卸载脚本开始执行"

# 停止服务
am force-stop com.phantom.api 2>/dev/null

# 清除数据
rm -rf /data/data/com.phantom.api 2>/dev/null

log "卸载完成"
