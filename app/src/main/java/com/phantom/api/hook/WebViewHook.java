package com.phantom.api.hook;

import android.app.Activity;
import android.os.Build;
import android.os.Process;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebView Hook - JS 注入 + prompt 拦截
 * 
 * 核心架构：
 * 1. 在页面加载完成时注入极简 JS SDK
 * 2. 拦截 prompt() 获取 JS 抛出的 DOM 数据
 * 3. 通过文件系统跨进程通信
 */
public class WebViewHook {
    
    private static final String TAG = "PhantomHook";
    private static final String PHANTOM_PROMPT_PREFIX = "phantom://";
    private static final String DOM_DATA_PATH = "/data/local/tmp/phantom_dom_";
    private static final String CMD_PATH = "/data/local/tmp/phantom_cmd_";
    
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals("com.phantom.api")) return;
        
        log("注入目标: " + lpparam.packageName);
        hookWebView(lpparam);
        hookUcWebView(lpparam);
        hookX5WebView(lpparam);
        startCommandPoller(lpparam);
    }
    
    private void hookWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);
            
            // Hook onPageFinished
            XposedHelpers.findAndHookMethod(webViewClass, "onPageFinished", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    WebView webView = (WebView) param.thisObject;
                    injectJsSdk(webView);
                }
            });
            
            // Hook WebChromeClient.onJsPrompt
            Class<?> clientClass = XposedHelpers.findClass("android.webkit.WebChromeClient", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(clientClass, "onJsPrompt",
                WebView.class, String.class, String.class, String.class, JsPromptResult.class, 
                new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String message = (String) param.args[1];
                    String defaultValue = (String) param.args[2];
                    
                    if (message != null && message.startsWith(PHANTOM_PROMPT_PREFIX)) {
                        JsPromptResult result = (JsPromptResult) param.args[4];
                        result.confirm(defaultValue);
                        param.setResult(true);
                        handlePhantomPrompt(message, defaultValue);
                    }
                }
            });
            
            log("WebView Hook 成功");
        } catch (Throwable t) {
            log("WebView Hook 失败: " + t.getMessage());
        }
    }
    
    private void injectJsSdk(WebView webView) {
        try {
            String js = 
                "(function(){" +
                "  if (window.__phantom_injected) return;" +
                "  window.__phantom_injected = true;" +
                "  window.__phantom = {" +
                "    getDOM: function() {" +
                "      var data = [];" +
                "      var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT, null, false);" +
                "      var node;" +
                "      while (node = walker.nextNode()) {" +
                "        var rect = node.getBoundingClientRect();" +
                "        if (rect.width > 0 && rect.height > 0) {" +
                "          var text = node.innerText || node.value || node.placeholder || '';" +
                "          if (text && text.trim().length < 200) {" +
                "            data.push({tag:node.tagName, text:text.trim().substring(0,100), x:Math.round(rect.x), y:Math.round(rect.y), w:Math.round(rect.width), h:Math.round(rect.height), id:node.id||'', cls:node.className||''});" +
                "          }" +
                "        }" +
                "      }" +
                "      return JSON.stringify(data);" +
                "    }," +
                "    click: function(sel) { var el = document.querySelector(sel); if (el) { el.click(); return true; } return false; }," +
                "    clickAt: function(x, y) { var el = document.elementFromPoint(x, y); if (el) { el.click(); return true; } return false; }," +
                "    eval: function(code) { return eval(code); }" +
                "  };" +
                "  setTimeout(function() { prompt('phantom://dom', window.__phantom.getDOM()); }, 500);" +
                "})();";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(js, null);
            } else {
                webView.loadUrl("javascript:" + js);
            }
            log("JS SDK 注入成功");
        } catch (Throwable t) {
            log("JS SDK 注入失败: " + t.getMessage());
        }
    }
    
    private void handlePhantomPrompt(String message, String data) {
        try {
            int pid = Process.myPid();
            File file = new File(DOM_DATA_PATH + pid + ".json");
            FileWriter writer = new FileWriter(file);
            writer.write(data != null ? data : "");
            writer.close();
            file.setReadable(true, false);
            file.setWritable(true, false);
            log("DOM 已保存: " + file.getPath());
        } catch (Throwable t) {
            log("保存 DOM 失败: " + t.getMessage());
        }
    }
    
    private void startCommandPoller(final XC_LoadPackage.LoadPackageParam lpparam) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    File cmdFile = new File(CMD_PATH + Process.myPid() + ".json");
                    if (cmdFile.exists()) {
                        String cmd = readFile(cmdFile);
                        cmdFile.delete();
                        if (cmd != null && !cmd.isEmpty()) {
                            executeCommand(cmd, lpparam);
                        }
                    }
                } catch (Exception e) {}
            }
        }).start();
    }
    
    private void executeCommand(String cmd, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(cmd);
            String action = json.optString("action", "");
            String js = "";
            
            switch (action) {
                case "capture_dom":
                    js = "prompt('phantom://dom', window.__phantom.getDOM());";
                    break;
                case "click":
                    js = "window.__phantom.click('" + json.optString("selector", "") + "');";
                    break;
                case "clickAt":
                    js = "window.__phantom.clickAt(" + json.optInt("x", 0) + ", " + json.optInt("y", 0) + ");";
                    break;
                case "eval":
                    js = "prompt('phantom://eval', String(" + json.optString("code", "") + "));";
                    break;
            }
            
            if (!js.isEmpty()) {
                executeJsInWebView(js, lpparam);
            }
        } catch (Throwable t) {
            log("执行命令失败: " + t.getMessage());
        }
    }
    
    private void executeJsInWebView(String js, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Activity activity = getCurrentActivity(lpparam);
            if (activity != null) {
                traverseViews(activity.getWindow().getDecorView(), (view) -> {
                    if (view instanceof WebView) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            ((WebView) view).evaluateJavascript(js, null);
                        }
                    }
                });
            }
        } catch (Throwable t) {}
    }
    
    private Activity getCurrentActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Object at = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader), 
                "currentActivityThread");
            return (Activity) XposedHelpers.callMethod(at, "getCurrentActivity");
        } catch (Throwable t) { return null; }
    }
    
    private void traverseViews(Object view, ViewVisitor visitor) {
        if (view instanceof android.view.View) {
            visitor.visit((android.view.View) view);
            if (view instanceof FrameLayout) {
                FrameLayout layout = (FrameLayout) view;
                for (int i = 0; i < layout.getChildCount(); i++) {
                    traverseViews(layout.getChildAt(i), visitor);
                }
            }
        }
    }
    
    private interface ViewVisitor { void visit(android.view.View view); }
    
    private String readFile(File file) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }
    
    private void hookUcWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> ucClass = XposedHelpers.findClass("com.uc.webview.export.WebView", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(ucClass, "onPageFinished", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    injectJsSdkToUc(param.thisObject);
                }
            });
            log("UC WebView Hook 成功");
        } catch (Throwable t) {}
    }
    
    private void injectJsSdkToUc(Object webView) {
        try {
            Method evalMethod = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
            String js = "/* Phantom JS SDK */";
            evalMethod.invoke(webView, js, null);
        } catch (Throwable t) {}
    }
    
    private void hookX5WebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> x5Class = XposedHelpers.findClass("com.tencent.smtt.sdk.WebView", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(x5Class, "onPageFinished", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    injectJsSdkToX5(param.thisObject);
                }
            });
            log("X5 WebView Hook 成功");
        } catch (Throwable t) {}
    }
    
    private void injectJsSdkToX5(Object webView) {
        try {
            Method evalMethod = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
            String js = "/* Phantom JS SDK */";
            evalMethod.invoke(webView, js, null);
        } catch (Throwable t) {}
    }
    
    private void log(String msg) {
        android.util.Log.i(TAG, msg);
    }
}
