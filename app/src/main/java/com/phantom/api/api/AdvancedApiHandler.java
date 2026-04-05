package com.phantom.api.api;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

/**
 * 高级 UI 操作 API 处理器
 * 
 * 端点：
 * /api/advanced/input - 文本输入（支持中文，通过剪贴板）
 * /api/advanced/clear - 清除输入框
 * /api/advanced/long_press - 长按
 * /api/advanced/double_tap - 双击
 * /api/advanced/drag - 拖拽
 * /api/advanced/pinch - 缩放手势
 * /api/advanced/keyevent - 按键事件
 * /api/advanced/scroll_to - 滚动到指定元素
 * /api/advanced/batch - 批量操作
 */
public class AdvancedApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Advanced";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
        if (a11yService == null) {
            return jsonError(503, "无障碍服务未启用");
        }
        
        switch (uri) {
            case "/api/advanced/input":
                return handleInput(a11yService, session);
            case "/api/advanced/clear":
                return handleClear(a11yService, session);
            case "/api/advanced/long_press":
                return handleLongPress(a11yService, session);
            case "/api/advanced/double_tap":
                return handleDoubleTap(a11yService, session);
            case "/api/advanced/drag":
                return handleDrag(a11yService, session);
            case "/api/advanced/pinch":
                return handlePinch(a11yService, session);
            case "/api/advanced/keyevent":
                return handleKeyevent(a11yService, session);
            case "/api/advanced/scroll_to":
                return handleScrollTo(a11yService, session);
            case "/api/advanced/batch":
                return handleBatch(a11yService, session);
            default:
                return jsonError(404, "Unknown: " + uri);
        }
    }
    
    // ==================== 文本输入（支持中文） ====================
    
    private NanoHTTPD.Response handleInput(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String text = body.optString("text", "");
        
        if (text.isEmpty()) {
            return jsonError(400, "缺少 text 参数");
        }
        
        try {
            // 通过剪贴板实现中文输入
            ClipboardManager clipboard = (ClipboardManager) service.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", text);
            clipboard.setPrimaryClip(clip);
            
            Thread.sleep(100);
            
            // 查找可编辑节点并粘贴
            AccessibilityNodeInfo rootNode = service.getRootNode();
            if (rootNode != null) {
                AccessibilityNodeInfo focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focusNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    focusNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    focusNode.recycle();
                    return jsonOk(new JSONObject()
                        .put("success", true)
                        .put("method", "clipboard_paste")
                        .put("length", text.length()));
                }
            }
            
            return jsonOk(new JSONObject()
                .put("success", true)
                .put("method", "clipboard_set")
                .put("message", "文本已复制到剪贴板"));
                
        } catch (Exception e) {
            Log.e(TAG, "输入失败", e);
            return jsonError(500, "输入失败: " + e.getMessage());
        }
    }
    
    // ==================== 清除输入框 ====================
    
    private NanoHTTPD.Response handleClear(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        try {
            AccessibilityNodeInfo rootNode = service.getRootNode();
            if (rootNode == null) {
                return jsonError(500, "无法获取根节点");
            }
            
            AccessibilityNodeInfo focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focusNode != null) {
                // 全选 + 剪切
                Bundle args = new Bundle();
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Integer.MAX_VALUE);
                focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
                focusNode.performAction(AccessibilityNodeInfo.ACTION_CUT);
                focusNode.recycle();
                
                return jsonOk(new JSONObject().put("success", true).put("action", "clear"));
            }
            
            return jsonError(404, "未找到输入焦点");
        } catch (Exception e) {
            return jsonError(500, "清除失败: " + e.getMessage());
        }
    }
    
    // ==================== 长按 ====================
    
    private NanoHTTPD.Response handleLongPress(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int x = body.optInt("x", -1);
        int y = body.optInt("y", -1);
        String text = body.optString("text", "");
        int duration = body.optInt("duration", 1000);
        
        if (!text.isEmpty() && x < 0) {
            int[] coords = findTextCoords(service, text);
            if (coords != null) { x = coords[0]; y = coords[1]; }
        }
        
        if (x < 0 || y < 0) return jsonError(400, "需要有效坐标或文本");
        
        final int fx = x, fy = y;
        boolean success = performGesture(service, gesture -> {
            Path path = new Path();
            path.moveTo(fx, fy);
            gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        });
        
        return jsonOk(new JSONObject().put("success", success).put("action", "long_press").put("x", x).put("y", y));
    }
    
    // ==================== 双击 ====================
    
    private NanoHTTPD.Response handleDoubleTap(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int x = body.optInt("x", -1);
        int y = body.optInt("y", -1);
        String text = body.optString("text", "");
        
        if (!text.isEmpty() && x < 0) {
            int[] coords = findTextCoords(service, text);
            if (coords != null) { x = coords[0]; y = coords[1]; }
        }
        
        if (x < 0 || y < 0) return jsonError(400, "需要有效坐标或文本");
        
        final int fx = x, fy = y;
        boolean success = performGesture(service, gesture -> {
            Path path1 = new Path(); path1.moveTo(fx, fy);
            Path path2 = new Path(); path2.moveTo(fx, fy);
            gesture.addStroke(new GestureDescription.StrokeDescription(path1, 0, 50));
            gesture.addStroke(new GestureDescription.StrokeDescription(path2, 100, 50));
        });
        
        return jsonOk(new JSONObject().put("success", success).put("action", "double_tap").put("x", x).put("y", y));
    }
    
    // ==================== 拖拽 ====================
    
    private NanoHTTPD.Response handleDrag(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int startX = body.optInt("startX", -1);
        int startY = body.optInt("startY", -1);
        int endX = body.optInt("endX", -1);
        int endY = body.optInt("endY", -1);
        int duration = body.optInt("duration", 500);
        
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return jsonError(400, "需要起始和结束坐标");
        }
        
        boolean success = performGesture(service, gesture -> {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        });
        
        return jsonOk(new JSONObject().put("success", success).put("action", "drag").put("from", startX+","+startY).put("to", endX+","+endY));
    }
    
    // ==================== 缩放手势 ====================
    
    private NanoHTTPD.Response handlePinch(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int centerX = body.optInt("centerX", 540);
        int centerY = body.optInt("centerY", 1200);
        String direction = body.optString("direction", "out");
        int distance = body.optInt("distance", 200);
        int duration = body.optInt("duration", 500);
        
        int offset = distance / 2;
        int startY1, endY1, startY2, endY2;
        
        if (direction.equals("out")) {
            startY1 = centerY; endY1 = centerY - offset;
            startY2 = centerY; endY2 = centerY + offset;
        } else {
            startY1 = centerY - offset; endY1 = centerY;
            startY2 = centerY + offset; endY2 = centerY;
        }
        
        final int fx = centerX, fsy1 = startY1, fey1 = endY1, fsy2 = startY2, fey2 = endY2, fd = duration;
        boolean success = performGesture(service, gesture -> {
            Path path1 = new Path(); path1.moveTo(fx, fsy1); path1.lineTo(fx, fey1);
            Path path2 = new Path(); path2.moveTo(fx, fsy2); path2.lineTo(fx, fey2);
            gesture.addStroke(new GestureDescription.StrokeDescription(path1, 0, fd));
            gesture.addStroke(new GestureDescription.StrokeDescription(path2, 0, fd));
        });
        
        return jsonOk(new JSONObject().put("success", success).put("action", "pinch").put("direction", direction));
    }
    
    // ==================== 按键事件 ====================
    
    private NanoHTTPD.Response handleKeyevent(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        int keycode = body.optInt("keycode", -1);
        String key = body.optString("key", "");
        
        if (keycode < 0 && !key.isEmpty()) {
            keycode = keycodeFromName(key);
        }
        
        if (keycode < 0) return jsonError(400, "需要 keycode 或 key 参数");
        
        int globalAction = keycodeToGlobalAction(keycode);
        if (globalAction < 0) return jsonError(400, "不支持的按键: " + keycode);
        
        boolean success = service.performGlobalAction(globalAction);
        return jsonOk(new JSONObject().put("success", success).put("action", "keyevent").put("key", key).put("keycode", keycode));
    }
    
    // ==================== 滚动到元素 ====================
    
    private NanoHTTPD.Response handleScrollTo(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String text = body.optString("text", "");
        int maxScrolls = body.optInt("max_scrolls", 10);
        String direction = body.optString("direction", "down");
        
        if (text.isEmpty()) return jsonError(400, "需要 text 参数");
        
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode == null) return jsonError(500, "无法获取根节点");
        
        // 先查找元素
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
        if (!nodes.isEmpty()) {
            return jsonOk(new JSONObject().put("success", true).put("found", true).put("scrolls", 0));
        }
        
        // 滚动查找
        int scrolls = 0;
        while (scrolls < maxScrolls) {
            int startY = direction.equals("down") ? 1800 : 600;
            int endY = direction.equals("down") ? 600 : 1800;
            
            final int fsy = startY, fey = endY;
            performGesture(service, gesture -> {
                Path path = new Path();
                path.moveTo(540, fsy);
                path.lineTo(540, fey);
                gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
            });
            
            Thread.sleep(500);
            scrolls++;
            
            rootNode = service.getRootNode();
            if (rootNode != null) {
                nodes = rootNode.findAccessibilityNodeInfosByText(text);
                if (!nodes.isEmpty()) {
                    return jsonOk(new JSONObject().put("success", true).put("found", true).put("scrolls", scrolls));
                }
            }
        }
        
        return jsonOk(new JSONObject().put("success", false).put("found", false).put("scrolls", scrolls).put("reason", "not_found_after_max_scrolls"));
    }
    
    // ==================== 批量操作 ====================
    
    private NanoHTTPD.Response handleBatch(PhantomAccessibilityService service, NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        JSONArray actions = body.optJSONArray("actions");
        
        if (actions == null || actions.length() == 0) {
            return jsonError(400, "需要 actions 数组");
        }
        
        JSONArray results = new JSONArray();
        
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String type = action.optString("type", "");
            JSONObject result = new JSONObject();
            
            try {
                switch (type) {
                    case "tap":
                        int tx = action.optInt("x", -1);
                        int ty = action.optInt("y", -1);
                        if (tx >= 0 && ty >= 0) {
                            performGesture(service, gesture -> {
                                Path path = new Path();
                                path.moveTo(tx, ty);
                                gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
                            });
                            result.put("success", true);
                        }
                        break;
                    case "swipe":
                        int sx = action.optInt("startX", 540);
                        int sy = action.optInt("startY", 1500);
                        int ex = action.optInt("endX", 540);
                        int ey = action.optInt("endY", 500);
                        performGesture(service, gesture -> {
                            Path path = new Path();
                            path.moveTo(sx, sy);
                            path.lineTo(ex, ey);
                            gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, 300));
                        });
                        result.put("success", true);
                        break;
                    case "wait":
                        int ms = action.optInt("ms", 500);
                        Thread.sleep(ms);
                        result.put("success", true);
                        break;
                    default:
                        result.put("success", false).put("error", "Unknown action: " + type);
                }
            } catch (Exception e) {
                result.put("success", false).put("error", e.getMessage());
            }
            
            results.put(result);
        }
        
        return jsonOk(new JSONObject().put("success", true).put("results", results));
    }
    
    // ==================== 辅助方法 ====================
    
    private interface GestureBuilder {
        void build(GestureDescription.Builder builder);
    }
    
    private boolean performGesture(PhantomAccessibilityService service, GestureBuilder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        
        try {
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            builder.build(gestureBuilder);
            GestureDescription gesture = gestureBuilder.build();
            
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] result = {false};
            
            AccessibilityService.GestureResultCallback callback = new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    result[0] = true;
                    latch.countDown();
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    result[0] = false;
                    latch.countDown();
                }
            };
            
            service.dispatchGesture(gesture, callback, null);
            
            latch.await(5, TimeUnit.SECONDS);
            return result[0];
        } catch (Exception e) {
            Log.e(TAG, "手势执行失败", e);
            return false;
        }
    }
    
    private int[] findTextCoords(PhantomAccessibilityService service, String text) {
        AccessibilityNodeInfo rootNode = service.getRootNode();
        if (rootNode == null) return null;
        
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
        if (nodes.isEmpty()) return null;
        
        Rect bounds = new Rect();
        nodes.get(0).getBoundsInScreen(bounds);
        return new int[]{bounds.centerX(), bounds.centerY()};
    }
    
    private int keycodeFromName(String name) {
        switch (name.toLowerCase()) {
            case "home": return 3;
            case "back": return 4;
            case "menu": return 82;
            case "enter": return 66;
            case "del": case "delete": return 67;
            case "volume_up": return 24;
            case "volume_down": return 25;
            case "power": return 26;
            case "tab": return 61;
            case "dpad_up": return 19;
            case "dpad_down": return 20;
            case "dpad_left": return 21;
            case "dpad_right": return 22;
            default: return -1;
        }
    }
    
    private int keycodeToGlobalAction(int keycode) {
        switch (keycode) {
            case 3: // HOME
                return AccessibilityService.GLOBAL_ACTION_HOME;
            case 4: // BACK
                return AccessibilityService.GLOBAL_ACTION_BACK;
            case 82: // MENU
                return AccessibilityService.GLOBAL_ACTION_RECENTS;
            case 25: // VOLUME_DOWN
                return AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
            case 26: // POWER
                return AccessibilityService.GLOBAL_ACTION_POWER_DIALOG;
            default:
                return -1;
        }
    }
    
    private JSONObject parseBody(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = session.getQueryParameterString();
        if (body == null || body.isEmpty()) {
            try {
                java.util.Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                body = files.get("postData");
            } catch (Exception e) {
                // ignore
            }
        }
        
        if (body == null || body.isEmpty()) {
            return new JSONObject();
        }
        
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            data.toString()
        );
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("success", false);
            obj.put("error", msg);
            obj.put("code", code);
        } catch (Exception e) {
            // ignore
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            obj.toString()
        );
    }
}