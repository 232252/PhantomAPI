package com.phantom.api.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Chrome CDP 桥接器 - 使用 LocalSocket WebSocket 实现
 */
public class CdpBridge {
    private static final String TAG = "CdpBridge";
    private static final String SOCKET_NAME = "chrome_devtools_remote";
    private static final String CURL_PATH = "/system/bin/curl";
    
    private CdpWebSocketClient wsClient;
    private String currentPageId;
    
    /**
     * 检测 Chrome DevTools 是否可用
     */
    public boolean isAvailable() {
        try {
            String cmd = CURL_PATH + " -s --abstract-unix-socket " + SOCKET_NAME + " http://localhost/json";
            Log.i(TAG, "执行检测命令: " + cmd);
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            int exitCode = p.exitValue();
            String output = sb.toString();
            
            Log.i(TAG, "检测结果: exitCode=" + exitCode + ", output=" + output.substring(0, Math.min(100, output.length())));
            
            if (finished && exitCode == 0 && output.startsWith("[")) {
                Log.i(TAG, "Chrome DevTools Socket 可用");
                return true;
            } else {
                Log.w(TAG, "Chrome DevTools Socket 不可用: exitCode=" + exitCode);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Chrome DevTools Socket 检测失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取页面列表 (HTTP /json)
     */
    public String getPageList() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                CURL_PATH + " -s --abstract-unix-socket " + SOCKET_NAME + " http://localhost/json"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            p.waitFor(3, TimeUnit.SECONDS);
            String result = sb.toString();
            Log.i(TAG, "获取页面列表成功: " + result.length() + " 字节");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "获取页面列表失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取第一个页面的 ID
     */
    private String getFirstPageId() {
        String pages = getPageList();
        if (pages == null) {
            Log.w(TAG, "页面列表为空");
            return null;
        }
        
        String trimmed = pages.trim();
        if (!trimmed.startsWith("[")) {
            Log.w(TAG, "页面列表格式无效: " + trimmed.substring(0, Math.min(50, trimmed.length())));
            return null;
        }
        
        // 简单解析 JSON 获取第一个页面 ID
        int idIdx = trimmed.indexOf("\"id\":");
        if (idIdx != -1) {
            int startQuote = trimmed.indexOf("\"", idIdx + 5);
            if (startQuote != -1) {
                int endQuote = trimmed.indexOf("\"", startQuote + 1);
                if (endQuote > startQuote) {
                    String pageId = trimmed.substring(startQuote + 1, endQuote);
                    Log.i(TAG, "找到页面 ID: " + pageId);
                    return pageId;
                }
            }
        }
        Log.w(TAG, "未找到页面 ID");
        return null;
    }
    
    /**
     * 确保 WebSocket 连接可用
     */
    private boolean ensureConnection() {
        // 如果已有连接，检查是否仍然有效
        if (wsClient != null && currentPageId != null) {
            return true;
        }
        
        // 获取页面 ID
        String pageId = getFirstPageId();
        if (pageId == null) {
            Log.w(TAG, "没有可用的 Chrome 页面");
            return false;
        }
        
        // 创建新的 WebSocket 连接
        wsClient = new CdpWebSocketClient(pageId);
        if (wsClient.connect()) {
            currentPageId = pageId;
            return true;
        } else {
            wsClient = null;
            currentPageId = null;
            return false;
        }
    }
    
    /**
     * 执行 JavaScript 并返回结果
     */
    public String evaluateJavaScript(String jsScript) {
        if (!ensureConnection()) {
            Log.w(TAG, "无法建立 WebSocket 连接");
            return null;
        }
        
        try {
            String result = wsClient.executeJavaScript(jsScript);
            Log.i(TAG, "JS 执行结果: " + (result != null ? result.substring(0, Math.min(200, result.length())) : "null"));
            return result;
        } catch (Exception e) {
            Log.e(TAG, "执行 JavaScript 失败: " + e.getMessage());
            // 重置连接
            wsClient = null;
            currentPageId = null;
            return null;
        }
    }
    
    /**
     * 获取 DOM (通过 Runtime.evaluate 注入 JS)
     */
    public String getDom() {
        String js = "(function(){" +
            "var r=[];var n=document.querySelectorAll('body *');" +
            "for(var i=0;i<n.length;i++){" +
            "  var t=n[i].innerText;" +
            "  if(t&&t.trim().length>0&&t.trim().length<100){" +
            "    var b=n[i].getBoundingClientRect();" +
            "    if(b.width>0&&b.height>0){" +
            "      r.push({t:t.trim(),x:Math.round(b.x),y:Math.round(b.y),w:Math.round(b.width),h:Math.round(b.height),tag:n[i].tagName});" +
            "    }" +
            "  }" +
            "}" +
            "return JSON.stringify(r);" +
            "})()";
        
        String result = evaluateJavaScript(js);
        if (result != null) {
            return extractValue(result);
        }
        return null;
    }
    
    /**
     * 从 CDP 返回结果中提取 value
     */
    private String extractValue(String cdpResult) {
        try {
            // {"id":1,"result":{"result":{"type":"string","value":"..."}}}
            int valIdx = cdpResult.indexOf("\"value\":\"");
            if (valIdx != -1) {
                int start = valIdx + 9;
                StringBuilder sb = new StringBuilder();
                boolean escape = false;
                for (int i = start; i < cdpResult.length(); i++) {
                    char c = cdpResult.charAt(i);
                    if (escape) {
                        escape = false;
                        if (c == 'n') sb.append('\n');
                        else if (c == 't') sb.append('\t');
                        else if (c == '"') sb.append('"');
                        else if (c == '\\') sb.append('\\');
                        else sb.append('\\').append(c);
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 CDP 结果失败: " + e.getMessage());
        }
        return cdpResult;
    }
    
    /**
     * 在页面中点击指定坐标
     */
    public boolean clickAt(double x, double y) {
        String js = String.format(
            "(function(){" +
            "  var el = document.elementFromPoint(%f, %f);" +
            "  if (el) { el.click(); return true; }" +
            "  return false;" +
            "})()",
            x, y
        );
        String result = evaluateJavaScript(js);
        return result != null && result.contains("true");
    }
    
    /**
     * 根据 CSS 选择器点击元素
     */
    public boolean clickSelector(String selector) {
        String js = String.format(
            "(function(){" +
            "  var el = document.querySelector('%s');" +
            "  if (el) { el.click(); return true; }" +
            "  return false;" +
            "})()",
            selector.replace("'", "\\'")
        );
        String result = evaluateJavaScript(js);
        return result != null && result.contains("true");
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
            currentPageId = null;
        }
    }
}
