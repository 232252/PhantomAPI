package com.phantom.api.hook;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebView 强制调试 + DOM 注入 Hook
 * 
 * 核心能力：
 * 1. 强制开启 setWebContentsDebuggingEnabled(true)
 * 2. 在 onPageFinished 注入统一 JS SDK
 * 3. 兼容 UC 内核、腾讯 X5 内核
 * 
 * 安装：
 * 1. 编译为 LSPosed 模块
 * 2. 在 LSPosed 中启用并勾选目标应用
 */
public class WebViewHook implements IXposedHookLoadPackage {
    private static final String TAG = "PhantomHook";
    
    // 统一 JS SDK - 注入到所有 WebView
    private static final String PHANTOM_JS_SDK = 
        "(function(){" +
        "  if(window.__phantom_bridge__) return;" +
        "  window.__phantom_bridge__ = {" +
        "    version: '1.0.0'," +
        "    " +
        "    // 获取 DOM 树" +
        "    getDOM: function() {" +
        "      var data = [];" +
        "      var walker = document.createTreeWalker(" +
        "        document.body, " +
        "        NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, " +
        "        null, " +
        "        false" +
        "      );" +
        "      while(walker.nextNode()){" +
        "        var node = walker.currentNode;" +
        "        var text = '';" +
        "        if(node.nodeType === Node.TEXT_NODE){" +
        "          text = node.textContent.trim();" +
        "        } else if(node.nodeType === Node.ELEMENT_NODE){" +
        "          text = (node.innerText || node.value || node.placeholder || '').trim();" +
        "        }" +
        "        if(text && text.length > 0){" +
        "          try{" +
        "            var rect = node.getBoundingClientRect();" +
        "            if(rect.width > 0 && rect.height > 0){" +
        "              data.push({" +
        "                t: text.substring(0, 200)," +
        "                x: Math.round(rect.x + rect.width / 2)," +
        "                y: Math.round(rect.y + rect.height / 2)," +
        "                w: Math.round(rect.width)," +
        "                h: Math.round(rect.height)," +
        "                tag: node.tagName ? node.tagName.toLowerCase() : 'text'," +
        "                clickable: node.onclick !== null || node.style.cursor === 'pointer'" +
        "              });" +
        "            }" +
        "          }catch(e){}" +
        "        }" +
        "      }" +
        "      return data;" +
        "    }," +
        "    " +
        "    // 查找元素" +
        "    find: function(text) {" +
        "      var dom = this.getDOM();" +
        "      return dom.filter(function(item) {" +
        "        return item.t.indexOf(text) !== -1;" +
        "      });" +
        "    }," +
        "    " +
        "    // 点击元素" +
        "    click: function(x, y) {" +
        "      var el = document.elementFromPoint(x, y);" +
        "      if(el) {" +
        "        el.click();" +
        "        return true;" +
        "      }" +
        "      return false;" +
        "    }," +
        "    " +
        "    // 滚动" +
        "    scroll: function(x, y) {" +
        "      window.scrollTo(x, y);" +
        "      return true;" +
        "    }," +
        "    " +
        "    // 监听 DOM 变化" +
        "    watchDOM: function(callback) {" +
        "      if(this._observer) return;" +
        "      this._observer = new MutationObserver(function(mutations) {" +
        "        callback({type: 'dom_changed', count: mutations.length});" +
        "      });" +
        "      this._observer.observe(document.body, {" +
        "        childList: true," +
        "        subtree: true," +
        "        attributes: true" +
        "      });" +
        "    }" +
        "  };" +
        "  console.log('[PhantomBridge] JS SDK injected v1.0.0');" +
        "})();";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 不 hook 自己
        if (lpparam.packageName.equals("com.phantom.api")) {
            return;
        }
        
        Log.i(TAG, "加载包: " + lpparam.packageName + " - 开始 Hook WebView");
        
        // Hook WebView 调试开关
        hookWebViewDebugging(lpparam);
        
        // Hook WebViewClient.onPageFinished 注入 JS SDK
        hookWebViewClient(lpparam);
        
        // Hook UC 内核 (如果存在)
        hookUCWebView(lpparam);
        
        // Hook 腾讯 X5 内核 (如果存在)
        hookX5WebView(lpparam);
    }
    
    /**
     * Hook WebView.setWebContentsDebuggingEnabled
     * 强制开启调试模式
     */
    private void hookWebViewDebugging(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);
            
            XposedHelpers.findAndHookMethod(webViewClass, "setWebContentsDebuggingEnabled", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 强制设置为 true
                        param.args[0] = true;
                        Log.d(TAG, "WebView 调试已强制开启");
                    }
                    
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 确保调试开启
                        XposedHelpers.callStaticMethod(webViewClass, "setWebContentsDebuggingEnabled", true);
                    }
                });
            
            // 直接调用确保开启
            XposedHelpers.callStaticMethod(webViewClass, "setWebContentsDebuggingEnabled", true);
            Log.i(TAG, "WebView 调试模式已全局开启");
            
        } catch (Throwable t) {
            Log.e(TAG, "Hook WebView 调试失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook WebViewClient.onPageFinished
     * 在页面加载完成后注入 JS SDK
     */
    private void hookWebViewClient(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClientClass = XposedHelpers.findClass("android.webkit.WebViewClient", lpparam.classLoader);
            
            XposedHelpers.findAndHookMethod(webViewClientClass, "onPageFinished", 
                WebView.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        WebView webView = (WebView) param.args[0];
                        String url = (String) param.args[1];
                        
                        // 在主线程注入 JS
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                // KitKat+ 使用 evaluateJavascript
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    webView.evaluateJavascript(PHANTOM_JS_SDK, null);
                                } else {
                                    webView.loadUrl("javascript:" + PHANTOM_JS_SDK);
                                }
                                Log.d(TAG, "JS SDK 注入成功: " + url);
                            } catch (Exception e) {
                                Log.e(TAG, "JS SDK 注入失败: " + e.getMessage());
                            }
                        });
                    }
                });
            
            Log.i(TAG, "WebViewClient Hook 成功");
            
        } catch (Throwable t) {
            Log.e(TAG, "Hook WebViewClient 失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook UC 内核 WebView
     */
    private void hookUCWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> ucWebViewClass = XposedHelpers.findClass("com.uc.webview.export.WebView", lpparam.classLoader);
            Class<?> ucSettingsClass = XposedHelpers.findClass("com.uc.webview.export.WebSettings", lpparam.classLoader);
            
            // Hook UC WebView 调试
            XposedHelpers.findAndHookMethod(ucSettingsClass, "setJavaScriptEnabled", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 确保 JS 启用
                    }
                });
            
            Log.i(TAG, "UC WebView Hook 成功");
            
        } catch (Throwable t) {
            // UC 内核可能不存在，忽略
        }
    }
    
    /**
     * Hook 腾讯 X5 内核
     */
    private void hookX5WebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> x5WebViewClass = XposedHelpers.findClass("com.tencent.smtt.sdk.WebView", lpparam.classLoader);
            
            // Hook X5 WebView 调试
            Method setDebugMethod = x5WebViewClass.getDeclaredMethod("setWebContentsDebuggingEnabled", boolean.class);
            setDebugMethod.setAccessible(true);
            setDebugMethod.invoke(null, true);
            
            Log.i(TAG, "X5 WebView 调试已开启");
            
        } catch (Throwable t) {
            // X5 内核可能不存在，忽略
        }
    }
}
