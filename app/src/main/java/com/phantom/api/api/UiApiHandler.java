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
        } else if (uri.equals("/api/ui/action")) {
            return handleAction(a11yService, session);
        } else if (uri.equals("/api/ui/tap")) {
            return handleTap(a11yService, session);
        } else if (uri.equals("/api/ui/swipe")) {
            return handleSwipe(a11yService, session);
        } else if (uri.equals("/api/ui/find")) {
            return handleFind(a11yService, session);
        } else if (uri.equals("/api/ui/back")) {
            return handleBack(a11yService);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    private NanoHTTPD.Response handleGetTree(PhantomAccessibilityService service) throws Exception {
        String treeJson = service.getNodeTreeJson();
        return HttpServerEngine.jsonSuccess("tree", new JSONObject(treeJson));
    }
    
    private NanoHTTPD.Response handleAction(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String strategy = body.optString("strategy", "text");
        String target = body.optString("target", "");
        
        List<AccessibilityNodeInfo> nodes = service.findNodesByText(target);
        
        if (nodes == null || nodes.isEmpty()) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "未找到: " + target);
        }
        
        AccessibilityNodeInfo targetNode = nodes.get(0);
        boolean success = service.clickNode(targetNode);
        
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
        
        return HttpServerEngine.jsonSuccess("clicked", success);
    }
    
    private NanoHTTPD.Response handleTap(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int x = body.getInt("x");
        int y = body.getInt("y");
        boolean success = service.clickAtPosition(x, y);
        return HttpServerEngine.jsonSuccess("tapped", success);
    }
    
    private NanoHTTPD.Response handleSwipe(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int startX = body.getInt("startX");
        int startY = body.getInt("startY");
        int endX = body.getInt("endX");
        int endY = body.getInt("endY");
        long duration = body.optLong("duration", 300);
        boolean success = service.performSwipe(startX, startY, endX, endY, duration);
        return HttpServerEngine.jsonSuccess("swiped", success);
    }
    
    private NanoHTTPD.Response handleFind(PhantomAccessibilityService service, 
            NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String text = params.get("text");
        
        if (text == null || text.isEmpty()) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, "需要 text 参数");
        }
        
        List<AccessibilityNodeInfo> nodes = service.findNodesByText(text);
        JSONArray nodesArray = new JSONArray();
        
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                JSONObject nodeJson = new JSONObject();
                nodeJson.put("text", node.getText() != null ? node.getText().toString() : "");
                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);
                nodeJson.put("bounds", bounds.toString());
                nodesArray.put(nodeJson);
                node.recycle();
            }
        }
        
        return HttpServerEngine.jsonSuccess("nodes", nodesArray);
    }
    
    private NanoHTTPD.Response handleBack(PhantomAccessibilityService service) throws Exception {
        boolean success = service.performBack();
        return HttpServerEngine.jsonSuccess("back", success);
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
