package com.phantom.api.hook;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebView 强制调试 Hook
 * 
 * 功能：
 * 1. 强制开启 WebView 调试端口
 * 2. 强制注入 JS 脚本获取 DOM
 * 3. 兼容阿里 UC 内核
 * 
 * 安装方式：
 * 1. 编译为 LSPosed 模块
 * 2. 在 LSPosed 中启用并勾选目标应用
 */
public class WebViewHook implements IXposedHookLoadPackage {
    private static final String TAG = "PhantomHook";
    
    // 要注入的 JS 脚本 - 获取页面所有文本和坐标
    private static final String DOM_EXTRACT_SCRIPT = 
        "(function(){" +
        "  if(window.__phantom_dom_extracted__) return;" +
        "  window.__phantom_dom_extracted__ = true;" +
        "  " +
        "  var data = [];" +
        "  var walker = document.createTreeWalker(" +
        "    document.body, " +
        "    NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, " +
        "    null, " +
        "    false" +
        "  );" +
        "  " +
        "  while(walker.nextNode()){" +
        "    var node = walker.currentNode;" +
        "    var text = '';" +
        "    " +
        "    if(node.nodeType === Node.TEXT_NODE){" +
        "      text = node.textContent.trim();" +
        "    } else if(node.nodeType === Node.ELEMENT_NODE){" +
        "      text = (node.innerText || node.value || node.placeholder || '').trim();" +
        "    }" +
        "    " +
        "    if(text && text.length > 0){" +
        "      try{" +
        "        var rect = node.getBoundingClientRect();" +
        "        if(rect.width > 0 && rect.height > 0){" +
        "          data.push({" +
        "            t: text.substring(0, 200)," +
        "            x: Math.round(rect.x + rect.width / 2)," +
        "            y: Math.round(rect.y + rect.height / 2)," +
        "            w: Math.round(rect.width)," +
        "            h: Math.round(rect.height)," +
        "            tag: node.tagName ? node.tagName.toLowerCase() : 'text'" +
        "          });" +
        "        }" +
        "      }catch(e){}" +
        "    }" +
        "  }" +
        "  " +
        "  // 通过 console.log 输出，可被拦截" +
        "  console.log('__PHANTOM_DOM__' + JSON.stringify(data));" +
        "})();";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 不 hook 自己
        if (lpparam.packageName.equals("com.phantom.api")) {
            return;
        }
        
        Log.i(TAG, "加载包: " + lpparam.packageName);
        
        // Hook WebView 调试开关
        hookWebViewDebugging(lpparam);
        
        // Hook WebViewClient.onPageFinished 注入 JS
        hookPageFinished(lpparam);
        
        // Hook UC WebView
        hookUcWebView(lpparam);
        
        // Hook 阿里 H5 容器
        hookAlibabaH5(lpparam);
    }
    
    /**
     * Hook WebView.setWebContentsDebuggingEnabled
     * 强制开启调试端口
     */
    private void hookWebViewDebugging(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebView",
                lpparam.classLoader,
                "setWebContentsDebuggingEnabled",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 强制设置为 true
                        param.args[0] = true;
                        Log.d(TAG, "[WebView] 强制开启调试端口: " + lpparam.packageName);
                    }
                }
            );
            
            Log.i(TAG, "WebView 调试 Hook 成功");
        } catch (Throwable t) {
            Log.e(TAG, "WebView 调试 Hook 失败", t);
        }
    }
    
    /**
     * Hook WebViewClient.onPageFinished
     * 页面加载完成后注入 JS
     */
    private void hookPageFinished(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebViewClient",
                lpparam.classLoader,
                "onPageFinished",
                WebView.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        WebView webView = (WebView) param.args[0];
                        String url = (String) param.args[1];
                        
                        Log.d(TAG, "[WebView] 页面加载完成: " + url);
                        
                        // 注入 DOM 提取脚本
                        try {
                            webView.evaluateJavascript(DOM_EXTRACT_SCRIPT, null);
                            Log.d(TAG, "[WebView] JS 注入成功");
                        } catch (Exception e) {
                            Log.e(TAG, "[WebView] JS 注入失败", e);
                        }
                    }
                }
            );
            
            Log.i(TAG, "onPageFinished Hook 成功");
        } catch (Throwable t) {
            Log.e(TAG, "onPageFinished Hook 失败", t);
        }
    }
    
    /**
     * Hook UC WebView
     * 兼容阿里系应用
     */
    private void hookUcWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // UC WebView 调试
            Class<?> ucWebViewClass = XposedHelpers.findClass(
                "com.uc.webview.export.WebView", 
                lpparam.classLoader
            );
            
            if (ucWebViewClass != null) {
                XposedHelpers.findAndHookMethod(
                    ucWebViewClass,
                    "setWebContentsDebuggingEnabled",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = true;
                            Log.d(TAG, "[UC WebView] 强制开启调试端口");
                        }
                    }
                );
                
                Log.i(TAG, "UC WebView Hook 成功");
            }
        } catch (Throwable t) {
            // UC WebView 不存在，忽略
        }
    }
    
    /**
     * Hook 阿里 H5 容器
     */
    private void hookAlibabaH5(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 阿里 H5 容器调试
            Class<?> h5ContainerClass = XposedHelpers.findClass(
                "com.alipay.mobile.h5container.api.H5Param", 
                lpparam.classLoader
            );
            
            if (h5ContainerClass != null) {
                Log.i(TAG, "检测到阿里 H5 容器");
            }
        } catch (Throwable t) {
            // 不存在，忽略
        }
        
        try {
            // Hook 支付宝/淘宝 UC 内核
            Class<?> tauWebViewClass = XposedHelpers.findClass(
                "com.taobao.tao.TauWebView", 
                lpparam.classLoader
            );
            
            if (tauWebViewClass != null) {
                Log.i(TAG, "检测到淘宝 TauWebView");
            }
        } catch (Throwable t) {
            // 不存在，忽略
        }
    }
    
    /**
     * Hook WebView.evaluateJavascript
     * 拦截 JS 执行结果
     */
    private void hookEvaluateJavascript(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebView",
                lpparam.classLoader,
                "evaluateJavascript",
                String.class,
                android.webkit.ValueCallback.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String script = (String) param.args[0];
                        Log.d(TAG, "[WebView] 执行 JS: " + script.substring(0, Math.min(100, script.length())));
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "evaluateJavascript Hook 失败", t);
        }
    }
}
