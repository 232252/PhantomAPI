package com.phantom.api.engine;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebView 调试器
 * 通过 Chrome DevTools Protocol (CDP) 与 WebView 通信
 * 
 * 核心功能：
 * 1. 连接 Unix Socket (webview_devtools_remote_{pid})
 * 2. 发送 JSON-RPC 指令
 * 3. 获取 DOM 树和元素坐标
 * 4. 在 Web 层执行点击
 */
public class WebViewDebugger {
    private static final String TAG = "WebViewDebugger";
    
    private final String socketName;
    private LocalSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader;
    private final AtomicInteger messageId = new AtomicInteger(1);
    private volatile boolean connected = false;
    
    public WebViewDebugger(String socketName) {
        this.socketName = socketName;
    }
    
    /**
     * 连接到 WebView 调试端口
     */
    public boolean connect() {
        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(
                socketName, 
                LocalSocketAddress.Namespace.ABSTRACT
            );
            socket.connect(address);
            socket.setSoTimeout(10000);
            
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            
            connected = true;
            Log.i(TAG, "已连接到 WebView 调试端口: " + socketName);
            
            // 启用必要的 CDP 域
            sendMessage("{\"id\":1,\"method\":\"Page.enable\"}");
            sendMessage("{\"id\":2,\"method\":\"DOM.enable\"}");
            sendMessage("{\"id\":3,\"method\":\"Runtime.enable\"}");
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "连接 WebView 失败", e);
            connected = false;
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        connected = false;
        try {
            if (reader != null) reader.close();
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            Log.e(TAG, "断开连接时出错", e);
        }
        Log.i(TAG, "已断开 WebView 调试连接");
    }
    
    /**
     * 发送消息并等待响应
     */
    public synchronized String sendMessage(String message) {
        if (!connected) {
            return "{\"error\": \"not connected\"}";
        }
        
        try {
            // 发送消息 (需要添加长度前缀)
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            String fullMessage = String.format("%d:%s", data.length, message);
            outputStream.write(fullMessage.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            
            // 读取响应
            String response = readMessage();
            return response;
        } catch (Exception e) {
            Log.e(TAG, "发送消息失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 读取消息 (WebSocket 帧格式)
     */
    private String readMessage() {
        try {
            // 读取长度前缀
            StringBuilder lengthStr = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1 && c != ':') {
                lengthStr.append((char) c);
            }
            
            if (c == -1) {
                return "{\"error\": \"connection closed\"}";
            }
            
            int length = Integer.parseInt(lengthStr.toString());
            
            // 读取消息体
            char[] buffer = new char[length];
            int read = reader.read(buffer, 0, length);
            
            return new String(buffer, 0, read);
        } catch (Exception e) {
            Log.e(TAG, "读取消息失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 获取 DOM 树
     */
    public String getDomTree() {
        // 获取文档
        String docRequest = String.format(
            "{\"id\":%d,\"method\":\"DOM.getDocument\",\"params\":{\"depth\":-1,\"pierce\":true}}",
            messageId.getAndIncrement()
        );
        
        String docResponse = sendMessage(docRequest);
        
        try {
            JSONObject response = new JSONObject(docResponse);
            if (response.has("error")) {
                return docResponse;
            }
            
            JSONObject result = response.getJSONObject("result");
            JSONObject root = result.getJSONObject("root");
            
            // 递归提取 DOM 信息
            JSONArray domTree = extractDomInfo(root, 0);
            
            JSONObject result2 = new JSONObject();
            result2.put("root", root);
            result2.put("flatTree", domTree);
            
            return result2.toString(2);
        } catch (Exception e) {
            Log.e(TAG, "解析 DOM 失败", e);
            return "{\"error\": \"" + e.getMessage() + "\", \"raw\": " + 
                JSONObject.quote(docResponse) + "}";
        }
    }
    
    /**
     * 递归提取 DOM 信息
     */
    private JSONArray extractDomInfo(JSONObject node, int depth) throws Exception {
        JSONArray result = new JSONArray();
        
        String nodeName = node.optString("nodeName", "");
        String nodeValue = node.optString("nodeValue", "");
        String localName = node.optString("localName", "");
        int nodeId = node.optInt("nodeId", 0);
        
        JSONObject info = new JSONObject();
        info.put("nodeId", nodeId);
        info.put("nodeName", nodeName);
        info.put("localName", localName);
        info.put("nodeValue", nodeValue.trim());
        info.put("depth", depth);
        
        // 获取属性
        if (node.has("attributes")) {
            JSONArray attrs = node.getJSONArray("attributes");
            JSONObject attributes = new JSONObject();
            for (int i = 0; i < attrs.length() - 1; i += 2) {
                attributes.put(attrs.getString(i), attrs.getString(i + 1));
            }
            info.put("attributes", attributes);
        }
        
        // 获取坐标
        JSONObject boxModel = getBoxModel(nodeId);
        if (boxModel != null) {
            info.put("boxModel", boxModel);
        }
        
        result.put(info);
        
        // 递归处理子节点
        if (node.has("children")) {
            JSONArray children = node.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) {
                JSONArray childResults = extractDomInfo(children.getJSONObject(i), depth + 1);
                for (int j = 0; j < childResults.length(); j++) {
                    result.put(childResults.get(j));
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取元素的 BoxModel (坐标信息)
     */
    public JSONObject getBoxModel(int nodeId) {
        try {
            String request = String.format(
                "{\"id\":%d,\"method\":\"DOM.getBoxModel\",\"params\":{\"nodeId\":%d}}",
                messageId.getAndIncrement(), nodeId
            );
            
            String response = sendMessage(request);
            JSONObject json = new JSONObject(response);
            
            if (json.has("result")) {
                JSONObject result = json.getJSONObject("result");
                JSONObject boxModel = new JSONObject();
                
                // content: [x1, y1, x2, y2, x3, y3, x4, y4]
                JSONArray content = result.optJSONArray("content");
                if (content != null && content.length() >= 8) {
                    boxModel.put("x1", content.getDouble(0));
                    boxModel.put("y1", content.getDouble(1));
                    boxModel.put("x2", content.getDouble(2));
                    boxModel.put("y2", content.getDouble(3));
                    boxModel.put("x3", content.getDouble(4));
                    boxModel.put("y3", content.getDouble(5));
                    boxModel.put("x4", content.getDouble(6));
                    boxModel.put("y4", content.getDouble(7));
                    
                    // 计算中心点
                    double centerX = (content.getDouble(0) + content.getDouble(2) + 
                                      content.getDouble(4) + content.getDouble(6)) / 4;
                    double centerY = (content.getDouble(1) + content.getDouble(3) + 
                                      content.getDouble(5) + content.getDouble(7)) / 4;
                    boxModel.put("centerX", centerX);
                    boxModel.put("centerY", centerY);
                }
                
                return boxModel;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 BoxModel 失败: " + nodeId, e);
        }
        return null;
    }
    
    /**
     * 执行 JavaScript
     */
    public String executeScript(String script) {
        try {
            // 转义脚本
            String escapedScript = JSONObject.quote(script);
            
            String request = String.format(
                "{\"id\":%d,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":%s,\"returnByValue\":true}}",
                messageId.getAndIncrement(), escapedScript
            );
            
            String response = sendMessage(request);
            JSONObject json = new JSONObject(response);
            
            if (json.has("result")) {
                JSONObject result = json.getJSONObject("result");
                if (result.has("result")) {
                    JSONObject value = result.getJSONObject("result");
                    return value.optString("value", value.toString());
                }
            }
            
            return response;
        } catch (Exception e) {
            Log.e(TAG, "执行脚本失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 在 Web 层点击
     * 使用 CDP 的 Input.dispatchMouseEvent
     */
    public boolean click(int x, int y) {
        try {
            // Mouse pressed
            String pressRequest = String.format(
                "{\"id\":%d,\"method\":\"Input.dispatchMouseEvent\"," +
                "\"params\":{\"type\":\"mousePressed\",\"x\":%d,\"y\":%d," +
                "\"button\":\"left\",\"clickCount\":1}}",
                messageId.getAndIncrement(), x, y
            );
            sendMessage(pressRequest);
            
            // 短暂延迟
            Thread.sleep(50);
            
            // Mouse released
            String releaseRequest = String.format(
                "{\"id\":%d,\"method\":\"Input.dispatchMouseEvent\"," +
                "\"params\":{\"type\":\"mouseReleased\",\"x\":%d,\"y\":%d," +
                "\"button\":\"left\",\"clickCount\":1}}",
                messageId.getAndIncrement(), x, y
            );
            sendMessage(releaseRequest);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Web 点击失败", e);
            return false;
        }
    }
    
    /**
     * 滚动页面
     */
    public boolean scroll(int deltaX, int deltaY) {
        try {
            String script = String.format("window.scrollBy(%d, %d)", deltaX, deltaY);
            executeScript(script);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "滚动失败", e);
            return false;
        }
    }
    
    /**
     * 按文本查找元素
     */
    public String findElementByText(String text) {
        try {
            String script = String.format(
                "(function(){" +
                "  var result = [];" +
                "  var walker = document.createTreeWalker(" +
                "    document.body, " +
                "    NodeFilter.SHOW_TEXT, " +
                "    null, " +
                "    false" +
                "  );" +
                "  while(walker.nextNode()){" +
                "    var node = walker.currentNode;" +
                "    if(node.textContent && node.textContent.indexOf('%s') >= 0){" +
                "      var rect = node.parentElement.getBoundingClientRect();" +
                "      result.push({" +
                "        text: node.textContent.trim()," +
                "        x: rect.x + rect.width / 2," +
                "        y: rect.y + rect.height / 2" +
                "      });" +
                "    }" +
                "  }" +
                "  return JSON.stringify(result);" +
                "})()",
                text
            );
            
            return executeScript(script);
        } catch (Exception e) {
            Log.e(TAG, "查找元素失败", e);
            return "[]";
        }
    }
    
    /**
     * 获取页面文本列表
     */
    public String getPageTexts() {
        String script = 
            "(function(){" +
            "  var result = [];" +
            "  var elements = document.querySelectorAll('*');" +
            "  for(var i = 0; i < elements.length; i++){" +
            "    var el = elements[i];" +
            "    if(el.innerText && el.innerText.trim().length > 0){" +
            "      var rect = el.getBoundingClientRect();" +
            "      if(rect.width > 0 && rect.height > 0){" +
            "        result.push({" +
            "          text: el.innerText.trim().substring(0, 100)," +
            "          x: Math.round(rect.x + rect.width / 2)," +
            "          y: Math.round(rect.y + rect.height / 2)," +
            "          width: Math.round(rect.width)," +
            "          height: Math.round(rect.height)," +
            "          tag: el.tagName.toLowerCase()" +
            "        });" +
            "      }" +
            "    }" +
            "  }" +
            "  return JSON.stringify(result);" +
            "})()";
        
        return executeScript(script);
    }
    
    public boolean isConnected() {
        return connected;
    }
}
