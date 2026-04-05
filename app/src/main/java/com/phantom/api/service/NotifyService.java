package com.phantom.api.service;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

/**
 * 通知监听服务
 * 借鉴自 GKD 通知处理机制
 */
public class NotifyService extends NotificationListenerService {
    private static final String TAG = "PhantomAPI-Notify";
    private static NotifyService instance;
    
    public static NotifyService getInstance() {
        return instance;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "通知监听服务已启动");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "通知监听服务已停止");
    }
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "收到通知: " + sbn.getPackageName());
        
        // 可以在这里实现通知回调机制
        // 类似 GKD 的规则匹配
        try {
            Notification notification = sbn.getNotification();
            if (notification != null && notification.extras != null) {
                String title = notification.extras.getString(Notification.EXTRA_TITLE, "");
                CharSequence textSeq = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
                String text = textSeq != null ? textSeq.toString() : "";
                
                Log.d(TAG, String.format("通知: [%s] %s - %s", sbn.getPackageName(), title, text));
            }
        } catch (Exception e) {
            Log.e(TAG, "处理通知失败", e);
        }
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "通知移除: " + sbn.getPackageName());
    }
}