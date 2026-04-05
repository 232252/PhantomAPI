package com.phantom.api.api;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * 应用管理 API 处理器
 * 借鉴自 MobiAgent, AutoJs6, Hamibot
 * 
 * 端点：
 * /api/app/list - 获取已安装应用列表
 * /api/app/launch - 启动应用
 * /api/app/stop - 停止应用
 * /api/app/clear - 清除应用数据
 * /api/app/info - 获取应用信息
 * /api/app/current - 获取前台应用
 * /api/app/recent - 获取最近任务
 * /api/shell/exec - 执行 Shell 命令
 * /api/clipboard/get - 获取剪贴板
 * /api/clipboard/set - 设置剪贴板
 * /api/media/volume - 音量控制
 * /api/media/brightness - 亮度控制
 */
public class AppApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-App";
    private final Context context;
    
    public AppApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        switch (uri) {
            case "/api/app/list": return handleAppList(session);
            case "/api/app/launch": return handleAppLaunch(session);
            case "/api/app/stop": return handleAppStop(session);
            case "/api/app/clear": return handleAppClear(session);
            case "/api/app/info": return handleAppInfo(session);
            case "/api/app/current": return handleAppCurrent(session);
            case "/api/app/recent": return handleAppRecent(session);
            case "/api/shell/exec": return handleShellExec(session);
            case "/api/clipboard/get": return handleClipboardGet(session);
            case "/api/clipboard/set": return handleClipboardSet(session);
            case "/api/media/volume": return handleMediaVolume(session);
            case "/api/media/brightness": return handleMediaBrightness(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    // ==================== 应用列表 ====================
    
    private NanoHTTPD.Response handleAppList(NanoHTTPD.IHTTPSession session) throws Exception {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        JSONArray list = new JSONArray();
        
        for (ResolveInfo info : apps) {
            JSONObject app = new JSONObject();
            app.put("packageName", info.activityInfo.packageName);
            app.put("appName", info.loadLabel(pm).toString());
            list.put(app);
        }
        
        return jsonOk(new JSONObject().put("success", true).put("count", list.length()).put("apps", list));
    }
    
    // ==================== 启动应用 ====================
    
    private NanoHTTPD.Response handleAppLaunch(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        
        if (packageName.isEmpty()) {
            return jsonError(400, "缺少 packageName 参数");
        }
        
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return jsonOk(new JSONObject().put("success", true).put("package", packageName));
            }
            return jsonError(404, "无法启动应用: " + packageName);
        } catch (Exception e) {
            return jsonError(500, "启动失败: " + e.getMessage());
        }
    }
    
    // ==================== 停止应用 ====================
    
    private NanoHTTPD.Response handleAppStop(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        
        if (packageName.isEmpty()) {
            return jsonError(400, "缺少 packageName 参数");
        }
        
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(packageName);
                return jsonOk(new JSONObject().put("success", true).put("package", packageName));
            }
            return jsonError(500, "无法获取 ActivityManager");
        } catch (Exception e) {
            return jsonError(500, "停止失败: " + e.getMessage());
        }
    }
    
    // ==================== 清除应用数据 ====================
    
    private NanoHTTPD.Response handleAppClear(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        
        if (packageName.isEmpty()) {
            return jsonError(400, "缺少 packageName 参数");
        }
        
        try {
            String result = executeShell("pm clear " + packageName);
            boolean success = result.contains("Success");
            return jsonOk(new JSONObject().put("success", success).put("result", result.trim()));
        } catch (Exception e) {
            return jsonError(500, "清除失败: " + e.getMessage());
        }
    }
    
    // ==================== 应用信息 ====================
    
    private NanoHTTPD.Response handleAppInfo(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String packageName = body.optString("packageName", "");
        
        if (packageName.isEmpty()) {
            return jsonError(400, "缺少 packageName 参数");
        }
        
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            JSONObject info = new JSONObject();
            info.put("packageName", packageName);
            info.put("appName", pm.getApplicationLabel(appInfo).toString());
            info.put("versionName", pm.getPackageInfo(packageName, 0).versionName);
            info.put("dataDir", appInfo.dataDir);
            info.put("sourceDir", appInfo.sourceDir);
            
            return jsonOk(new JSONObject().put("success", true).put("info", info));
        } catch (Exception e) {
            return jsonError(404, "应用不存在: " + packageName);
        }
    }
    
    // ==================== 当前应用 ====================
    
    private NanoHTTPD.Response handleAppCurrent(NanoHTTPD.IHTTPSession session) throws Exception {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        String packageName = "";
        
        if (service != null) {
            android.view.accessibility.AccessibilityNodeInfo root = service.getRootNode();
            if (root != null) {
                packageName = root.getPackageName() != null ? root.getPackageName().toString() : "";
                root.recycle();
            }
        }
        
        return jsonOk(new JSONObject().put("success", true).put("packageName", packageName));
    }
    
    // ==================== 最近任务 ====================
    
    private NanoHTTPD.Response handleAppRecent(NanoHTTPD.IHTTPSession session) throws Exception {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        JSONArray tasks = new JSONArray();
        
        if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<ActivityManager.AppTask> appTasks = am.getAppTasks();
            for (ActivityManager.AppTask task : appTasks) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                JSONObject taskJson = new JSONObject();
                if (info.baseActivity != null) {
                    taskJson.put("packageName", info.baseActivity.getPackageName());
                }
                tasks.put(taskJson);
            }
        }
        
        return jsonOk(new JSONObject().put("success", true).put("count", tasks.length()).put("tasks", tasks));
    }
    
    // ==================== Shell 执行 ====================
    
    private NanoHTTPD.Response handleShellExec(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String command = body.optString("command", "");
        
        if (command.isEmpty()) {
            return jsonError(400, "缺少 command 参数");
        }
        
        try {
            String result = executeShell(command);
            return jsonOk(new JSONObject().put("success", true).put("output", result));
        } catch (Exception e) {
            return jsonError(500, "执行失败: " + e.getMessage());
        }
    }
    
    // ==================== 剪贴板 ====================
    
    private NanoHTTPD.Response handleClipboardGet(NanoHTTPD.IHTTPSession session) throws Exception {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            String text = "";
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence cs = clip.getItemAt(0).getText();
                    text = cs != null ? cs.toString() : "";
                }
            }
            return jsonOk(new JSONObject().put("success", true).put("text", text));
        } catch (Exception e) {
            return jsonError(500, "获取剪贴板失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleClipboardSet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String text = body.optString("text", "");
        
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("text", text);
                clipboard.setPrimaryClip(clip);
                return jsonOk(new JSONObject().put("success", true).put("length", text.length()));
            }
            return jsonError(500, "无法访问剪贴板");
        } catch (Exception e) {
            return jsonError(500, "设置剪贴板失败: " + e.getMessage());
        }
    }
    
    // ==================== 媒体控制 ====================
    
    private NanoHTTPD.Response handleMediaVolume(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int volume = body.optInt("volume", -1);
        String stream = body.optString("stream", "music");
        
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return jsonError(500, "无法获取 AudioManager");
            
            int streamType = AudioManager.STREAM_MUSIC;
            switch (stream) {
                case "ring": streamType = AudioManager.STREAM_RING; break;
                case "alarm": streamType = AudioManager.STREAM_ALARM; break;
                case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
            }
            
            int maxVolume = am.getStreamMaxVolume(streamType);
            int currentVolume = am.getStreamVolume(streamType);
            
            if (volume >= 0) {
                am.setStreamVolume(streamType, Math.min(volume, maxVolume), AudioManager.FLAG_SHOW_UI);
                return jsonOk(new JSONObject().put("success", true).put("volume", volume).put("max", maxVolume));
            }
            
            return jsonOk(new JSONObject().put("success", true).put("volume", currentVolume).put("max", maxVolume));
        } catch (Exception e) {
            return jsonError(500, "音量控制失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleMediaBrightness(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int brightness = body.optInt("brightness", -1);
        
        try {
            if (brightness >= 0 && brightness <= 255) {
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
                return jsonOk(new JSONObject().put("success", true).put("brightness", brightness));
            }
            
            int current = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return jsonOk(new JSONObject().put("success", true).put("brightness", current));
        } catch (Exception e) {
            return jsonError(500, "亮度控制失败: " + e.getMessage());
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private String executeShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec("sh");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
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