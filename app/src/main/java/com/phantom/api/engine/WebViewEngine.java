package com.phantom.api.engine;

import android.util.Log;

import com.phantom.api.util.AppUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * WebView 引擎 - 基于 JS 注入 + 文件 IPC
 * 
 * 架构：
 * 1. LSPosed 模块注入 JS SDK 到目标 WebView
 * 2. JS 通过 prompt() 将数据传出
 * 3. 数据通过文件系统跨进程通信
 * 4. 本引擎读取文件并返回 HTTP 响应
 */
public class WebViewEngine {
    private static final String TAG = "WebViewEngine";
    
    private static final String DOM_DATA_PATH = "/data/local/tmp/phantom_dom_";
    private static final String CMD_PATH = "/data/local/tmp/phantom_cmd_";
    
    /**
     * 检测可用的 DevTools 端口 (保留兼容)
     */
    public static java.util.List<String> detectDevToolsSockets() {
        java.util.List<String> sockets = new java.util.ArrayList<>();
        
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("cat /proc/net/unix | grep devtools_remote\nexit\n".getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                int atIndex = line.lastIndexOf('@');
                if (atIndex != -1) {
                    sockets.add(line.substring(atIndex + 1));
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
     * 获取 DOM 树 (通过文件 IPC)
     */
    public static JSONObject getDOM(android.content.Context context, String packageName) {
        try {
            // 1. 获取目标进程 PID
            int pid = AppUtils.getForegroundPid(context, packageName);
            if (pid <= 0) {
                // 尝试使用当前前台应用
                String foreground = AppUtils.getForegroundPackage(context);
                pid = AppUtils.getForegroundPid(context, foreground);
            }
            
            if (pid <= 0) {
                return error("无法获取目标进程 PID");
            }
            
            // 2. 发送捕获命令
            sendCommand(pid, "capture_dom", null);
            
            // 3. 等待数据写入 (最多 2 秒)
            File domFile = new File(DOM_DATA_PATH + pid + ".json");
            long start = System.currentTimeMillis();
            
            while (!domFile.exists() && System.currentTimeMillis() - start < 2000) {
                Thread.sleep(100);
            }
            
            if (!domFile.exists()) {
                return error("DOM 数据超时，请确保 LSPosed 模块已启用");
            }
            
            // 4. 读取数据
            String json = readFile(domFile);
            domFile.delete();
            
            if (json == null || json.isEmpty()) {
                return error("DOM 数据为空");
            }
            
            // 5. 解析并返回
            JSONArray nodes = new JSONArray(json);
            JSONObject result = new JSONObject();
            result.put("nodeCount", nodes.length());
            result.put("nodes", nodes);
            result.put("pid", pid);
            result.put("package", packageName);
            
            return result;
            
        } catch (Exception e) {
            return error("获取 DOM 失败: " + e.getMessage());
        }
    }
    
    /**
     * 点击 WebView 内元素
     */
    public static boolean clickAt(android.content.Context context, String packageName, 
            String selector, int x, int y) {
        try {
            int pid = AppUtils.getForegroundPid(context, packageName);
            if (pid <= 0) {
                String foreground = AppUtils.getForegroundPackage(context);
                pid = AppUtils.getForegroundPid(context, foreground);
            }
            
            if (pid <= 0) return false;
            
            JSONObject params = new JSONObject();
            if (selector != null && !selector.isEmpty()) {
                params.put("selector", selector);
                sendCommand(pid, "click", params);
            } else {
                params.put("x", x);
                params.put("y", y);
                sendCommand(pid, "clickAt", params);
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "点击失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 执行 JavaScript
     */
    public static JSONObject evaluateJavaScript(android.content.Context context, 
            String packageName, String script) {
        try {
            int pid = AppUtils.getForegroundPid(context, packageName);
            if (pid <= 0) {
                String foreground = AppUtils.getForegroundPackage(context);
                pid = AppUtils.getForegroundPid(context, foreground);
            }
            
            if (pid <= 0) {
                return error("无法获取目标进程 PID");
            }
            
            // 发送执行命令
            JSONObject params = new JSONObject();
            params.put("code", script);
            sendCommand(pid, "eval", params);
            
            // 等待结果
            File resultFile = new File(DOM_DATA_PATH + pid + ".json");
            long start = System.currentTimeMillis();
            
            while (!resultFile.exists() && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(100);
            }
            
            if (resultFile.exists()) {
                String result = readFile(resultFile);
                resultFile.delete();
                
                JSONObject resp = new JSONObject();
                resp.put("success", true);
                resp.put("result", result);
                return resp;
            }
            
            return error("JS 执行超时");
            
        } catch (Exception e) {
            return error("执行 JS 失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送命令到目标进程
     */
    private static void sendCommand(int pid, String action, JSONObject params) {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("action", action);
            if (params != null) {
                cmd.put("params", params);
            }
            
            File cmdFile = new File(CMD_PATH + pid + ".json");
            FileWriter writer = new FileWriter(cmdFile);
            writer.write(cmd.toString());
            writer.close();
            
            cmdFile.setReadable(true, false);
            cmdFile.setWritable(true, false);
            
            Log.i(TAG, "命令已发送: " + cmd.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "发送命令失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取文件内容
     */
    private static String readFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 构造错误响应
     */
    private static JSONObject error(String message) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("success", false);
            resp.put("error", message);
            return resp;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
