package com.phantom.api.engine;

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生 UI 引擎
 * 负责 UI 节点遍历、查找和操作
 */
public class NativeUiEngine {
    private static final String TAG = "NativeUiEngine";
    
    /**
     * 获取当前 UI 树
     */
    public static JSONObject getUiTree() {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            JSONObject error = new JSONObject();
            try {
                error.put("error", "Accessibility service not available");
            } catch (Exception e) {}
            return error;
        }
        
        try {
            String treeJson = service.getNodeTreeJson();
            return new JSONObject(treeJson);
        } catch (Exception e) {
            Log.e(TAG, "获取 UI 树失败", e);
            return new JSONObject();
        }
    }
    
    /**
     * 查找节点
     */
    public static List<AccessibilityNodeInfo> findNodesByText(String text) {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return new ArrayList<>();
        }
        return service.findNodesByText(text);
    }
    
    /**
     * 查找节点通过 ID
     */
    public static List<AccessibilityNodeInfo> findNodesById(String id) {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return new ArrayList<>();
        }
        return service.findNodesById(id);
    }
    
    /**
     * 点击节点
     */
    public static boolean clickNode(AccessibilityNodeInfo node) {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return false;
        }
        return service.clickNode(node);
    }
    
    /**
     * 点击坐标
     */
    public static boolean clickAt(int x, int y) {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return false;
        }
        return service.clickAtPosition(x, y);
    }
    
    /**
     * 滑动
     */
    public static boolean swipe(int startX, int startY, int endX, int endY, long duration) {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return false;
        }
        return service.performSwipe(startX, startY, endX, endY, duration);
    }
    
    /**
     * 返回
     */
    public static boolean goBack() {
        PhantomAccessibilityService service = PhantomAccessibilityService.getInstance();
        if (service == null) {
            return false;
        }
        return service.performBack();
    }
}
