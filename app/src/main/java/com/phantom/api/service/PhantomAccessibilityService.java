package com.phantom.api.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PhantomAPI 无障碍服务
 * 负责原生 UI 节点感知和节点级操作
 */
public class PhantomAccessibilityService extends AccessibilityService {
    private static final String TAG = "PhantomA11y";
    
    private static PhantomAccessibilityService instance;
    private static final Object lock = new Object();
    
    private AccessibilityNodeInfo cachedRootNode;
    private long lastUpdateTime = 0;
    private static final long CACHE_TTL = 500; // 缓存 500ms
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "无障碍服务创建");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "无障碍服务已连接");
        instance = this;
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 更新缓存
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务中断");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "无障碍服务销毁");
        synchronized (lock) {
            instance = null;
        }
    }
    
    /**
     * 获取服务实例
     */
    public static PhantomAccessibilityService getInstance() {
        synchronized (lock) {
            return instance;
        }
    }
    
    /**
     * 检查服务是否可用
     */
    public static boolean isAvailable() {
        return getInstance() != null;
    }
    
    /**
     * 获取根节点
     */
    public AccessibilityNodeInfo getRootNode() {
        return getRootInActiveWindow();
    }
    
    /**
     * 获取节点树 JSON
     */
    public String getNodeTreeJson() {
        AccessibilityNodeInfo root = getRootNode();
        if (root == null) {
            return "{\"error\": \"无法获取根节点\"}";
        }
        
        try {
            JSONObject tree = nodeToJson(root, 0);
            return tree.toString(2);
        } catch (Exception e) {
            Log.e(TAG, "转换节点树失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        } finally {
            root.recycle();
        }
    }
    
    /**
     * 将节点转换为 JSON
     */
    private JSONObject nodeToJson(AccessibilityNodeInfo node, int depth) throws Exception {
        JSONObject json = new JSONObject();
        
        // 基本信息
        json.put("depth", depth);
        json.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
        json.put("packageName", node.getPackageName() != null ? node.getPackageName().toString() : "");
        json.put("text", node.getText() != null ? node.getText().toString() : "");
        json.put("contentDescription", node.getContentDescription() != null ? 
            node.getContentDescription().toString() : "");
        json.put("viewIdResourceName", node.getViewIdResourceName() != null ? 
            node.getViewIdResourceName() : "");
        
        // 状态信息
        json.put("clickable", node.isClickable());
        json.put("scrollable", node.isScrollable());
        json.put("checkable", node.isCheckable());
        json.put("checked", node.isChecked());
        json.put("enabled", node.isEnabled());
        json.put("focusable", node.isFocusable());
        json.put("focused", node.isFocused());
        json.put("selected", node.isSelected());
        json.put("visible", node.isVisibleToUser());
        
        // 坐标信息
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        JSONObject boundsJson = new JSONObject();
        boundsJson.put("left", bounds.left);
        boundsJson.put("top", bounds.top);
        boundsJson.put("right", bounds.right);
        boundsJson.put("bottom", bounds.bottom);
        boundsJson.put("centerX", bounds.centerX());
        boundsJson.put("centerY", bounds.centerY());
        boundsJson.put("width", bounds.width());
        boundsJson.put("height", bounds.height());
        json.put("bounds", boundsJson);
        
        // 子节点
        JSONArray children = new JSONArray();
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                children.put(nodeToJson(child, depth + 1));
                child.recycle();
            }
        }
        json.put("children", children);
        json.put("childCount", childCount);
        
        return json;
    }
    
    /**
     * 按文本查找节点
     */
    public List<AccessibilityNodeInfo> findNodesByText(String text) {
        AccessibilityNodeInfo root = getRootNode();
        if (root == null) {
            return new ArrayList<>();
        }
        
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        findNodesByTextRecursive(root, text, result);
        root.recycle();
        return result;
    }
    
    private void findNodesByTextRecursive(AccessibilityNodeInfo node, String text, 
            List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        
        String nodeText = node.getText() != null ? node.getText().toString() : "";
        String nodeDesc = node.getContentDescription() != null ? 
            node.getContentDescription().toString() : "";
        
        if (nodeText.contains(text) || nodeDesc.contains(text)) {
            result.add(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findNodesByTextRecursive(child, text, result);
                // 注意：这里不回收 child，由调用者负责回收
            }
        }
    }
    
    /**
     * 按资源 ID 查找节点
     */
    public List<AccessibilityNodeInfo> findNodesById(String id) {
        AccessibilityNodeInfo root = getRootNode();
        if (root == null) {
            return new ArrayList<>();
        }
        
        List<AccessibilityNodeInfo> result = root.findAccessibilityNodeInfosByViewId(id);
        root.recycle();
        return result;
    }
    
    /**
     * 点击节点
     */
    public boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        // 尝试直接点击
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        
        // 查找可点击的父节点
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                boolean clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                return clicked;
            }
            AccessibilityNodeInfo nextParent = parent.getParent();
            parent.recycle();
            parent = nextParent;
        }
        
        // 使用坐标点击
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return clickAtPosition(bounds.centerX(), bounds.centerY());
    }
    
    /**
     * 在指定坐标点击
     */
    public boolean clickAtPosition(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return performGestureClick(x, y);
        }
        return false;
    }
    
    /**
     * 在指定坐标长按
     */
    public boolean performLongClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        // 长按 500ms
        GestureDescription.StrokeDescription longClickStroke = 
            new GestureDescription.StrokeDescription(clickPath, 0, 500);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(longClickStroke)
            .build();
        
        return dispatchGesture(gesture, null, null);
    }
    
    /**
     * 使用手势执行点击 (Android 7.0+)
     */
    private boolean performGestureClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        GestureDescription.StrokeDescription clickStroke = 
            new GestureDescription.StrokeDescription(clickPath, 0, 100);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(clickStroke)
            .build();
        
        return dispatchGesture(gesture, null, null);
    }
    
    /**
     * 执行滑动手势
     */
    public boolean performSwipe(int startX, int startY, int endX, int endY, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        
        GestureDescription.StrokeDescription swipeStroke = 
            new GestureDescription.StrokeDescription(swipePath, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(swipeStroke)
            .build();
        
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};
        
        GestureResultCallback callback = new GestureResultCallback() {
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
        
        dispatchGesture(gesture, callback, null);
        
        try {
            latch.await(durationMs + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        
        return result[0];
    }
    
    /**
     * 输入文本
     */
    public boolean inputText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        
        // 先聚焦
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        
        // 清空现有文本
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle.EMPTY);
        
        // 设置新文本
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
    
    /**
     * 执行返回操作
     */
    public boolean performBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }
    
    /**
     * 执行 Home 操作
     */
    public boolean performHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }
    
    /**
     * 执行最近任务操作
     */
    public boolean performRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
    
    /**
     * 打开通知栏
     */
    public boolean performNotifications() {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }
    
    /**
     * 打开快捷设置
     */
    public boolean performQuickSettings() {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }
    
    /**
     * 截屏 (Android 9.0+)
     */
    public boolean performScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        }
        return false;
    }
}
