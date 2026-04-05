# PhantomAPI 技能文档

> **一句话介绍**: 通过 HTTP API 控制 Android 设备，零截图/OCR，基于 AccessibilityNodeInfo 和 DOM 树。

---

## 🚀 快速入门（3分钟上手）

### 第一步：确认服务运行

```bash
curl http://192.168.110.140:9999/api/ping
# 返回 {"status":"pong"} 表示服务正常
```

### 第二步：获取设备信息

```bash
curl http://192.168.110.140:9999/api/sys/info
# 返回设备型号、系统版本、屏幕尺寸等
```

### 第三步：点击屏幕

```bash
# 方式1：坐标点击
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"x":540,"y":960}'

# 方式2：文本点击（自动查找并点击）
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"text":"确定","clickable":true}'
```

---

## 📱 设备信息

| 项目 | 值 |
|------|-----|
| 设备 | Xiaomi MI 6 |
| 系统 | Android 13 (SDK 33) |
| Root | Magisk + LSPosed |
| HTTP 端口 | `9999` |
| 默认 IP | `192.168.110.140` |

---

## 📚 API 完整手册

### 🔧 基础 API

#### `GET /api/ping` - 测试连接

```bash
curl http://192.168.110.140:9999/api/ping
```

**返回**:
```json
{"status":"pong","timestamp":1775358569880,"uptime":68038}
```

**用途**: 检测服务是否在线，获取运行时长。

---

#### `GET /api/sys/info` - 设备信息

```bash
curl http://192.168.110.140:9999/api/sys/info
```

**返回字段**:
| 字段 | 说明 | 示例 |
|------|------|------|
| brand | 品牌 | Xiaomi |
| model | 型号 | MI 6 |
| sdkVersion | SDK 版本 | 33 |
| screen.width | 屏幕宽度 | 1088 |
| screen.height | 屏幕高度 | 2400 |

---

#### `GET /api/sys/activity` - 前台 Activity

```bash
curl http://192.168.110.140:9999/api/sys/activity
```

**返回**:
```json
{
  "success": true,
  "package": "cn.xuexi.android",
  "activity": "cn.xuexi.android.MainActivity",
  "activityShort": "MainActivity"
}
```

**用途**: 判断当前在哪个 App 的哪个页面。

---

### 📲 UI 域 API（核心）

#### `GET /api/ui/tree` - 获取 UI 树

```bash
curl http://192.168.110.140:9999/api/ui/tree | python3 -m json.tool
```

**返回**: 完整的 AccessibilityNodeInfo 树，包含每个节点的：
- text（文本）
- className（类名）
- bounds（坐标范围）
- clickable（是否可点击）
- viewIdResourceName（资源ID）

**用途**: 分析页面结构，找到目标元素坐标。

---

#### `GET /api/ui/find` - 查找节点 ⭐

```bash
# 基本查找
curl "http://192.168.110.140:9999/api/ui/find?text=登录"

# 只返回可点击节点（自动向上找父节点）
curl "http://192.168.110.140:9999/api/ui/find?text=积分中心&clickable=true"

# 限制向上查找层数
curl "http://192.168.110.140:9999/api/ui/find?text=确定&clickable=true&upward=5"
```

**参数说明**:
| 参数 | 必填 | 说明 |
|------|------|------|
| text | 是* | 查找的文本内容 |
| id | 是* | 资源ID（如 `com.xxx:id/btn`） |
| clickable | 否 | `true` 时只返回可点击节点 |
| upward | 否 | 向上查找父节点层数，默认 3 |

> *text 和 id 二选一

**返回示例**:
```json
{
  "success": true,
  "count": 1,
  "nodes": [{
    "text": "",
    "matchedText": "积分中心",
    "className": "android.widget.LinearLayout",
    "clickable": true,
    "bounds": {
      "left": 597, "top": 84, "right": 806, "bottom": 228,
      "centerX": 701, "centerY": 156
    },
    "upward": 1
  }]
}
```

**重点**: 使用 `clickable=true` 可以直接获取可点击坐标！

---

#### `POST /api/ui/tap` - 点击 ⭐⭐⭐

**方式1：坐标点击**
```bash
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"x":540,"y":960}'
```

**方式2：文本点击（推荐）**
```bash
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"text":"确定","clickable":true}'
```

