package com.phantom.api.api;

import android.content.Context;

import com.phantom.api.engine.HttpServerEngine;
import com.phantom.api.service.PhantomAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;

/**
 * 高级手势 API 处理器
 * 借鉴自 AutoJs6, MobiAgent
 */
public class GestureApiHandler implements HttpServerEngine.ApiHandler {
    private final Context context;
    
    public GestureApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        switch (uri) {
            case "/api/gesture/swipe": return handleSwipe(session);
            case "/api/gesture/fling": return handleFling(session);
            case "/api/gesture/drag": return handleDrag(session);
            case "/api/gesture/pinch": return handlePinch(session);
            case "/api/gesture/path": return handlePath(session);
            case "/api/gesture/bezier": return handleBezier(session);
            case "/api/gesture/sequence": return handleSequence(session);
            case "/api/gesture/tap": return handleTap(session);
            case "/api/gesture/double_tap": return handleDoubleTap(session);
            case "/api/gesture/long_press": return handleLongPress(session);
            case "/api/gesture/scroll": return handleScroll(session);
            case "/api/gesture/pattern": return handlePattern(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    private NanoHTTPD.Response handleSwipe(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        String dir = b.optString("direction", "up");
        int dist = b.optInt("distance", 500);
        int dur = b.optInt("duration", 300);
        int[] sz = getScreenSize();
        float sx = sz[0]/2f, sy = sz[1]/2f, ex = sx, ey = sy;
        switch (dir) {
            case "up": ey = sy - dist; break;
            case "down": ey = sy + dist; break;
            case "left": ex = sx - dist; break;
            case "right": ex = sx + dist; break;
        }
        boolean ok = swipe(sx, sy, ex, ey, dur);
        return jsonOk(new JSONObject().put("success", ok).put("direction", dir).put("distance", dist));
    }
    
    private NanoHTTPD.Response handleFling(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        String dir = b.optString("direction", "up");
        int[] sz = getScreenSize();
        float sx = sz[0]/2f, sy, ex = sx, ey;
        switch (dir) {
            case "up": sy = sz[1]*0.8f; ey = sz[1]*0.1f; break;
            case "down": sy = sz[1]*0.2f; ey = sz[1]*0.9f; break;
            case "left": sx = sz[0]*0.9f; ex = sz[0]*0.1f; sy = sz[1]/2f; ey = sy; break;
            case "right": sx = sz[0]*0.1f; ex = sz[0]*0.9f; sy = sz[1]/2f; ey = sy; break;
            default: sy = sz[1]*0.8f; ey = sz[1]*0.2f;
        }
        return jsonOk(new JSONObject().put("success", swipe(sx, sy, ex, ey, 100)));
    }
    
    private NanoHTTPD.Response handleDrag(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        float sx = (float)b.optDouble("startX");
        float sy = (float)b.optDouble("startY");
        float ex = (float)b.optDouble("endX");
        float ey = (float)b.optDouble("endY");
        int dur = b.optInt("duration", 500);
        return jsonOk(new JSONObject().put("success", swipe(sx, sy, ex, ey, dur)));
    }
    
    private NanoHTTPD.Response handlePinch(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        int cx = (int)b.optDouble("centerX", 540);
        int cy = (int)b.optDouble("centerY", 1200);
        boolean zoomOut = !"in".equals(b.optString("direction"));
        int dist = (int)b.optDouble("distance", 200);
        boolean ok = s != null && s.pinch(cx, cy, zoomOut, dist);
        return jsonOk(new JSONObject().put("success", ok));
    }
    
    private NanoHTTPD.Response handlePath(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        JSONArray pts = b.optJSONArray("points");
        int dur = b.optInt("duration", 1000);
        if (pts == null || pts.length() < 2) return jsonError(400, "need 2+ points");
        List<int[]> path = new ArrayList<>();
        for (int i = 0; i < pts.length(); i++) {
            JSONObject p = pts.getJSONObject(i);
            path.add(new int[]{(int)p.optDouble("x"), (int)p.optDouble("y")});
        }
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        boolean ok = s != null && s.gesturePath(path, dur);
        return jsonOk(new JSONObject().put("success", ok));
    }
    
    private NanoHTTPD.Response handleBezier(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        float sx = (float)b.optDouble("startX", 100);
        float sy = (float)b.optDouble("startY", 1200);
        float ex = (float)b.optDouble("endX", 900);
        float ey = (float)b.optDouble("endY", 1200);
        float cx = (float)b.optDouble("ctrlX", 500);
        float cy = (float)b.optDouble("ctrlY", 600);
        int dur = b.optInt("duration", 500);
        List<int[]> path = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            float t = i / 30f;
            float x = (1-t)*(1-t)*sx + 2*(1-t)*t*cx + t*t*ex;
            float y = (1-t)*(1-t)*sy + 2*(1-t)*t*cy + t*t*ey;
            path.add(new int[]{(int)x, (int)y});
        }
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        boolean ok = s != null && s.gesturePath(path, dur);
        return jsonOk(new JSONObject().put("success", ok));
    }
    
    private NanoHTTPD.Response handleSequence(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        JSONArray gs = b.optJSONArray("gestures");
        if (gs == null) return jsonError(400, "need gestures");
        JSONArray results = new JSONArray();
        for (int i = 0; i < gs.length(); i++) {
            JSONObject g = gs.getJSONObject(i);
            String type = g.optString("type", "");
            boolean ok = false;
            try {
                switch (type) {
                    case "tap":
                        ok = tap((float)g.optDouble("x"), (float)g.optDouble("y"));
                        break;
                    case "swipe":
                        ok = swipe((float)g.optDouble("startX"), (float)g.optDouble("startY"),
                                   (float)g.optDouble("endX"), (float)g.optDouble("endY"),
                                   g.optInt("duration", 300));
                        break;
                    case "longPress":
                        ok = longPress((float)g.optDouble("x"), (float)g.optDouble("y"), g.optInt("duration", 1000));
                        break;
                    case "wait":
                        Thread.sleep(g.optInt("ms", 100));
                        ok = true;
                        break;
                }
            } catch (Exception e) { ok = false; }
            results.put(new JSONObject().put("index", i).put("type", type).put("success", ok));
            Thread.sleep(g.optInt("delay", 100));
        }
        return jsonOk(new JSONObject().put("success", true).put("results", results));
    }
    
    private NanoHTTPD.Response handleTap(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        float x = (float)b.optDouble("x");
        float y = (float)b.optDouble("y");
        return jsonOk(new JSONObject().put("success", tap(x, y)));
    }
    
    private NanoHTTPD.Response handleDoubleTap(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        float x = (float)b.optDouble("x");
        float y = (float)b.optDouble("y");
        boolean ok = tap(x, y);
        Thread.sleep(100);
        ok = ok && tap(x, y);
        return jsonOk(new JSONObject().put("success", ok));
    }
    
    private NanoHTTPD.Response handleLongPress(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        float x = (float)b.optDouble("x");
        float y = (float)b.optDouble("y");
        int dur = b.optInt("duration", 1000);
        return jsonOk(new JSONObject().put("success", longPress(x, y, dur)));
    }
    
    private NanoHTTPD.Response handleScroll(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        String dir = b.optString("direction", "down");
        int dist = (int)b.optDouble("distance", 500);
        int[] sz = getScreenSize();
        float cx = sz[0]/2f, sy, ey;
        if (dir.equals("down")) {
            sy = sz[1]*0.3f;
            ey = sy + dist;
        } else {
            sy = sz[1]*0.7f;
            ey = sy - dist;
        }
        return jsonOk(new JSONObject().put("success", swipe(cx, sy, cx, ey, 300)));
    }
    
    private NanoHTTPD.Response handlePattern(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject b = parseBody(session);
        JSONArray pts = b.optJSONArray("points");
        if (pts == null || pts.length() < 2) return jsonError(400, "need 2+ points");
        float startX = (float)b.optDouble("startX", 100);
        float startY = (float)b.optDouble("startY", 500);
        float cell = (float)b.optDouble("cellSize", 200);
        int dur = b.optInt("duration", 1000);
        List<int[]> path = new ArrayList<>();
        for (int i = 0; i < pts.length(); i++) {
            int p = pts.getInt(i);
            path.add(new int[]{(int)(startX + (p % 3) * cell), (int)(startY + (p / 3) * cell)});
        }
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        boolean ok = s != null && s.gesturePath(path, dur);
        return jsonOk(new JSONObject().put("success", ok));
    }
    
    private int[] getScreenSize() {
        int[] sz = {1080, 2400};
        try {
            android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                android.graphics.Point p = new android.graphics.Point();
                wm.getDefaultDisplay().getSize(p);
                sz[0] = p.x;
                sz[1] = p.y;
            }
        } catch (Exception e) {}
        return sz;
    }
    
    private boolean tap(float x, float y) {
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        return s != null && s.tap((int)x, (int)y);
    }
    
    private boolean swipe(float sx, float sy, float ex, float ey, int dur) {
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        return s != null && s.swipe((int)sx, (int)sy, (int)ex, (int)ey, dur);
    }
    
    private boolean longPress(float x, float y, int dur) {
        PhantomAccessibilityService s = PhantomAccessibilityService.getInstance();
        return s != null && s.longPress((int)x, (int)y, dur);
    }
    
    private JSONObject parseBody(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = session.getQueryParameterString();
        if (body == null || body.isEmpty()) {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            body = files.get("postData");
        }
        return body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
    }
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", data.toString());
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject o = new JSONObject();
        try { o.put("success", false).put("error", msg).put("code", code); } catch (Exception e) {}
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", o.toString());
    }
}
