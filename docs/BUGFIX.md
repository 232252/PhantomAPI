# PhantomAPI 问题修复记录

## 2026-04-05 WebView DOM 提取完整实现

### 问题 1: WebViewClient.onJsPrompt Hook 失败

**错误信息:**
```
java.lang.NoSuchMethodError: android.webkit.WebViewClient#onJsPrompt(android.webkit.WebView,java.lang.String,java.lang.String,java.lang.String,android.webkit.JsPromptResult)#exact
```

**原因:**
- `findAndHookMethod` 无法找到精确匹配的方法签名
- Android 不同版本的 API 可能有细微差异

**解决方案:**
使用 `XposedBridge.hookMethod` 直接 hook 方法对象:
```java
Method[] methods = WebChromeClient.class.getDeclaredMethods();
for (Method m : methods) {
    if ("onJsPrompt".equals(m.getName())) {
        XposedBridge.hookMethod(m, jsPromptHook);
    }
}
```

---

### 问题 2: onJsPrompt 参数顺序错误

**现象:**
日志显示 `onJsPrompt intercepted: https://www.baidu.com/` 而不是 `phantom://dom`

**原因:**
参数索引错误:
- `args[1]` 是 URL，不是 message
- `args[2]` 是 message
- `args[3]` 是 defaultValue (实际数据)

**解决方案:**
```java
String message = (String) param.args[2];      // prompt 的第一个参数
String defaultValue = (String) param.args[3]; // prompt 的第二个参数(数据)
```

---

### 问题 3: DOM 文件过期

**现象:**
API 返回 `dom_unavailable`，日志显示 `DOM file expired (age: 15000ms)`

**原因:**
新鲜度阈值 (10秒) 太短，网络延迟导致文件过期

**解决方案:**
将阈值从 10 秒改为 30 秒:
```java
if (fileAge < 30000) {  // 30 秒内新鲜
    // ...
}
```

---

### 问题 4: WebChromeClient 未设置

**现象:**
WebView 页面加载完成，但 `onJsPrompt` 未被调用

**原因:**
WebView 必须设置 `WebChromeClient` 才能处理 `prompt()` 对话框

**解决方案:**
在 `WebViewTestActivity` 中添加:
```java
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public boolean onJsPrompt(WebView view, String url, String message, 
            String defaultValue, JsPromptResult result) {
        return super.onJsPrompt(view, url, message, defaultValue, result);
    }
});
```

---

### 问题 5: Chrome 是独立 Chromium 实现

**现象:**
WebView Hook 对 Chrome 浏览器无效

**原因:**
Chrome 使用独立的 Chromium 内核，不走系统 WebView

**解决方案:**
1. 实现 Chrome CDP 方案 (`/api/cdp/*`)
2. 使用 Accessibility API 作为备选 (`/api/ui/tree`)

---

### 问题 6: 第三方应用 WebView 未被 Hook

**现象:**
B站、抖音等应用的 WebView 未被 hook

**原因:**
LSPosed 模块需要在 Manager 中手动启用作用域

**解决方案:**
1. 在 LSPosed Manager 中为目标应用启用模块
2. 提供 `/api/scope/apps` API 列出需要启用的应用

---

## 关键技术点

### 1. prompt() 作为 IPC 通道

**原理:**
- JS 执行 `prompt('phantom://dom', jsonData)`
- Java 端 hook `onJsPrompt` 拦截
- 无需轮询，实时性高

**优势:**
- 页面加载完成即自动触发
- 数据量无限制 (JSON 字符串)
- 兼容所有 WebView

### 2. 文件 IPC

**路径:** `/data/local/tmp/phantom/dom.json`

**权限:**
```bash
mkdir -p /data/local/tmp/phantom
chmod 777 /data/local/tmp/phantom
```

### 3. Chrome DevTools Protocol

**检测 Chrome:**
```bash
# Unix Socket
cat /proc/net/unix | grep chrome_devtools

# HTTP 请求
curl --abstract-unix-socket chrome_devtools_remote http://localhost/json
```
