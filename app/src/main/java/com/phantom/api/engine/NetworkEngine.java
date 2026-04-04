package com.phantom.api.engine;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络引擎
 * 
 * 能力：
 * 1. 连接拓扑（谁在对外连接）
 * 2. 流量统计（按应用）
 * 3. 实时监控
 */
public class NetworkEngine {
    private static final String TAG = "NetworkEngine";
    
    private final Context context;
    private final PackageManager pm;
    
    // 连接状态缓存
    private List<ConnectionInfo> cachedConnections = new ArrayList<>();
    private Map<String, TrafficInfo> cachedTraffic = new HashMap<>();
    private long lastUpdateTime = 0;
    private static final long CACHE_TTL = 1000; // 1秒缓存
    
    public NetworkEngine(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
    }
    
    /**
     * 获取所有 TCP 连接
     */
    public List<ConnectionInfo> getConnections() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < CACHE_TTL) {
            return cachedConnections;
        }
        
        List<ConnectionInfo> connections = new ArrayList<>();
        
        // 读取 IPv4 连接
        connections.addAll(parseTcpFile("/proc/net/tcp"));
        connections.addAll(parseTcpFile("/proc/net/tcp6"));
        
        cachedConnections = connections;
        lastUpdateTime = now;
        
        return connections;
    }
    
    /**
     * 解析 /proc/net/tcp 文件
     */
    private List<ConnectionInfo> parseTcpFile(String path) {
        List<ConnectionInfo> list = new ArrayList<>();
        
        try {
            // 使用 root 权限读取
            Process process = Runtime.getRuntime().exec("su");
            OutputStream os = process.getOutputStream();
            os.write(("cat " + path + "\n").getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            os.close();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // 跳过标题行
                }
                
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 10) continue;
                
                ConnectionInfo info = new ConnectionInfo();
                
                // 解析本地地址
                String[] local = parts[1].split(":");
                info.localAddress = parseIpAddress(local[0]);
                info.localPort = Integer.parseInt(local[1], 16);
                
                // 解析远程地址
                String[] remote = parts[2].split(":");
                info.remoteAddress = parseIpAddress(remote[0]);
                info.remotePort = Integer.parseInt(remote[1], 16);
                
                // 解析状态
                int state = Integer.parseInt(parts[3], 16);
                info.state = getTcpState(state);
                
                // 解析 UID
                info.uid = Integer.parseInt(parts[7]);
                
                // 反查包名
                String[] packages = pm.getPackagesForUid(info.uid);
                if (packages != null && packages.length > 0) {
                    info.packageName = packages[0];
                    try {
                        info.appName = pm.getApplicationLabel(
                            pm.getApplicationInfo(info.packageName, 0)).toString();
                    } catch (Exception e) {
                        info.appName = info.packageName;
                    }
                }
                
                info.protocol = path.contains("6") ? "TCP6" : "TCP";
                list.add(info);
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            Log.e(TAG, "解析 " + path + " 失败: " + e.getMessage());
        }
        
        return list;
    }
    
    /**
     * 解析十六进制 IP 地址
     */
    private String parseIpAddress(String hex) {
        try {
            if (hex.length() == 8) {
                // IPv4
                int addr = Integer.parseInt(hex, 16);
                return ((addr >> 24) & 0xFF) + "." +
                       ((addr >> 16) & 0xFF) + "." +
                       ((addr >> 8) & 0xFF) + "." +
                       (addr & 0xFF);
            } else if (hex.length() == 32) {
                // IPv6 - 简化处理
                return hex;
            }
        } catch (Exception e) {}
        return hex;
    }
    
    /**
     * 获取 TCP 状态名称
     */
    private String getTcpState(int state) {
        switch (state) {
            case 0x01: return "ESTABLISHED";
            case 0x02: return "SYN_SENT";
            case 0x03: return "SYN_RECV";
            case 0x04: return "FIN_WAIT1";
            case 0x05: return "FIN_WAIT2";
            case 0x06: return "TIME_WAIT";
            case 0x07: return "CLOSE";
            case 0x08: return "CLOSE_WAIT";
            case 0x09: return "LAST_ACK";
            case 0x0A: return "LISTEN";
            case 0x0B: return "CLOSING";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * 获取流量统计
     */
    public Map<String, TrafficInfo> getTrafficStats() {
        Map<String, TrafficInfo> stats = new HashMap<>();
        
        // 读取每个 UID 的流量
        File uidStatDir = new File("/proc/uid_stat");
        if (uidStatDir.exists() && uidStatDir.isDirectory()) {
            for (File uidDir : uidStatDir.listFiles()) {
                try {
                    int uid = Integer.parseInt(uidDir.getName());
                    String[] packages = pm.getPackagesForUid(uid);
                    
                    if (packages != null && packages.length > 0) {
                        String pkg = packages[0];
                        TrafficInfo info = new TrafficInfo();
                        info.uid = uid;
                        info.packageName = pkg;
                        
                        // 读取接收流量
                        File rcvFile = new File(uidDir, "tcp_rcv");
                        if (rcvFile.exists()) {
                            info.rxBytes = readLongFile(rcvFile);
                        }
                        
                        // 读取发送流量
                        File sndFile = new File(uidDir, "tcp_snd");
                        if (sndFile.exists()) {
                            info.txBytes = readLongFile(sndFile);
                        }
                        
                        stats.put(pkg, info);
                    }
                } catch (Exception e) {}
            }
        }
        
        // 使用 TrafficStats API 作为补充
        try {
            for (android.content.pm.ApplicationInfo app : pm.getInstalledApplications(0)) {
                if (!stats.containsKey(app.packageName)) {
                    TrafficInfo info = new TrafficInfo();
                    info.packageName = app.packageName;
                    info.uid = app.uid;
                    info.rxBytes = android.net.TrafficStats.getUidRxBytes(app.uid);
                    info.txBytes = android.net.TrafficStats.getUidTxBytes(app.uid);
                    if (info.rxBytes > 0 || info.txBytes > 0) {
                        stats.put(app.packageName, info);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TrafficStats 获取失败: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * 读取文件中的长整型
     */
    private long readLongFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            reader.close();
            return Long.parseLong(line.trim());
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * 转换为 JSON
     */
    public JSONObject connectionsToJson() {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        
        for (ConnectionInfo info : getConnections()) {
            JSONObject conn = new JSONObject();
            try {
                conn.put("uid", info.uid);
                conn.put("package", info.packageName);
                conn.put("appName", info.appName);
                conn.put("proto", info.protocol);
                conn.put("local", info.localAddress + ":" + info.localPort);
                conn.put("remote", info.remoteAddress + ":" + info.remotePort);
                conn.put("state", info.state);
                array.put(conn);
            } catch (Exception e) {}
        }
        
        try {
            result.put("connections", array);
            result.put("count", array.length());
        } catch (Exception e) {}
        
        return result;
    }
    
    public JSONObject trafficToJson() {
        JSONObject result = new JSONObject();
        JSONObject perApp = new JSONObject();
        
        for (Map.Entry<String, TrafficInfo> entry : getTrafficStats().entrySet()) {
            try {
                JSONObject app = new JSONObject();
                app.put("rx_bytes", entry.getValue().rxBytes);
                app.put("tx_bytes", entry.getValue().txBytes);
                perApp.put(entry.getKey(), app);
            } catch (Exception e) {}
        }
        
        try {
            result.put("per_app", perApp);
            result.put("count", perApp.length());
        } catch (Exception e) {}
        
        return result;
    }
    
    /**
     * 连接信息
     */
    public static class ConnectionInfo {
        public int uid;
        public String packageName;
        public String appName;
        public String protocol;
        public String localAddress;
        public int localPort;
        public String remoteAddress;
        public int remotePort;
        public String state;
    }
    
    /**
     * 流量信息
     */
    public static class TrafficInfo {
        public int uid;
        public String packageName;
        public long rxBytes;
        public long txBytes;
    }
}
