package com.phantom.api.api;

import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;

import fi.iki.elonen.NanoHTTPD;

/**
 * Chrome CDP API 处理器
 * 
 * 通过 Chrome DevTools Protocol 获取 Chrome/Chromium 浏览器的页面信息
 * 
 * Chrome 使用 Unix Domain Socket: @chrome_devtools_remote
 * CDP 协议基于 WebSocket，但 HTTP 端点也可以直接访问
 */
public class ChromeCdpHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-CDP";
    
    // Chrome DevTools Unix Socket
    private static final String CHROME_SOCKET = "@chrome_devtools_remote";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.endsWith("/cdp/pages")) {
            return handleGetPages();
        } else if (uri.endsWith("/cdp/dom")) {
            return handleGetDom(session);
        } else if (uri.endsWith("/cdp/title")) {
            return handleGetTitle();
        } else {
            return handleDefault();
        }
    }
    
    private NanoHTTPD.Response handleDefault() {
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("message", "Chrome CDP API 可用");
            result.put("endpoints", new JSONArray()
                .put("/cdp/pages")
                .put("/cdp/dom")
                .put("/cdp/title"));
        } catch (Exception e) {}
        
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * 获取 Chrome 打开的页面列表
     */
    private NanoHTTPD.Response handleGetPages() {
        try {
            String response = sendCdpRequest("/json");
            
            if (response == null) {
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                    "无法连接 Chrome DevTools，请确保 Chrome 正在运行");
            }
            
            JSONArray pages = new JSONArray(response);
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("count", pages.length());
            result.put("pages", pages);
            
            return HttpServerEngine.jsonSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "Get pages error: " + e.getMessage());
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "获取页面列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前页面的 DOM
     */
    private NanoHTTPD.Response handleGetDom(NanoHTTPD.IHTTPSession session) throws Exception {
        try {
            // 1. 获取页面列表
            String pagesResponse = sendCdpRequest("/json");
            if (pagesResponse == null) {
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                    "无法连接 Chrome DevTools");
            }
            
            JSONArray pages = new JSONArray(pagesResponse);
            if (pages.length() == 0) {
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                    "Chrome 没有打开任何页面");
            }
            
            // 2. 获取第一个页面的信息
            JSONObject firstPage = pages.getJSONObject(0);
            String webSocketUrl = firstPage.optString("webSocketDebuggerUrl", "");
            
            if (webSocketUrl.isEmpty()) {
                // 备选方案：使用 HTTP 接口
                String dom = getDomViaHttp(firstPage);
                if (dom != null) {
                    return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        "application/json; charset=utf-8",
                        dom
                    );
                }
                
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                    "无法获取 DOM");
            }
            
            // 3. 通过 WebSocket 获取 DOM
            // TODO: 实现 WebSocket 通信
            // 目前先返回 Accessibility 方案的结果
            
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_IMPLEMENTED, 
                "WebSocket CDP 尚未实现，请使用 /api/ui/tree 作为备选方案");
            
        } catch (Exception e) {
            Log.e(TAG, "Get DOM error: " + e.getMessage());
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "获取 DOM 失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前页面标题
     */
    private NanoHTTPD.Response handleGetTitle() {
        try {
            String pagesResponse = sendCdpRequest("/json");
            if (pagesResponse == null) {
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                    "无法连接 Chrome DevTools");
            }
            
            JSONArray pages = new JSONArray(pagesResponse);
            if (pages.length() == 0) {
                return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, 
                    "Chrome 没有打开任何页面");
            }
            
            JSONObject firstPage = pages.getJSONObject(0);
            String title = firstPage.optString("title", "");
            String url = firstPage.optString("url", "");
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("title", title);
            result.put("url", url);
            
            return HttpServerEngine.jsonSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "Get title error: " + e.getMessage());
            return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, 
                "获取标题失败: " + e.getMessage());
        }
    }
    
    /**
     * 通过 HTTP 接口获取 DOM
     */
    private String getDomViaHttp(JSONObject pageInfo) {
        // 暂时返回 null，需要 WebSocket 才能获取完整 DOM
        return null;
    }
    
    /**
     * 发送 CDP HTTP 请求
     * 
     * 注意：Chrome DevTools 使用 Unix Domain Socket
     * 需要通过 shell 命令或 native 代码访问
     */
    private String sendCdpRequest(String endpoint) {
        try {
            // 方法1：通过 shell 执行 curl
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c", 
                "curl --abstract-unix-socket chrome_devtools_remote http://localhost" + endpoint
            });
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0 && output.length() > 0) {
                Log.i(TAG, "CDP response: " + output.substring(0, Math.min(100, output.length())));
                return output.toString();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "CDP request error: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检查 Chrome DevTools 是否可用
     */
    public static boolean isChromeDevToolsAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c", 
                "cat /proc/net/unix | grep chrome_devtools"
            });
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            return line != null && line.contains("chrome_devtools");
            
        } catch (Exception e) {
            return false;
        }
    }
}
