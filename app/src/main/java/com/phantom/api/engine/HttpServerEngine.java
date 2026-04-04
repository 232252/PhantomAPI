package com.phantom.api.engine;

import android.content.Context;
import android.util.Log;

import com.phantom.api.api.SysApiHandler;
import com.phantom.api.api.UiApiHandler;
import com.phantom.api.api.WebApiHandler;
import com.phantom.api.api.NetApiHandler;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP 服务器引擎
 * 基于 NanoHTTPD 实现轻量级 RESTful API 服务
 */
public class HttpServerEngine extends NanoHTTPD {
    private static final String TAG = "HttpServerEngine";
    
    private final Context context;
    private final Map<String, ApiHandler> handlers = new HashMap<>();
    
    public HttpServerEngine(Context context, int port) {
        super(port);
        this.context = context;
        registerHandlers();
    }
    
    private void registerHandlers() {
        handlers.put("/api/ping", this::handlePing);
        handlers.put("/api/sys", new SysApiHandler(context));
        handlers.put("/api/ui", new UiApiHandler());
        handlers.put("/api/web", new WebApiHandler());
        handlers.put("/api/net", new NetApiHandler());
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().toString();
        
        Log.d(TAG, "收到请求: " + method + " " + uri);
        
        try {
            if (Method.OPTIONS.equals(session.getMethod())) {
                return newCorsResponse(Response.Status.OK, "");
            }
            
            String path = uri.split("\\?")[0];
            
            for (Map.Entry<String, ApiHandler> entry : handlers.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    return entry.getValue().handle(session);
                }
            }
            
            return jsonError(Response.Status.NOT_FOUND, "Not found: " + uri);
            
        } catch (Exception e) {
            Log.e(TAG, "处理请求失败", e);
            return jsonError(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
    }
    
    private Response handlePing(IHTTPSession session) throws Exception {
        JSONObject result = new JSONObject();
        result.put("status", "pong");
        result.put("timestamp", System.currentTimeMillis());
        return jsonSuccess(result);
    }
    
    public static Response jsonSuccess(JSONObject data) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", data.toString());
    }
    
    public static Response jsonSuccess(String key, Object value) throws Exception {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put(key, value);
        return jsonSuccess(result);
    }
    
    public static Response jsonError(Response.Status status, String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("success", false);
            error.put("error", message);
        } catch (Exception e) {}
        return newFixedLengthResponse(status, "application/json", error.toString());
    }
    
    private Response newCorsResponse(Response.Status status, String content) {
        Response response = newFixedLengthResponse(status, "application/json", content);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }
    
    public interface ApiHandler {
        Response handle(IHTTPSession session) throws Exception;
    }
    
    public void startServer() throws IOException {
        start(SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "HTTP 服务已启动，端口: " + getListeningPort());
    }
}
