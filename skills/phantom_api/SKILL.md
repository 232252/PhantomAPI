# PhantomAPI 技能

通过 PhantomAPI 控制 Android 设备。PhantomAPI 是基于 Magisk/LSPosed 的系统级 Android 控制服务，通过局域网 HTTP API 暴露能力，**零截图/OCR**，基于 AccessibilityNodeInfo 和 DOM 树。

## 触发条件

当用户提到以下关键词时自动触发：
- "控制手机"、"控制 Android"、"自动化"
- "点击坐标"、"滑动屏幕"、"手势操作"
- "获取 UI 树"、"查找节点"、"Accessibility"
- "前台应用"、"安装应用"、"包名"
- "WebView"、"DOM 提取"、"H5 调试"
- "Chrome"、"CDP"、"DevTools"
- "网络连接"、"流量统计"、"WiFi"
- "PhantomAPI"、"LSPosed"、"Magisk"

---

## 设备信息

| 项目 | 值 |
|------|-----|
| 设备 | Xiaomi MI 6 |
| 系统 | Android 13 (SDK 33) |
| Root | Magisk + LSPosed |
| IP | `192.168.110.140:5555` (ADB) |
| HTTP 端口 | `9999` |
| IPC 目录 | `/data/local/tmp/phantom/` |

---

## API 端点总览

### 🌐 Web 域 `/api/web/*` (核心功能)

| 端点 | 方法 | 描述 | 示例 |
|------|------|------|------|
| `/api/web/detect` | GET | 检测 WebView 状态 | `curl $IP:9999/api/web/detect` |
| `/api/web/dom` | GET | 获取 WebView DOM 数据 | `curl $IP:9999/api/web/dom` |
| `/api/web/find` | GET | 查找元素 (`?text=xxx`) | `curl "$IP:9999/api/web/find?text=登录"` |
| `/api/web/click` | POST | 点击元素 (坐标/文本/索引) | `POST {"x":500,"y":1000}` |

### 🔌 Chrome CDP 域 `/api/cdp/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/cdp/pages` | GET | 获取 Chrome 页面列表 |
| `/api/cdp/title` | GET | 获取当前页面标题 |

### 📲 UI 域 `/api/ui/*`

| 端点 | 方法 | 描述 | 支持显式等待 |
|------|------|------|-------------|
| `/api/ui/tree` | GET | 获取 Accessibility UI 树 | - |
| `/api/ui/find` | GET | 查找节点 (`?text=xxx`) | - |
| `/api/ui/tap` | POST | 坐标点击 | ✅ |
| `/api/ui/swipe` | POST | 滑动 | - |
| `/api/ui/back` | POST | 返回键 | - |
| `/api/ui/wait` | POST | 显式等待节点 | - |
| `/api/ui/action` | POST | 节点操作 | - |

### 🖥️ 系统域 `/api/sys/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/sys/info` | GET | 设备信息 |
| `/api/sys/foreground` | GET | 前台应用 |
| `/api/sys/packages` | GET | 已安装应用列表 |

### 📡 网络域 `/api/net/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/net/status` | GET | 网络状态 |
| `/api/net/wifi` | GET | WiFi 信息 |
| `/api/net/connections` | GET | 连接拓扑 |
| `/api/net/traffic` | GET | 流量统计 |

### ⚙️ 辅助域 `/api/scope/*`

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/scope/apps` | GET | 列出需要启用 LSPosed 作用域的应用 |

---

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

### 点击元素 (多种方式)

```bash
# 坐标点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \n  -H "Content-Type: application/json" \n  -d '{"x": 500, "y": 1000}'

# 文本点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \n  -H "Content-Type: application/json" \n  -d '{"text": "登录"}'

