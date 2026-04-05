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
import com.phantom.api.api.AdvancedApiHandler;
import com.phantom.api.api.SelectorApiHandler;
import com.phantom.api.api.AppApiHandler;
import com.phantom.api.api.WebViewApiHandler;
import com.phantom.api.api.NotifyApiHandler;
import com.phantom.api.api.FileApiHandler;
import com.phantom.api.api.BrowserApiHandler;
import com.phantom.api.api.GestureApiHandler;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP 服务器引擎 v1.5.0
 * 
 * 特性：
 * 1. 只读操作并发执行（查询、获取信息等）
 * 2. 写操作串行执行（点击、滑动、启动应用等）
 * 3. 自动区分操作类型
 */
public class HttpServerEngine extends NanoHTTPD {
    private static final String TAG = "HttpServerEngine";
    
    private final Context context;
    private final Map<String, ApiHandler> handlers = new HashMap<>();
    private final ExecutorService readExecutor = Executors.newCachedThreadPool();
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 启动时间
    private static final long startTime = System.currentTimeMillis();
    
    // 只读操作集合（可以并发执行）
    private static final Set<String> READ_ONLY_PREFIXES = new HashSet<String>() {{
        add("/api/ping");
        add("/api/sys/");
        add("/api/ui/tree");
        add("/api/ui/find");
        add("/api/web/detect");
        add("/api/web/dom");
        add("/api/web/find");
        add("/api/net/");
        add("/api/app/list");
        add("/api/app/current");
        add("/api/app/info");
        add("/api/app/recent");
        add("/api/clipboard/get");
        add("/api/file/list");
        add("/api/file/exists");
        add("/api/file/read");
        add("/api/notify/list");
        add("/api/selector/query");
        add("/api/selector/exists");
        add("/api/browser/");
        add("/api/webview/dom");
        add("/api/cdp/pages");
    }};
    
    // 写操作关键字（需要串行执行）
    private static final Set<String> WRITE_KEYWORDS = new HashSet<String>() {{
        add("/tap");
        add("/swipe");
        add("/click");
        add("/input");
        add("/back");
        add("/home");
        add("/action");
        add("/launch");
        add("/stop");
        add("/clear");
        add("/write");
        add("/append");
        add("/delete");
        add("/mkdir");
        add("/copy");
        add("/move");
        add("/download");
        add("/inject");
        add("/exec");
        add("/set");
        add("/pinch");
        add("/fling");
        add("/drag");
        add("/path");
        add("/bezier");
        add("/sequence");
        add("/scroll");
        add("/pattern");
        add("/gesture/");
        add("/advanced/");
        add("/media/");
    }};
    
    public HttpServerEngine(Context context, int port) {
        super(port);
        this.context = context;
        registerHandlers();
    }
    
    private void registerHandlers() {
        // 基础 API
        handlers.put("/api/ping", this::handlePing);
        handlers.put("/api/sys", new SysApiHandler(context));
        handlers.put("/api/ui", new UiApiHandler());
        handlers.put("/api/web", new WebApiHandler(context));
        handlers.put("/api/net", new NetApiHandler(context));
        handlers.put("/api/cdp", new ChromeCdpHandler());
        handlers.put("/api/scope", new ScopeHelperHandler(context));
        
        // 高级 API (v1.1.0+)
        handlers.put("/api/advanced", new AdvancedApiHandler());
        handlers.put("/api/selector", new SelectorApiHandler());
        
        // 应用管理 API (v1.3.0+)
        handlers.put("/api/app", new AppApiHandler(context));
        handlers.put("/api/clipboard", new AppApiHandler(context));
        handlers.put("/api/shell", new AppApiHandler(context));
        handlers.put("/api/media", new AppApiHandler(context));
        
        // WebView 注入 API (v1.3.0+)
        handlers.put("/api/webview", new WebViewApiHandler(context));
        
        // 通知监听 API (v1.3.0+)
        handlers.put("/api/notify", new NotifyApiHandler(context));
        
        // 文件操作 API (v1.3.0+)
        handlers.put("/api/file", new FileApiHandler(context));
        
        // 浏览器注入 API (v1.4.0+)
        handlers.put("/api/browser", new BrowserApiHandler(context));
        
        // 高级手势 API (v1.4.0+)
        handlers.put("/api/gesture", new GestureApiHandler(context));
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
        
        try {
            String path = uri.split("\\?")[0];
            
            // 查找处理器
            ApiHandler handler = null;
            for (Map.Entry<String, ApiHandler> entry : handlers.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    handler = entry.getValue();
                    break;
                }
            }
            
            if (handler == null) {
                return jsonError(Response.Status.NOT_FOUND, "Not found: " + uri);
            }
            
            final ApiHandler finalHandler = handler;
            
            // 判断是否为只读操作
            if (isReadOnlyOperation(path)) {
                // 只读操作：直接执行（并发）
                Log.d(TAG, "并发执行（只读）: " + path);
                return finalHandler.handle(session);
            } else {
                // 写操作：串行执行
                Log.d(TAG, "串行执行（写操作）: " + path);
                return executeSequential(finalHandler, session);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "处理请求失败", e);
            return jsonError(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
    }
    
    /**
     * 判断是否为只读操作
     */
    private boolean isReadOnlyOperation(String path) {
        // 检查是否匹配只读前缀
        for (String prefix : READ_ONLY_PREFIXES) {
            if (path.startsWith(prefix) || path.equals(prefix)) {
                // 但要排除写操作关键字
                for (String keyword : WRITE_KEYWORDS) {
                    if (path.contains(keyword)) {
                        return false;
                    }
                }
                return true;
            }
        }
        
        // 检查是否包含写操作关键字
        for (String keyword : WRITE_KEYWORDS) {
            if (path.contains(keyword)) {
                return false;
            }
        }
        
        // 默认认为是只读（安全起见）
        return true;
    }
    
    /**
     * 串行执行写操作
     */
    private Response executeSequential(ApiHandler handler, IHTTPSession session) {
        try {
            // 使用单线程执行器保证顺序
            return writeExecutor.submit(() -> {
                try {
                    return handler.handle(session);
                } catch (Exception e) {
                    Log.e(TAG, "串行执行失败", e);
                    return jsonError(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
                }
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "提交任务失败", e);
            return jsonError(Response.Status.INTERNAL_ERROR, "Timeout or error: " + e.getMessage());
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
        return jsonSuccess(new JSONObject().put(key, value));
    }
    
    public static Response jsonError(Response.IStatus status, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", message);
            error.put("code", status.getRequestStatus());
            Response response = newFixedLengthResponse(status, "application/json", error.toString());
            addCorsHeaders(response);
            return response;
        } catch (Exception e) {
            return newFixedLengthResponse(status, "application/json", "{\"error\":\"" + message + "\"}");
        }
    }
    
    private Response newCorsResponse(Response.IStatus status, String body) {
        Response response = newFixedLengthResponse(status, "application/json", body);
        addCorsHeaders(response);
        return response;
    }
    
    private static void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }
    
    @Override
    public void stop() {
        super.stop();
        readExecutor.shutdownNow();
        writeExecutor.shutdownNow();
        Log.i(TAG, "HTTP 服务器已停止");
    }
    
    /**
     * API 处理器接口
     */
    public interface ApiHandler {
        Response handle(IHTTPSession session) throws Exception;
    }
}
