package com.phantom.api.util;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.service.PhantomAccessibilityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
     * 获取前台应用包名 (Android 10+ 兼容方案)
     * 优先使用 UsageStatsManager，降级使用无障碍服务
     */
    public static String getForegroundPackage(Context context) {
        // 方案一：UsageStatsManager (Android 5.0+)
        try {
            UsageStatsManager usm = (UsageStatsManager) 
                context.getSystemService(Context.USAGE_STATS_SERVICE);
            
            if (usm != null) {
                long now = System.currentTimeMillis();
                List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, now - 2000, now);
                
                if (stats != null && !stats.isEmpty()) {
                    // 找到最近使用时间最新的 App
                    UsageStats latest = Collections.max(stats, 
                        Comparator.comparingLong(UsageStats::getLastTimeUsed));
                    
                    if (latest != null && latest.getLastTimeUsed() >= now - 3000) {
                        return latest.getPackageName();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "UsageStatsManager 获取失败: " + e.getMessage());
        }
        
        // 方案二：通过无障碍服务获取当前窗口包名
        try {
            PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
            if (a11yService != null) {
                AccessibilityNodeInfo root = a11yService.getRootInActiveWindow();
                if (root != null) {
                    CharSequence pkg = root.getPackageName();
                    if (pkg != null) {
                        return pkg.toString();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "无障碍服务获取失败: " + e.getMessage());
        }
        
        // 方案三：通过 dumpsys activity (需要 root)
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = process.getOutputStream();
            os.write("dumpsys activity activities | grep mResumedActivity\n".getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            os.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            
            if (line != null && line.contains("/")) {
                // 解析格式: mResumedActivity: ActivityRecord{xxx u0 com.package.name/.ActivityName}
                int start = line.indexOf(" ");
                if (start > 0) {
                    String pkg = line.substring(start).trim().split("/")[0];
                    if (!pkg.isEmpty() && !pkg.equals("ActivityRecord{")) {
                        return pkg.split(" ")[0];
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "dumpsys 获取失败: " + e.getMessage());
        }
        
        return "unknown";
    }
    
    /**
     * 获取前台应用的 PID
     */
    public static int getForegroundPid(Context context, String packageName) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
            
            List<android.app.ActivityManager.RunningAppProcessInfo> processes = 
                am.getRunningAppProcesses();
            
            if (processes != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo proc : processes) {
                    if (proc.processName.equals(packageName)) {
                        return proc.pid;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 PID 失败: " + e.getMessage());
        }
        
        // 备用：通过 pidof 命令
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = process.getOutputStream();
            os.write(("pidof " + packageName + "\n").getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            os.close();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            
            if (line != null && !line.isEmpty()) {
                return Integer.parseInt(line.trim().split(" ")[0]);
            }
        } catch (Exception e) {
            Log.e(TAG, "pidof 获取失败: " + e.getMessage());
        }
        
        return -1;
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
