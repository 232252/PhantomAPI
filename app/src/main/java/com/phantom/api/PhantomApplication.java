package com.phantom.api;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.phantom.api.service.PhantomAccessibilityService;
import com.phantom.api.service.PhantomHttpService;
import com.phantom.api.util.NetworkUtils;
import com.phantom.api.util.ProcessUtils;

/**
 * PhantomAPI 应用入口
 * 系统级常驻后台服务
 */
public class PhantomApplication extends Application {
    private static final String TAG = "PhantomAPI";
    private static PhantomApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.i(TAG, "========================================");
        Log.i(TAG, "PhantomAPI 启动中...");
        Log.i(TAG, "进程: " + ProcessUtils.getCurrentProcessName(this));
        Log.i(TAG, "SDK: " + Build.VERSION.SDK_INT);
        Log.i(TAG, "设备: " + Build.MODEL);
        Log.i(TAG, "========================================");
        
        // 检查是否是主进程
        if (ProcessUtils.isMainProcess(this)) {
            // 初始化网络工具
            NetworkUtils.init(this);
            // 启动 HTTP 服务
            startHttpService();
        }
    }
    
    private void startHttpService() {
        Intent intent = new Intent(this, PhantomHttpService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Log.i(TAG, "HTTP 服务启动命令已发送");
    }
    
    public static PhantomApplication getInstance() {
        return instance;
    }
    
    public static Context getContext() {
        return instance.getApplicationContext();
    }
}