**方式3：点击并等待**
```bash
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"x":540,"y":300}'

# 然后等待结果出现
curl -X POST http://192.168.110.140:9999/api/ui/wait \n  -H "Content-Type: application/json" \n  -d '{"mode":"text_appear","text":"成功","timeout_ms":5000}'
```

---

#### `POST /api/ui/wait` - 显式等待 ⭐

**等待文本出现**:
```bash
curl -X POST http://192.168.110.140:9999/api/ui/wait \n  -H "Content-Type: application/json" \n  -d '{"mode":"text_appear","text":"签到成功","timeout_ms":5000}'
```

**等待文本消失**（如"加载中..."）:
```bash
curl -X POST http://192.168.110.140:9999/api/ui/wait \n  -H "Content-Type: application/json" \n  -d '{"mode":"text_disappear","text":"加载中","timeout_ms":5000}'
```

**参数**:
| 参数 | 必填 | 说明 |
|------|------|------|
| mode | 是 | `text_appear` 或 `text_disappear` |
| text | 是 | 等待的文本 |
| timeout_ms | 否 | 超时时间，默认 5000ms |
| interval_ms | 否 | 轮询间隔，默认 300ms |

---

#### `POST /api/ui/action` - 节点操作

```bash
# 点击
curl -X POST http://192.168.110.140:9999/api/ui/action \n  -H "Content-Type: application/json" \n  -d '{"action":"click","text":"签到"}'

# 长按
curl -X POST http://192.168.110.140:9999/api/ui/action \n  -H "Content-Type: application/json" \n  -d '{"action":"long_click","text":"消息"}'

# 向前滚动
curl -X POST http://192.168.110.140:9999/api/ui/action \n  -H "Content-Type: application/json" \n  -d '{"action":"scroll_forward","text":"列表"}'
```

**支持的 action**:
| action | 说明 |
|--------|------|
| click | 点击 |
| long_click | 长按 |
| scroll_forward | 向前滚动 |
| scroll_backward | 向后滚动 |

---

#### `POST /api/ui/swipe` - 滑动

```bash
# 从下往上滑（下拉刷新）
curl -X POST http://192.168.110.140:9999/api/ui/swipe \n  -H "Content-Type: application/json" \n  -d '{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":300}'

# 从左往右滑
curl -X POST http://192.168.110.140:9999/api/ui/swipe \n  -H "Content-Type: application/json" \n  -d '{"startX":100,"startY":960,"endX":900,"endY":960,"duration":300}'
```

---

#### `POST /api/ui/back` - 返回键

```bash
curl -X POST http://192.168.110.140:9999/api/ui/back
```

---

#### `GET /api/ui/home` - 回到桌面

```bash
curl http://192.168.110.140:9999/api/ui/home
```

---

#### `POST /api/ui/safe_back` - 安全返回 ⭐

**问题**: 普通返回可能直接退出 App。

**解决**: `safe_back` 会连续按返回，但检测到退出 App 时立即停止。

```bash
curl -X POST http://192.168.110.140:9999/api/ui/safe_back \n  -H "Content-Type: application/json" \n  -d '{"max_tries":5,"check_package":"cn.xuexi.android","interval_ms":400}'
```

**参数**:
| 参数 | 说明 |
|------|------|
| max_tries | 最多按几次返回 |
| check_package | 检测的包名，退出此包则停止 |
| interval_ms | 每次返回间隔 |

---

### 🌐 Web 域 API

#### `GET /api/web/detect` - 检测 WebView

```bash
curl http://192.168.110.140:9999/api/web/detect
```

**返回**:
```json
{"method":"webview_js_inject","status":"available","hasCachedDom":true,"cacheAge":1234}
```

---

#### `GET /api/web/dom` - 获取 WebView DOM

```bash
curl http://192.168.110.140:9999/api/web/dom | python3 -m json.tool
```

**返回**: 所有可见文本元素的坐标信息：
```json
[
  {"t":"百度一下","x":500,"y":200,"w":100,"h":40,"tag":"BUTTON"},
  {"t":"登录","x":300,"y":800,"w":80,"h":30,"tag":"A"}
]
```

---

#### `GET /api/web/find` - 查找 WebView 元素

```bash
curl "http://192.168.110.140:9999/api/web/find?text=登录"
```

---

#### `POST /api/web/click` - WebView 点击