# 索引点击
curl -X POST "http://192.168.110.140:9999/api/web/click" \n  -H "Content-Type: application/json" \n  -d '{"index": 5}'
```

### 显式等待 (点击后等待结果)

```bash
# 点击搜索框，等待"搜索结果"出现，超时 5 秒
curl -X POST "http://192.168.110.140:9999/api/ui/tap" \n  -H "Content-Type: application/json" \n  -d '{
    "x": 540,
    "y": 300,
    "wait": {
      "condition": {"type": "text", "text": "搜索结果"},
      "timeout_ms": 5000,
      "interval_ms": 200
    }
  }'
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
curl -s "http://192.168.110.140:9999/api/ui/tree" | python3 -m json.tool | head -50
```

---

## WebView DOM 提取机制

### 核心流程

```
页面加载 → onPageFinished Hook → 注入 JS → prompt() → onJsPrompt Hook → 写文件 → API 读取
```

### IPC 文件

- **路径**: `/data/local/tmp/phantom/dom.json`
- **新鲜度**: 30 秒
- **策略**: 用完即焚，防止脏数据

### 第三方应用支持

1. 打开 LSPosed Manager
2. 模块 → PhantomAPI → 勾选目标应用
3. 重启目标应用

### 验证 Hook

```bash
adb logcat | grep "LSPosed-Bridge: PhantomHook"
# 应看到: Loading hook for: tv.danmaku.bili
```

---

## 性能优化指南

### 🚀 客户端优化 (强烈推荐)

PhantomAPI 是 HTTP 服务，真正的瓶颈在：
- 网络往返 (RTT)
- UI/DOM 树传输
- 客户端逻辑

#### 1. 连接池 & Keep-Alive

```python
# Python 示例
import requests

session = requests.Session()
session.headers.update({'Connection': 'keep-alive'})

# 复用连接
response = session.get('http://192.168.110.140:9999/api/ui/tree')
```

#### 2. 本地缓存

```python
import time

CACHE = {}
CACHE_TTL = 30  # 秒

def get_cached(endpoint, ttl=CACHE_TTL):
    key = endpoint
    now = time.time()
    
    if key in CACHE:
        data, ts = CACHE[key]
        if now - ts < ttl:
            return data
    
    data = requests.get(f"http://192.168.110.140:9999{endpoint}").json()
    CACHE[key] = (data, now)
    return data

# 变化慢的接口可以缓存
device_info = get_cached('/api/sys/info', ttl=300)  # 5 分钟
packages = get_cached('/api/sys/packages', ttl=60)   # 1 分钟
```

#### 3. 超时 & 重试策略

```python
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

def create_session():
    session = requests.Session()
    
    # 重试策略
    retry = Retry(
        total=3,
        backoff_factor=0.1,
        status_forcelist=[500, 502, 503, 504]
    )
    
    adapter = HTTPAdapter(max_retries=retry)
    session.mount('http://', adapter)
    
    return session

# 局域网建议短超时
session = create_session()
response = session.get('http://192.168.110.140:9999/api/ping', timeout=1.0)
```

#### 4. 减少数据传输

```bash
# 只获取需要的字段 (如果 API 支持)
curl "http://192.168.110.140:9999/api/ui/tree?fields=text,className,bounds"
```

---

## 多语言 SDK 示例

### Python SDK

```python
#!/usr/bin/env python3
"""PhantomAPI Python SDK"""

import requests
from typing import Optional, Dict, Any

class PhantomAPI:
    def __init__(self, host: str = "192.168.110.140", port: int = 9999):
        self.base_url = f"http://{host}:{port}"
        self.session = requests.Session()
    
    def _get(self, endpoint: str) -> Dict:
        return self.session.get(f"{self.base_url}{endpoint}", timeout=5).json()
    
    def _post(self, endpoint: str, data: Dict) -> Dict:
        return self.session.post(f"{self.base_url}{endpoint}", json=data, timeout=5).json()
    
    # 系统域
    def ping(self) -> Dict:
        return self._get("/api/ping")
    
    def device_info(self) -> Dict:
        return self._get("/api/sys/info")
    
    def foreground_app(self) -> Dict:
        return self._get("/api/sys/foreground")
    
    # UI 域
    def ui_tree(self) -> Dict:
        return self._get("/api/ui/tree")
    
    def find_node(self, text: str) -> Dict:
        return self._get(f"/api/ui/find?text={text}")
    
    def tap(self, x: int, y: int, wait: Optional[Dict] = None) -> Dict:
        body = {"x": x, "y": y}
        if wait:
            body["wait"] = wait
        return self._post("/api/ui/tap", body)
    
    def swipe(self, sx: int, sy: int, ex: int, ey: int, duration: int = 300) -> Dict:
        return self._post("/api/ui/swipe", {
            "startX": sx, "startY": sy,
            "endX": ex, "endY": ey,
            "duration": duration
        })
    
    def back(self) -> Dict:
        return self._post("/api/ui/back", {})
    
    # Web 域
    def web_dom(self) -> Dict:
        return self._get("/api/web/dom")
    
    def web_find(self, text: str) -> Dict:
        return self._get(f"/api/web/find?text={text}")
    
    def web_click(self, x: int = None, y: int = None, text: str = None) -> Dict:
        if text:
            return self._post("/api/web/click", {"text": text})
        return self._post("/api/web/click", {"x": x, "y": y})
    
    # CDP 域
    def cdp_pages(self) -> Dict:
        return self._get("/api/cdp/pages")
    
    def cdp_title(self) -> Dict:
        return self._get("/api/cdp/title")


