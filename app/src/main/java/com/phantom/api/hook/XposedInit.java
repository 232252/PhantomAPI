package com.phantom.api.hook;

import com.phantom.api.hook.WebViewHook;

/**
 * Xposed 模块入口
 * 在 assets/xposed_init 中声明此类
 */
public class XposedInit implements de.robv.android.xposed.IXposedHookZygoteInit,
                                    de.robv.android.xposed.IXposedHookLoadPackage {
    
    @Override
    public void initZygote(de.robv.android.xposed.IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        // Zygote 初始化时调用
    }
    
    @Override
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        new WebViewHook().handleLoadPackage(lpparam);
    }
}
