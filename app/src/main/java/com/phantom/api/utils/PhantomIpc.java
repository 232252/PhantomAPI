package com.phantom.api.utils;

import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 无 PID 依赖的 IPC 工具类
 * 废弃之前的 cmd_{pid}.json，使用固定文件名
 */
public class PhantomIpc {
    private static final String TAG = "PhantomIpc";
    
    public static final String BASE_DIR = "/data/local/tmp/phantom";
    public static final String CMD_FILE = BASE_DIR + "/cmd.json";
    public static final String DOM_RESULT_FILE = BASE_DIR + "/dom.json";
    
    /**
     * 宿主 App 调用：写入命令
     */
    public static void sendCommand(String jsonCmd) {
        try {
            File cmdFile = new File(CMD_FILE);
            Files.write(cmdFile.toPath(), jsonCmd.getBytes(StandardCharsets.UTF_8));
            // 使用 root 权限设置文件权限和 SELinux 上下文（同步等待）
            try {
                Process p1 = Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 666 " + CMD_FILE});
                p1.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", "chcon u:object_r:shell_data_file:s0 " + CMD_FILE});
                p2.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                Log.i(TAG, "文件权限和 SELinux 上下文已设置");
            } catch (Exception e) {
                Log.w(TAG, "设置文件权限失败: " + e.getMessage());
            }
            Log.i(TAG, "命令已发送: " + jsonCmd);
        } catch (Exception e) {
            Log.e(TAG, "发送命令失败: " + e.getMessage());
        }
    }
    
    /**
     * 宿主 App 调用：阻塞等待 LSPosed 模块返回结果
     * 使用 root 权限读取文件以绕过权限限制
     */
    public static String waitForResult(long timeoutMs) {
        File f = new File(DOM_RESULT_FILE);
        long start = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Thread.sleep(100);
                if (f.exists()) {
                    // 使用 root 权限读取文件
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + DOM_RESULT_FILE});
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    
                    String res = sb.toString().trim();
                    if (!res.isEmpty()) {
                        // 读取后删除文件
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "rm -f " + DOM_RESULT_FILE}).waitFor();
                        Log.i(TAG, "收到结果: " + res.substring(0, Math.min(100, res.length())));
                        return res;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "读取结果异常: " + e.getMessage());
            }
        }
        Log.w(TAG, "等待结果超时");
        return null; // 超时
    }
    
    /**
     * 清理残留（防止脏数据）
     */
    public static void cleanUp() {
        new File(CMD_FILE).delete();
        new File(DOM_RESULT_FILE).delete();
    }
    
    /**
     * 确保目录存在且有正确权限
     */
    public static void ensureDir() {
        try {
            File dir = new File(BASE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            dir.setReadable(true, false);
            dir.setWritable(true, false);
            dir.setExecutable(true, false);
        } catch (Exception e) {
            Log.e(TAG, "创建目录失败: " + e.getMessage());
        }
    }
}
