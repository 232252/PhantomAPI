package com.phantom.api.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用工具类
 */
public class AppUtils {
    private static final String TAG = "AppUtils";
    
    /**
     * 获取已安装应用列表
     */
    public static List<AppInfo> getInstalledApps(Context context) {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        
        for (PackageInfo pkg : packages) {
            AppInfo info = new AppInfo();
            info.packageName = pkg.packageName;
            info.versionName = pkg.versionName;
            info.versionCode = pkg.versionCode;
            
            ApplicationInfo appInfo = pkg.applicationInfo;
            info.label = pm.getApplicationLabel(appInfo).toString();
            info.isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            info.isDebuggable = (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            
            apps.add(info);
        }
        
        return apps;
    }
    
    /**
     * 获取前台应用包名
     */
    public static String getForegroundPackage(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) 
                context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<android.app.ActivityManager.RunningAppProcessInfo> processes = 
                    am.getRunningAppProcesses();
                if (processes != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            return process.processName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取前台应用失败", e);
        }
        return null;
    }
    
    /**
     * 应用信息
     */
    public static class AppInfo {
        public String packageName;
        public String label;
        public String versionName;
        public long versionCode;
        public boolean isSystem;
        public boolean isDebuggable;
    }
}
