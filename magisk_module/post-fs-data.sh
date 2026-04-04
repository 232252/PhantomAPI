#!/system/bin/sh
# PhantomAPI - post-fs-data.sh
# 在文件系统挂载后执行

MODDIR=${0%/*}

log() {
    echo "PhantomAPI: $1" >> /cache/phantomapi.log
}

log "post-fs-data 开始执行"

# 检查系统分区是否可写
if ! mountpoint -q /system; then
    mount -o rw,remount /system 2>/dev/null || \
    mount -o rw,remount / 2>/dev/null
fi

# 复制 APK 到系统目录
if [ -f "$MODDIR/system/priv-app/PhantomAPI/PhantomAPI.apk" ]; then
    log "检查 APK 文件存在"
    
    # 设置权限
    chmod 644 "$MODDIR/system/priv-app/PhantomAPI/PhantomAPI.apk"
    chown root:root "$MODDIR/system/priv-app/PhantomAPI/PhantomAPI.apk"
    
    log "APK 权限设置完成"
fi

log "post-fs-data 执行完成"
