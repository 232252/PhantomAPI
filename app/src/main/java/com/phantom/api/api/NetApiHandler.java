package com.phantom.api.api;

import android.content.Context;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.engine.NetworkEngine;
import com.phantom.api.util.NetworkUtils;

import org.json.JSONObject;

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
            return handleConnections();
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
     */
    private NanoHTTPD.Response handleConnections() throws Exception {
        JSONObject connections = networkEngine.connectionsToJson();
        return HttpServerEngine.jsonSuccess(connections);
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
