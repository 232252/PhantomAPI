package com.phantom.api.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.phantom.api.api.SysApiHandler;
import com.phantom.api.api.UiApiHandler;
import com.phantom.api.api.WebApiHandler;
import com.phantom.api.api.NetApiHandler;
import com.phantom.api.api.ChromeCdpHandler;
import com.phantom.api.api.ScopeHelperHandler;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP 服务器引擎
 * 
 * 特性：
 * 1. 异步处理请求，不阻塞 NanoHTTPD 线程
 * 2. 显式等待支持
 * 3. 请求节流/防抖
 */
public class HttpServerEngine extends NanoHTTPD {
    private static final String TAG = "HttpServerEngine";
    
    private final Context context;
    private final Map<String, ApiHandler> handlers = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 节流控制
    private final Map<String, AtomicLong> lastRequestTime = new HashMap<>();
    private static final long THROTTLE_MS = 100; // 100ms 节流
    
    // 启动时间
    private static final long startTime = System.currentTimeMillis();
    
    public HttpServerEngine(Context context, int port) {
        super(port);
        this.context = context;
        registerHandlers();
    }
    
    private void registerHandlers() {
        handlers.put("/api/ping", this::handlePing);
        handlers.put("/api/sys", new SysApiHandler(context));
        handlers.put("/api/ui", new UiApiHandler());
        handlers.put("/api/web", new WebApiHandler(context));
        handlers.put("/api/net", new NetApiHandler(context));
        handlers.put("/api/cdp", new ChromeCdpHandler());
        handlers.put("/api/scope", new ScopeHelperHandler(context));
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().toString();
        
        Log.d(TAG, "收到请求: " + method + " " + uri);
        
        // CORS 预检
        if (Method.OPTIONS.equals(session.getMethod())) {
            return newCorsResponse(Response.Status.OK, "");
        }
        
        // 节流检查（对高频操作）
        if (uri.contains("/tap") || uri.contains("/swipe")) {
            String key = uri + "_" + getClientIp(session);
            AtomicLong lastTime = lastRequestTime.computeIfAbsent(key, k -> new AtomicLong(0));
            long now = System.currentTimeMillis();
            long elapsed = now - lastTime.get();
            
            if (elapsed < THROTTLE_MS) {
                Log.w(TAG, "请求节流: " + uri);
                return jsonError(Response.Status.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
            }
            lastTime.set(now);
        }
        
        try {
            String path = uri.split("\\?")[0];
            
            for (Map.Entry<String, ApiHandler> entry : handlers.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    // 异步处理
                    return entry.getValue().handle(session);
                }
            }
            
            return jsonError(Response.Status.NOT_FOUND, "Not found: " + uri);
            
        } catch (Exception e) {
            Log.e(TAG, "处理请求失败", e);
            return jsonError(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
    }
    
    /**
     * 获取客户端 IP
     */
    private String getClientIp(IHTTPSession session) {
        try {
            return session.getRemoteIpAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Ping 处理
     */
    private Response handlePing(IHTTPSession session) throws Exception {
        JSONObject result = new JSONObject();
        result.put("status", "pong");
        result.put("timestamp", System.currentTimeMillis());
        result.put("uptime", getUptime());
        return jsonSuccess(result);
    }
    
    /**
     * 获取运行时间
     */
    private long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
    
    // 静态工具方法
    public static Response jsonSuccess(JSONObject data) {
        Response response = newFixedLengthResponse(Response.Status.OK, "application/json", data.toString());
        addCorsHeaders(response);
        return response;
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
        Response response = newFixedLengthResponse(status, "application/json", error.toString());
        addCorsHeaders(response);
        return response;
    }
    
    private static void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    private Response newCorsResponse(Response.Status status, String content) {
        Response response = newFixedLengthResponse(status, "application/json", content);
        addCorsHeaders(response);
        return response;
    }
    
    /**
     * 等待条件满足
     */
    public static boolean waitForCondition(ConditionChecker checker, long timeoutMs, long intervalMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                if (checker.check()) {
                    return true;
                }
                Thread.sleep(intervalMs);
            } catch (Exception e) {
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 条件检查器接口
     */
    public interface ConditionChecker {
        boolean check() throws Exception;
    }
    
    /**
     * API 处理器接口
     */
    public interface ApiHandler {
        Response handle(IHTTPSession session) throws Exception;
    }
    
    public void startServer() throws IOException {
        start(SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "HTTP 服务已启动，端口: " + getListeningPort());
    }
    
    public void stopServer() {
        stop();
        executor.shutdown();
        Log.i(TAG, "HTTP 服务已停止");
    }
}
