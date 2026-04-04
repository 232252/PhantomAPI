# PhantomAPI

**系统级 Android 控制服务** - 基于 Magisk/LSPosed，通过局域网暴露 RESTful API，实现 UI 感知与底层控制。

## 核心特性

- **零截图/OCR**: 基于 AccessibilityNodeInfo 和 DOM 树的 UI 感知
- **低延迟控制**: InputManager.injectInputEvent() 实现毫秒级响应（< 30ms）
- **WebView 调试**: 强制开启 WebView 调试端口，支持 CDP 协议
- **RESTful API**: 局域网 HTTP 服务，端口 9999
- **显式等待**: 支持时序控制，等待条件满足
- **连接拓扑**: 实时监控网络连接和流量

## 架构

```
┌─────────────────────────────────────────┐
│           PhantomAPI Service            │
├─────────────────────────────────────────┤
│  HTTP Server (NanoHTTPD)    Port: 9999  │
├─────────────────────────────────────────┤
│  Accessibility Service    │  CDP Bridge │
│  (UI Tree + Gestures)     │  (WebView)  │
├─────────────────────────────────────────┤
│  InputInjectionEngine (Root)            │
│  InputManager.injectInputEvent()        │
└─────────────────────────────────────────┘
```

## API 端点

### 系统域 `/api/sys/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/sys/info` | GET | 设备信息（品牌、型号、屏幕、内存等） |
| `/api/sys/foreground` | GET | 前台应用包名 |
| `/api/sys/packages` | GET | 已安装应用列表 |

### UI 域 `/api/ui/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/ui/tree` | GET | 获取完整 UI 树（AccessibilityNodeInfo） |
| `/api/ui/find` | GET | 查找包含指定文本的节点 |
| `/api/ui/tap` | POST | 点击指定坐标（支持显式等待） |
| `/api/ui/swipe` | POST | 滑动手势 |
| `/api/ui/back` | POST | 返回键 |
| `/api/ui/wait` | POST | 显式等待条件 |
| `/api/ui/action` | POST | 节点操作（click/scroll） |

### WebView 域 `/api/web/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/web/detect` | GET | 检测 WebView 调试可用性 |
| `/api/web/sockets` | GET | 列出可用 DevTools Socket |
| `/api/web/dom` | GET | 获取 DOM 树（CDP） |
| `/api/web/execute` | POST | 执行 JavaScript |
| `/api/web/click` | POST | CDP 级别点击 |
| `/api/web/cdp` | POST | 原始 CDP 命令 |

### 网络域 `/api/net/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/net/status` | GET | 网络连接状态 |
| `/api/net/wifi` | GET | WiFi 信息 |
| `/api/net/connections` | GET | 连接拓扑（谁在连哪里） |
| `/api/net/traffic` | GET | 流量统计（按应用） |

## 安装要求

- Android 8.0+ (SDK 26+)
- Root 权限 (Magisk)
- LSPosed (必须，用于 WebView Hook)

## 安装方式

### 方式一：Magisk 模块安装（推荐）
```bash
# 1. 编译 APK
./gradlew assembleDebug

# 2. 将 APK 复制到 Magisk 模块
cp app/build/outputs/apk/debug/app-debug.apk magisk_module/system/priv-app/PhantomAPI/PhantomAPI.apk

# 3. 打包 Magisk 模块
cd magisk_module && zip -r ../PhantomAPI.zip .

# 4. 在 Magisk 中安装模块
adb push ../PhantomAPI.zip /sdcard/
# 然后在 Magisk Manager 中选择本地安装

# 5. 重启设备
adb reboot
```

### 方式二：普通安装（功能受限）
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 使用

### 1. 启用无障碍服务
设置 > 无障碍 > PhantomAPI > 开启

### 2. 启用 LSPosed 模块
LSPosed Manager > 模块 > PhantomAPI > 勾选目标应用

### 3. 访问 API
```bash
# Ping 测试
curl http://<设备IP>:9999/api/ping

# 获取设备信息
curl http://<设备IP>:9999/api/sys/info

# 获取 UI 树
curl http://<设备IP>:9999/api/ui/tree

# 点击坐标
curl -X POST http://<设备IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{"x": 540, "y": 960}'

# 显式等待
curl -X POST http://<设备IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{
    "x": 540, 
    "y": 300,
    "wait": {
      "condition": {"type": "text", "text": "搜索结果"},
      "timeout_ms": 5000
    }
  }'
```

## 显式等待

所有操作接口支持显式等待：

```json
{
  "action": {"type": "tap", "target": "text:搜索"},
  "wait": {
    "condition": {"type": "text", "text": "搜索结果"},
    "timeout_ms": 5000,
    "interval_ms": 200
  }
}
```

支持的条件类型：
- `text`: 等待文本出现
- `id`: 等待资源 ID 出现
- `gone`: 等待元素消失

## 零截图约束

本项目严格遵守零截图约束：
- ❌ 不使用 `screencap`
- ❌ 不使用 `MediaProjection`
- ❌ 不使用 `SurfaceControl.captureLayers`
- ❌ 不使用任何 OCR SDK

所有 UI 感知均基于 AccessibilityNodeInfo 和 DOM 树。

## 安全合规

1. 仅监听局域网接口
2. 建议添加 Token 鉴权
3. 仅在授权测试环境使用
4. 不收集任何用户数据

## 编译

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

## 验收测试

详见 [ACCEPTANCE.md](ACCEPTANCE.md)

## 许可证

MIT License

## CoPaw 技能

配套 CoPaw 技能位于 `skills/phantom_api/` 目录：

```bash
# 使用 CLI 工具
./skills/phantom_api/phantom_api.sh <device_ip> ping
./skills/phantom_api/phantom_api.sh <device_ip> info
./skills/phantom_api/phantom_api.sh <device_ip> find 设置
./skills/phantom_api/phantom_api.sh <device_ip> tap 540 960
```

## 致谢

- NanoHTTPD - 轻量级 HTTP 服务
- LSPosed - Xposed 框架
- Magisk - 系统级 Root 方案
