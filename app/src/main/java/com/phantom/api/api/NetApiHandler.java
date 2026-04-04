package com.phantom.api.api;

import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.util.NetworkUtils;

import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;

/**
 * 网络域 API 处理器
 */
public class NetApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "NetApiHandler";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.equals("/api/net/status")) {
            return handleNetStatus();
        } else if (uri.equals("/api/net/wifi")) {
            return handleWifiInfo();
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    private NanoHTTPD.Response handleNetStatus() throws Exception {
        JSONObject result = new JSONObject();
        result.put("connected", NetworkUtils.isNetworkConnected());
        result.put("wifi", NetworkUtils.isWifiConnected());
        result.put("mobile", NetworkUtils.isMobileConnected());
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private NanoHTTPD.Response handleWifiInfo() throws Exception {
        JSONObject wifi = NetworkUtils.getWifiInfo();
        return HttpServerEngine.jsonSuccess(wifi);
    }
}
