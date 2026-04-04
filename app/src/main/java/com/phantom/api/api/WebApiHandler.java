package com.phantom.api.api;

import android.content.Context;
import android.util.Log;

import com.phantom.api.engine.CdpBridge;
import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * WebView 域 API 处理器
 * 
 * 端点：
 * /api/web/detect - 检测 WebView 调试可用性
 * /api/web/sockets - 列出可用 DevTools Socket
 * /api/web/dom - 获取 DOM 树
 * /api/web/execute - 执行 JavaScript
 * /api/web/click - CDP 级别点击
 * /api/web/cdp - 原始 CDP 命令
 */
public class WebApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "WebApiHandler";
    private final Context context;
    
    public WebApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.equals("/api/web/debug") || uri.equals("/api/web/detect")) {
            return handleDetect();
        } else if (uri.equals("/api/web/sockets")) {
            return handleListSockets();
        } else if (uri.equals("/api/web/dom")) {
            return handleGetDom(session);
        } else if (uri.equals("/api/web/execute")) {
            return handleExecute(session);
        } else if (uri.equals("/api/web/click")) {
            return handleClick(session);
        } else if (uri.equals("/api/web/cdp")) {
            return handleCdp(session);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    /**
     * 检测 WebView 调试可用性
     */
    private NanoHTTPD.Response handleDetect() throws Exception {
        JSONObject result = new JSONObject();
        
        List<String> sockets = CdpBridge.detectDevToolsSockets();
        result.put("available", !sockets.isEmpty());
        result.put("socketCount", sockets.size());
        result.put("method", "cdp");
        result.put("status", sockets.isEmpty() ? "no_devtools" : "ready");
        
        JSONArray socketsArray = new JSONArray();
        for (String socket : sockets) {
            socketsArray.put(socket);
        }
        result.put("sockets", socketsArray);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 列出可用 Socket
     */
    private NanoHTTPD.Response handleListSockets() throws Exception {
        List<String> sockets = CdpBridge.detectDevToolsSockets();
        
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        
        for (String socket : sockets) {
            JSONObject socketInfo = new JSONObject();
            socketInfo.put("name", socket);
            socketInfo.put("type", getSocketType(socket));
            array.put(socketInfo);
        }
        
        result.put("sockets", array);
        result.put("count", sockets.size());
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取 Socket 类型
     */
    private String getSocketType(String socket) {
        if (socket.contains("chrome_devtools")) {
            return "chrome";
        } else if (socket.contains("webview")) {
            return "webview";
        } else if (socket.contains("stetho")) {
            return "stetho";
        }
        return "unknown";
    }
    
    /**
     * 获取 DOM 树
     */
    private NanoHTTPD.Response handleGetDom(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String socketName = params.get("socket");
        
        if (socketName == null || socketName.isEmpty()) {
            // 自动选择第一个可用 socket
            List<String> sockets = CdpBridge.detectDevToolsSockets();
            if (sockets.isEmpty()) {
                return HttpServerEngine.jsonError(
                    NanoHTTPD.Response.Status.NOT_FOUND, 
                    "没有可用的 DevTools Socket");
            }
            socketName = sockets.get(0);
        }
        
        JSONObject dom = CdpBridge.getDOM(socketName);
        if (dom == null) {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "获取 DOM 失败");
        }
        
        JSONObject result = new JSONObject();
        result.put("socket", socketName);
        result.put("dom", dom);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 执行 JavaScript
     */
    private NanoHTTPD.Response handleExecute(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String script = body.optString("script", "");
        String socketName = body.optString("socket", "");
        
        if (script.isEmpty()) {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.BAD_REQUEST, 
                "需要 script 参数");
        }
        
        if (socketName.isEmpty()) {
            List<String> sockets = CdpBridge.detectDevToolsSockets();
            if (sockets.isEmpty()) {
                return HttpServerEngine.jsonError(
                    NanoHTTPD.Response.Status.NOT_FOUND, 
                    "没有可用的 DevTools Socket");
            }
            socketName = sockets.get(0);
        }
        
        JSONObject result = CdpBridge.evaluateJavaScript(socketName, script);
        
        JSONObject response = new JSONObject();
        response.put("executed", true);
        response.put("socket", socketName);
        response.put("result", result);
        
        return HttpServerEngine.jsonSuccess(response);
    }
    
    /**
     * CDP 级别点击
     */
    private NanoHTTPD.Response handleClick(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        int x = body.getInt("x");
        int y = body.getInt("y");
        String socketName = body.optString("socket", "");
        
        if (socketName.isEmpty()) {
            List<String> sockets = CdpBridge.detectDevToolsSockets();
            if (sockets.isEmpty()) {
                return HttpServerEngine.jsonError(
                    NanoHTTPD.Response.Status.NOT_FOUND, 
                    "没有可用的 DevTools Socket");
            }
            socketName = sockets.get(0);
        }
        
        boolean success = CdpBridge.clickAt(socketName, x, y);
        
        JSONObject response = new JSONObject();
        response.put("clicked", success);
        response.put("x", x);
        response.put("y", y);
        response.put("socket", socketName);
        
        return HttpServerEngine.jsonSuccess(response);
    }
    
    /**
     * 原始 CDP 命令
     */
    private NanoHTTPD.Response handleCdp(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String method = body.getString("method");
        JSONObject params = body.optJSONObject("params");
        String socketName = body.optString("socket", "");
        
        if (socketName.isEmpty()) {
            List<String> sockets = CdpBridge.detectDevToolsSockets();
            if (sockets.isEmpty()) {
                return HttpServerEngine.jsonError(
                    NanoHTTPD.Response.Status.NOT_FOUND, 
                    "没有可用的 DevTools Socket");
            }
            socketName = sockets.get(0);
        }
        
        CdpBridge.CdpConnection conn = CdpBridge.connect(socketName);
        if (conn == null) {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "连接 DevTools 失败");
        }
        
        JSONObject result = conn.sendCommand(method, params);
        
        JSONObject response = new JSONObject();
        response.put("method", method);
        response.put("status", "executed");
        response.put("socket", socketName);
        response.put("result", result);
        
        return HttpServerEngine.jsonSuccess(response);
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
