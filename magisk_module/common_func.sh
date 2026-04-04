#!/system/bin/sh
# PhantomAPI - common_func.sh

# 日志函数
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] PhantomAPI: $1" >> /cache/phantomapi.log
}

# 检查是否为系统应用
is_system_app() {
    pm path com.phantom.api | grep -q "priv-app"
}

# 获取设备信息
get_device_info() {
    echo "SDK: $(getprop ro.build.version.sdk)"
    echo "Device: $(getprop ro.product.model)"
    echo "Android: $(getprop ro.build.version.release)"
}

# 检查权限
check_permission() {
    pm list permissions -g | grep -q "android.permission.INJECT_EVENTS"
}

# 检查网络
check_network() {
    ping -c 1 8.8.8.8 > /dev/null 2>&1
}

# 获取 IP 地址
get_ip() {
    ifconfig wlan0 2>/dev/null | grep "inet addr" | awk '{print $2}' | cut -d: -f2
}
