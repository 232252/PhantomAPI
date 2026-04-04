package com.phantom.api.api;

import android.content.Context;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.engine.WebViewEngine;
import com.phantom.api.util.AppUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * WebView 域 API 处理器
 * 
 * 基于 JS 注入 + 文件 IPC 架构
 * 
 * 端点：
 * /api/web/detect - 检测 WebView 调试可用性
 * /api/web/sockets - 列出可用 Socket
 * /api/web/dom - 获取 DOM 树
 * /api/web/execute - 执行 JavaScript
 * /api/web/click - WebView 内点击
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
        
        if (uri.equals("/api/web/detect")) {
            return handleDetect();
        } else if (uri.equals("/api/web/sockets")) {
            return handleListSockets();
        } else if (uri.equals("/api/web/dom")) {
            return handleGetDom(session);
        } else if (uri.equals("/api/web/execute")) {
            return handleExecute(session);
        } else if (uri.equals("/api/web/click")) {
            return handleClick(session);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    /**
     * 检测 WebView 调试可用性
     */
    private NanoHTTPD.Response handleDetect() throws Exception {
        JSONObject result = new JSONObject();
        
        List<String> sockets = WebViewEngine.detectDevToolsSockets();
        String foreground = AppUtils.getForegroundPackage(context);
        
        result.put("available", !sockets.isEmpty());
        result.put("socketCount", sockets.size());
        result.put("method", "js_injection");
        result.put("status", "ready");
        result.put("foregroundApp", foreground);
        
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
        List<String> sockets = WebViewEngine.detectDevToolsSockets();
        
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
        if (socket.contains("chrome_devtools")) return "chrome";
        if (socket.contains("webview")) return "webview";
        if (socket.contains("stetho")) return "stetho";
        return "unknown";
    }
    
    /**
     * 获取 DOM 树
     */
    private NanoHTTPD.Response handleGetDom(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String packageName = params.get("package");
        
        if (packageName == null || packageName.isEmpty()) {
            packageName = AppUtils.getForegroundPackage(context);
        }
        
        JSONObject dom = WebViewEngine.getDOM(context, packageName);
        
        JSONObject result = new JSONObject();
        result.put("package", packageName);
        result.put("dom", dom);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 执行 JavaScript
     */
    private NanoHTTPD.Response handleExecute(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String script = body.optString("script", "");
        String packageName = body.optString("package", "");
        
        if (script.isEmpty()) {
            return HttpServerEngine.jsonError(
                NanoHTTPD.Response.Status.BAD_REQUEST, 
                "需要 script 参数");
        }
        
        if (packageName.isEmpty()) {
            packageName = AppUtils.getForegroundPackage(context);
        }
        
        JSONObject result = WebViewEngine.evaluateJavaScript(context, packageName, script);
        
        JSONObject response = new JSONObject();
        response.put("executed", true);
        response.put("package", packageName);
        response.put("result", result);
        
        return HttpServerEngine.jsonSuccess(response);
    }
    
    /**
     * WebView 内点击
     */
    private NanoHTTPD.Response handleClick(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String packageName = body.optString("package", "");
        String selector = body.optString("selector", "");
        int x = body.optInt("x", 0);
        int y = body.optInt("y", 0);
        
        if (packageName.isEmpty()) {
            packageName = AppUtils.getForegroundPackage(context);
        }
        
        boolean success = WebViewEngine.clickAt(context, packageName, selector, x, y);
        
        JSONObject result = new JSONObject();
        result.put("clicked", success);
        result.put("package", packageName);
        if (!selector.isEmpty()) {
            result.put("selector", selector);
        } else {
            result.put("x", x);
            result.put("y", y);
        }
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 解析查询参数
     */
    private Map<String, String> parseQueryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> params = new HashMap<>();
        try {
            Map<String, List<String>> query = session.getParameters();
            for (Map.Entry<String, List<String>> entry : query.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    params.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析参数失败: " + e.getMessage());
        }
        return params;
    }
    
    /**
     * 解析 JSON 请求体
     */
    private JSONObject parseJsonBody(NanoHTTPD.IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body != null && !body.isEmpty()) {
                return new JSONObject(body);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 JSON 失败: " + e.getMessage());
        }
        return new JSONObject();
    }
}
