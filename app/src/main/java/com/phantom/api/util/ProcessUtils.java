package com.phantom.api.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;

import java.util.List;

/**
 * 进程工具类
 */
public class ProcessUtils {
    
    /**
     * 获取当前进程名称
     */
    public static String getCurrentProcessName(Context context) {
        int pid = Process.myPid();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == pid) {
                        return process.processName;
                    }
                }
            }
        }
        return "unknown";
    }
    
    /**
     * 判断是否是主进程
     */
    public static boolean isMainProcess(Context context) {
        String currentProcess = getCurrentProcessName(context);
        String packageName = context.getPackageName();
        return packageName.equals(currentProcess);
    }
}
