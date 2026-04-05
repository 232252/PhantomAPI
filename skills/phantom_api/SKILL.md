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
"""PhantomAPI Python SDK v4.0 - 支持 v1.4.0 所有 API"""
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
    
    # ========== 基础 API ==========
    def ping(self):
        return self._get("/api/ping")
    
    def device_info(self):
        return self._get("/api/sys/info")
    
    def activity(self):
        return self._get("/api/sys/activity")
    
    # ========== UI API ==========
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
    
    # ========== 应用管理 API ==========
    def app_list(self):
        """获取已安装应用列表"""
        return self._get("/api/app/list")
    
    def app_launch(self, package):
        """启动应用"""
        return self._post("/api/app/launch", {"packageName": package})
    
    def app_stop(self, package):
        """停止应用"""
        return self._post("/api/app/stop", {"packageName": package})
    
    def app_current(self):
        """获取当前前台应用"""
        return self._get("/api/app/current")
    
    # ========== 剪贴板 API ==========
    def clipboard_get(self):
        """读取剪贴板"""
        return self._get("/api/clipboard/get")
    
    def clipboard_set(self, text):
        """设置剪贴板"""
        return self._post("/api/clipboard/set", {"text": text})
    
    # ========== Shell API ==========
    def shell(self, command):
        """执行 Shell 命令"""
        return self._post("/api/shell/exec", {"command": command})
    
    # ========== 文件 API ==========
    def file_list(self, path="/sdcard"):
        """列出目录"""
        return self._post("/api/file/list", {"path": path})
    
    def file_read(self, path):
        """读取文件"""
        return self._post("/api/file/read", {"path": path})
    
    def file_write(self, path, content):
        """写入文件"""
        return self._post("/api/file/write", {"path": path, "content": content})
    
    def file_exists(self, path):
        """检查文件存在"""
        return self._post("/api/file/exists", {"path": path})
    
    # ========== WebView API ==========
    def webview_eval(self, script):
        """在 WebView 中执行 JS"""
        return self._post("/api/webview/eval", {"script": script})
    
    def webview_click(self, selector=None, x=None, y=None):
        """WebView 元素点击"""
        if selector:
            return self._post("/api/webview/click", {"selector": selector})
        return self._post("/api/webview/click", {"x": x, "y": y})
    
    def webview_input(self, selector, text):
        """WebView 输入"""
        return self._post("/api/webview/input", {"selector": selector, "text": text})
    
    # ========== 高级手势 API (v1.4.0) ==========
    def gesture_swipe(self, direction, distance=500, duration=300):
        """方向滑动 (up/down/left/right)"""
        return self._post("/api/gesture/swipe", {
            "direction": direction, "distance": distance, "duration": duration
        })
    
    def gesture_fling(self, direction):
        """快速滑动"""
        return self._post("/api/gesture/fling", {"direction": direction})
    
    def gesture_drag(self, sx, sy, ex, ey, duration=500):
        """拖拽"""
        return self._post("/api/gesture/drag", {
            "startX": sx, "startY": sy, "endX": ex, "endY": ey, "duration": duration
        })
    
    def gesture_pinch(self, direction="out", cx=540, cy=1200, distance=200):
        """双指缩放"""
        return self._post("/api/gesture/pinch", {
            "centerX": cx, "centerY": cy, "direction": direction, "distance": distance
        })
    
    def gesture_path(self, points, duration=1000):
        """路径手势 - points: [(x1,y1), (x2,y2), ...]"""
        pts = [{"x": p[0], "y": p[1]} for p in points]
        return self._post("/api/gesture/path", {"points": pts, "duration": duration})
    
    def gesture_bezier(self, sx, sy, ex, ey, cx, cy, duration=500):
        """贝塞尔曲线滑动"""
        return self._post("/api/gesture/bezier", {
            "startX": sx, "startY": sy, "endX": ex, "endY": ey,
            "ctrlX": cx, "ctrlY": cy, "duration": duration
        })
    
    def gesture_sequence(self, gestures):
        """手势序列 - gestures: [{"type":"tap","x":540,"y":1000}, ...]"""
        return self._post("/api/gesture/sequence", {"gestures": gestures})
    
    def gesture_tap(self, x, y):
        """手势点击"""
        return self._post("/api/gesture/tap", {"x": x, "y": y})
    
    def gesture_double_tap(self, x, y):
        """双击"""
        return self._post("/api/gesture/double_tap", {"x": x, "y": y})
    
    def gesture_long_press(self, x, y, duration=1000):
        """长按"""
        return self._post("/api/gesture/long_press", {"x": x, "y": y, "duration": duration})
    
    def gesture_scroll(self, direction="down", distance=500):
        """滚动"""
        return self._post("/api/gesture/scroll", {"direction": direction, "distance": distance})
    
    def gesture_pattern(self, points, start_x=100, start_y=500, cell_size=200):
        """图案解锁 - points: [0,4,8] 表示 3x3 网格点位"""
        return self._post("/api/gesture/pattern", {
            "points": points, "startX": start_x, "startY": start_y, "cellSize": cell_size
        })
    
    # ========== 浏览器注入 API (v1.4.0) ==========
    def browser_form_fill(self, data):
        """表单填充 - data: {"username":"xxx","password":"xxx"}"""
        return self._post("/api/browser/form/fill", {"data": data})
    
    def browser_form_submit(self, selector="form"):
        """表单提交"""
        return self._post("/api/browser/form/submit", {"selector": selector})
    
    def browser_xpath(self, xpath):
        """XPath 查询"""
        return self._post("/api/browser/xpath", {"xpath": xpath})
    
    def browser_query_all(self, selector):
        """CSS 选择器批量查询"""
        return self._post("/api/browser/queryAll", {"selector": selector})
    
    def browser_attr_get(self, selector, attr):
        """获取元素属性"""
        return self._post("/api/browser/attr/get", {"selector": selector, "attr": attr})
    
    def browser_attr_set(self, selector, attr, value):
        """设置元素属性"""
        return self._post("/api/browser/attr/set", {"selector": selector, "attr": attr, "value": value})
    
    def browser_css_get(self, selector, prop):
        """获取元素样式"""
        return self._post("/api/browser/css/get", {"selector": selector, "property": prop})
    
    def browser_table(self, selector, headers=True):
        """提取表格数据"""
        return self._post("/api/browser/table/extract", {"selector": selector, "headers": headers})
    
    def browser_text_get(self, selector):
        """获取文本内容"""
        return self._post("/api/browser/text/get", {"selector": selector})
    
    def browser_text_set(self, selector, text):
        """设置文本内容"""
        return self._post("/api/browser/text/set", {"selector": selector, "text": text})
    
    def browser_html_get(self, selector=None):
        """获取 HTML"""
        return self._post("/api/browser/html/get", {"selector": selector} if selector else {})
    
    def browser_meta(self):
        """获取页面 Meta 信息"""
        return self._post("/api/browser/meta", {})
    
    def browser_links(self):
        """获取所有链接"""
        return self._post("/api/browser/links", {})
    
    def browser_images(self):
        """获取所有图片"""
        return self._post("/api/browser/images", {})
    
    def browser_navigate(self, url):
        """页面导航"""
        return self._post("/api/browser/navigate", {"url": url})
    
    def browser_history(self):
        """获取历史记录"""
        return self._post("/api/browser/history", {})
    
    def browser_reload(self):
        """刷新页面"""
        return self._post("/api/browser/reload", {})
    
    def browser_execute(self, script):
        """执行脚本"""
        return self._post("/api/browser/execute", {"script": script})
    
    def browser_evaluate(self, script):
        """执行脚本并返回结果"""
        return self._post("/api/browser/evaluate", {"script": script})
    
    # ========== 文件操作 API (补充) ==========
    def file_append(self, path, content):
        """追加文件"""
        return self._post("/api/file/append", {"path": path, "content": content})
    
    def file_delete(self, path):
        """删除文件"""
        return self._post("/api/file/delete", {"path": path})
    
    def file_mkdir(self, path):
        """创建目录"""
        return self._post("/api/file/mkdir", {"path": path})
    
    # ========== 应用管理 API (补充) ==========
    def app_recent(self):
        """最近应用列表"""
        return self._get("/api/app/recent")
    
    # ========== 高级操作 ==========
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
    
    def input_text(self, text):
        """中文输入（通过剪贴板）"""
        return self._post("/api/advanced/input", {"text": text})


