# PhantomAPI 验收测试清单

## 1. 权限 & Magisk 一致性

### 测试用例 1.1: 系统应用验证
```bash
# 安装模块后重启
adb shell dumpsys package com.phantom.api | grep -E "userId=|flags="

# 预期结果
userId=1000 (system) 或有 FLAG_SYSTEM|FLAG_PRIVILEGED 标志
```

### 测试用例 1.2: 服务稳定性
```bash
# 检查服务是否被系统杀掉
adb shell ps -A | grep phantom

# 预期结果
服务持续运行，PID 稳定
```

---

## 2. UI 引擎

### 测试用例 2.1: UI 树完整性
```bash
# 打开系统设置
adb shell am start -a android.settings.SETTINGS

# 获取 UI 树
curl http://<IP>:9999/api/ui/tree

# 预期结果
JSON 中存在节点 text=WLAN（或"无线网络"）
节点包含合法 bounds: {left, top, right, bottom}
```

### 测试用例 2.2: 节点查找
```bash
curl "http://<IP>:9999/api/ui/find?text=WLAN"

# 预期结果
返回包含 WLAN 的节点列表，带有坐标信息
```

### 测试用例 2.3: 点击操作
```bash
# 点击 WLAN 设置
curl -X POST http://<IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{"x": 540, "y": 300}'

# 预期结果
页面跳转到 WLAN 列表
```

### 测试用例 2.4: 显式等待
```bash
curl -X POST http://<IP>:9999/api/ui/tap \
  -H "Content-Type: application/json" \
  -d '{
    "x": 540, 
    "y": 300,
    "wait": {
      "condition": {"type": "text", "text": "WLAN"},
      "timeout_ms": 5000
    }
  }'

# 预期结果
点击后等待 WLAN 文本出现，最多 5 秒
```

---

## 3. Web/CDP 引擎

### 测试用例 3.1: DevTools 检测
```bash
# 打开浏览器
adb shell am start -a android.intent.action.VIEW -d https://www.baidu.com

# 检测调试状态
curl http://<IP>:9999/api/web/detect

# 预期结果
{"available": true, "socketCount": 1, "sockets": ["chrome_devtools_remote"]}
```

### 测试用例 3.2: DOM 获取
```bash
curl http://<IP>:9999/api/web/dom

# 预期结果
返回 DOM 级结构，包含页面真实文字与坐标
```

### 测试用例 3.3: JavaScript 执行
```bash
curl -X POST http://<IP>:9999/api/web/execute \
  -H "Content-Type: application/json" \
  -d '{"script": "document.title"}'

# 预期结果
返回页面标题
```

### 测试用例 3.4: CDP 点击
```bash
curl -X POST http://<IP>:9999/api/web/click \
  -H "Content-Type: application/json" \
  -d '{"x": 540, "y": 200}'

# 预期结果
页面发生对应交互，无障碍服务没有收到点击事件（Web 层穿透）
```

---

## 4. 网络引擎

### 测试用例 4.1: 连接拓扑
```bash
# 打开浏览器访问某网站
curl http://<IP>:9999/api/net/connections

# 预期结果
列表中能看到浏览器包名、目标 IP:443（HTTPS）
```

### 测试用例 4.2: 流量统计
```bash
# 播放视频
curl http://<IP>:9999/api/net/traffic

# 预期结果
对应包名的 rx_bytes 持续增长
```

---

## 5. 延时指标

### 测试用例 5.1: performAction 延迟
```bash
# 使用节点点击
# 预期: < 50ms
```

### 测试用例 5.2: InputManager 注入延迟
```bash
# 使用坐标点击
# 预期: < 30ms
```

### 测试用例 5.3: ADB input 延迟（对比）
```bash
adb shell input tap 540 960
# 预期: > 100ms
```

---

## 6. 零截图约束

### 测试用例 6.1: 代码扫描
```bash
# 扫描仓库
grep -r "screencap" app/src
grep -r "MediaProjection" app/src
grep -r "SurfaceControl.captureLayers" app/src
grep -r "OCR" app/src

# 预期结果
0 命中
```

### 测试用例 6.2: 权限检查
```bash
# AndroidManifest.xml 检查
grep "android.permission.CAPTURE" app/src/main/AndroidManifest.xml

# 预期结果
0 命中
```

---

## 7. LSPosed WebView Hook

### 测试用例 7.1: 调试强制开启
```bash
# 打开任意使用 WebView 的应用
# 检查 DevTools Socket
adb shell "su -c 'cat /proc/net/unix | grep devtools'"

# 预期结果
看到 webview_devtools_remote socket
```

### 测试用例 7.2: JS SDK 注入
```bash
# 检查日志
adb logcat | grep PhantomBridge

# 预期结果
看到 "[PhantomBridge] JS SDK injected" 日志
```

---

## 8. 安全检查

### 测试用例 8.1: 端口绑定
```bash
# 检查端口绑定
adb shell netstat -tuln | grep 9999

# 预期结果
仅绑定局域网接口（非 0.0.0.0:9999 绑定公网）
```

### 测试用例 8.2: 无截图 API
```bash
# 确认无截图相关代码
grep -r "takeScreenshot" app/src
grep -r "capture" app/src | grep -i "screen"

# 预期结果
0 命中（或仅有注释说明）
```

---

## 验收结果汇总

| 模块 | 用例数 | 通过 | 失败 | 通过率 |
|------|--------|------|------|--------|
| 权限 & Magisk | 2 | - | - | - |
| UI 引擎 | 4 | - | - | - |
| Web/CDP 引擎 | 4 | - | - | - |
| 网络引擎 | 2 | - | - | - |
| 延时指标 | 3 | - | - | - |
| 零截图约束 | 2 | - | - | - |
| LSPosed Hook | 2 | - | - | - |
| 安全检查 | 2 | - | - | - |
| **总计** | **21** | - | - | - |

---

## 验收标准

- [ ] 所有测试用例通过率 ≥ 95%
- [ ] 零截图约束 100% 通过
- [ ] UI 树完整性 100% 通过
- [ ] Web/CDP 引擎 ≥ 75% 通过
- [ ] 延迟指标符合要求
