#!/system/bin/sh
# PhantomAPI - post-fs-data.sh
# 在文件系统挂载后执行

MODDIR="${0%/*}"
LOGFILE="/data/adb/PhantomAPI.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE"
}

log "=== PhantomAPI post-fs-data ==="

# 1. 创建绝对共享目录
mkdir -p /data/local/tmp/phantom
set_perm_recursive /data/local/tmp/phantom 0 0 0777 0777 2>/dev/null
chmod 777 /data/local/tmp/phantom
log "创建共享目录: /data/local/tmp/phantom"

# 2. 注入 SELinux 规则，允许任何 App 都能读写该目录
# 这一步是 LSPosed 模块能与宿主 App 通信的绝对前提
magiskpolicy --live "allow * shell_data_file file { read write open getattr create unlink rename }" 2>/dev/null
magiskpolicy --live "allow * shell_data_file dir { read write open getattr create add_name remove_name search }" 2>/dev/null
log "SELinux 规则已注入"

# 3. 允许连接 Chrome 的抽象 Unix Socket (用于 CDP)
magiskpolicy --live "allow untrusted_app untrusted_app unix_stream_socket connectto" 2>/dev/null
log "Chrome Socket 规则已注入"

# 4. 确保 priv-app 目录存在 (备用)
if [ ! -d "/system/priv-app/PhantomAPI" ]; then
    mkdir -p "/system/priv-app/PhantomAPI" 2>/dev/null
fi

# 设置权限
set_perm_recursive /data/adb/modules/PhantomAPI 0 0 0755 0644 2>/dev/null

log "post-fs-data 完成"