# ========== 使用示例 ==========
if __name__ == "__main__":
    api = PhantomAPI()
    
    # 测试连接
    print("Ping:", api.ping())
    
    # 设备信息
    print("Device:", api.device_info())
    
    # 启动应用
    api.app_launch("com.android.chrome")
    time.sleep(2)
    
    # 手势操作
    api.gesture_swipe("up", distance=500)  # 向上滑动
    api.gesture_tap(540, 1200)  # 点击
    api.gesture_pinch("out")  # 放大
    
    # 手势序列
    api.gesture_sequence([
        {"type": "tap", "x": 540, "y": 1000},
        {"type": "wait", "ms": 500},
        {"type": "swipe", "startX": 540, "startY": 1500, "endX": 540, "endY": 500, "duration": 300}
    ])
    
    # 表单填充
    api.browser_form_fill({"username": "user123", "password": "pass456"})
    
    # 点击并等待
    if api.tap_and_wait("签到", "签到成功"):
        print("签到成功！")
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

---

## 🎮 高级手势 API (v1.4.0新增)

借鉴自 AutoJs6、MobiAgent 的高级手势控制能力。

### 方向滑动

```bash
# 向上滑动
curl -X POST http://192.168.110.140:9999/api/gesture/swipe \
  -H "Content-Type: application/json" \
  -d '{"direction":"up","distance":500,"duration":300}'

# direction: up, down, left, right
```

