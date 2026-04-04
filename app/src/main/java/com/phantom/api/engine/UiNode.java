package com.phantom.api.engine;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

/**
 * UI 节点封装类
 */
public class UiNode {
    private final AccessibilityNodeInfo nodeInfo;
    
    public UiNode(AccessibilityNodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }
    
    public String getText() {
        CharSequence text = nodeInfo.getText();
        return text != null ? text.toString() : null;
    }
    
    public String getContentDescription() {
        CharSequence desc = nodeInfo.getContentDescription();
        return desc != null ? desc.toString() : null;
    }
    
    public String getClassName() {
        CharSequence className = nodeInfo.getClassName();
        return className != null ? className.toString() : null;
    }
    
    public String getPackageName() {
        CharSequence packageName = nodeInfo.getPackageName();
        return packageName != null ? packageName.toString() : null;
    }
    
    public Rect getBounds() {
        Rect bounds = new Rect();
        nodeInfo.getBoundsInScreen(bounds);
        return bounds;
    }
    
    public boolean isClickable() {
        return nodeInfo.isClickable();
    }
    
    public boolean isScrollable() {
        return nodeInfo.isScrollable();
    }
    
    public boolean isEditable() {
        return nodeInfo.isEditable();
    }
    
    public boolean isEnabled() {
        return nodeInfo.isEnabled();
    }
    
    public boolean isVisible() {
        return nodeInfo.isVisibleToUser();
    }
    
    public int getChildCount() {
        return nodeInfo.getChildCount();
    }
    
    public UiNode getChild(int index) {
        AccessibilityNodeInfo child = nodeInfo.getChild(index);
        return child != null ? new UiNode(child) : null;
    }
    
    public AccessibilityNodeInfo getNodeInfo() {
        return nodeInfo;
    }
    
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("text", getText());
            json.put("contentDescription", getContentDescription());
            json.put("className", getClassName());
            json.put("packageName", getPackageName());
            json.put("clickable", isClickable());
            json.put("scrollable", isScrollable());
            json.put("editable", isEditable());
            json.put("enabled", isEnabled());
            json.put("visible", isVisible());
            json.put("childCount", getChildCount());
            
            Rect bounds = getBounds();
            JSONObject boundsJson = new JSONObject();
            boundsJson.put("left", bounds.left);
            boundsJson.put("top", bounds.top);
            boundsJson.put("right", bounds.right);
            boundsJson.put("bottom", bounds.bottom);
            json.put("bounds", boundsJson);
        } catch (Exception e) {}
        return json;
    }
}
