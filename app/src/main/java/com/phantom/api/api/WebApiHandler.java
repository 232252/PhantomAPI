package com.phantom.api.api;

import android.util.Log;

import com.phantom.api.hook.WebViewHook;
import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Web API 处理器
 * 
 * 核心逻辑：
 * 1. LSPosed 模块在页面加载完时自动把 DOM 写到文件
 * 2. API 只需要读取文件即可
 * 3. 点击通过坐标 + Accessibility tap 实现
 */
public class WebApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Web";
    
    // 缓存最后一次读取的 DOM 数据
    private static String cachedDomJson = null;
    private static long cachedDomTime = 0;
    
    public WebApiHandler() {}
    
    public WebApiHandler(android.content.Context context) {}
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.endsWith("/detect")) {
            return handleDetect();
        } else if (uri.endsWith("/dom")) {
            return handleGetDom();
        } else if (uri.endsWith("/title")) {
            return handleGetTitle();
        } else if (uri.endsWith("/click")) {
            return handleWebClick(session);
        } else if (uri.endsWith("/find")) {
            return handleFind(session);
        } else {
            return handleDefault();
        }
    }
    
    private NanoHTTPD.Response handleDefault() {
        String json = "{\"success\":true,\"message\":\"Web API 可用\",\"endpoints\":[\"/detect\",\"/dom\",\"/title\",\"/click\",\"/find\"]}";
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8",
                json
        );
    }
    
    private NanoHTTPD.Response handleDetect() {
        File domFile = new File(WebViewHook.DOM_RESULT_FILE);
        boolean hasCachedDom = false;
        
        if (domFile.exists()) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(domFile.toPath(), BasicFileAttributes.class);
                long fileAge = System.currentTimeMillis() - attrs.lastModifiedTime().toMillis();
                hasCachedDom = fileAge < 30000;
            } catch (Exception e) {}
        }
        
        String json = String.format(
            "{\"method\":\"webview_js_inject\",\"status\":\"available\",\"hasCachedDom\":%b}",
            hasCachedDom
        );
        
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8",
                json
        );
    }
    
    public NanoHTTPD.Response handleGetDom() {
        File domFile = new File(WebViewHook.DOM_RESULT_FILE);
        
        if (domFile.exists()) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(domFile.toPath(), BasicFileAttributes.class);
                long fileAge = System.currentTimeMillis() - attrs.lastModifiedTime().toMillis();
                
                if (fileAge < 30000) {
                    String json = new String(Files.readAllBytes(domFile.toPath()), "UTF-8");
                    domFile.delete();
                    
                    cachedDomJson = json;
                    cachedDomTime = System.currentTimeMillis();
                    
                    Log.i(TAG, "DOM returned from cache (" + json.length() + " bytes)");
                    
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "application/json; charset=utf-8",
                            json
                    );
                } else {
                    domFile.delete();
                    Log.w(TAG, "DOM file expired (age: " + fileAge + "ms)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading DOM file: " + e.getMessage());
                domFile.delete();
            }
        }
        
        if (cachedDomJson != null && System.currentTimeMillis() - cachedDomTime < 30000) {
            Log.i(TAG, "DOM returned from memory cache");
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json; charset=utf-8",
                    cachedDomJson
            );
        }
        
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "application/json; charset=utf-8",
                "{\"error\":\"dom_unavailable\", \"hint\":\"请确保当前前台页面是基于 WebView 的网页，且已完全加载\"}"
        );
    }
    
    public NanoHTTPD.Response handleGetTitle() {
        try {
            String domJson = getDomJson();
            if (domJson != null) {
                JSONArray dom = new JSONArray(domJson);
                for (int i = 0; i < dom.length(); i++) {
                    JSONObject item = dom.getJSONObject(i);
                    String tag = item.optString("tag", "");
                    String text = item.optString("t", "");
                    if ("TITLE".equalsIgnoreCase(tag) || "H1".equalsIgnoreCase(tag)) {
                        JSONObject result = new JSONObject();
                        result.put("success", true);
                        result.put("title", text);
                        return HttpServerEngine.jsonSuccess(result);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting title: " + e.getMessage());
        }
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "无法获取页面标题");
    }
    
    public NanoHTTPD.Response handleFind(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session);
        String text = params.get("text");
        String tag = params.get("tag");
        
        String domJson = getDomJson();
        if (domJson == null) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                "DOM 数据不可用，请先加载页面");
        }
        
        JSONArray dom = new JSONArray(domJson);
        JSONArray results = new JSONArray();
        
        for (int i = 0; i < dom.length(); i++) {
            JSONObject item = dom.getJSONObject(i);
            boolean match = true;
            
            if (text != null && !text.isEmpty()) {
                String itemText = item.optString("t", "");
                if (!itemText.contains(text)) match = false;
            }
            
            if (tag != null && !tag.isEmpty()) {
                String itemTag = item.optString("tag", "");
                if (!itemTag.equalsIgnoreCase(tag)) match = false;
            }
            
            if (match) results.put(item);
        }
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("count", results.length());
        result.put("elements", results);
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * Web 点击 - 支持坐标/文本/索引三种模式
     */
    public NanoHTTPD.Response handleWebClick(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        
        // 模式1：坐标点击
        if (body.has("x") && body.has("y")) {
            int x = body.getInt("x");
            int y = body.getInt("y");
            return performTapClick(x, y);
        }
        
        // 模式2：文本点击
        if (body.has("text")) {
            String text = body.getString("text");
            boolean partial = body.optBoolean("partial", true);
            int index = body.optInt("index", 0);
            return performTextClick(text, partial, index);
        }
        
        // 模式3：索引点击
        if (body.has("index")) {
            int index = body.getInt("index");
            return performIndexClick(index);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, 
            "需要 x/y 或 text 或 index 参数");
    }
    
    private JSONObject performTapClickJson(int x, int y) throws Exception {
        PhantomAccessibilityService a11yService = PhantomAccessibilityService.getInstance();
        
        JSONObject result = new JSONObject();
        
        if (a11yService == null) {
            result.put("success", false);
            result.put("error", "无障碍服务未启用");
            return result;
        }
        
        boolean success = a11yService.clickAtPosition(x, y);
        
        result.put("success", success);
        result.put("method", "coordinate_tap");
        result.put("x", x);
        result.put("y", y);
        
        Log.i(TAG, "Web click at (" + x + ", " + y + "): " + success);
        
        return result;
    }
    
    private NanoHTTPD.Response performTapClick(int x, int y) throws Exception {
        JSONObject result = performTapClickJson(x, y);
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private NanoHTTPD.Response performTextClick(String text, boolean partial, int index) throws Exception {
        String domJson = getDomJson();
        if (domJson == null) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                "DOM 数据不可用");
        }
        
        JSONArray dom = new JSONArray(domJson);
        int matchCount = 0;
        
        for (int i = 0; i < dom.length(); i++) {
            JSONObject item = dom.getJSONObject(i);
            String itemText = item.optString("t", "");
            
            boolean match = partial ? itemText.contains(text) : itemText.equals(text);
            
            if (match) {
                if (matchCount == index) {
                    int x = item.getInt("x") + item.getInt("w") / 2;
                    int y = item.getInt("y") + item.getInt("h") / 2;
                    
                    JSONObject result = new JSONObject();
                    result.put("found", true);
                    result.put("matched_text", itemText);
                    result.put("center", x + "," + y);
                    
                    result.put("click_result", performTapClickJson(x, y));
                    
                    return HttpServerEngine.jsonSuccess(result);
                }
                matchCount++;
            }
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
            "未找到文本: " + text + " (匹配数: " + matchCount + ")");
    }
    
    private NanoHTTPD.Response performIndexClick(int index) throws Exception {
        String domJson = getDomJson();
        if (domJson == null) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                "DOM 数据不可用");
        }
        
        JSONArray dom = new JSONArray(domJson);
        
        if (index < 0 || index >= dom.length()) {
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, 
                "索引越界: " + index);
        }
        
        JSONObject item = dom.getJSONObject(index);
        int x = item.getInt("x") + item.getInt("w") / 2;
        int y = item.getInt("y") + item.getInt("h") / 2;
        
        JSONObject result = new JSONObject();
        result.put("index", index);
        result.put("text", item.optString("t", ""));
        result.put("center", x + "," + y);
        
        result.put("click_result", performTapClickJson(x, y));
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private String getDomJson() {
        File domFile = new File(WebViewHook.DOM_RESULT_FILE);
        if (domFile.exists()) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(domFile.toPath(), BasicFileAttributes.class);
                long fileAge = System.currentTimeMillis() - attrs.lastModifiedTime().toMillis();
                
                if (fileAge < 30000) {
                    String json = new String(Files.readAllBytes(domFile.toPath()), "UTF-8");
                    cachedDomJson = json;
                    cachedDomTime = System.currentTimeMillis();
                    return json;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading DOM: " + e.getMessage());
            }
        }
        
        if (cachedDomJson != null && System.currentTimeMillis() - cachedDomTime < 60000) {
            return cachedDomJson;
        }
        
        return null;
    }
    
    private JSONObject parseJsonBody(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");
        return new JSONObject(body != null ? body : "{}");
    }
    
    private Map<String, String> parseQueryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, String> entry : session.getParms().entrySet()) {
            params.put(entry.getKey(), entry.getValue());
        }
        return params;
    }
}
