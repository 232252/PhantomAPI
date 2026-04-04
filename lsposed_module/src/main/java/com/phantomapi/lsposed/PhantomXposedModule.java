package com.phantomapi.lsposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PhantomXposedModule implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 开启 WebView 调试模式
        hookWebViewDebugging(lpparam);

        // Hook WebViewClient.onPageFinished 用于 JS 注入
        hookWebViewClient(lpparam);

        // Hook WebView.addJavascriptInterface 用于接收 JS 回调
        hookJavascriptInterface(lpparam);
    }

    // 开启 WebView 调试模式
    private void hookWebViewDebugging(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = lpparam.classLoader.loadClass("android.webkit.WebView");
            XposedBridge.hookAllMethods(webViewClass, "setWebContentsDebuggingEnabled", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // 强制设置为 true
                    param.args[0] = true;
                }
            });
        } catch (ClassNotFoundException e) {
            XposedBridge.log("Failed to hook WebView debugging: " + e.getMessage());
        }
    }

    // Hook WebViewClient.onPageFinished 用于 JS 注入
    private void hookWebViewClient(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClientClass = lpparam.classLoader.loadClass("android.webkit.WebViewClient");
            XposedBridge.hookAllMethods(webViewClientClass, "onPageFinished", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object webView = param.args[0];
                    String url = (String) param.args[1];

                    // 注入 JS 脚本
                    injectJavaScript(webView);
                }
            });
        } catch (ClassNotFoundException e) {
            XposedBridge.log("Failed to hook WebViewClient: " + e.getMessage());
        }
    }

    // Hook WebView.addJavascriptInterface 用于接收 JS 回调
    private void hookJavascriptInterface(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = lpparam.classLoader.loadClass("android.webkit.WebView");
            XposedBridge.hookAllMethods(webViewClass, "addJavascriptInterface", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // 允许添加任意 JavaScriptInterface
                    // 这里可以添加我们自己的接口
                }
            });
        } catch (ClassNotFoundException e) {
            XposedBridge.log("Failed to hook addJavascriptInterface: " + e.getMessage());
        }
    }

    // 注入 JS 脚本
    private void injectJavaScript(Object webView) {
        try {
            // 构造 JS 脚本
            String js = "" +
                    "(function() {" +
                    "    var elements = document.body.getElementsByTagName('*');" +
                    "    var result = [];" +
                    "    for (var i = 0; i < elements.length; i++) {" +
                    "        var element = elements[i];" +
                    "        var rect = element.getBoundingClientRect();" +
                    "        result.push({" +
                    "            text: element.innerText," +
                    "            tag: element.tagName," +
                    "            x: rect.left," +
                    "            y: rect.top," +
                    "            width: rect.width," +
                    "            height: rect.height" +
                    "        });" +
                    "    }" +
                    "    // 这里可以通过 WebView.addJavascriptInterface 回调到 Java 层" +
                    "    console.log(JSON.stringify(result));" +
                    "}());";

            // 调用 evaluateJavascript 方法
            Class<?> webViewClass = webView.getClass();
            webViewClass.getMethod("evaluateJavascript", String.class, Class.forName("android.webkit.ValueCallback")).invoke(
                    webView, js, null
            );
        } catch (Exception e) {
            XposedBridge.log("Failed to inject JavaScript: " + e.getMessage());
        }
    }
}