### 快速滑动 (Fling)

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/fling \
  -H "Content-Type: application/json" \
  -d '{"direction":"up"}'
```

### 拖拽

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/drag \
  -H "Content-Type: application/json" \
  -d '{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":500}'
```

### 双指缩放

```bash
# 放大
curl -X POST http://192.168.110.140:9999/api/gesture/pinch \
  -H "Content-Type: application/json" \
  -d '{"centerX":540,"centerY":1200,"direction":"out","distance":200}'

# 缩小
curl -X POST http://192.168.110.140:9999/api/gesture/pinch \
  -H "Content-Type: application/json" \
  -d '{"centerX":540,"centerY":1200,"direction":"in","distance":200}'
```

### 路径手势

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/path \
  -H "Content-Type: application/json" \
  -d '{"points":[{"x":100,"y":1000},{"x":500,"y":500},{"x":900,"y":1000}],"duration":1000}'
```

### 贝塞尔曲线滑动

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/bezier \
  -H "Content-Type: application/json" \
  -d '{"startX":100,"startY":1200,"endX":900,"endY":1200,"ctrlX":500,"ctrlY":600,"duration":500}'
```

### 手势序列

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/sequence \
  -H "Content-Type: application/json" \
  -d '{
    "gestures": [
      {"type":"tap","x":540,"y":1000},
      {"type":"wait","ms":500},
      {"type":"swipe","startX":540,"startY":1500,"endX":540,"endY":500,"duration":300},
      {"type":"longPress","x":540,"y":1200,"duration":1000}
    ]
  }'
```

**返回**:
```json
{"success":true,"results":[{"index":0,"type":"tap","success":true},{"index":1,"type":"wait","success":true},{"index":2,"type":"swipe","success":true},{"index":3,"type":"longPress","success":true}]}
```

### 点击

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/tap \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":1200}'
```

### 双击

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/double_tap \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":1200}'
```

### 长按

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/long_press \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":1200,"duration":1000}'
```

### 滚动

```bash
curl -X POST http://192.168.110.140:9999/api/gesture/scroll \
  -H "Content-Type: application/json" \
  -d '{"direction":"down","distance":500}'
```

### 图案解锁

```bash
# 点位编号: 0-8 (3x3网格)
# 0 1 2
# 3 4 5
# 6 7 8
curl -X POST http://192.168.110.140:9999/api/gesture/pattern \
  -H "Content-Type: application/json" \
  -d '{"points":[0,4,8,5,2],"startX":100,"startY":500,"cellSize":200,"duration":1000}'
```

---

## 🌐 浏览器注入 API (v1.4.0新增)

借鉴自 FP-Browser 的浏览器注入能力，支持高级 DOM 操作。

### 表单填充

```bash
curl -X POST http://192.168.110.140:9999/api/browser/form/fill \
  -H "Content-Type: application/json" \
  -d '{"data":{"username":"user123","password":"pass456"}}'
```

### 表单提交

```bash
curl -X POST http://192.168.110.140:9999/api/browser/form/submit \
  -H "Content-Type: application/json" \
  -d '{"selector":"#loginForm"}'
```

### 表单数据提取

