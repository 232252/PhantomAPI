package com.phantom.api.api;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.NotifyService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * 通知监听 API 处理器
 * 借鉴自 GKD, Hamibot
 * 
 * 端点：
 * /api/notify/list - 获取通知列表
 * /api/notify/clear - 清除通知
 * /api/notify/click - 点击通知
 * /api/notify/action - 执行通知动作
 * /api/notify/wait - 等待特定通知
 */
public class NotifyApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Notify";
    private final Context context;
    
    public NotifyApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        switch (uri) {
            case "/api/notify/list": return handleList(session);
            case "/api/notify/clear": return handleClear(session);
            case "/api/notify/wait": return handleWait(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    private NanoHTTPD.Response handleList(NanoHTTPD.IHTTPSession session) throws Exception {
        NotifyService service = NotifyService.getInstance();
        if (service == null) {
            return jsonError(500, "通知服务未启动");
        }
        
        StatusBarNotification[] notifications = service.getActiveNotifications();
        JSONArray list = new JSONArray();
        
        for (StatusBarNotification sbn : notifications) {
            JSONObject notify = parseNotification(sbn);
            list.put(notify);
        }
        
        return jsonOk(new JSONObject().put("success", true).put("count", list.length()).put("notifications", list));
    }
    
    private NanoHTTPD.Response handleClear(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        int id = body.optInt("id", -1);
        String key = body.optString("key", "");
        
        NotifyService service = NotifyService.getInstance();
        if (service == null) {
            return jsonError(500, "通知服务未启动");
        }
        
        try {
            if (!key.isEmpty()) {
                service.cancelNotification(key);
            } else if (!packageName.isEmpty() && id >= 0) {
                service.cancelNotification(packageName, null, id);
            } else {
                service.cancelAllNotifications();
            }
            return jsonOk(new JSONObject().put("success", true));
        } catch (Exception e) {
            return jsonError(500, "清除失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleWait(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        String textContains = body.optString("textContains", "");
        String titleContains = body.optString("titleContains", "");
        int timeout = body.optInt("timeout", 30000);
        
        NotifyService service = NotifyService.getInstance();
        if (service == null) {
            return jsonError(500, "通知服务未启动");
        }
        
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeout) {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            
            for (StatusBarNotification sbn : notifications) {
                boolean match = true;
                
                if (!packageName.isEmpty() && !sbn.getPackageName().equals(packageName)) {
                    match = false;
                }
                
                if (match && (!textContains.isEmpty() || !titleContains.isEmpty())) {
                    Notification notification = sbn.getNotification();
                    Bundle extras = notification.extras;
                    
                    String title = extras.getString(Notification.EXTRA_TITLE, "");
                    CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
                    String text = textSeq != null ? textSeq.toString() : "";
                    
                    if (!titleContains.isEmpty() && !title.contains(titleContains)) {
                        match = false;
                    }
                    if (!textContains.isEmpty() && !text.contains(textContains)) {
                        match = false;
                    }
                }
                
                if (match) {
                    JSONObject result = parseNotification(sbn);
                    return jsonOk(new JSONObject().put("success", true).put("found", true).put("notification", result));
                }
            }
            
            Thread.sleep(500);
        }
        
        return jsonOk(new JSONObject().put("success", false).put("found", false).put("error", "timeout"));
    }
    
    private JSONObject parseNotification(StatusBarNotification sbn) {
        JSONObject notify = new JSONObject();
        try {
            notify.put("key", sbn.getKey());
            notify.put("packageName", sbn.getPackageName());
            notify.put("id", sbn.getId());
            notify.put("postTime", sbn.getPostTime());
            notify.put("isOngoing", sbn.isOngoing());
            notify.put("isClearable", sbn.isClearable());
            
            Notification notification = sbn.getNotification();
            if (notification != null) {
                Bundle extras = notification.extras;
                if (extras != null) {
                    notify.put("title", extras.getString(Notification.EXTRA_TITLE, ""));
                    CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
                    notify.put("text", textSeq != null ? textSeq.toString() : "");
                    CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    notify.put("bigText", bigText != null ? bigText.toString() : "");
                }
                notify.put("priority", notification.priority);
                notify.put("category", notification.category);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析通知失败", e);
        }
        return notify;
    }
    
    private JSONObject parseBody(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = session.getQueryParameterString();
        if (body == null || body.isEmpty()) {
            java.util.Map<String, String> files = new java.util.HashMap<>();
            session.parseBody(files);
            body = files.get("postData");
        }
        return body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
    }
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", data.toString());
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject obj = new JSONObject();
        try { obj.put("success", false).put("error", msg).put("code", code); } catch (Exception e) {}
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", obj.toString());
    }
}