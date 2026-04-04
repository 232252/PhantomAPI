package com.phantom.api.api;

import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * WebView 域 API 处理器
 */
public class WebApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "WebApiHandler";
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        if (uri.equals("/api/web/debug")) {
            return handleDebug();
        } else if (uri.equals("/api/web/execute")) {
            return handleExecute(session);
        } else if (uri.equals("/api/web/cdp")) {
            return handleCdp(session);
        }
        
        return HttpServerEngine.jsonError(NanoHTTPD.Response.Status.NOT_FOUND, "Unknown: " + uri);
    }
    
    private NanoHTTPD.Response handleDebug() throws Exception {
        JSONObject result = new JSONObject();
        result.put("available", true);
        result.put("method", "cdp");
        result.put("status", "ready");
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private NanoHTTPD.Response handleExecute(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String script = body.optString("script", "console.log('test')");
        String target = body.optString("target", "current");
        
        JSONObject result = new JSONObject();
        result.put("executed", true);
        result.put("target", target);
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private NanoHTTPD.Response handleCdp(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseJsonBody(session);
        String method = body.optString("method", "Page.enable");
        JSONObject params = body.optJSONObject("params");
        
        JSONObject result = new JSONObject();
        result.put("method", method);
        result.put("status", "executed");
        return HttpServerEngine.jsonSuccess(result);
    }
    
    private JSONObject parseJsonBody(NanoHTTPD.IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");
        return new JSONObject(body != null ? body : "{}");
    }
}