```bash
curl -X POST http://192.168.110.140:9999/api/browser/form/data \
  -H "Content-Type: application/json" \
  -d '{"selector":"form"}'
```

### 属性获取

```bash
curl -X POST http://192.168.110.140:9999/api/browser/attr/get \
  -H "Content-Type: application/json" \
  -d '{"selector":"#myInput","attr":"value"}'
```

### 属性设置

```bash
curl -X POST http://192.168.110.140:9999/api/browser/attr/set \
  -H "Content-Type: application/json" \
  -d '{"selector":"#myInput","attr":"value","value":"new value"}'
```

### 样式获取

```bash
curl -X POST http://192.168.110.140:9999/api/browser/css/get \
  -H "Content-Type: application/json" \
  -d '{"selector":".myClass","property":"background-color"}'
```

### 样式设置

```bash
curl -X POST http://192.168.110.140:9999/api/browser/css/set \
  -H "Content-Type: application/json" \
  -d '{"selector":".myClass","property":"display","value":"none"}'
```

### XPath 查询

```bash
curl -X POST http://192.168.110.140:9999/api/browser/xpath \
  -H "Content-Type: application/json" \
  -d '{"xpath":"//div[@class=\"content\"]/p"}'
```

### CSS选择器批量查询

```bash
curl -X POST http://192.168.110.140:9999/api/browser/queryAll \
  -H "Content-Type: application/json" \
  -d '{"selector":".item"}'
```

### 表格数据提取

```bash
curl -X POST http://192.168.110.140:9999/api/browser/table/extract \
  -H "Content-Type: application/json" \
  -d '{"selector":"table.data-table","headers":true}'
```

### 获取文本内容

```bash
curl -X POST http://192.168.110.140:9999/api/browser/text/get \
  -H "Content-Type: application/json" \
  -d '{"selector":".content"}'
```

### 设置文本内容

```bash
curl -X POST http://192.168.110.140:9999/api/browser/text/set \
  -H "Content-Type: application/json" \
  -d '{"selector":".content","text":"新内容"}'
```

### 获取 HTML

```bash
curl -X POST http://192.168.110.140:9999/api/browser/html/get \
  -H "Content-Type: application/json" \
  -d '{"selector":"#container"}'
```

### 获取页面 Meta 信息

```bash
curl -X POST http://192.168.110.140:9999/api/browser/meta
```

**返回**: title, description, keywords, viewport 等 meta 信息。

### 获取所有链接

```bash
curl -X POST http://192.168.110.140:9999/api/browser/links
```

**返回**: 页面所有链接的 URL 和文本。

### 获取所有图片

```bash
curl -X POST http://192.168.110.140:9999/api/browser/images
```

**返回**: 页面所有图片的 src、alt、width、height。

### 页面导航

```bash
curl -X POST http://192.168.110.140:9999/api/browser/navigate \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

### 历史记录

```bash
curl -X POST http://192.168.110.140:9999/api/browser/history
```

**返回**: 浏览历史列表。

### 刷新页面

```bash
curl -X POST http://192.168.110.140:9999/api/browser/reload
```

### 执行脚本

```bash
curl -X POST http://192.168.110.140:9999/api/browser/execute \
  -H "Content-Type: application/json" \
  -d '{"script":"document.querySelector(\".btn\").click()"}'
```

### 执行脚本并返回结果

```bash
curl -X POST http://192.168.110.140:9999/api/browser/evaluate \
  -H "Content-Type: application/json" \
  -d '{"script":"document.title"}'
