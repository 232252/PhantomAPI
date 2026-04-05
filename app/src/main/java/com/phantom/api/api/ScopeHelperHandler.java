package com.phantom.api.api;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * LSPosed 模块作用域辅助 API
 * 
 * 帮助用户了解哪些应用需要启用 LSPosed 作用域
 */
public class ScopeHelperHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Scope";
    
    private final android.content.Context context;
    
    public ScopeHelperHandler(android.content.Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.endsWith("/scope/apps")) {
            return handleGetWebViewApps();
        } else if (uri.endsWith("/scope/check")) {
            return handleCheckScope();
        } else {
            return handleDefault();
        }
    }
    
    private NanoHTTPD.Response handleDefault() {
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("message", "LSPosed 作用域辅助 API");
            result.put("endpoints", new JSONArray()
                .put("/scope/apps - 获取可能使用 WebView 的应用列表")
                .put("/scope/check - 检查当前前台应用是否被 hook"));
        } catch (Exception e) {}
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取可能使用 WebView 的应用列表
     */
    private NanoHTTPD.Response handleGetWebViewApps() {
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            JSONArray webViewApps = new JSONArray();
            
            // 常见使用 WebView 的应用包名模式
            String[] webViewPatterns = {
                "com.tencent.mm",      // 微信
                "com.tencent.mobileqq", // QQ
                "com.taobao.taobao",   // 淘宝
                "com.jingdong.app.mall", // 京东
                "tv.danmaku.bili",     // B站
                "com.ss.android.ugc.aweme", // 抖音
                "com.sina.weibo",      // 微博
                "com.zhihu.android",   // 知乎
                "com.twitter.android", // Twitter
                "com.facebook.katana", // Facebook
                "com.instagram.android", // Instagram
                "mark.via",            // Via 浏览器
                "alook.browser.play"   // Alook 浏览器
            };
            
            for (ApplicationInfo app : apps) {
                String packageName = app.packageName;
                
                // 检查是否匹配已知 WebView 应用
                for (String pattern : webViewPatterns) {
                    if (packageName.contains(pattern) || pattern.contains(packageName)) {
                        JSONObject appInfo = new JSONObject();
                        appInfo.put("packageName", packageName);
                        appInfo.put("name", pm.getApplicationLabel(app));
                        appInfo.put("isWebViewApp", true);
                        webViewApps.put(appInfo);
                        break;
                    }
                }
                
                // 检查是否请求了网络权限（可能使用 WebView）
                try {
                    String[] permissions = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions;
                    if (permissions != null) {
                        for (String perm : permissions) {
                            if ("android.permission.INTERNET".equals(perm)) {
                                // 有网络权限，可能是 WebView 应用
                                // 但不重复添加
                                break;
                            }
                        }
                    }
                } catch (Exception e) {}
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("count", webViewApps.length());
            result.put("apps", webViewApps);
            result.put("hint", "请在 LSPosed Manager 中为这些应用启用 PhantomAPI 模块");
            
            return HttpServerEngine.jsonSuccess(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Get WebView apps error: " + e.getMessage());
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "获取应用列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查当前前台应用是否被 hook
     */
    private NanoHTTPD.Response handleCheckScope() {
        try {
            // 获取前台应用
            android.app.ActivityManager am = (android.app.ActivityManager) 
                context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            
            String currentPackage = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.ActivityManager.AppTask task = am.getAppTasks().get(0);
                if (task != null && task.getTaskInfo() != null) {
                    currentPackage = task.getTaskInfo().baseActivity.getPackageName();
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("currentPackage", currentPackage);
            result.put("isHooked", false); // 需要通过日志检查
            result.put("hint", "请检查 logcat 中是否有 'LSPosed-Bridge: PhantomHook -> Loading hook for: " + currentPackage + "'");
            
            return HttpServerEngine.jsonSuccess(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Check scope error: " + e.getMessage());
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "检查失败: " + e.getMessage());
        }
    }
}
