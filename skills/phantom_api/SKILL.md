# PhantomAPI 技能

通过 PhantomAPI 控制 Android 设备。PhantomAPI 是基于 Magisk/LSPosed 的系统级 Android 控制服务，通过局域网 HTTP API 暴露能力，**零截图/OCR**，基于 AccessibilityNodeInfo 和 DOM 树。

## 触发条件

当用户提到以下关键词时自动触发：
- "控制手机"、"控制 Android"
- "点击坐标"、"滑动屏幕"
- "获取 UI 树"、"查找节点"
- "前台应用"、"安装应用"
- "WebView"、"DOM 提取"
- "Chrome"、"CDP"
- "网络连接"、"流量统计"
- "PhantomAPI"

## 设备信息

| 项目 | 值 |
|------|-----|
| 设备 | Xiaomi MI 6 |
| 系统 | Android 13 (SDK 33) |
| IP | 192.168.110.140:5555 |
| HTTP 端口 | 9999 |

## API 端点

### Web 域 `/api/web/*` (核心功能)

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/web/detect` | GET | 检测 WebView 状态 |
| `/api/web/dom` | GET | 获取 WebView DOM 数据 |
| `/api/web/find` | GET | 查找元素 (`?text=xxx`) |
| `/api/web/click` | POST | 点击元素 (坐标/文本/索引) |

### Chrome CDP 域 `/api/cdp/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/cdp/pages` | GET | 获取 Chrome 页面列表 |
| `/api/cdp/title` | GET | 获取当前页面标题 |

### UI 域 `/api/ui/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/ui/tree` | GET | 获取 Accessibility UI 树 |
| `/api/ui/find` | GET | 查找节点 |
| `/api/ui/tap` | POST | 坐标点击 |
| `/api/ui/swipe` | POST | 滑动 |
| `/api/ui/back` | POST | 返回键 |

### 系统域 `/api/sys/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/sys/info` | GET | 设备信息 |
| `/api/sys/foreground` | GET | 前台应用 |
| `/api/sys/packages` | GET | 已安装应用 |

### 网络域 `/api/net/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/net/status` | GET | 网络状态 |
| `/api/net/connections` | GET | 连接拓扑 |

### 辅助域 `/api/scope/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/scope/apps` | GET | 列出需要启用 LSPosed 作用域的应用 |

## 使用示例

### 获取 WebView DOM

```bash
# 1. 打开 WebView 测试页面
adb shell am start -n com.phantom.api/.WebViewTestActivity

# 2. 等待加载完成
sleep 5

# 3. 获取 DOM
curl -s "http://192.168.110.140:9999/api/web/dom" | python3 -m json.tool
```

### 查找元素

```bash
# 查找包含"百度"的元素
curl -s "http://192.168.110.140:9999/api/web/find?text=百度" | python3 -m json.tool
```

### 点击元素

```bash
# 坐标点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \
  -H "Content-Type: application/json" \
  -d '{"x": 500, "y": 1000}'

# 文本点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \
  -H "Content-Type: application/json" \
  -d '{"text": "登录"}'

# 索引点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \
  -H "Content-Type: application/json" \
  -d '{"index": 5}'
```

### Chrome CDP

```bash
# 打开 Chrome
adb shell am start -a android.intent.action.VIEW -d https://www.baidu.com com.android.chrome

# 获取页面列表
curl -s "http://192.168.110.140:9999/api/cdp/pages" | python3 -m json.tool

# 获取标题
curl -s "http://192.168.110.140:9999/api/cdp/title"
```

### 获取 UI 树

```bash
curl -s "http://192.168.110.140:9999/api/ui/tree" | python3 -m json.tool | head -30
```

### 坐标点击

```bash
curl -X POST "http://192.168.110.140:9999/api/ui/tap" \
  -H "Content-Type: application/json" \
  -d '{"x": 540, "y": 960}'
```

## WebView DOM 提取机制

### 核心流程

```
页面加载 → onPageFinished Hook → 注入 JS → prompt() → onJsPrompt Hook → 写文件 → API 读取
```

### IPC 文件

- 路径: `/data/local/tmp/phantom/dom.json`
- 新鲜度: 30 秒
- 用完即焚，防止脏数据

### 第三方应用支持

1. 打开 LSPosed Manager
2. 模块 → PhantomAPI → 勾选目标应用
3. 重启目标应用

### 验证 Hook

```bash
adb logcat | grep "LSPosed-Bridge: PhantomHook"
# 应看到: Loading hook for: tv.danmaku.bili
```

## CLI 工具

```bash
./phantom_api.sh <device_ip> <command> [args]

# 命令:
#   ping              - 测试连接
#   info              - 设备信息
#   foreground        - 前台应用
#   tree              - UI 树
#   tap <x> <y>       - 点击
#   swipe <sx> <sy> <ex> <ey> - 滑动
#   back              - 返回键
#   web-dom           - 获取 WebView DOM
#   web-find <text>   - 查找元素
#   web-click <x> <y> - Web 点击
#   cdp-pages         - Chrome 页面列表
#   cdp-title         - Chrome 标题
```

## 项目地址

**GitHub**: https://github.com/232252/PhantomAPI

**文档**:
- `README.md` - 项目说明
- `docs/ARCHITECTURE.md` - 架构设计
- `docs/BUGFIX.md` - 问题修复记录
