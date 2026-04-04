package com.phantom.api.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.phantom.api.PhantomApplication;
import com.phantom.api.R;
import com.phantom.api.engine.HttpServerEngine;

/**
 * PhantomAPI HTTP 服务
 * 监听局域网端口，提供 RESTful API
 */
public class PhantomHttpService extends Service {
    private static final String TAG = "PhantomHttpService";
    private static final String CHANNEL_ID = "phantom_api_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int DEFAULT_PORT = 9999;
    
    private static boolean isRunning = false;
    
    private HttpServerEngine httpServer;
    private int currentPort = DEFAULT_PORT;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "HTTP 服务创建");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 启动 HTTP 服务器
        startHttpServer();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "HTTP 服务启动命令");
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "HTTP 服务销毁");
        stopHttpServer();
        isRunning = false;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PhantomAPI 服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("PhantomAPI 后台服务运行中");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
               .setContentTitle("PhantomAPI 运行中")
               .setContentText("端口: " + currentPort)
               .setOngoing(true)
               .setPriority(Notification.PRIORITY_LOW);
        
        return builder.build();
    }
    
    private void startHttpServer() {
        try {
            httpServer = new HttpServerEngine(this, currentPort);
            httpServer.start();
            isRunning = true;
            Log.i(TAG, "HTTP 服务器启动成功，端口: " + currentPort);
            Toast.makeText(this, "PhantomAPI 已启动，端口: " + currentPort, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "HTTP 服务器启动失败", e);
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
}