```

---

## 📁 文件操作 API (补充)

### 追加文件

```bash
curl -X POST http://192.168.110.140:9999/api/file/append \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard/log.txt","content":"新日志行\n"}'
```

### 删除文件

```bash
curl -X POST http://192.168.110.140:9999/api/file/delete \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard/temp.txt"}'
```

### 创建目录

```bash
curl -X POST http://192.168.110.140:9999/api/file/mkdir \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard/newdir"}'
```

---

## 📱 应用管理 API (补充)

### 最近应用列表

```bash
curl http://192.168.110.140:9999/api/app/recent
```

**返回**: 最近使用的应用列表。

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
| **应用列表** | `GET /api/app/list` |
| **启动应用** | `POST /api/app/launch {"packageName":"com.xxx"}` |
| **停止应用** | `POST /api/app/stop {"packageName":"com.xxx"}` |
| **当前应用** | `GET /api/app/current` |
| **Shell执行** | `POST /api/shell/exec {"command":"ls"}` |
| **剪贴板读取** | `GET /api/clipboard/get` |
| **剪贴板设置** | `POST /api/clipboard/set {"text":"xxx"}` |
| **音量控制** | `GET /api/media/volume` |
| **亮度控制** | `GET /api/media/brightness` |
| **通知列表** | `GET /api/notify/list` |
| **文件列表** | `POST /api/file/list {"path":"/sdcard"}` |
| **WebView注入** | `POST /api/webview/eval {"script":"document.title"}` |
| **手势-方向滑动** | `POST /api/gesture/swipe {"direction":"up","distance":500}` |
| **手势-快速滑动** | `POST /api/gesture/fling {"direction":"up"}` |
| **手势-缩放** | `POST /api/gesture/pinch {"direction":"out","distance":200}` |
| **手势-路径** | `POST /api/gesture/path {"points":[{"x":100,"y":1000},{"x":500,"y":500}]}` |
| **手势-序列** | `POST /api/gesture/sequence {"gestures":[{"type":"tap","x":540,"y":1000}]}` |
| **手势-图案解锁** | `POST /api/gesture/pattern {"points":[0,4,8,5,2]}` |
| **浏览器-表单填充** | `POST /api/browser/form/fill {"data":{"user":"xxx","pass":"xxx"}}` |
| **浏览器-XPath** | `POST /api/browser/xpath {"xpath":"//div[@class=\"content\"]"}` |
| **浏览器-属性** | `POST /api/browser/attr/get {"selector":"#id","attr":"value"}` |
| **浏览器-表格** | `POST /api/browser/table/extract {"selector":"table","headers":true}` |
| **浏览器-获取文本** | `POST /api/browser/text/get {"selector":".content"}` |
| **浏览器-获取HTML** | `POST /api/browser/html/get {"selector":"#id"}` |
| **浏览器-获取链接** | `POST /api/browser/links` |
| **浏览器-获取图片** | `POST /api/browser/images` |
| **浏览器-导航** | `POST /api/browser/navigate {"url":"https://xxx"}` |
| **浏览器-刷新** | `POST /api/browser/reload` |
| **浏览器-执行JS** | `POST /api/browser/execute {"script":"xxx"}` |
| **文件-追加** | `POST /api/file/append {"path":"/sdcard/log.txt","content":"xxx"}` |
| **文件-删除** | `POST /api/file/delete {"path":"/sdcard/temp.txt"}` |
| **文件-创建目录** | `POST /api/file/mkdir {"path":"/sdcard/newdir"}` |
| **应用-最近列表** | `GET /api/app/recent` |

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

## 🚀 应用管理 API (v1.3.0新增)

借鉴自 MobiAgent、Hamibot 的应用管理能力。

### 应用列表

```bash
curl http://192.168.110.140:9999/api/app/list
```

**返回**: 所有已安装的应用列表，包含包名和应用名。

---

### 启动应用

```bash
curl -X POST http://192.168.110.140:9999/api/app/launch \
  -H "Content-Type: application/json" \
  -d '{"packageName":"com.android.chrome"}'
```

---

### 停止应用

```bash
curl -X POST http://192.168.110.140:9999/api/app/stop \
  -H "Content-Type: application/json" \
  -d '{"packageName":"com.android.chrome"}'
```

---

### 清除应用数据

```bash
curl -X POST http://192.168.110.140:9999/api/app/clear \
  -H "Content-Type: application/json" \
  -d '{"packageName":"com.example.app"}'
```

---

### 获取当前前台应用

```bash
curl http://192.168.110.140:9999/api/app/current
```

---

### 应用信息

```bash
curl -X POST http://192.168.110.140:9999/api/app/info \
  -H "Content-Type: application/json" \
  -d '{"packageName":"com.android.chrome"}'
```

---

## 💻 Shell 执行 API (v1.3.0新增)

```bash
curl -X POST http://192.168.110.140:9999/api/shell/exec \
  -H "Content-Type: application/json" \
  -d '{"command":"pm list packages"}'
```

---

## 📋 剪贴板 API (v1.3.0新增)

### 读取剪贴板

```bash
curl http://192.168.110.140:9999/api/clipboard/get
```

### 设置剪贴板

```bash
curl -X POST http://192.168.110.140:9999/api/clipboard/set \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello PhantomAPI!"}'
```

---

## 🔊 媒体控制 API (v1.3.0新增)

### 音量控制

```bash
# 获取当前音量
curl http://192.168.110.140:9999/api/media/volume