```bash
# 坐标点击
curl -X POST http://192.168.110.140:9999/api/web/click \n  -H "Content-Type: application/json" \n  -d '{"x":540,"y":960}'

# 文本点击
curl -X POST http://192.168.110.140:9999/api/web/click \n  -H "Content-Type: application/json" \n  -d '{"text":"登录"}'
```

---

### 📡 网络域 API

#### `GET /api/net/status` - 网络状态

```bash
curl http://192.168.110.140:9999/api/net/status
# {"connected":true,"wifi":true,"mobile":false}
```

---

#### `GET /api/net/connections` - 连接列表

```bash
# 获取所有连接
curl http://192.168.110.140:9999/api/net/connections

# 按包名过滤
curl "http://192.168.110.140:9999/api/net/connections?package=cn.xuexi.android"
```

---

## 🔗 链式调用与组合操作

### 场景1：点击 → 等待 → 确认

```bash
# 1. 点击按钮
curl -X POST http://192.168.110.140:9999/api/ui/tap \n  -H "Content-Type: application/json" \n  -d '{"text":"签到","clickable":true}'

# 2. 等待结果
curl -X POST http://192.168.110.140:9999/api/ui/wait \n  -H "Content-Type: application/json" \n  -d '{"mode":"text_appear","text":"签到成功","timeout_ms":5000}'
```

### 场景2：查找 → 获取坐标 → 点击

```bash
# 1. 查找元素
RESULT=$(curl -s "http://192.168.110.140:9999/api/ui/find?text=确定&clickable=true")

# 2. 提取坐标
X=$(echo $RESULT | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['nodes'][0]['bounds']['centerX'])")
Y=$(echo $RESULT | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['nodes'][0]['bounds']['centerY'])")

# 3. 点击
curl -X POST http://192.168.110.140:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d "{\"x\":$X,\"y\":$Y}"
```

### 场景3：滚动查找

```bash
# 滚动直到找到目标文本
for i in {1..5}; do
  if curl -s "http://192.168.110.140:9999/api/ui/find?text=目标文字" | grep -q '"count":[1-9]'; then
    echo "找到了！"
    break
  fi
  curl -X POST http://192.168.110.140:9999/api/ui/swipe \
    -H "Content-Type: application/json" \
    -d '{"startX":540,"startY":1500,"endX":540,"endY":500}'
  sleep 1
done
```

---

## 🐍 Python SDK（推荐）

```python
#!/usr/bin/env python3
"""PhantomAPI Python SDK v3.0"""
import requests
import time

class PhantomAPI:
    def __init__(self, host="192.168.110.140", port=9999):
        self.base_url = f"http://{host}:{port}"
        self.session = requests.Session()
    
    def _get(self, endpoint):
        return self.session.get(f"{self.base_url}{endpoint}", timeout=5).json()
    
    def _post(self, endpoint, data):
        return self.session.post(f"{self.base_url}{endpoint}", json=data, timeout=5).json()
    
    # 基础
    def ping(self):
        return self._get("/api/ping")
    
    def device_info(self):
        return self._get("/api/sys/info")
    
    def activity(self):
        return self._get("/api/sys/activity")
    
    # UI
    def find(self, text, clickable=True):
        return self._get(f"/api/ui/find?text={text}&clickable={clickable}")
    
    def find_coords(self, text):
        """查找并返回坐标"""
        r = self.find(text)
        nodes = r.get('nodes', [])
        if nodes:
            b = nodes[0]['bounds']
            return (b['centerX'], b['centerY'])
        return None
    
    def tap(self, x=None, y=None, text=None, clickable=True):
        if text:
            return self._post("/api/ui/tap", {"text": text, "clickable": clickable})
        return self._post("/api/ui/tap", {"x": x, "y": y})
    
    def swipe(self, sx, sy, ex, ey, duration=300):
        return self._post("/api/ui/swipe", {
            "startX": sx, "startY": sy, "endX": ex, "endY": ey, "duration": duration
        })
    
    def swipe_up(self, distance=800):
        return self.swipe(540, 1500, 540, 1500-distance)
    
    def swipe_down(self, distance=800):
        return self.swipe(540, 500, 540, 500+distance)
    
    def back(self):
        return self._post("/api/ui/back", {})
    
    def home(self):
        return self._get("/api/ui/home")
    
    def wait(self, text, mode="text_appear", timeout=5000):
        return self._post("/api/ui/wait", {
            "mode": mode, "text": text, "timeout_ms": timeout
        })
    
    def wait_appear(self, text, timeout=5000):
        return self.wait(text, "text_appear", timeout).get('success', False)
    
    def wait_disappear(self, text, timeout=5000):
        return self.wait(text, "text_disappear", timeout).get('success', False)
    
    def safe_back(self, package, max_tries=5):
        return self._post("/api/ui/safe_back", {
            "check_package": package, "max_tries": max_tries
        })
    
    # 高级操作
    def tap_and_wait(self, text, wait_text, timeout=5000):
        """点击并等待结果"""
        self.tap(text=text, clickable=True)
        return self.wait_appear(wait_text, timeout)
    
    def scroll_find(self, text, max_swipes=5):
        """滚动查找"""
        for _ in range(max_swipes):
            coords = self.find_coords(text)
            if coords:
                return coords
            self.swipe_up()
            time.sleep(0.5)
        return None
    
    def batch_tap(self, coords, interval=0.3):
        """批量点击"""
        results = []
        for x, y in coords:
            results.append(self.tap(x, y))
            time.sleep(interval)
        return results


# 使用示例
if __name__ == "__main__":
    api = PhantomAPI()
    
    # 测试
    print(api.ping())
    
    # 点击并等待
    if api.tap_and_wait("签到", "签到成功"):
        print("签到成功！")
    
    # 滚动查找
    coords = api.scroll_find("目标文字")
    if coords:
        api.tap(*coords)
```

