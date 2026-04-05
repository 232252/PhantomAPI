package com.phantom.api.api;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 原生 UI 域 API 处理器 v2.0
 * 
 * 端点：
 * /api/ui/tree - 获取 UI 树
 * /api/ui/find - 查找节点（支持 clickable/upward 参数）
 * /api/ui/tap - 点击坐标或文本
 * /api/ui/swipe - 滑动
 * /api/ui/back - 返回键
 * /api/ui/home - 回到桌面
 * /api/ui/wait - 显式等待（支持 text_appear/text_disappear 模式）
 * /api/ui/action - 节点操作（支持 click/long_click/scroll 等）
 * /api/ui/safe_back - 安全返回（不退出 App）
 */
public class UiApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-UI";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        String method = session.getMethod().toString();
        
        PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
        if (a11yService == null) {
            return jsonError(503, "无障碍服务未启用");
        }
        
        if (uri.equals("/api/ui/tree")) {
            return handleTree(a11yService);
        } else if (uri.equals("/api/ui/find")) {
            return handleFind(a11yService, session);
        } else if (uri.equals("/api/ui/tap")) {
            return handleTap(a11yService, session);
        } else if (uri.equals("/api/ui/swipe")) {
            return handleSwipe(a11yService, session);
        } else if (uri.equals("/api/ui/back")) {
            return handleBack(a11yService);
        } else if (uri.equals("/api/ui/home")) {
            return handleHome(a11yService);
        } else if (uri.equals("/api/ui/wait")) {
            return handleWait(a11yService, session);
        } else if (uri.equals("/api/ui/action")) {
            return handleAction(a11yService, session);
        } else if (uri.equals("/api/ui/safe_back")) {
            return handleSafeBack(a11yService, session);
        }
        
        return jsonError(404, "Unknown: " + uri);
    }
    
    // ==================== 辅助方法 ====================
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8",
                data.toString()
        );
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("success", false);
            obj.put("error", msg);
        } catch (Exception e) {}
        
        NanoHTTPD.Response.Status status = code == 404 ? 
            NanoHTTPD.Response.Status.NOT_FOUND : 
            (code == 503 ? NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE : 
                NanoHTTPD.Response.Status.INTERNAL_ERROR);
        
        return NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString());
    }
    
    private Map<String, String> parseQueryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> params = new HashMap<>();
        try {
            String query = session.getQueryParameterString();
            if (query != null && !query.isEmpty()) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        params.put(java.net.URLDecoder.decode(kv[0], "UTF-8"), 
                                   java.net.URLDecoder.decode(kv[1], "UTF-8"));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析查询参数失败: " + e.getMessage());
        }
        return params;
    }
    
    private JSONObject parseJsonBody(NanoHTTPD.IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body != null && !body.isEmpty()) {
                return new JSONObject(body);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 JSON body 失败: " + e.getMessage());
        }
        return new JSONObject();
    }
    
    // ==================== 节点工具方法 ====================
    
    /**
     * 刷新节点信息（借鉴自 AutoJs6）
     * 滑动后需要刷新才能获取正确的坐标
     */
    private void refreshNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            node.refresh();
        } catch (Exception e) {
            Log.w(TAG, "刷新节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 刷新整个节点树
     */
    private void refreshNodeTree(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            node.refresh();
        } catch (Exception e) {}
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                refreshNodeTree(child);
            }
        }
    }
    
    /**
     * 检查坐标是否有效
     * 负坐标表示元素在屏幕外
     */
    private boolean isValidBounds(Rect rect, int screenWidth, int screenHeight) {
        if (rect == null) return false;
        // 允许部分在屏幕外，但中心点必须在屏幕内
        int centerX = rect.centerX();
        int centerY = rect.centerY();
        return centerX >= 0 && centerY >= 0 && 
               centerX <= screenWidth && centerY <= screenHeight;
    }
    
    /**
     * 节点转 JSON（简化版，用于 find 返回）
     * 修复：刷新节点、验证坐标、添加可见性检测
     */
    private JSONObject nodeToJson(AccessibilityNodeInfo node, String matchedText, int upward) {
        JSONObject json = new JSONObject();
        try {
            // 刷新节点获取最新坐标
            refreshNode(node);
            
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            
            // 检查可见性
            boolean visibleToUser = node.isVisibleToUser();
            
            json.put("text", node.getText() != null ? node.getText().toString() : "");
            json.put("matchedText", matchedText);
            json.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
            json.put("viewIdResourceName", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
            json.put("clickable", node.isClickable());
            json.put("enabled", node.isEnabled());
            json.put("visible", visibleToUser);
            json.put("upward", upward);
            
            // 坐标有效性标记
            boolean validCoords = rect.left >= 0 && rect.top >= 0;
            
            JSONObject bounds = new JSONObject();
            bounds.put("left", rect.left);
            bounds.put("top", rect.top);
            bounds.put("right", rect.right);
            bounds.put("bottom", rect.bottom);
            bounds.put("centerX", rect.centerX());
            bounds.put("centerY", rect.centerY());
            bounds.put("width", rect.width());
            bounds.put("height", rect.height());
            bounds.put("valid", validCoords);
            json.put("bounds", bounds);
            
            // 添加警告信息
            if (!validCoords) {
                json.put("warning", "坐标可能无效（元素在屏幕外）");
            }
            if (!visibleToUser) {
                json.put("warning", "元素不可见");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "节点转 JSON 失败: " + e.getMessage());
        }
        return json;
    }
    
    /**
     * 向上查找可点击的父节点
     */
    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node, int maxUpward) {
        AccessibilityNodeInfo current = node.getParent();
        int tries = maxUpward;
        while (current != null && tries-- > 0) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null;
    }
    
    /**
     * 获取前台包名
     */
    private String getForegroundPackage() {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys activity activities | grep mResumedActivity");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
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
     * 检查文本是否存在于节点树中
     */
    private boolean checkTextExists(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        
        CharSequence cs = node.getText();
        if (cs != null && cs.toString().contains(text)) return true;
        
        cs = node.getContentDescription();
        if (cs != null && cs.toString().contains(text)) return true;
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (checkTextExists(child, text)) {
                if (child != null) child.recycle();
                return true;
            }
        }
        return false;
    }
    
    /**
     * 查找第一个文本匹配的节点
     */
    private AccessibilityNodeInfo findFirstTextNode(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        
        CharSequence cs = node.getText();
        if (cs != null && cs.toString().contains(text)) {
            return node;
        }
        
        cs = node.getContentDescription();
        if (cs != null && cs.toString().contains(text)) {
            return node;
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findFirstTextNode(child, text);
            if (found != null) return found;
        }
        return null;
    }
    
    // ==================== 各端点实现 ====================
    
    /**
     * GET /api/ui/tree
     * 获取完整的 UI 树
     */
    private NanoHTTPD.Response handleTree(PhantomAccessibilityService service) throws Exception {
        String treeJson = service.getNodeTreeJson();
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("tree", new JSONObject(treeJson));
        return jsonOk(result);
    }
    
    /**
     * GET /api/ui/find?text=xxx&clickable=true&upward=3&validBounds=true
     * 查找节点，支持可点击性过滤和父节点回溯
     * 新增 validBounds 参数过滤无效坐标（负坐标）
     */
    private NanoHTTPD.Response handleFind(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String text = params.get("text");
        String id = params.get("id");
        boolean requireClickable = "true".equals(params.get("clickable"));
        boolean validBoundsOnly = "true".equals(params.get("validBounds"));
        int upward = params.containsKey("upward") ? Integer.parseInt(params.get("upward")) : 3;
        
        if ((text == null || text.isEmpty()) && (id == null || id.isEmpty())) {
            return jsonError(400, "缺少 text 或 id 参数");
        }
        
        // 刷新根节点获取最新状态
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode != null) {
            refreshNodeTree(rootNode);
            rootNode.recycle();
        }
        
        List<AccessibilityNodeInfo> nodes = null;
        if (text != null && !text.isEmpty()) {
            nodes = service.findNodesByText(text);
        } else {
            nodes = service.findNodesById(id);
        }
        
        JSONArray nodesArray = new JSONArray();
        
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                // 刷新节点
                refreshNode(node);
                
                // 检查坐标有效性
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                boolean validBounds = bounds.centerX() >= 0 && bounds.centerY() >= 0;
                
                // 如果要求有效坐标且坐标无效，跳过
                if (validBoundsOnly && !validBounds) {
                    node.recycle();
                    continue;
                }
                
                AccessibilityNodeInfo resultNode = node;
                String matchedText = node.getText() != null ? node.getText().toString() : "";
                int upwardLevel = 0;
                
                // 如果需要可点击节点，向上查找
                if (requireClickable && !node.isClickable()) {
                    AccessibilityNodeInfo clickableParent = findClickableParent(node, upward);
                    if (clickableParent != null) {
                        // 计算向上层数
                        AccessibilityNodeInfo current = node.getParent();
                        while (current != null && !current.equals(clickableParent)) {
                            upwardLevel++;
                            AccessibilityNodeInfo parent = current.getParent();
                            current.recycle();
                            current = parent;
                        }
                        if (current != null) current.recycle();
                        upwardLevel++;
                        
                        resultNode = clickableParent;
                        // 刷新父节点
                        refreshNode(resultNode);
                    }
                }
                
                JSONObject nodeJson = nodeToJson(resultNode, matchedText, upwardLevel);
                nodeJson.put("originallyClickable", node.isClickable());
                if (requireClickable && resultNode != node) {
                    nodeJson.put("resolvedClickable", true);
                }
                nodesArray.put(nodeJson);
                
                node.recycle();
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("count", nodesArray.length());
        result.put("query", text != null ? text : id);
        result.put("clickable", requireClickable);
        result.put("upward", upward);
        result.put("nodes", nodesArray);
        
        return jsonOk(result);
    }
    
    /**
     * POST /api/ui/tap
     * {"x":100,"y":200} 或 {"text":"签到","clickable":true,"upward":3}
     */
    private NanoHTTPD.Response handleTap(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        int x = -1, y = -1;
        boolean tapped = false;
        String method = "coordinate";
        String text = null;
        
        // 模式1：直接坐标
        if (body.has("x") && body.has("y")) {
            x = body.getInt("x");
            y = body.getInt("y");
            tapped = service.clickAtPosition(x, y);
        }
        // 模式2：按文本查找后点击
        else if (body.has("text")) {
            text = body.getString("text");
            boolean requireClickable = body.optBoolean("clickable", false);
            int upward = body.optInt("upward", 3);
            
            List<AccessibilityNodeInfo> nodes = service.findNodesByText(text);
            if (!nodes.isEmpty()) {
                AccessibilityNodeInfo targetNode = nodes.get(0);
                
                // 如果需要可点击节点
                if (requireClickable && !targetNode.isClickable()) {
                    AccessibilityNodeInfo clickableParent = findClickableParent(targetNode, upward);
                    if (clickableParent != null) {
                        targetNode.recycle();
                        targetNode = clickableParent;
                    }
                }
                
                Rect bounds = new Rect();
                targetNode.getBoundsInScreen(bounds);
                x = bounds.centerX();
                y = bounds.centerY();
                tapped = service.clickAtPosition(x, y);
                method = "text_match";
                
                targetNode.recycle();
            } else {
                JSONObject result = new JSONObject();
                result.put("tapped", false);
                result.put("method", "text_match");
                result.put("text", text);
                result.put("reason", "node_not_found");
                return jsonOk(result);
            }
            
            for (AccessibilityNodeInfo node : nodes) {
                node.recycle();
            }
        } else {
            return jsonError(400, "缺少 x/y 或 text 参数");
        }
        
        JSONObject result = new JSONObject();
        result.put("tapped", tapped);
        result.put("method", method);
        result.put("x", x);
        result.put("y", y);
        if (text != null) {
            result.put("text", text);
        }
        
        return jsonOk(result);
    }
    
    /**
     * POST /api/ui/swipe
     * {"x1":100,"y1":500,"x2":100,"y2":100,"duration":300}
     */
    private NanoHTTPD.Response handleSwipe(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        int startX = body.getInt("startX");
        int startY = body.getInt("startY");
        int endX = body.getInt("endX");
        int endY = body.getInt("endY");
        long duration = body.optLong("duration", 300);
        
        boolean swiped = service.performSwipe(startX, startY, endX, endY, duration);
        
        JSONObject result = new JSONObject();
        result.put("swiped", swiped);
        result.put("from", startX + "," + startY);
        result.put("to", endX + "," + endY);
        result.put("duration", duration);
        
        return jsonOk(result);
    }
    
    /**
     * POST /api/ui/back
     * 返回键
     */
    private NanoHTTPD.Response handleBack(PhantomAccessibilityService service) throws Exception {
        boolean success = service.performBack();
        
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("action", "back");
        
        return jsonOk(result);
    }
    
    /**
     * GET /api/ui/home
     * 回到桌面
     */
    private NanoHTTPD.Response handleHome(PhantomAccessibilityService service) throws Exception {
        try {
            Runtime.getRuntime().exec("input keyevent KEYCODE_HOME");
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("action", "home");
            result.put("message", "已发送 HOME 键事件");
            
            return jsonOk(result);
        } catch (Exception e) {
            return jsonError(500, "HOME 键执行失败: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/ui/wait
     * {"mode":"text_appear","text":"签到成功","timeout_ms":5000,"interval_ms":300}
     * 
     * mode: text_appear（等待文本出现）, text_disappear（等待文本消失）
     */
    private NanoHTTPD.Response handleWait(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        String mode = body.optString("mode", "text_appear");
        String text = body.optString("text", "");
        int timeoutMs = body.optInt("timeout_ms", 5000);
        int intervalMs = body.optInt("interval_ms", 300);
        
        if (text.isEmpty()) {
            return jsonError(400, "缺少 text 参数");
        }
        
        long start = System.currentTimeMillis();
        boolean success = false;
        AccessibilityNodeInfo matchedNode = null;
        
        while (System.currentTimeMillis() - start < timeoutMs) {
            AccessibilityNodeInfo root = service.getRootNode();
            if (root != null) {
                try {
                    boolean textExists = checkTextExists(root, text);
                    
                    if ("text_appear".equals(mode) && textExists) {
                        success = true;
                        matchedNode = findFirstTextNode(root, text);
                        break;
                    }
                    
                    if ("text_disappear".equals(mode) && !textExists) {
                        success = true;
                        break;
                    }
                } finally {
                    root.recycle();
                }
            }
            
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("mode", mode);
        result.put("text", text);
        result.put("elapsed_ms", elapsed);
        result.put("timeout_ms", timeoutMs);
        
        if (success) {
            result.put("reason", "matched");
            if (matchedNode != null) {
                Rect rect = new Rect();
                matchedNode.getBoundsInScreen(rect);
                
                JSONObject nodeObj = new JSONObject();
                nodeObj.put("text", matchedNode.getText() != null ? matchedNode.getText().toString() : "");
                nodeObj.put("className", matchedNode.getClassName() != null ? matchedNode.getClassName().toString() : "");
                nodeObj.put("clickable", matchedNode.isClickable());
                
                JSONObject bounds = new JSONObject();
                bounds.put("left", rect.left);
                bounds.put("top", rect.top);
                bounds.put("right", rect.right);
                bounds.put("bottom", rect.bottom);
                bounds.put("centerX", rect.centerX());
                bounds.put("centerY", rect.centerY());
                nodeObj.put("bounds", bounds);
                
                result.put("node", nodeObj);
            }
        } else {
            result.put("reason", "timeout");
        }
        
        return jsonOk(result);
    }
    
    /**
     * POST /api/ui/action
     * {"action":"click","text":"签到"} 或 {"action":"click","id":"com.xxx:id/btn"}
     * 
     * action: click, long_click, scroll_forward, scroll_backward
     */
    private NanoHTTPD.Response handleAction(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        String action = body.optString("action", "click");
        String text = body.optString("text", "");
        String id = body.optString("id", "");
        int waitMsAfter = body.optInt("wait_ms_after", 0);
        
        // 查找节点
        List<AccessibilityNodeInfo> nodes = null;
        if (!text.isEmpty()) {
            nodes = service.findNodesByText(text);
        } else if (!id.isEmpty()) {
            nodes = service.findNodesById(id);
        } else {
            return jsonError(400, "缺少 text 或 id 参数");
        }
        
        if (nodes == null || nodes.isEmpty()) {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("found", false);
            result.put("reason", "node_not_found");
            result.put("action", action);
            return jsonOk(result);
        }
        
        AccessibilityNodeInfo targetNode = nodes.get(0);
        boolean performed = false;
        
        switch (action) {
            case "click":
                performed = service.clickNode(targetNode);
                break;
            case "long_click":
                // 长按实现
                Rect bounds = new Rect();
                targetNode.getBoundsInScreen(bounds);
                performed = service.performLongClick(bounds.centerX(), bounds.centerY());
                break;
            case "scroll_forward":
                performed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                break;
            case "scroll_backward":
                performed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                break;
            default:
                // 回收节点
                for (AccessibilityNodeInfo node : nodes) {
                    node.recycle();
                }
                JSONObject errResult = new JSONObject();
                errResult.put("success", false);
                errResult.put("reason", "unsupported_action");
                errResult.put("action", action);
                errResult.put("supported_actions", new JSONArray().put("click").put("long_click").put("scroll_forward").put("scroll_backward"));
                return jsonOk(errResult);
        }
        
        // 构建返回节点信息
        Rect rect = new Rect();
        targetNode.getBoundsInScreen(rect);
        
        JSONObject nodeInfo = new JSONObject();
        nodeInfo.put("text", targetNode.getText() != null ? targetNode.getText().toString() : "");
        nodeInfo.put("className", targetNode.getClassName() != null ? targetNode.getClassName().toString() : "");
        nodeInfo.put("clickable", targetNode.isClickable());
        
        JSONObject boundsInfo = new JSONObject();
        boundsInfo.put("left", rect.left);
        boundsInfo.put("top", rect.top);
        boundsInfo.put("right", rect.right);
        boundsInfo.put("bottom", rect.bottom);
        nodeInfo.put("bounds", boundsInfo);
        
        // 回收节点
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
        
        // 等待
        if (waitMsAfter > 0) {
            try {
                Thread.sleep(waitMsAfter);
            } catch (InterruptedException e) {}
        }
        
        JSONObject result = new JSONObject();
        result.put("success", performed);
        result.put("action", action);
        result.put("found", true);
        result.put("performed", performed);
        result.put("node", nodeInfo);
        
        return jsonOk(result);
    }
    
    /**
     * POST /api/ui/safe_back
     * {"max_tries":5,"check_package":"com.xuexi.android","interval_ms":400}
     * 
     * 安全返回：连续按返回键直到页面变化，但不退出 App
     */
    private NanoHTTPD.Response handleSafeBack(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        int maxTries = body.optInt("max_tries", 5);
        String checkPackage = body.optString("check_package", "");
        int intervalMs = body.optInt("interval_ms", 400);
        
        // 如果没有提供检查包名，获取当前前台包名
        if (checkPackage.isEmpty()) {
            checkPackage = getForegroundPackage();
        }
        
        long start = System.currentTimeMillis();
        int tries = 0;
        boolean exited = false;
        String finalPackage = checkPackage;
        
        for (int i = 0; i < maxTries; i++) {
            tries++;
            
            // 执行返回
            service.performBack();
            
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                break;
            }
            
            // 检查当前包名
            String currentPackage = getForegroundPackage();
            
            // 如果包名变了，说明退出了 App
            if (!currentPackage.equals(checkPackage)) {
                exited = true;
                finalPackage = currentPackage;
                break;
            }
            
            // 检查 UI 树是否变化（简化：用节点数判断）
            AccessibilityNodeInfo root = service.getRootNode();
            if (root != null) {
                // 简单判断：如果窗口内容变化，认为返回成功
                // 这里可以扩展为更精确的判断逻辑
                root.recycle();
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        
        JSONObject result = new JSONObject();
        
        if (exited) {
            result.put("success", false);
            result.put("reason", "exited_app");
            result.put("tries", tries);
            result.put("elapsed_ms", elapsed);
            result.put("final_package", finalPackage);
            result.put("expected_package", checkPackage);
        } else {
            result.put("success", true);
            result.put("tries", tries);
            result.put("elapsed_ms", elapsed);
            result.put("final_package", finalPackage);
            result.put("message", "已执行 " + tries + " 次返回，仍在目标 App 内");
        }
        
        return jsonOk(result);
    }
}