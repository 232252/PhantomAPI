package com.phantom.api.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 入口 - 简化版
 * 
 * 对所有有 UI 的应用注入 WebViewHook
 */
public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "PhantomHook";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只对有 UI 的应用生效，过滤掉系统底层纯后台服务
        if (lpparam.processName == null || lpparam.processName.contains(":")) {
            return; // 跳过子进程（如 Chrome 的渲染进程），只管主进程
        }
        
        // 跳过自身应用（测试时可以注释掉）
        // if (lpparam.packageName.equals("com.phantom.api")) {
        //     return;
        // }
        
        XposedBridge.log(TAG + " -> Loading hook for: " + lpparam.packageName);
        
        try {
            WebViewHook hook = new WebViewHook();
            hook.handleLoadPackage(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " -> Failed to load hook: " + t.getMessage());
        }
    }
}
