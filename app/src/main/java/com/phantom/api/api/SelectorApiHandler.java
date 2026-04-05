package com.phantom.api.api;

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * 选择器 API 处理器 - 借鉴自 GKD
 * 
 * 端点：
 * /api/selector/query - 选择器查询
 * /api/selector/click - 选择器点击
 * /api/selector/exists - 选择器存在检查
 * 
 * 选择器语法：
 * {"text": "登录", "clickable": true} // 文本匹配且可点击
 * {"textContains": "签到", "limit": 5} // 文本包含
 * {"id": "btn_login"} // ID匹配
 * {"classNameContains": "Button"} // 类名包含
 */
public class SelectorApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Selector";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
        if (a11yService == null) {
            return jsonError(503, "无障碍服务未启用");
        }
        
        switch (uri) {
            case "/api/selector/query":
                return handleQuery(a11yService, session);
            case "/api/selector/click":
                return handleClick(a11yService, session);
            case "/api/selector/exists":
                return handleExists(a11yService, session);
            default:
                return jsonError(404, "Unknown: " + uri);
        }
    }
    
    private NanoHTTPD.Response handleQuery(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        JSONObject selector = body.optJSONObject("selector");
        if (selector == null) selector = body;
        
        boolean refresh = body.optBoolean("refresh", true);
        int limit = body.optInt("limit", 100);
        
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode == null) return jsonError(500, "无法获取根节点");
        
        if (refresh) refreshNodeTree(rootNode);
        
        List<MatchResult> matches = new ArrayList<>();
        findNodes(rootNode, selector, 0, matches, limit);
        
        JSONArray nodes = new JSONArray();
        for (MatchResult m : matches) nodes.put(nodeToJson(m.node, m.bounds));
        
        return jsonOk(new JSONObject().put("success", true).put("count", matches.size()).put("nodes", nodes));
    }
    
    private NanoHTTPD.Response handleClick(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        JSONObject selector = body.optJSONObject("selector");
        if (selector == null) selector = body;
        
        boolean refresh = body.optBoolean("refresh", true);
        int index = body.optInt("index", 0);
        
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode == null) return jsonError(500, "无法获取根节点");
        
        if (refresh) refreshNodeTree(rootNode);
        
        List<MatchResult> matches = new ArrayList<>();
        findNodes(rootNode, selector, 0, matches, index + 1);
        
        if (index >= matches.size()) {
            return jsonOk(new JSONObject().put("success", false).put("reason", "not_found"));
        }
        
        MatchResult match = matches.get(index);
        AccessibilityNodeInfo node = match.node;
        node.refresh();
        
        boolean clicked = false;
        if (node.isClickable()) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        
        // 尝试父节点
        if (!clicked) {
            AccessibilityNodeInfo parent = node.getParent();
            int tries = 5;
            while (parent != null && !clicked && tries-- > 0) {
                if (parent.isClickable()) {
                    clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (!clicked) {
                    AccessibilityNodeInfo p = parent.getParent();
                    parent.recycle();
                    parent = p;
                }
            }
            if (parent != null) parent.recycle();
        }
        
        // 坐标点击
        if (!clicked && match.bounds.centerX() >= 0) {
            clicked = service.clickAtPosition(match.bounds.centerX(), match.bounds.centerY());
        }
        
        return jsonOk(new JSONObject().put("success", clicked).put("text", node.getText() != null ? node.getText().toString() : ""));
    }
    
    private NanoHTTPD.Response handleExists(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        JSONObject selector = body.optJSONObject("selector");
        if (selector == null) selector = body;
        
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode == null) return jsonOk(new JSONObject().put("exists", false));
        
        refreshNodeTree(rootNode);
        
        List<MatchResult> matches = new ArrayList<>();
        findNodes(rootNode, selector, 0, matches, 1);
        
        return jsonOk(new JSONObject().put("exists", !matches.isEmpty()).put("count", matches.size()));
    }
    
    // 选择器匹配
    private void findNodes(AccessibilityNodeInfo node, JSONObject sel, int depth, List<MatchResult> results, int limit) {
        if (node == null || results.size() >= limit) return;
        
        try { node.refresh(); } catch (Exception e) {}
        
        if (matchesSelector(node, sel, depth)) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            results.add(new MatchResult(node, bounds, depth));
        }
        
        for (int i = 0; i < node.getChildCount() && results.size() < limit; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) findNodes(child, sel, depth + 1, results, limit);
        }
    }
    
    private boolean matchesSelector(AccessibilityNodeInfo node, JSONObject sel, int depth) {
        if (node == null) return false;
        
        String text = node.getText() != null ? node.getText().toString() : "";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        
        if (sel.has("text") && !matchesPattern(text, sel.optString("text"))) return false;
        if (sel.has("textContains") && !text.contains(sel.optString("textContains"))) return false;
        if (sel.has("textStartsWith") && !text.startsWith(sel.optString("textStartsWith"))) return false;
        if (sel.has("textEndsWith") && !text.endsWith(sel.optString("textEndsWith"))) return false;
        if (sel.has("id") && !id.contains(sel.optString("id"))) return false;
        if (sel.has("idContains") && !id.contains(sel.optString("idContains"))) return false;
        if (sel.has("className") && !cls.contains(sel.optString("className"))) return false;
        if (sel.has("classNameContains") && !cls.contains(sel.optString("classNameContains"))) return false;
        
        if (sel.has("clickable") && node.isClickable() != sel.optBoolean("clickable")) return false;
        if (sel.has("scrollable") && node.isScrollable() != sel.optBoolean("scrollable")) return false;
        if (sel.has("enabled") && node.isEnabled() != sel.optBoolean("enabled")) return false;
        if (sel.has("visible") && sel.optBoolean("visible") && !node.isVisibleToUser()) return false;
        
        if (sel.has("depth") && depth != sel.optInt("depth")) return false;
        if (sel.has("depthMin") && depth < sel.optInt("depthMin")) return false;
        if (sel.has("depthMax") && depth > sel.optInt("depthMax")) return false;
        
        if (sel.optBoolean("validBounds", false)) {
            Rect b = new Rect();
            node.getBoundsInScreen(b);
            if (b.left < 0 || b.top < 0) return false;
        }
        
        return true;
    }
    
    private boolean matchesPattern(String text, String pattern) {
        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            try {
                return Pattern.compile(pattern.substring(1, pattern.length() - 1)).matcher(text).find();
            } catch (Exception e) { return text.equals(pattern); }
        }
        return text.equals(pattern);
    }
    
    private void refreshNodeTree(AccessibilityNodeInfo node) {
        if (node == null) return;
        try { node.refresh(); } catch (Exception e) {}
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) refreshNodeTree(child);
        }
    }
    
    private JSONObject nodeToJson(AccessibilityNodeInfo node, Rect bounds) {
        JSONObject json = new JSONObject();
        try {
            json.put("text", node.getText() != null ? node.getText().toString() : "");
            json.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
            json.put("id", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
            json.put("clickable", node.isClickable());
            json.put("visible", node.isVisibleToUser());
            json.put("bounds", boundsToJson(bounds));
        } catch (Exception e) {}
        return json;
    }
    
    private JSONObject boundsToJson(Rect bounds) {
        JSONObject json = new JSONObject();
        try {
            json.put("left", bounds.left);
            json.put("top", bounds.top);
            json.put("right", bounds.right);
            json.put("bottom", bounds.bottom);
            json.put("centerX", bounds.centerX());
            json.put("centerY", bounds.centerY());
            json.put("valid", bounds.left >= 0 && bounds.top >= 0);
        } catch (Exception e) {}
        return json;
    }
    
    private JSONObject parseBody(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = session.getQueryParameterString();
        if (body == null || body.isEmpty()) {
            java.util.Map<String, String> files = new java.util.HashMap<>();
            session.parseBody(files);
            body = files.get("postData");
        }
        return body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
    }
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", data.toString());
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject obj = new JSONObject();
        try { obj.put("success", false).put("error", msg).put("code", code); } catch (Exception e) {}
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", obj.toString());
    }
    
    private static class MatchResult {
        AccessibilityNodeInfo node;
        Rect bounds;
        int depth;
        MatchResult(AccessibilityNodeInfo n, Rect b, int d) { node = n; bounds = b; depth = d; }
    }
}