---

## 🔥 高级 API (v1.1新增)

### 文本输入（支持中文）

```bash
# 通过剪贴板输入中文
curl -X POST http://192.168.110.140:9999/api/advanced/input \
  -H "Content-Type: application/json" \
  -d '{"text":"你好世界"}'
```

**返回**: `{"success":true,"method":"clipboard_paste","length":5}`

---

### 清除输入框

```bash
curl -X POST http://192.168.110.140:9999/api/advanced/clear
```

清除当前焦点的输入框内容。

---

### 长按

```bash
# 坐标长按
curl -X POST http://192.168.110.140:9999/api/advanced/long_press \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":960,"duration":1000}'

# 文本长按
curl -X POST http://192.168.110.140:9999/api/advanced/long_press \
  -H "Content-Type: application/json" \
  -d '{"text":"复制","duration":500}'
```

---

### 双击

```bash
curl -X POST http://192.168.110.140:9999/api/advanced/double_tap \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":960}'
```

---

### 拖拽

```bash
curl -X POST http://192.168.110.140:9999/api/advanced/drag \
  -H "Content-Type: application/json" \
  -d '{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":500}'
```

---

### 缩放手势

```bash
# 放大
curl -X POST http://192.168.110.140:9999/api/advanced/pinch \
  -H "Content-Type: application/json" \
  -d '{"centerX":540,"centerY":1200,"direction":"out","distance":300}'

# 缩小
curl -X POST http://192.168.110.140:9999/api/advanced/pinch \
  -H "Content-Type: application/json" \
  -d '{"centerX":540,"centerY":1200,"direction":"in","distance":300}'
```

---

### 按键事件

```bash
# 返回键
curl -X POST http://192.168.110.140:9999/api/advanced/keyevent \
  -H "Content-Type: application/json" \
  -d '{"key":"back"}'

# 主页键
curl -X POST http://192.168.110.140:9999/api/advanced/keyevent \
  -H "Content-Type: application/json" \
  -d '{"key":"home"}'

# 支持的按键: home, back, menu, enter, del, volume_up, volume_down, power, tab, dpad_up/down/left/right
```

---

### 滚动到元素

```bash
# 向下滚动查找"积分"元素，最多滚动10次
curl -X POST http://192.168.110.140:9999/api/advanced/scroll_to \
  -H "Content-Type: application/json" \
  -d '{"text":"积分","max_scrolls":10,"direction":"down"}'
```

**返回**: `{"success":true,"found":true,"scrolls":3}`

---

### 批量操作

```bash
curl -X POST http://192.168.110.140:9999/api/advanced/batch \
  -H "Content-Type: application/json" \
  -d '{
    "actions": [
      {"type":"tap","x":540,"y":500},
      {"type":"wait","ms":500},
      {"type":"swipe","startX":540,"startY":1500,"endX":540,"endY":500}
    ]
  }'
```

**返回**: `{"success":true,"results":[{"success":true},{"success":true},{"success":true}]}`

---

## 📋 常用操作速查表

