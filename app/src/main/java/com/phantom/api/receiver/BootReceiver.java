package com.phantom.api.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.phantom.api.service.PhantomHttpService;

/**
 * 开机自启动接收器
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PhantomAPI";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "收到广播: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.i(TAG, "系统启动完成，启动 PhantomAPI 服务");
            
            Intent serviceIntent = new Intent(context, PhantomHttpService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
