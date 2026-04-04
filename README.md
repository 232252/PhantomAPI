# PhantomAPI

基于 Magisk/LSPosed 的系统级 Android 控制服务，通过局域网暴露 RESTful API，实现 UI 感知与底层控制。

## 核心特性

- **零截图/OCR**: 基于 AccessibilityNodeInfo 和 DOM 树的 UI 感知
- **低延迟控制**: InputManager.injectInputEvent() 实现毫秒级响应
- **WebView 调试**: 强制开启 WebView 调试端口，支持 CDP 协议
- **RESTful API**: 局域网 HTTP 服务，端口 9999

## API 端点

### 系统域 `/api/sys/*`
- `GET /api/sys/info` - 设备信息
- `GET /api/sys/foreground` - 前台应用
- `GET /api/sys/packages` - 已安装应用列表

### UI 域 `/api/ui/*`
- `GET /api/ui/tree` - 获取 UI 树
- `GET /api/ui/find?text=xxx` - 查找节点
- `POST /api/ui/tap` - 点击坐标
- `POST /api/ui/swipe` - 滑动手势
- `POST /api/ui/back` - 返回键

### WebView 域 `/api/web/*`
- `GET /api/web/debug` - 调试状态
- `POST /api/web/execute` - 执行 JS
- `POST /api/web/cdp` - CDP 协议

### 网络域 `/api/net/*`
- `GET /api/net/status` - 网络状态
- `GET /api/net/wifi` - WiFi 信息

## 安装要求

- Android 8.0+ (SDK 26+)
- Root 权限 (Magisk)
- LSPosed (可选，用于 WebView Hook)

## 编译

```bash
# 设置 ANDROID_HOME
export ANDROID_HOME=/path/to/android-sdk

# 编译
./gradlew assembleDebug
```

## 使用

1. 安装 APK
2. 启用无障碍服务
3. 访问 `http://<设备IP>:9999/api/ping`

## 架构

```
┌─────────────────────────────────────────┐
│           PhantomAPI Service            │
├─────────────────────────────────────────┤
│  HTTP Server (NanoHTTPD)    Port: 9999  │
├─────────────────────────────────────────┤
│  Accessibility Service    │  WebView    │
│  (UI Tree + Gestures)     │  Hook       │
├─────────────────────────────────────────┤
│  InputInjectionEngine (Root)            │
│  InputManager.injectInputEvent()        │
└─────────────────────────────────────────┘
```

## 许可证

MIT License

## CoPaw 技能

配套 CoPaw 技能位于 `skills/phantom_api/` 目录，提供便捷的 CLI 工具：

```bash
# 使用 CLI 工具
./skills/phantom_api/phantom_api.sh <device_ip> ping
./skills/phantom_api/phantom_api.sh <device_ip> info
./skills/phantom_api/phantom_api.sh <device_ip> find 设置
./skills/phantom_api/phantom_api.sh <device_ip> tap 540 960
```
