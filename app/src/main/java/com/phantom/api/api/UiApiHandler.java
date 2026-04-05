package com.phantom.api.api;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 原生 UI 域 API 处理器
 * 
 * 端点：
 * /api/ui/tree - 获取 UI 树
 * /api/ui/find - 查找节点
 * /api/ui/tap - 点击坐标
 * /api/ui/swipe - 滑动
 * /api/ui/back - 返回键
 * /api/ui/wait - 显式等待
 * /api/ui/action - 节点操作
 */
public class UiApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "UiApiHandler";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
        if (a11yService == null) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, 
                "无障碍服务未启用");
        }
        
        if (uri.equals("/api/ui/tree")) {
            return handleGetTree(a11yService);
        } else if (uri.equals("/api/ui/find")) {
            return handleFind(a11yService, session);
        } else if (uri.equals("/api/ui/tap")) {
            return handleTap(a11yService, session);
        } else if (uri.equals("/api/ui/swipe")) {
            return handleSwipe(a11yService, session);
        } else if (uri.equals("/api/ui/back")) {
            return handleBack(a11yService);
        } else if (uri.equals("/api/ui/smart_back")) {
            return handleSmartBack(a11yService, session);
        } else if (uri.equals("/api/ui/home")) {
            return handleHome(a11yService);
        } else if (uri.equals("/api/ui/wait")) {
            return handleWait(a11yService, session);
        } else if (uri.equals("/api/ui/action")) {
            return handleAction(a11yService, session);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    /**
     * 获取 UI 树
     */
    private NanoHTTPD.Response handleGetTree(PhantomAccessibilityService service) throws Exception {
        String treeJson = service.getNodeTreeJson();
        return HttpServerEngine.jsonSuccess("tree", new JSONObject(treeJson));
    }
    
    /**
     * 查找节点
     * 支持 clickable 参数：当 clickable=true 时，返回可点击的父节点
     */
    private NanoHTTPD.Response handleFind(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String text = params.get("text");
        String id = params.get("id");
        String className = params.get("class");
        boolean requireClickable = "true".equals(params.get("clickable"));
        
        List<AccessibilityNodeInfo> nodes = null;
        
        if (text != null && !text.isEmpty()) {
            nodes = service.findNodesByText(text);
        } else if (id != null && !id.isEmpty()) {
            nodes = service.findNodesById(id);
        } else {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.BAD_REQUEST, 
                "需要 text 或 id 参数");
        }
        
        JSONArray nodesArray = new JSONArray();
        
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                AccessibilityNodeInfo resultNode = node;
                
                // 如果需要可点击节点，向上查找
                if (requireClickable && !node.isClickable()) {
                    AccessibilityNodeInfo clickableParent = findClickableParent(node);
                    if (clickableParent != null) {
                        resultNode = clickableParent;
                    }
                }
                
                JSONObject nodeJson = nodeToJson(resultNode);
                nodeJson.put("originally_clickable", node.isClickable());
                if (requireClickable && resultNode != node) {
                    nodeJson.put("resolved_clickable", true);
                }
                nodesArray.put(nodeJson);
                
                if (resultNode != node) {
                    // 不要回收新找到的父节点
                }
                node.recycle();
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("count", nodesArray.length());
        result.put("nodes", nodesArray);
        result.put("clickable_filter", requireClickable);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 向上查找可点击的父节点
     * @param node 起始节点
     * @return 可点击的父节点，如果找不到返回 null
     */
    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node.getParent();
        int maxDepth = 5; // 最多向上找 5 层，防止找到根节点
        
        while (current != null && maxDepth-- > 0) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null; // 实在找不到可点击的父节点，返回 null
    }
    
    /**
     * 点击坐标
     */
    private NanoHTTPD.Response handleTap(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int x = body.getInt("x");
        int y = body.getInt("y");
        
        // 检查是否有等待条件
        JSONObject wait = body.optJSONObject("wait");
        
        boolean success = service.clickAtPosition(x, y);
        
        // 如果有等待条件，执行等待
        if (wait != null && success) {
            success = performWait(service, wait);
        }
        
        JSONObject result = new JSONObject();
        result.put("tapped", success);
        result.put("x", x);
        result.put("y", y);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 滑动
     */
    private NanoHTTPD.Response handleSwipe(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int startX = body.getInt("startX");
        int startY = body.getInt("startY");
        int endX = body.getInt("endX");
        int endY = body.getInt("endY");
        long duration = body.optLong("duration", 300);
        
        boolean success = service.performSwipe(startX, startY, endX, endY, duration);
        
        JSONObject result = new JSONObject();
        result.put("swiped", success);
        result.put("startX", startX);
        result.put("startY", startY);
        result.put("endX", endX);
        result.put("endY", endY);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 返回键
     */
    private NanoHTTPD.Response handleBack(PhantomAccessibilityService service) throws Exception {
        boolean success = service.performBack();
        return HttpServerEngine.jsonSuccess("back", success);
    }
    
    /**
     * Home 键 - 回到桌面
     * GET /api/ui/home
     */
    private NanoHTTPD.Response handleHome(PhantomAccessibilityService service) throws Exception {
        try {
            // 模拟按下 HOME 键
            Runtime.getRuntime().exec("input keyevent KEYCODE_HOME");
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("action", "home");
            result.put("message", "已发送 HOME 键事件");
            
            return HttpServerEngine.jsonSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "Home 键执行失败: " + e.getMessage());
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "Home 键执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 智能返回 - 连续按返回键直到页面变化
     * POST /api/ui/smart_back
     * Body: {"max_tries": 5, "current_package": "com.example.app"}
     */
    private NanoHTTPD.Response handleSmartBack(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int maxTries = body.optInt("max_tries", 5);
        String currentPackage = body.optString("current_package", "");
        
        // 如果没有提供当前包名，尝试获取
        if (currentPackage.isEmpty()) {
            currentPackage = getForegroundPackage();
        }
        
        for (int i = 0; i < maxTries; i++) {
            // 执行返回
            service.performBack();
            Thread.sleep(500);
            
            // 检查当前包名
            String newPackage = getForegroundPackage();
            
            // 如果退出了当前 App，说明退多了
            if (!newPackage.equals(currentPackage)) {
                JSONObject result = new JSONObject();
                result.put("success", false);
                result.put("error", "exited_app");
                result.put("new_package", newPackage);
                result.put("tries", i + 1);
                return HttpServerEngine.jsonSuccess(result);
            }
            
            // 简单判断：如果 UI 树大小变化超过 20%，认为页面切换成功
            // 这里可以扩展为更精确的 Activity 名获取逻辑
            if (checkUiTreeChanged(service)) {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("tries", i + 1);
                result.put("message", "检测到页面变化");
                return HttpServerEngine.jsonSuccess(result);
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("tries", maxTries);
        result.put("message", "已执行 " + maxTries + " 次返回");
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取前台应用包名
     */
    private String getForegroundPackage() {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys activity activities | grep mResumedActivity");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                // 格式: mResumedActivity: ActivityRecord{xxx u0 com.package.name/xxx}
                int slashIndex = line.indexOf("/");
                if (slashIndex > 0) {
                    int spaceIndex = line.lastIndexOf(" ", slashIndex);
                    if (spaceIndex > 0) {
                        return line.substring(spaceIndex + 1, slashIndex);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取前台包名失败: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * 检查 UI 树是否变化（用于智能返回判断）
     */
    private int lastNodeCount = 0;
    private boolean checkUiTreeChanged(PhantomAccessibilityService service) {
        try {
            String treeJson = service.getNodeTreeJson();
            JSONObject tree = new JSONObject(treeJson);
            int nodeCount = tree.optInt("node_count", 0);
            
            // 变化超过 20% 认为是页面切换
            if (lastNodeCount > 0 && Math.abs(nodeCount - lastNodeCount) > lastNodeCount * 0.2) {
                lastNodeCount = nodeCount;
                return true;
            }
            lastNodeCount = nodeCount;
        } catch (Exception e) {
            Log.e(TAG, "检查 UI 树变化失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 显式等待
     */
    private NanoHTTPD.Response handleWait(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        JSONObject condition = body.getJSONObject("condition");
        long timeout = body.optLong("timeout_ms", 5000);
        long interval = body.optLong("interval_ms", 200);
        
        boolean success = performWait(service, body);
        
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("condition", condition);
        result.put("timeout_ms", timeout);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 执行等待
     */
    private boolean performWait(PhantomAccessibilityService service, JSONObject waitConfig) {
        try {
            JSONObject condition = waitConfig.getJSONObject("condition");
            long timeout = waitConfig.optLong("timeout_ms", 5000);
            long interval = waitConfig.optLong("interval_ms", 200);
            
            String type = condition.getString("type");
            
            switch (type) {
                case "text":
                    String text = condition.getString("text");
                    return HttpServerEngine.waitForCondition(() -> {
                        List<AccessibilityNodeInfo> nodes = service.findNodesByText(text);
                        boolean found = nodes != null && !nodes.isEmpty();
                        if (nodes != null) {
                            for (AccessibilityNodeInfo node : nodes) {
                                node.recycle();
                            }
                        }
                        return found;
                    }, timeout, interval);
                    
                case "id":
                    String id = condition.getString("id");
                    return HttpServerEngine.waitForCondition(() -> {
                        List<AccessibilityNodeInfo> nodes = service.findNodesById(id);
                        boolean found = nodes != null && !nodes.isEmpty();
                        if (nodes != null) {
                            for (AccessibilityNodeInfo node : nodes) {
                                node.recycle();
                            }
                        }
                        return found;
                    }, timeout, interval);
                    
                case "gone":
                    String goneText = condition.getString("text");
                    return HttpServerEngine.waitForCondition(() -> {
                        List<AccessibilityNodeInfo> nodes = service.findNodesByText(goneText);
                        boolean gone = nodes == null || nodes.isEmpty();
                        if (nodes != null) {
                            for (AccessibilityNodeInfo node : nodes) {
                                node.recycle();
                            }
                        }
                        return gone;
                    }, timeout, interval);
                    
                default:
                    return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "等待执行失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 节点操作
     */
    private NanoHTTPD.Response handleAction(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String strategy = body.optString("strategy", "text");
        String target = body.optString("target", "");
        String action = body.optString("action", "click");
        
        List<AccessibilityNodeInfo> nodes = null;
        
        switch (strategy) {
            case "text":
                nodes = service.findNodesByText(target);
                break;
            case "id":
                nodes = service.findNodesById(target);
                break;
        }
        
        if (nodes == null || nodes.isEmpty()) {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.NOT_FOUND, 
                "未找到节点: " + target);
        }
        
        AccessibilityNodeInfo targetNode = nodes.get(0);
        boolean success = false;
        
        switch (action) {
            case "click":
                success = service.clickNode(targetNode);
                break;
            case "long_click":
                // 长按
                break;
            case "scroll_forward":
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                break;
            case "scroll_backward":
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                break;
        }
        
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
        
        // 检查等待条件
        JSONObject wait = body.optJSONObject("wait");
        if (wait != null && success) {
            success = performWait(service, wait);
        }
        
        return HttpServerEngine.jsonSuccess("success", success);
    }
    
    /**
     * 节点转 JSON
     */
    private JSONObject nodeToJson(AccessibilityNodeInfo node) {
        JSONObject json = new JSONObject();
        try {
            json.put("text", node.getText() != null ? node.getText().toString() : "");
            json.put("contentDescription", node.getContentDescription() != null ? 
                node.getContentDescription().toString() : "");
            json.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
            json.put("viewId", node.getViewIdResourceName());
            json.put("clickable", node.isClickable());
            json.put("scrollable", node.isScrollable());
            json.put("enabled", node.isEnabled());
            json.put("checkable", node.isCheckable());
            json.put("checked", node.isChecked());
            
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            json.put("bounds", bounds.toString());
            
        } catch (Exception e) {}
        return json;
    }
    
    private JSONObject parseJsonBody(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");
        return new JSONObject(body != null ? body : "{}");
    }
    
    private Map<String, String> parseQueryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : session.getParameters().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                params.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return params;
    }
}
