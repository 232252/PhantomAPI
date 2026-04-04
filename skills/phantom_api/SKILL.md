# PhantomAPI 技能

通过 PhantomAPI 控制 Android 设备。PhantomAPI 是基于 AccessibilityNodeInfo 的 Android 控制服务，通过局域网 HTTP API 暴露能力，**零截图/OCR**。

## 触发条件

当用户提到以下关键词时自动触发：
- "控制手机"、"控制 Android"
- "点击坐标"、"滑动屏幕"
- "获取 UI 树"、"查找节点"
- "前台应用"、"安装应用"
- "PhantomAPI"

## 使用方式

### 1. 设备连接
```bash
# 确保设备已安装 PhantomAPI 并启用无障碍服务
# 服务地址: http://<设备IP>:9999
```

### 2. API 调用示例

#### Ping 测试
```bash
curl http://<设备IP>:9999/api/ping
```

#### 获取设备信息
```bash
curl http://<设备IP>:9999/api/sys/info | jq .
```

#### 获取前台应用
```bash
curl http://<设备IP>:9999/api/sys/foreground
```

#### 获取 UI 树
```bash
curl http://<设备IP>:9999/api/ui/tree
```

#### 查找 UI 节点
```bash
curl "http://<设备IP>:9999/api/ui/find?text=设置"
```

#### 点击坐标
```bash
curl -X POST http://<设备IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{"x": 540, "y": 960}'
```

#### 滑动
```bash
curl -X POST http://<设备IP>:9999/api/ui/swipe \
  -H "Content-Type: application/json" \
  -d '{"startX": 540, "startY": 1500, "endX": 540, "endY": 500, "duration": 300}'
```

#### 返回键
```bash
curl -X POST http://<设备IP>:9999/api/ui/back
```

#### WebView 调试
```bash
# 检查调试状态
curl http://<设备IP>:9999/api/web/debug

# 执行 JavaScript
curl -X POST http://<设备IP>:9999/api/web/execute \
  -H "Content-Type: application/json" \
  -d '{"script": "document.title"}'
```

## 核心能力

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
| `/api/ui/tap` | POST | 点击指定坐标 |
| `/api/ui/swipe` | POST | 滑动手势 |
| `/api/ui/back` | POST | 返回键 |

### WebView 域 `/api/web/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/web/debug` | GET | WebView 调试状态 |
| `/api/web/execute` | POST | 执行 JavaScript |
| `/api/web/cdp` | POST | Chrome DevTools Protocol |

### 网络域 `/api/net/*`
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/net/status` | GET | 网络连接状态 |
| `/api/net/wifi` | GET | WiFi 信息 |

## 典型工作流

### 1. 点击按钮
```bash
# 1. 获取 UI 树
UI_TREE=$(curl -s http://<设备IP>:9999/api/ui/tree)

# 2. 查找目标按钮
NODE=$(curl -s "http://<设备IP>:9999/api/ui/find?text=确定")

# 3. 解析坐标并点击
# 从 bounds 中提取中心坐标

# 4. 点击
curl -X POST http://<设备IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{"x": <x>, "y": <y>}'
```

### 2. 浏览器自动化
```bash
# 1. 打开浏览器
adb shell "am start -a android.intent.action.VIEW -d https://example.com"

# 2. 等待加载
sleep 3

# 3. 获取 WebView 内容
curl http://<设备IP>:9999/api/ui/tree

# 4. 执行 JS
curl -X POST http://<设备IP>:9999/api/web/execute \
  -H "Content-Type: application/json" \
  -d '{"script": "document.querySelector(\"#search\").value = \"test\""}'
```

## 注意事项

1. **无障碍服务必须启用**: 设置 > 无障碍 > PhantomAPI > 开启
2. **设备需要 Root**: 用于 WebView 调试端口访问
3. **同一局域网**: 确保控制端和设备在同一网络

## 项目地址

GitHub: https://github.com/232252/PhantomAPI
