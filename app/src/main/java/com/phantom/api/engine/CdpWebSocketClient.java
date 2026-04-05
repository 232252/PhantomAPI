package com.phantom.api.engine;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chrome CDP WebSocket 客户端 - 使用 LocalSocket 连接抽象 Unix Socket
 */
public class CdpWebSocketClient {
    private static final String TAG = "CdpWebSocketClient";
    private static final String SOCKET_NAME = "chrome_devtools_remote";
    
    private LocalSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String pageId;
    
    public CdpWebSocketClient(String pageId) {
        this.pageId = pageId;
    }
    
    /**
     * 连接到 Chrome DevTools WebSocket
     */
    public boolean connect() {
        try {
            socket = new LocalSocket();
            // 连接到抽象 Unix Socket
            LocalSocketAddress address = new LocalSocketAddress(
                SOCKET_NAME, 
                LocalSocketAddress.Namespace.ABSTRACT
            );
            socket.connect(address);
            
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            
            Log.i(TAG, "已连接到 Unix Socket: " + SOCKET_NAME);
            
            // 发送 WebSocket 握手
            String wsKey = "dGhlIHNhbXBsZSBub25jZQ=="; // base64 编码的随机字符串
            String handshake = String.format(
                "GET /devtools/page/%s HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: %s\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n",
                pageId, wsKey
            );
            
            outputStream.write(handshake.getBytes());
            outputStream.flush();
            Log.i(TAG, "已发送 WebSocket 握手请求");
            
            // 读取握手响应
            byte[] buffer = new byte[4096];
            int bytesRead = inputStream.read(buffer);
            String response = new String(buffer, 0, bytesRead);
            Log.i(TAG, "收到响应: " + response.substring(0, Math.min(200, response.length())));
            
            // 检查握手是否成功
            if (response.contains("101") && response.contains("Switching Protocols")) {
                Log.i(TAG, "WebSocket 握手成功");
                return true;
            } else {
                Log.e(TAG, "WebSocket 握手失败: " + response.split("\r\n")[0]);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 发送 CDP 命令
     */
    public String sendCommand(String method, String params) {
        try {
            String message = String.format("{\"id\":1,\"method\":\"%s\",\"params\":%s}", method, params);
            byte[] wsFrame = createWebSocketFrame(message.getBytes());
            
            outputStream.write(wsFrame);
            outputStream.flush();
            Log.i(TAG, "已发送命令: " + method);
            
            // 读取响应
            byte[] buffer = new byte[65536];
            int bytesRead = inputStream.read(buffer);
            String response = parseWebSocketFrame(buffer, bytesRead);
            
            Log.i(TAG, "收到响应: " + response.substring(0, Math.min(200, response.length())));
            return response;
            
        } catch (Exception e) {
            Log.e(TAG, "发送命令失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建 WebSocket 帧
     */
    private byte[] createWebSocketFrame(byte[] payload) {
        int len = payload.length;
        byte[] frame;
        
        if (len <= 125) {
            frame = new byte[2 + len];
            frame[0] = (byte) 0x81; // FIN + Text frame
            frame[1] = (byte) len;
            System.arraycopy(payload, 0, frame, 2, len);
        } else if (len <= 65535) {
            frame = new byte[4 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 126;
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            System.arraycopy(payload, 0, frame, 4, len);
        } else {
            throw new RuntimeException("Payload too large");
        }
        
        return frame;
    }
    
    /**
     * 解析 WebSocket 帧
     */
    private String parseWebSocketFrame(byte[] buffer, int length) {
        if (length < 2) return null;
        
        int opcode = buffer[0] & 0x0F;
        boolean masked = (buffer[1] & 0x80) != 0;
        int payloadLen = buffer[1] & 0x7F;
        
        int offset = 2;
        if (payloadLen == 126) {
            payloadLen = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
            offset = 4;
        } else if (payloadLen == 127) {
            // 处理大 payload
            payloadLen = 0;
            for (int i = 0; i < 8; i++) {
                payloadLen = (payloadLen << 8) | (buffer[2 + i] & 0xFF);
            }
            offset = 10;
        }
        
        byte[] payload = new byte[payloadLen];
        if (masked) {
            byte[] mask = new byte[4];
            System.arraycopy(buffer, offset, mask, 0, 4);
            offset += 4;
            for (int i = 0; i < payloadLen; i++) {
                payload[i] = (byte) (buffer[offset + i] ^ mask[i % 4]);
            }
        } else {
            System.arraycopy(buffer, offset, payload, 0, payloadLen);
        }
        
        return new String(payload);
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行 JavaScript 并返回结果
     */
    public String executeJavaScript(String jsCode) {
        String params = String.format("{\"expression\":\"%s\",\"returnByValue\":true}", 
            escapeJson(jsCode));
        return sendCommand("Runtime.evaluate", params);
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
