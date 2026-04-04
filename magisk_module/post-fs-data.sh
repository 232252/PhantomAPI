#!/system/bin/sh
# PhantomAPI - post-fs-data.sh
# 在文件系统挂载后执行

MODDIR="${0%/*}"
LOGFILE="/data/adb/PhantomAPI.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE"
}

log "=== PhantomAPI post-fs-data ==="

# 确保 priv-app 目录存在
if [ ! -d "/system/priv-app/PhantomAPI" ]; then
    log "创建 priv-app 目录"
    mkdir -p "/system/priv-app/PhantomAPI"
fi

# 检查 APK 是否存在
APK_SOURCE="$MODPATH/system/priv-app/PhantomAPI/PhantomAPI.apk"
if [ -f "$APK_SOURCE" ]; then
    log "APK 文件存在: $APK_SOURCE"
else
    log "警告: APK 文件不存在"
fi

# 设置权限
set_perm_recursive /data/adb/modules/PhantomAPI 0 0 0755 0644 2>/dev/null

log "post-fs-data 完成"
