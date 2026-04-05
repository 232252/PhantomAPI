# PhantomAPI 更新日志

## v1.5.0 (2026-04-05)

### 新增功能
- **并发控制**: 智能区分只读操作和写操作
  - 只读操作（查询、获取信息等）可并发执行
  - 写操作（点击、滑动、启动应用等）串行执行保证安全
  - 自动识别操作类型，无需手动指定

### 技术细节
- 使用双线程池架构：
  - `readExecutor`: CachedThreadPool 用于只读操作并发
  - `writeExecutor`: SingleThreadExecutor 用于写操作串行
- 识别规则：
  - 只读前缀：`/api/ping`, `/api/sys/`, `/api/ui/tree`, `/api/app/list` 等
  - 写操作关键字：`/tap`, `/swipe`, `/click`, `/launch`, `/stop` 等

---

## v1.4.0 (2026-04-05)

### 新增功能
- **BrowserApiHandler**: 浏览器注入 API（21个）
  - 表单自动填充和提交
  - 元素属性/样式/文本操作
  - XPath/CSS 选择器查询
  - 表格提取、链接/图片获取
  - 导航、历史记录、刷新
  - JavaScript 执行

- **GestureApiHandler**: 高级手势 API（12个）
  - tap, double_tap, long_press
  - swipe, fling, drag, pinch, scroll
  - path, bezier, sequence, pattern

### 已验证
- gesture/tap ✅
- gesture/swipe ✅
- gesture/pinch ✅
- gesture/scroll ✅
- file/list ✅
- app/list ✅ (返回75个应用)
- clipboard/set ✅
- shell/exec ✅
- selector/query ✅
- ui/tap ✅
- ui/tree ✅
- ping ✅

---

## v1.3.0 (2026-04-04)

### 新增功能
- **AppApiHandler**: 应用管理 API
- **WebViewApiHandler**: WebView 注入 API
- **NotifyApiHandler**: 通知监听 API
- **FileApiHandler**: 文件操作 API

---

## v1.2.0 (2026-04-04)

### 新增功能
- **SelectorApiHandler**: 选择器 API
- 修复滑动后坐标变成负数问题

---

## v1.1.0 (2026-04-03)

### 新增功能
- **AdvancedApiHandler**: 高级手势操作 API

---

## v1.0.0 (2026-04-01)

### 初始版本
- 基础 HTTP 服务器
- 无障碍服务集成
- UI 操作 API
- 系统信息 API