| 需求 | 命令 |
|------|------|
| 测试连接 | `curl IP:9999/api/ping` |
| 设备信息 | `curl IP:9999/api/sys/info` |
| 当前Activity | `curl IP:9999/api/sys/activity` |
| 点击坐标 | `POST /api/ui/tap {"x":540,"y":960}` |
| 点击文本 | `POST /api/ui/tap {"text":"确定","clickable":true}` |
| 查找节点 | `GET /api/ui/find?text=登录&clickable=true&validBounds=true` |
| 等待出现 | `POST /api/ui/wait {"mode":"text_appear","text":"成功"}` |
| 返回键 | `POST /api/ui/back` |
| 回桌面 | `GET /api/ui/home` |
| 向上滑 | `POST /api/ui/swipe {"startX":540,"startY":1500,"endX":540,"endY":500}` |
| **中文输入** | `POST /api/advanced/input {"text":"你好"}` |
| **长按** | `POST /api/advanced/long_press {"x":540,"y":960}` |
| **双击** | `POST /api/advanced/double_tap {"x":540,"y":960}` |
| **拖拽** | `POST /api/advanced/drag {"startX":540,"startY":1500,"endX":540,"endY":500}` |
| **缩放** | `POST /api/advanced/pinch {"direction":"out"}` |
| **按键** | `POST /api/advanced/keyevent {"key":"back"}` |
| **滚动查找** | `POST /api/advanced/scroll_to {"text":"积分"}` |
| **选择器查询** | `POST /api/selector/query {"clickable":true,"validBounds":true}` |
| **选择器点击** | `POST /api/selector/click {"textContains":"签到"}` |

---

## 🎯 选择器 API (v1.2新增)

借鉴自 GKD 的选择器语法，支持灵活的元素匹配：

### 查询元素

```bash
# 查找所有可点击且坐标有效的元素
curl -X POST http://192.168.110.140:9999/api/selector/query \
  -H "Content-Type: application/json" \
  -d '{"clickable":true,"visible":true,"validBounds":true,"limit":10}'
```

### 选择器参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `text` | 文本精确匹配 | `"text":"登录"` |
| `textContains` | 文本包含 | `"textContains":"签到"` |
| `textStartsWith` | 文本开头 | `"textStartsWith":"学习"` |
| `id` | ID包含匹配 | `"id":"btn_login"` |
| `className` | 类名包含 | `"className":"Button"` |
| `clickable` | 可点击 | `true` |
| `scrollable` | 可滚动 | `true` |
| `visible` | 可见性 | `true` |
| `validBounds` | 坐标有效（非负） | `true` |
| `depth` | 精确深度 | `5` |
| `depthMin/Max` | 深度范围 | `2` / `10` |
| `limit` | 返回数量限制 | `10` |

### 选择器点击

```bash
# 点击包含"签到"文本的第一个元素
curl -X POST http://192.168.110.140:9999/api/selector/click \
  -H "Content-Type: application/json" \
  -d '{"textContains":"签到"}'

# 点击第3个匹配的元素
curl -X POST http://192.168.110.140:9999/api/selector/click \
  -H "Content-Type: application/json" \
  -d '{"clickable":true,"index":2}'
```

### 存在检查

```bash
curl -X POST http://192.168.110.140:9999/api/selector/exists \
  -H "Content-Type: application/json" \
  -d '{"textContains":"确定"}'
# 返回: {"exists":true,"count":1}
```

### 坐标问题修复说明

滑动后部分元素的坐标可能变成负数（元素滑出屏幕上方），使用 `validBounds:true` 可过滤掉这些无效坐标的元素。

```bash
# 只返回坐标有效的元素
curl -X POST http://192.168.110.140:9999/api/selector/query \
  -H "Content-Type: application/json" \
  -d '{"clickable":true,"validBounds":true}'
```

---

## ⚠️ 注意事项

1. **IP地址**: 设备与控制端需在同一局域网
2. **无障碍服务**: 必须启用 PhantomAPI 无障碍服务
3. **WebView Hook**: 第三方App需在LSPosed中启用作用域
4. **超时**: 局域网建议1-2秒超时
5. **连接复用**: 使用Session提升性能

---

## 🔗 相关链接

- **GitHub**: https://github.com/232252/PhantomAPI
- **本地项目**: `/home/admin/Documents/kongzhi/PhantomAPI`
- **APK**: `releases/PhantomAPI-v1.0.2-debug.apk`