# 使用示例
if __name__ == "__main__":
    api = PhantomAPI()
    
    # 测试连接
    print(api.ping())
    
    # 点击并等待
    api.tap(540, 300, wait={
        "condition": {"type": "text", "text": "搜索结果"},
        "timeout_ms": 5000
    })
```

### Go SDK

```go
package phantomapi

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type Client struct {
	BaseURL    string
	HTTPClient *http.Client
}

func NewClient(host string, port int) *Client {
	return &Client{
		BaseURL: fmt.Sprintf("http://%s:%d", host, port),
		HTTPClient: &http.Client{
			Timeout: 5 * time.Second,
			Transport: &http.Transport{
				MaxIdleConns:        10,
				IdleConnTimeout:     30 * time.Second,
				DisableCompression:  false,
			},
		},
	}
}

func (c *Client) get(endpoint string, v interface{}) error {
	resp, err := c.HTTPClient.Get(c.BaseURL + endpoint)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return json.NewDecoder(resp.Body).Decode(v)
}

func (c *Client) post(endpoint string, body io.Reader, v interface{}) error {
	resp, err := c.HTTPClient.Post(c.BaseURL+endpoint, "application/json", body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return json.NewDecoder(resp.Body).Decode(v)
}

// Ping 测试连接
func (c *Client) Ping() (map[string]interface{}, error) {
	var result map[string]interface{}
	err := c.get("/api/ping", &result)
	return result, err
}

// Tap 点击坐标
func (c *Client) Tap(x, y int) (map[string]interface{}, error) {
	var result map[string]interface{}
	body := fmt.Sprintf(`{"x":%d,"y":%d}`, x, y)
	err := c.post("/api/ui/tap", strings.NewReader(body), &result)
	return result, err
}

// Swipe 滑动
func (c *Client) Swipe(sx, sy, ex, ey, duration int) (map[string]interface{}, error) {
	var result map[string]interface{}
	body := fmt.Sprintf(`{"startX":%d,"startY":%d,"endX":%d,"endY":%d,"duration":%d}`, sx, sy, ex, ey, duration)
	err := c.post("/api/ui/swipe", strings.NewReader(body), &result)
	return result, err
}

// GetUITree 获取 UI 树
func (c *Client) GetUITree() (map[string]interface{}, error) {
	var result map[string]interface{}
	err := c.get("/api/ui/tree", &result)
	return result, err
}

// WebClick Web 点击
func (c *Client) WebClick(x, y int) (map[string]interface{}, error) {
	var result map[string]interface{}
	body := fmt.Sprintf(`{"x":%d,"y":%d}`, x, y)
	err := c.post("/api/web/click", strings.NewReader(body), &result)
	return result, err
}
```

### Node.js SDK

```javascript
const axios = require('axios');

class PhantomAPI {
  constructor(host = '192.168.110.140', port = 9999) {
    this.client = axios.create({
      baseURL: `http://${host}:${port}`,
      timeout: 5000,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  // 系统域
  async ping() {
    const { data } = await this.client.get('/api/ping');
    return data;
  }

  async deviceInfo() {
    const { data } = await this.client.get('/api/sys/info');
    return data;
  }

  // UI 域
  async uiTree() {
    const { data } = await this.client.get('/api/ui/tree');
    return data;
  }

  async findNode(text) {
    const { data } = await this.client.get(`/api/ui/find?text=${encodeURIComponent(text)}`);
    return data;
  }

  async tap(x, y, waitCondition = null) {
    const body = { x, y };
    if (waitCondition) {
      body.wait = waitCondition;
    }
    const { data } = await this.client.post('/api/ui/tap', body);
    return data;
  }

  async swipe(sx, sy, ex, ey, duration = 300) {
    const { data } = await this.client.post('/api/ui/swipe', {
      startX: sx, startY: sy, endX: ex, endY: ey, duration
    });
    return data;
  }

  async back() {
    const { data } = await this.client.post('/api/ui/back', {});
    return data;
  }

  // Web 域
  async webDom() {
    const { data } = await this.client.get('/api/web/dom');
    return data;
  }

  async webClick(options) {
    const { data } = await this.client.post('/api/web/click', options);
    return data;
  }

  // CDP 域
  async cdpPages() {
    const { data } = await this.client.get('/api/cdp/pages');
    return data;
  }

  async cdpTitle() {
    const { data } = await this.client.get('/api/cdp/title');
    return data;
  }
}

// 使用示例
async function main() {
  const api = new PhantomAPI();
  
  console.log(await api.ping());
  
  // 点击并等待
  await api.tap(540, 300, {
    condition: { type: 'text', text: '搜索结果' },
    timeout_ms: 5000
  });
}

main();
```

---

## CLI 工具

```bash
# 基本用法
./phantom_api.sh <device_ip> <command> [args]

# 系统命令
./phantom_api.sh 192.168.110.140 ping
./phantom_api.sh 192.168.110.140 info
./phantom_api.sh 192.168.110.140 foreground
./phantom_api.sh 192.168.110.140 packages

# UI 命令
./phantom_api.sh 192.168.110.140 tree
./phantom_api.sh 192.168.110.140 find "设置"
./phantom_api.sh 192.168.110.140 tap 540 960
./phantom_api.sh 192.168.110.140 swipe 540 1500 540 500
./phantom_api.sh 192.168.110.140 back
./phantom_api.sh 192.168.110.140 wait "确定" 5000

# Web 命令
./phantom_api.sh 192.168.110.140 web-detect
./phantom_api.sh 192.168.110.140 web-dom
./phantom_api.sh 192.168.110.140 web-find "百度"
./phantom_api.sh 192.168.110.140 web-click 540 960
./phantom_api.sh 192.168.110.140 web-click "登录"

# CDP 命令
./phantom_api.sh 192.168.110.140 cdp-pages
./phantom_api.sh 192.168.110.140 cdp-title

# 网络命令
./phantom_api.sh 192.168.110.140 net-status
./phantom_api.sh 192.168.110.140 net-connections
```

---

## 最佳实践

### 1. 错误处理

```python
def safe_tap(api, x, y, retries=3):
    for i in range(retries):
        try:
            result = api.tap(x, y)
            if result.get('success'):
                return result
        except requests.Timeout:
            print(f"超时，重试 {i+1}/{retries}")
        except requests.RequestException as e:
            print(f"请求失败: {e}")
    return None
```

### 2. 等待策略

```python
def wait_and_tap(api, find_text, tap_x, tap_y, timeout_ms=5000):
    """查找元素并点击"""
    # 先等待元素出现
    result = api.tap(tap_x, tap_y, wait={
        "condition": {"type": "text", "text": find_text},
        "timeout_ms": timeout_ms
    })
    return result
```

### 3. 批量操作

```python
def batch_tap(api, coordinates):
    """批量点击"""
    results = []
    for x, y in coordinates:
        result = api.tap(x, y)
        results.append(result)
        time.sleep(0.5)  # 间隔避免过快
    return results
```

### 4. 条件执行

```python
def smart_tap(api, text, fallback_coords):
    """智能点击：先尝试文本点击，失败则用坐标"""
    result = api.web_click(text=text)
    if not result.get('success'):
        print(f"文本点击失败，使用备用坐标: {fallback_coords}")
        result = api.web_click(x=fallback_coords[0], y=fallback_coords[1])
    return result
```

---

## 故障排查

### 问题: WebView DOM 为空

```bash
# 检查 IPC 文件
adb shell su -c "ls -la /data/local/tmp/phantom/"

# 检查 LSPosed Hook
adb logcat | grep "LSPosed-Bridge: PhantomHook"

# 手动触发 DOM 提取
adb shell su -c "cat /data/local/tmp/phantom/dom.json"
```

### 问题: Chrome CDP 无响应

```bash
# 检查 Chrome DevTools
adb shell su -c "cat /proc/net/unix | grep chrome_devtools"

# 手动测试 CDP
adb shell su -c "curl --abstract-unix-socket chrome_devtools_remote http://localhost/json"
```

### 问题: 第三方应用 WebView 未被 Hook

1. 打开 LSPosed Manager
2. 模块 → PhantomAPI → 勾选目标应用
3. 强制停止并重新打开目标应用

---

## 项目地址

**GitHub**: https://github.com/232252/PhantomAPI

**文档**:
- `README.md` - 项目说明
- `docs/ARCHITECTURE.md` - 架构设计
- `docs/BUGFIX.md` - 问题修复记录
- `VALIDATION_REPORT.md` - 接口验证报告
- `FINAL_REPORT.md` - 项目总结