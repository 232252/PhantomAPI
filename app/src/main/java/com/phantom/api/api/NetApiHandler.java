package com.phantom.api.api;

import android.content.Context;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.engine.NetworkEngine;
import com.phantom.api.util.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 网络域 API 处理器
 */
public class NetApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "NetApiHandler";
    private final NetworkEngine networkEngine;
    
    public NetApiHandler(Context context) {
        this.networkEngine = new NetworkEngine(context);
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.equals("/api/net/status")) {
            return handleNetStatus();
        } else if (uri.equals("/api/net/wifi")) {
            return handleWifiInfo();
        } else if (uri.equals("/api/net/connections")) {
            return handleConnections(session);
        } else if (uri.equals("/api/net/traffic")) {
            return handleTraffic();
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    /**
     * 网络连接状态
     */
    private NanoHTTPD.Response handleNetStatus() throws Exception {
        JSONObject result = new JSONObject();
        result.put("connected", NetworkUtils.isNetworkConnected());
        result.put("wifi", NetworkUtils.isWifiConnected());
        result.put("mobile", NetworkUtils.isMobileConnected());
        return HttpServerEngine.jsonSuccess(result);
    }
    
    /**
     * WiFi 信息
     */
    private NanoHTTPD.Response handleWifiInfo() throws Exception {
        JSONObject wifi = NetworkUtils.getWifiInfo();
        return HttpServerEngine.jsonSuccess(wifi);
    }
    
    /**
     * 连接拓扑
     * 获取所有 TCP 连接信息
     * 支持按包名过滤: GET /api/net/connections?package=com.example.app
     */
    private NanoHTTPD.Response handleConnections(NanoHTTPD.IHTTPSession session) throws Exception {
        // 解析查询参数
        Map<String, String> params = parseQueryParams(session);
        String filterPackage = params.get("package");
        
        JSONObject allConnections = networkEngine.connectionsToJson();
        
        // 如果指定了包名，进行过滤
        if (filterPackage != null && !filterPackage.isEmpty()) {
            JSONObject filteredResult = filterConnectionsByPackage(allConnections, filterPackage);
            return HttpServerEngine.jsonSuccess(filteredResult);
        }
        
        return HttpServerEngine.jsonSuccess(allConnections);
    }
    
    /**
     * 按包名过滤连接
     */
    private JSONObject filterConnectionsByPackage(JSONObject allConnections, String packageName) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray filteredConnections = new JSONArray();
        int totalCount = 0;
        int establishedCount = 0;
        
        // 遍历所有应用
        if (allConnections.has("by_package")) {
            JSONObject byPackage = allConnections.getJSONObject("by_package");
            
            // 查找指定包名
            if (byPackage.has(packageName)) {
                JSONObject packageInfo = byPackage.getJSONObject(packageName);
                result.put("package", packageName);
                result.put("app_name", packageInfo.optString("app_name", packageName));
                
                JSONArray connections = packageInfo.optJSONArray("connections");
                if (connections != null) {
                    for (int i = 0; i < connections.length(); i++) {
                        JSONObject conn = connections.getJSONObject(i);
                        filteredConnections.put(conn);
                        totalCount++;
                        if ("ESTABLISHED".equals(conn.optString("state"))) {
                            establishedCount++;
                        }
                    }
                }
            }
        }
        
        result.put("connections", filteredConnections);
        result.put("total_count", totalCount);
        result.put("established_count", establishedCount);
        
        return result;
    }
    
    /**
     * 解析 URL 查询参数
     */
    private Map<String, String> parseQueryParams(NanoHTTPD.IHTTPSession session) {
        Map<String, String> params = new HashMap<>();
        try {
            String query = session.getQueryParameterString();
            if (query != null && !query.isEmpty()) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        params.put(java.net.URLDecoder.decode(kv[0], "UTF-8"), 
                                   java.net.URLDecoder.decode(kv[1], "UTF-8"));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析查询参数失败: " + e.getMessage());
        }
        return params;
    }
    
    /**
     * 流量统计
     * 按应用统计流量
     */
    private NanoHTTPD.Response handleTraffic() throws Exception {
        JSONObject traffic = networkEngine.trafficToJson();
        return HttpServerEngine.jsonSuccess(traffic);
    }
}
