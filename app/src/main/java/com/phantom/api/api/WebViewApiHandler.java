package com.phantom.api.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;

/**
 * WebView 注入 API 处理器
 * 借鉴自 AutoJs6 InjectableWebClient, FP-Browser
 * 
 * 端点：
 * /api/webview/inject - 注入 JavaScript 代码
 * /api/webview/eval - 执行 JavaScript 并返回结果
 * /api/webview/dom - 获取 DOM 树
 * /api/webview/click - 在 WebView 中点击元素
 * /api/webview/input - 在 WebView 中输入文本
 * /api/webview/scroll - 滚动 WebView
 * /api/webview/cookies - 获取/设置 Cookie
 * /api/webview/storage - 获取 LocalStorage/SessionStorage
 * /api/webview/wait - 等待元素出现
 */
public class WebViewApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-WebView";
    private final Context context;
    private final Handler mainHandler;
    private WebView currentWebView;
    private final Queue<PendingScript> pendingScripts = new LinkedList<>();
    private final ScriptBridge scriptBridge = new ScriptBridge();
    
    public WebViewApiHandler(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        switch (uri) {
            case "/api/webview/inject": return handleInject(session);
            case "/api/webview/eval": return handleEval(session);
            case "/api/webview/dom": return handleDom(session);
            case "/api/webview/click": return handleClick(session);
            case "/api/webview/input": return handleInput(session);
            case "/api/webview/scroll": return handleScroll(session);
            case "/api/webview/cookies": return handleCookies(session);
            case "/api/webview/storage": return handleStorage(session);
            case "/api/webview/wait": return handleWait(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    public void setWebView(WebView webView) {
        this.currentWebView = webView;
        setupWebView(webView);
        while (!pendingScripts.isEmpty()) {
            PendingScript pending = pendingScripts.poll();
            injectScript(webView, pending.script, pending.callback);
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.addJavascriptInterface(scriptBridge, "_phantom");
    }
    
    private void injectScript(WebView webView, String script, Object callback) {
        mainHandler.post(() -> webView.evaluateJavascript(script, result -> {}));
    }
    
    private NanoHTTPD.Response handleInject(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String script = body.optString("script", "");
        if (script.isEmpty()) return jsonError(400, "缺少 script 参数");
        
        if (currentWebView == null) {
            pendingScripts.offer(new PendingScript(script, null));
            return jsonOk(new JSONObject().put("success", true).put("status", "pending"));
        }
        
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(script, result -> {
                    success.set(true);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(10, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", success.get()));
    }
    
    private NanoHTTPD.Response handleEval(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String script = body.optString("script", "");
        if (script.isEmpty()) return jsonError(400, "缺少 script 参数");
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        AtomicReference<String> resultRef = new AtomicReference<>("null");
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript("(function() { try { return JSON.stringify(" + script + "); } catch(e) { return JSON.stringify({error: e.message}); } })();", result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        if (latch.await(10, TimeUnit.SECONDS)) {
            return jsonOk(new JSONObject().put("success", true).put("result", resultRef.get()));
        }
        return jsonError(408, "执行超时");
    }
    
    private NanoHTTPD.Response handleDom(NanoHTTPD.IHTTPSession session) throws Exception {
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript("document.documentElement.outerHTML", result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        if (latch.await(5, TimeUnit.SECONDS)) {
            return jsonOk(new JSONObject().put("success", true).put("html", resultRef.get()));
        }
        return jsonError(408, "获取 DOM 超时");
    }
    
    private NanoHTTPD.Response handleClick(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        int x = body.optInt("x", -1);
        int y = body.optInt("y", -1);
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        String clickScript;
        if (!selector.isEmpty()) {
            clickScript = "(function() { var el = document.querySelector('" + selector + "'); if (el) { el.click(); return true; } return false; })()";
        } else if (x >= 0 && y >= 0) {
            clickScript = "(function() { var event = new MouseEvent('click', {bubbles: true, cancelable: true, clientX: " + x + ", clientY: " + y + "}); document.elementFromPoint(" + x + ", " + y + ").dispatchEvent(event); return true; })()";
        } else {
            return jsonError(400, "需要 selector 或 x/y 参数");
        }
        
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(clickScript, result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("result", resultRef.get()));
    }
    
    private NanoHTTPD.Response handleInput(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String text = body.optString("text", "");
        boolean clear = body.optBoolean("clear", false);
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        if (selector.isEmpty()) return jsonError(400, "缺少 selector 参数");
        
        String escapedText = text.replace("", "").replace("'", "\'").replace("\n", "\n");
        String inputScript = "(function() { var el = document.querySelector('" + selector + "'); if (!el) return 'element not found'; if (" + clear + ") el.value = ''; el.value += '" + escapedText + "'; el.dispatchEvent(new Event('input', {bubbles: true})); el.dispatchEvent(new Event('change', {bubbles: true})); return el.value; })()";
        
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(inputScript, result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("value", resultRef.get()));
    }
    
    private NanoHTTPD.Response handleScroll(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String direction = body.optString("direction", "down");
        int distance = body.optInt("distance", 300);
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        String scrollScript;
        switch (direction) {
            case "up": scrollScript = "window.scrollBy(0, -" + distance + ")"; break;
            case "down": scrollScript = "window.scrollBy(0, " + distance + ")"; break;
            case "left": scrollScript = "window.scrollBy(-" + distance + ", 0)"; break;
            case "right": scrollScript = "window.scrollBy(" + distance + ", 0)"; break;
            default: return jsonError(400, "无效的 direction: " + direction);
        }
        
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(scrollScript, result -> {
                    success.set(true);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", success.get()));
    }
    
    private NanoHTTPD.Response handleCookies(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String action = body.optString("action", "get");
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        String cookieScript;
        if ("get".equals(action)) {
            cookieScript = "document.cookie";
        } else if ("clear".equals(action)) {
            cookieScript = "(function() { document.cookie.split(';').forEach(function(c) { document.cookie = c.replace(/^ +/, '').replace(/=.*/, '=;expires=' + new Date().toUTCString() + ';path=/'); }); return true; })()";
        } else {
            return jsonError(400, "无效的 action: " + action);
        }
        
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(cookieScript, result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("cookies", resultRef.get()));
    }
    
    private NanoHTTPD.Response handleStorage(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String type = body.optString("type", "local");
        String action = body.optString("action", "get");
        String key = body.optString("key", "");
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        
        String storage = "local".equals(type) ? "localStorage" : "sessionStorage";
        String script;
        
        switch (action) {
            case "get":
                script = key.isEmpty() ? "JSON.stringify(" + storage + ")" : storage + ".getItem('" + key + "')";
                break;
            case "clear":
                script = storage + ".clear(); 'cleared'";
                break;
            default:
                return jsonError(400, "无效的 action: " + action);
        }
        
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(script, result -> {
                    resultRef.set(result);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("data", resultRef.get()));
    }
    
    private NanoHTTPD.Response handleWait(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        int timeout = body.optInt("timeout", 10000);
        
        if (currentWebView == null) return jsonError(500, "没有可用的 WebView");
        if (selector.isEmpty()) return jsonError(400, "缺少 selector 参数");
        
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeout) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            CountDownLatch latch = new CountDownLatch(1);
            
            mainHandler.post(() -> {
                try {
                    currentWebView.evaluateJavascript("(function() { return !!document.querySelector('" + selector + "'); })()", result -> {
                        found.set("true".equals(result));
                        latch.countDown();
                    });
                } catch (Exception e) {
                    latch.countDown();
                }
            });
            
            latch.await(1, TimeUnit.SECONDS);
            
            if (found.get()) {
                return jsonOk(new JSONObject().put("success", true).put("found", true));
            }
            
            Thread.sleep(200);
        }
        
        return jsonOk(new JSONObject().put("success", false).put("found", false).put("error", "timeout"));
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
    
    // 内部类
    private static class PendingScript {
        String script;
        Object callback;
        PendingScript(String script, Object callback) {
            this.script = script;
            this.callback = callback;
        }
    }
    
    private class ScriptBridge {
        @JavascriptInterface
        public String eval(String script) {
            return script; // 简化实现
        }
    }
}