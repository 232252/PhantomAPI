package com.phantom.api.hook;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JsPromptResult;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebView Hook - 统一注入版
 * 
 * 核心机制：
 * 1. Hook onPageFinished：页面加载完成时自动注入 JS
 * 2. JS 用 prompt() 把 DOM 数据抛回 Java
 * 3. Hook onJsPrompt：拦截 prompt，将数据写入本地文件
 * 
 * 优势：
 * - 无需 PID、无需命令轮询
 * - 页面加载完即自动触发，实时性高
 * - 覆盖所有使用 android.webkit.WebView 的应用
 */
public class WebViewHook {
    private static final String TAG = "PhantomHook";
    
    // 统一 IPC 文件路径
    public static final String DOM_RESULT_FILE = "/data/local/tmp/phantom/dom.json";
    
    // 统一注入的 JS 脚本
    private static final String DOM_EXTRACT_JS =
            "try {" +
            "  var r = [];" +
            "  var n = document.querySelectorAll('body *');" +
            "  for (var i = 0; i < n.length; i++) {" +
            "    var t = n[i].innerText;" +
            "    if (t && t.trim().length > 0 && t.trim().length < 200) {" +
            "      var b = n[i].getBoundingClientRect();" +
            "      if (b.width > 0 && b.height > 0) {" +
            "        r.push({t: t.trim(), x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), tag: n[i].tagName});" +
            "      }" +
            "    }" +
            "  }" +
            "  prompt('phantom://dom', JSON.stringify(r));" +
            "} catch(e) { prompt('phantom://error', e.message || String(e)); }";
    
    // onJsPrompt 的通用回调
    private static final XC_MethodHook jsPromptHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // args: WebView view, String url, String message, String defaultValue, JsPromptResult result
            // args[0] = WebView view
            // args[1] = String url
            // args[2] = String message (prompt 的第一个参数)
            // args[3] = String defaultValue (prompt 的第二个参数，即数据)
            // args[4] = JsPromptResult result
            
            String message = (String) param.args[2];  // 修正：message 在 args[2]
            String defaultValue = (String) param.args[3];  // 修正：defaultValue 在 args[3]
            
            Log.i(TAG, ">>> onJsPrompt intercepted: " + message);
            
            if ("phantom://dom".equals(message) && defaultValue != null) {
                param.setResult(true); // 拦截掉，不让网页真正弹窗
                Log.i(TAG, ">>> Received DOM via prompt, length: " + defaultValue.length());
                writeDomToFile(defaultValue);
            } else if ("phantom://error".equals(message)) {
                param.setResult(true);
                Log.e(TAG, "JS inject error: " + defaultValue);
            }
        }
    };

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 强制开启所有 WebView 的调试能力
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "setWebContentsDebuggingEnabled", 
                boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = true;
                }
            });
        } catch (Throwable ignored) {}
        
        // 2. Hook WebViewClient.onJsPrompt (使用 hookAllMethods 确保覆盖所有)
        try {
            Method[] methods = WebViewClient.class.getDeclaredMethods();
            for (Method m : methods) {
                if ("onJsPrompt".equals(m.getName())) {
                    XposedBridge.hookMethod(m, jsPromptHook);
                    Log.i(TAG, ">>> Hooked WebViewClient.onJsPrompt");
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Hook WebViewClient.onJsPrompt failed: " + e);
        }
        
        // 3. Hook WebChromeClient.onJsPrompt (使用 hookAllMethods 确保覆盖所有)
        try {
            Method[] methods = WebChromeClient.class.getDeclaredMethods();
            for (Method m : methods) {
                if ("onJsPrompt".equals(m.getName())) {
                    XposedBridge.hookMethod(m, jsPromptHook);
                    Log.i(TAG, ">>> Hooked WebChromeClient.onJsPrompt");
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Hook WebChromeClient.onJsPrompt failed: " + e);
        }
        
        // 4. Hook onPageFinished
        try {
            XposedHelpers.findAndHookMethod(WebViewClient.class, "onPageFinished", 
                    WebView.class, String.class, 
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final WebView webView = (WebView) param.args[0];
                            final String url = (String) param.args[1];
                            
                            if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return;
                            
                            String lastUrl = (String) webView.getTag();
                            if (url.equals(lastUrl)) return;
                            webView.setTag(url);
                            
                            Log.i(TAG, ">>> Page loaded, injecting JS: " + url);
                            
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        webView.evaluateJavascript(DOM_EXTRACT_JS, null);
                                    } catch (Throwable e) {
                                        Log.e(TAG, "evaluateJavascript error: " + e);
                                    }
                                }
                            });
                        }
                    }
            );
            Log.i(TAG, ">>> onPageFinished hook success");
        } catch (Throwable e) {
            Log.e(TAG, "Hook onPageFinished failed: " + e);
        }
    }
    
    private static void writeDomToFile(final String jsonStr) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream fos = null;
                try {
                    File dir = new File("/data/local/tmp/phantom");
                    if (!dir.exists()) dir.mkdirs();
                    
                    File file = new File(DOM_RESULT_FILE);
                    fos = new FileOutputStream(file, false);
                    fos.write(jsonStr.getBytes("UTF-8"));
                    fos.flush();
                    
                    Log.i(TAG, ">>> DOM successfully written to file (" + jsonStr.length() + " bytes)");
                } catch (Throwable e) {
                    Log.e(TAG, ">>> Failed to write DOM file: " + e.getMessage());
                } finally {
                    if (fos != null) {
                        try { fos.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }
}
