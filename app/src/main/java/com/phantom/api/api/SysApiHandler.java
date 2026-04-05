package com.phantom.api.api;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.util.AppUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * 系统域 API 处理器
 * /api/sys/*
 */
public class SysApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "SysApiHandler";
    private final Context context;
    
    public SysApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        String method = session.getMethod().toString();
        
        Log.d(TAG, "处理请求: " + method + " " + uri);
        
        // 路由分发
        if (uri.equals("/api/sys/info")) {
            return handleDeviceInfo();
        } else if (uri.equals("/api/sys/foreground")) {
            return handleForegroundApp();
        } else if (uri.equals("/api/sys/activity")) {
            return handleForegroundActivity();
        } else if (uri.equals("/api/sys/packages")) {
            return handleInstalledPackages();
        } else {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                "Unknown endpoint: " + uri);
        }
    }
    
    /**
     * 获取设备信息
     * GET /api/sys/info
     */
    private NanoHTTPD.Response handleDeviceInfo() throws Exception {
        JSONObject info = new JSONObject();
        
        // 设备基本信息
        info.put("brand", Build.BRAND);
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("model", Build.MODEL);
        info.put("device", Build.DEVICE);
        info.put("product", Build.PRODUCT);
        info.put("board", Build.BOARD);
        info.put("hardware", Build.HARDWARE);
        
        // 系统信息
        info.put("sdkVersion", Build.VERSION.SDK_INT);
        info.put("releaseVersion", Build.VERSION.RELEASE);
        info.put("securityPatch", Build.VERSION.SECURITY_PATCH);
        info.put("incremental", Build.VERSION.INCREMENTAL);
        info.put("codename", Build.VERSION.CODENAME);
        
        // CPU 信息
        info.put("cpuAbi", Build.SUPPORTED_ABIS[0]);
        info.put("cpuAbis", new JSONArray(Build.SUPPORTED_ABIS));
        
        // 屏幕信息
        android.view.Display display = ((android.view.WindowManager) 
            context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        
        JSONObject screen = new JSONObject();
        screen.put("width", metrics.widthPixels);
        screen.put("height", metrics.heightPixels);
        screen.put("density", metrics.density);
        screen.put("densityDpi", metrics.densityDpi);
        screen.put("scaledDensity", metrics.scaledDensity);
        info.put("screen", screen);
        
        // 内存信息
        android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
            .getMemoryInfo(memInfo);
        
        JSONObject memory = new JSONObject();
        memory.put("totalMem", memInfo.totalMem);
        memory.put("availMem", memInfo.availMem);
        memory.put("threshold", memInfo.threshold);
        memory.put("lowMemory", memInfo.lowMemory);
        info.put("memory", memory);
        
        // 包信息
        info.put("packageName", context.getPackageName());
        info.put("versionName", context.getPackageManager()
            .getPackageInfo(context.getPackageName(), 0).versionName);
        
        return HttpServerEngine.jsonSuccess(info);
    }
    
    /**
     * 获取前台应用
     * GET /api/sys/foreground
     */
    private NanoHTTPD.Response handleForegroundApp() throws Exception {
        String foregroundPackage = AppUtils.getForegroundPackage(context);
        
        JSONObject result = new JSONObject();
        result.put("packageName", foregroundPackage != null ? foregroundPackage : "unknown");
        
        if (foregroundPackage != null) {
            try {
                android.content.pm.ApplicationInfo appInfo = context.getPackageManager()
                    .getApplicationInfo(foregroundPackage, 0);
                result.put("appName", context.getPackageManager().getApplicationLabel(appInfo));
            } catch (Exception e) {
                result.put("appName", foregroundPackage);
            }
        }
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取前台 Activity 信息（包含完整 Activity 名）
     * GET /api/sys/activity
     * 利用系统级权限，通过 ActivityManager.getRunningTasks() 获取顶层 Activity
     */
    @SuppressWarnings("deprecation")
    private NanoHTTPD.Response handleForegroundActivity() throws Exception {
        JSONObject result = new JSONObject();
        
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) 
                context.getSystemService(Context.ACTIVITY_SERVICE);
            
            // 因为是系统 App，可以直接调用 getRunningTasks
            List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            
            if (tasks != null && !tasks.isEmpty()) {
                android.content.ComponentName top = tasks.get(0).topActivity;
                
                result.put("success", true);
                result.put("package", top.getPackageName());
                result.put("activity", top.getClassName());
                
                // 简化的 Activity 名（去掉包名前缀）
                String shortName = top.getClassName();
                if (shortName.startsWith(top.getPackageName())) {
                    shortName = shortName.substring(top.getPackageName().length());
                    if (shortName.startsWith(".")) {
                        shortName = shortName.substring(1);
                    }
                }
                result.put("activityShort", shortName);
                
                // 任务栈大小
                result.put("numActivities", tasks.get(0).numActivities);
            } else {
                result.put("success", false);
                result.put("error", "no_tasks");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取前台 Activity 失败: " + e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取已安装应用列表
     * GET /api/sys/packages
     */
    private NanoHTTPD.Response handleInstalledPackages() throws Exception {
        List<AppUtils.AppInfo> apps = AppUtils.getInstalledApps(context);
        JSONArray packages = new JSONArray();
        
        for (AppUtils.AppInfo app : apps) {
            JSONObject pkg = new JSONObject();
            pkg.put("packageName", app.packageName);
            pkg.put("label", app.label);
            pkg.put("versionName", app.versionName);
            pkg.put("versionCode", app.versionCode);
            pkg.put("isSystem", app.isSystem);
            pkg.put("isDebuggable", app.isDebuggable);
            packages.put(pkg);
        }
        
        JSONObject result = new JSONObject();
        result.put("count", apps.size());
        result.put("packages", packages);
        
        return HttpServerEngine.jsonSuccess(result);
    }
}
