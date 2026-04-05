package com.phantom.api.engine;

import android.util.Log;

import com.phantom.api.utils.PhantomIpc;

/**
 * WebView 引擎 - 重构版
 * 使用固定文件名的 IPC 方式
 */
public class WebViewEngine {
    private static final String TAG = "WebViewEngine";
    
    /**
     * 获取 DOM 数据
     * @return DOM JSON 字符串
     */
    public String getDom() {
        Log.i(TAG, "开始获取 DOM");
        
        // 使用新的 IPC 方式
        PhantomIpc.cleanUp();
        PhantomIpc.sendCommand("{\"action\":\"capture_dom\"}");
        
        String result = PhantomIpc.waitForResult(3000);
        
        if (result != null) {
            Log.i(TAG, "DOM 获取成功: " + result.substring(0, Math.min(100, result.length())));
        } else {
            Log.w(TAG, "DOM 获取超时");
        }
        
        return result;
    }
    
    /**
     * 获取页面标题
     */
    public String getTitle() {
        Log.i(TAG, "开始获取标题");
        
        PhantomIpc.cleanUp();
        PhantomIpc.sendCommand("{\"action\":\"get_title\"}");
        
        return PhantomIpc.waitForResult(2000);
    }
    
    /**
     * 在 WebView 中点击指定坐标
     */
    public boolean clickAt(int x, int y) {
        Log.i(TAG, "WebView 点击: (" + x + ", " + y + ")");
        
        PhantomIpc.cleanUp();
        PhantomIpc.sendCommand("{\"action\":\"clickAt\",\"x\":" + x + ",\"y\":" + y + "}");
        
        String result = PhantomIpc.waitForResult(2000);
        return "true".equals(result);
    }
    
    /**
     * 执行 JavaScript
     */
    public String evalJs(String code) {
        Log.i(TAG, "执行 JS: " + code);
        
        try {
            org.json.JSONObject cmd = new org.json.JSONObject();
            cmd.put("action", "eval");
            cmd.put("code", code);
            
            PhantomIpc.cleanUp();
            PhantomIpc.sendCommand(cmd.toString());
            
            return PhantomIpc.waitForResult(2000);
        } catch (Exception e) {
            Log.e(TAG, "执行 JS 失败: " + e.getMessage());
            return null;
        }
    }
}