# 设置音量
curl -X POST http://192.168.110.140:9999/api/media/volume \
  -H "Content-Type: application/json" \
  -d '{"volume":10,"stream":"music"}'

# stream 可选值: music, ring, alarm, notification
```

### 亮度控制

```bash
# 获取当前亮度
curl http://192.168.110.140:9999/api/media/brightness

# 设置亮度 (0-255)
curl -X POST http://192.168.110.140:9999/api/media/brightness \
  -H "Content-Type: application/json" \
  -d '{"brightness":128}'
```

---

## 🔔 通知监听 API (v1.3.0新增)

借鉴自 GKD 的通知处理机制。

### 获取通知列表

```bash
curl http://192.168.110.140:9999/api/notify/list
```

**注意**: 需要先在系统设置中授权 PhantomAPI 访问通知。

### 等待特定通知

```bash
curl -X POST http://192.168.110.140:9999/api/notify/wait \
  -H "Content-Type: application/json" \
  -d '{"packageName":"com.example.app","titleContains":"验证码","timeout":30000}'
```

---

## 📁 文件操作 API (v1.3.0新增)

### 检查文件存在

```bash
curl -X POST http://192.168.110.140:9999/api/file/exists \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard"}'
```

### 列出目录

```bash
curl -X POST http://192.168.110.140:9999/api/file/list \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard"}'
```

### 读取文件

```bash
curl -X POST http://192.168.110.140:9999/api/file/read \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard/test.txt"}'
```

### 写入文件

```bash
curl -X POST http://192.168.110.140:9999/api/file/write \
  -H "Content-Type: application/json" \
  -d '{"path":"/sdcard/test.txt","content":"Hello"}'
```

### 下载文件

```bash
curl -X POST http://192.168.110.140:9999/api/file/download \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/file.zip","path":"/sdcard/download/file.zip"}'
```

---

## 🌐 WebView 注入 API (v1.3.0新增)

借鉴自 AutoJs6 的 InjectableWebClient，支持在 WebView 中执行 JavaScript。

### 注入脚本

```bash
curl -X POST http://192.168.110.140:9999/api/webview/inject \
  -H "Content-Type: application/json" \
  -d '{"script":"alert(\"Hello\")"}'
```

### 执行并返回结果

```bash
curl -X POST http://192.168.110.140:9999/api/webview/eval \
  -H "Content-Type: application/json" \
  -d '{"script":"document.title"}'
```

### WebView 点击

```bash
# 通过选择器点击
curl -X POST http://192.168.110.140:9999/api/webview/click \
  -H "Content-Type: application/json" \
  -d '{"selector":".btn-login"}'

# 通过坐标点击
curl -X POST http://192.168.110.140:9999/api/webview/click \
  -H "Content-Type: application/json" \
  -d '{"x":540,"y":960}'
```

### WebView 输入

```bash
curl -X POST http://192.168.110.140:9999/api/webview/input \
  -H "Content-Type: application/json" \
  -d '{"selector":"#username","text":"user123"}'
```

### WebView 滚动

```bash
curl -X POST http://192.168.110.140:9999/api/webview/scroll \
  -H "Content-Type: application/json" \
  -d '{"direction":"down","distance":300}'
```

### 等待元素出现

```bash
curl -X POST http://192.168.110.140:9999/api/webview/wait \
  -H "Content-Type: application/json" \
  -d '{"selector":".loading","timeout":10000}'
```

### Cookie 操作

```bash
# 获取 Cookie
curl -X POST http://192.168.110.140:9999/api/webview/cookies \
  -H "Content-Type: application/json" \
  -d '{"action":"get"}'

# 清除 Cookie
curl -X POST http://192.168.110.140:9999/api/webview/cookies \
  -H "Content-Type: application/json" \
  -d '{"action":"clear"}'
```

### Storage 操作

```bash
# 获取 LocalStorage
curl -X POST http://192.168.110.140:9999/api/webview/storage \
  -H "Content-Type: application/json" \
  -d '{"type":"local","action":"get"}'

# 获取 SessionStorage
curl -X POST http://192.168.110.140:9999/api/webview/storage \
  -H "Content-Type: application/json" \
  -d '{"type":"session","action":"get"}'
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