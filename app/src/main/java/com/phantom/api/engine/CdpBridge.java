package com.phantom.api.engine;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chrome DevTools Protocol 桥接引擎
 * 
 * 通过 Unix Socket 连接 webview_devtools_remote_{pid}
 * 实现 DOM 级别的获取和操作
 */
public class CdpBridge {
    private static final String TAG = "CdpBridge";
    
    private static final String DEVTOOLS_PREFIX = "webview_devtools_remote_";
    private static final String CHROME_DEVTOOLS = "chrome_devtools_remote";
    
    // 连接缓存
    private static final Map<String, CdpConnection> connections = new ConcurrentHashMap<>();
    
    /**
     * 检测可用的 DevTools 端口
     */
    public static List<String> detectDevToolsSockets() {
        List<String> sockets = new ArrayList<>();
        
        try {
            // 使用 root 权限读取
            Process process = Runtime.getRuntime().exec("su");
            OutputStream os = process.getOutputStream();
            os.write("cat /proc/net/unix\n".getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            os.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("devtools_remote")) {
                    // 提取 socket 名称
                    int atIndex = line.lastIndexOf('@');
                    if (atIndex != -1) {
                        String socketName = line.substring(atIndex + 1);
                        if (!sockets.contains(socketName)) {
                            sockets.add(socketName);
                        }
                    }
                }
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            Log.e(TAG, "检测 DevTools 失败: " + e.getMessage());
        }
        
        return sockets;
    }
    
    /**
     * 连接到 DevTools Socket
     */
    public static CdpConnection connect(String socketName) {
        if (connections.containsKey(socketName)) {
            return connections.get(socketName);
        }
        
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(socketName));
            
            CdpConnection conn = new CdpConnection(socket);
            connections.put(socketName, conn);
            
            Log.i(TAG, "连接 DevTools 成功: " + socketName);
            return conn;
            
        } catch (Exception e) {
            Log.e(TAG, "连接 DevTools 失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取 DOM 树
     */
    public static JSONObject getDOM(String socketName) {
        CdpConnection conn = connect(socketName);
        if (conn == null) {
            return null;
        }
        
        try {
            // 发送 DOM.getDocument
            JSONObject result = conn.sendCommand("DOM.getDocument", new JSONObject()
                .put("depth", -1)
                .put("pierce", true));
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "获取 DOM 失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取节点盒模型
     */
    public static JSONObject getBoxModel(String socketName, int nodeId) {
        CdpConnection conn = connect(socketName);
        if (conn == null) {
            return null;
        }
        
        try {
            return conn.sendCommand("DOM.getBoxModel", new JSONObject()
                .put("nodeId", nodeId));
            
        } catch (Exception e) {
            Log.e(TAG, "获取盒模型失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 执行 JavaScript
     */
    public static JSONObject evaluateJavaScript(String socketName, String script) {
        CdpConnection conn = connect(socketName);
        if (conn == null) {
            return null;
        }
        
        try {
            return conn.sendCommand("Runtime.evaluate", new JSONObject()
                .put("expression", script)
                .put("returnByValue", true));
            
        } catch (Exception e) {
            Log.e(TAG, "执行 JS 失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 点击坐标
     */
    public static boolean clickAt(String socketName, int x, int y) {
        CdpConnection conn = connect(socketName);
        if (conn == null) {
            return false;
        }
        
        try {
            // 鼠标按下
            conn.sendCommand("Input.dispatchMouseEvent", new JSONObject()
                .put("type", "mousePressed")
                .put("x", x)
                .put("y", y)
                .put("button", "left")
                .put("clickCount", 1));
            
            // 鼠标释放
            conn.sendCommand("Input.dispatchMouseEvent", new JSONObject()
                .put("type", "mouseReleased")
                .put("x", x)
                .put("y", y)
                .put("button", "left")
                .put("clickCount", 1));
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "CDP 点击失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * CDP 连接封装
     */
    public static class CdpConnection {
        private final LocalSocket socket;
        private final OutputStream output;
        private final BufferedReader input;
        private int messageId = 0;
        
        public CdpConnection(LocalSocket socket) throws Exception {
            this.socket = socket;
            this.output = socket.getOutputStream();
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        
        /**
         * 发送命令并等待响应
         */
        public synchronized JSONObject sendCommand(String method, JSONObject params) throws Exception {
            int id = ++messageId;
            
            JSONObject command = new JSONObject();
            command.put("id", id);
            command.put("method", method);
            if (params != null) {
                command.put("params", params);
            }
            
            // 发送 WebSocket 帧 (简化版)
            String message = command.toString();
            output.write(0x81); // Text frame
            output.write(message.length());
            output.write(message.getBytes("UTF-8"));
            output.flush();
            
            // 读取响应
            String response = readResponse();
            if (response != null) {
                return new JSONObject(response);
            }
            
            return null;
        }
        
        /**
         * 读取响应
         */
        private String readResponse() throws Exception {
            // 简化的 WebSocket 帧读取
            int opcode = input.read();
            if ((opcode & 0x81) != 0x81) {
                return null;
            }
            
            int length = input.read();
            char[] buffer = new char[length];
            input.read(buffer, 0, length);
            
            return new String(buffer);
        }
        
        /**
         * 关闭连接
         */
        public void close() {
            try {
                socket.close();
            } catch (Exception e) {}
        }
    }
}
