# PhantomAPI - Android 系统级控制服务

基于 Magisk/LSPosed 的系统级 Android 控制服务，通过局域网暴露 RESTful API，实现 UI 感知与底层控制。

## ⚠️ 核心原则

**绝对禁止截图和 OCR** - 必须基于 AccessibilityNodeInfo 和 DOM 树

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                      PhantomAPI                             │
├─────────────────────────────────────────────────────────────┤
│  HTTP Server (NanoHTTPD) :9999                              │
│  ├── /api/sys/*   - 系统信息、安装应用列表                   │
│  ├── /api/ui/*    - Accessibility UI 树、点击、滑动         │
│  ├── /api/web/*   - WebView DOM 提取、元素查找、点击        │
│  ├── /api/net/*   - 网络代理设置                            │
│  └── /api/cdp/*   - Chrome DevTools Protocol                │
├─────────────────────────────────────────────────────────────┤
│  LSPosed Hook (WebViewHook)                                 │
│  ├── onPageFinished → 注入 JS 脚本                          │
│  ├── prompt() → 数据回传                                    │
│  └── 写入 /data/local/tmp/phantom/dom.json                  │
├─────────────────────────────────────────────────────────────┤
│  AccessibilityService                                       │
│  ├── UI 树遍历                                              │
│  ├── 坐标点击/滑动                                          │
│  └── 节点操作                                               │
└─────────────────────────────────────────────────────────────┘
```

## API 端点

### Web API (`/api/web/*`)

| 端点 | 方法 | 功能 | 参数 |
|------|------|------|------|
| `/api/web/detect` | GET | 检测 WebView 状态 | - |
| `/api/web/dom` | GET | 获取 DOM 数据 | - |
| `/api/web/find` | GET | 查找元素 | `text`, `tag` |
| `/api/web/click` | POST | 点击元素 | `{"x":100,"y":200}` 或 `{"text":"登录"}` |

### Chrome CDP API (`/api/cdp/*`)

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/cdp/pages` | GET | 获取 Chrome 页面列表 |
| `/api/cdp/title` | GET | 获取当前页面标题 |

### UI API (`/api/ui/*`)

| 端点 | 方法 | 功能 | 参数 |
|------|------|------|------|
| `/api/ui/tree` | GET | 获取 Accessibility UI 树 | - |
| `/api/ui/tap` | POST | 坐标点击 | `{"x":100,"y":200}` |
| `/api/ui/swipe` | POST | 滑动 | `{"startX":100,"startY":100,"endX":200,"endY":200}` |
| `/api/ui/back` | POST | 返回键 | - |

## WebView DOM 提取机制

### 核心流程

```
1. 页面加载完成
   ↓
2. LSPosed Hook onPageFinished 自动注入 JS
   >>> Page loaded, injecting JS: https://www.baidu.com/
   ↓
3. JS 提取 DOM 并通过 prompt() 发送
   prompt('phantom://dom', JSON.stringify(domData))
   ↓
4. LSPosed Hook onJsPrompt 拦截并写入文件
   >>> Received DOM via prompt, length: 8628
   >>> DOM successfully written to file
   ↓
5. API /api/web/dom 返回 DOM 数据
```

### 关键代码

```java
// WebViewHook.java - 注入 JS
private static final String DOM_EXTRACT_JS =
    "try {" +
    "  var r = [];" +
    "  var n = document.querySelectorAll('body *');" +
    "  for (var i = 0; i < n.length; i++) {" +
    "    var t = n[i].innerText;" +
    "    if (t && t.trim().length > 0 && t.trim().length < 200) {" +
    "      var b = n[i].getBoundingClientRect();" +
    "      if (b.width > 0 && b.height > 0) {" +
    "        r.push({t: t.trim(), x: Math.round(b.x), y: Math.round(b.y), " +
    "               w: Math.round(b.width), h: Math.round(b.height), tag: n[i].tagName});" +
    "      }" +
    "    }" +
    "  }" +
    "  prompt('phantom://dom', JSON.stringify(r));" +
    "} catch(e) { prompt('phantom://error', e.message || String(e)); }";
```

## 安装

### 前置要求

- Android 设备已 Root (Magisk)
- LSPosed 框架已安装
- 设备与控制端在同一局域网

### 安装步骤

1. **编译 APK**
   ```bash
   cd PhantomAPI
   ./gradlew assembleDebug
   ```

2. **安装 APK**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **启用 LSPosed 模块**
   - 打开 LSPosed Manager
   - 进入「模块」页面
   - 找到 PhantomAPI 并启用
   - 勾选需要 hook 的应用作用域
   - 重启目标应用

4. **启用无障碍服务**
   - 设置 → 无障碍 → PhantomAPI → 开启

5. **启动服务**
   ```bash
   adb shell am start -n com.phantom.api/.MainActivity
   ```

## 使用示例

### 获取 WebView DOM

```bash
# 1. 打开 WebView 页面
adb shell am start -n com.phantom.api/.WebViewTestActivity

# 2. 等待页面加载完成
sleep 5

# 3. 获取 DOM
curl -s "http://192.168.110.140:9999/api/web/dom" | python3 -m json.tool
```

### 查找并点击元素

```bash
# 查找包含"百度"的元素
curl -s "http://192.168.110.140:9999/api/web/find?text=百度"

# 点击文本为"登录"的元素
curl -X POST "http://192.168.110.140:9999/api/web/click" \
  -H "Content-Type: application/json" \
  -d '{"text": "登录"}'

# 坐标点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \
  -H "Content-Type: application/json" \
  -d '{"x": 500, "y": 1000}'
```

### Chrome CDP

```bash
# 获取 Chrome 页面列表
curl -s "http://192.168.110.140:9999/api/cdp/pages" | python3 -m json.tool

# 获取当前页面标题
curl -s "http://192.168.110.140:9999/api/cdp/title"
```

## 第三方应用 WebView 支持

### 问题
LSPosed 模块需要在 LSPosed Manager 中手动启用作用域才能 hook 第三方应用。

### 解决方案

1. 打开 **LSPosed Manager**
2. 进入 **模块** → **PhantomAPI**
3. 勾选需要 hook 的应用（如微信、淘宝、B站等）
4. 重启目标应用

### 验证

```bash
# 启动目标应用后检查日志
adb logcat | grep "LSPosed-Bridge: PhantomHook"
# 应该看到: LSPosed-Bridge: PhantomHook -> Loading hook for: tv.danmaku.bili
```

## 文件结构

```
PhantomAPI/
├── app/src/main/java/com/phantom/api/
│   ├── MainActivity.java              # 主 Activity
│   ├── WebViewTestActivity.java       # WebView 测试 Activity
│   ├── api/
│   │   ├── WebApiHandler.java         # Web API 处理器
│   │   ├── UiApiHandler.java          # UI API 处理器
│   │   ├── SysApiHandler.java         # 系统 API 处理器
│   │   ├── NetApiHandler.java         # 网络 API 处理器
│   │   ├── ChromeCdpHandler.java      # Chrome CDP 处理器
│   │   └── ScopeHelperHandler.java    # 作用域辅助 API
│   ├── engine/
│   │   ├── HttpServerEngine.java      # HTTP 服务器引擎
│   │   └── WebViewEngine.java         # WebView 引擎
│   ├── hook/
│   │   ├── WebViewHook.java           # WebView Hook 核心
│   │   └── XposedInit.java            # Xposed 入口
│   └── service/
│       ├── PhantomAccessibilityService.java  # 无障碍服务
│       └── PhantomHttpService.java    # HTTP 服务
├── magisk_module/                     # Magisk 模块配置
└── README.md
```

## 测试结果

| API | 状态 | 测试结果 |
|-----|------|---------|
| `/api/web/detect` | ✅ | `hasCachedDom: true/false` |
| `/api/web/dom` | ✅ | 百度首页 8628 bytes |
| `/api/web/find` | ✅ | 找到 17 个匹配元素 |
| `/api/web/click` | ✅ | 坐标/文本点击成功 |
| `/api/cdp/pages` | ✅ | Chrome 12 个页面 |
| `/api/cdp/title` | ✅ | `{"title":"百度一下"}` |
| `/api/ui/tree` | ✅ | Accessibility UI 树 |

## 设备信息

- **设备**: Xiaomi MI 6
- **系统**: Android 13 (SDK 33)
- **Root**: Magisk + LSPosed
- **端口**: 9999
