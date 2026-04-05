package com.phantom.api.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;

/**
 * 高级浏览器注入 API 处理器
 * 借鉴自 AutoJs6, FP-Browser, Playwright
 * 
 * 端点：
 * /api/browser/form/fill - 表单自动填充
 * /api/browser/form/submit - 表单提交
 * /api/browser/form/data - 提取表单数据
 * /api/browser/attr/get - 获取元素属性
 * /api/browser/attr/set - 设置元素属性
 * /api/browser/css/get - 获取元素样式
 * /api/browser/css/set - 设置元素样式
 * /api/browser/text/get - 获取元素文本
 * /api/browser/text/set - 设置元素文本
 * /api/browser/html/get - 获取 HTML
 * /api/browser/xpath - XPath 查询
 * /api/browser/queryAll - 查询所有匹配元素
 * /api/browser/table/extract - 提取表格数据
 * /api/browser/meta - 获取页面元数据
 * /api/browser/links - 获取所有链接
 * /api/browser/images - 获取所有图片信息
 * /api/browser/navigate - 页面导航
 * /api/browser/history - 历史记录操作
 * /api/browser/reload - 刷新页面
 * /api/browser/execute - 执行脚本
 * /api/browser/evaluate - 求值表达式
 */
public class BrowserApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-Browser";
    private final Context context;
    private final Handler mainHandler;
    private WebView currentWebView;
    
    public BrowserApiHandler(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setWebView(WebView webView) {
        this.currentWebView = webView;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        switch (uri) {
            case "/api/browser/form/fill": return handleFormFill(session);
            case "/api/browser/form/submit": return handleFormSubmit(session);
            case "/api/browser/form/data": return handleFormData(session);
            case "/api/browser/attr/get": return handleAttrGet(session);
            case "/api/browser/attr/set": return handleAttrSet(session);
            case "/api/browser/css/get": return handleCssGet(session);
            case "/api/browser/css/set": return handleCssSet(session);
            case "/api/browser/text/get": return handleTextGet(session);
            case "/api/browser/text/set": return handleTextSet(session);
            case "/api/browser/html/get": return handleHtmlGet(session);
            case "/api/browser/xpath": return handleXPath(session);
            case "/api/browser/queryAll": return handleQueryAll(session);
            case "/api/browser/table/extract": return handleTableExtract(session);
            case "/api/browser/meta": return handleMeta(session);
            case "/api/browser/links": return handleLinks(session);
            case "/api/browser/images": return handleImages(session);
            case "/api/browser/navigate": return handleNavigate(session);
            case "/api/browser/history": return handleHistory(session);
            case "/api/browser/reload": return handleReload(session);
            case "/api/browser/execute": return handleExecute(session);
            case "/api/browser/evaluate": return handleEvaluate(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    private NanoHTTPD.Response handleFormFill(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        JSONObject formData = body.optJSONObject("data");
        if (formData == null) return jsonError(400, "缺少 data 参数");
        
        StringBuilder script = new StringBuilder("(function() { var filled = [];");
        JSONArray names = formData.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i);
                String value = formData.optString(name);
                String escapedName = escapeJsString(name);
                String escapedValue = escapeJsString(value);
                script.append("var el=document.querySelector('[name=\\\"").append(escapedName).append("\\\"],#").append(escapedName).append("');"); 
                script.append("if(el){el.value='").append(escapedValue).append("';el.dispatchEvent(new Event('input',{bubbles:true}));filled.push('").append(escapedName).append("');}");
            }
        }
        script.append(" return filled; })()");
        return executeScript(script.toString());
    }
    
    private NanoHTTPD.Response handleFormSubmit(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "form");
        return executeScript("(function(){var f=document.querySelector('" + selector + "');if(f){f.submit();return true;}return false;})()");
    }
    
    private NanoHTTPD.Response handleFormData(NanoHTTPD.IHTTPSession session) throws Exception {
        String script = "(function(){var d={};document.querySelectorAll('input,select,textarea').forEach(function(e){if(e.name)d[e.name]=e.value});return JSON.stringify(d)})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleAttrGet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String attr = body.optString("attr", "");
        if (selector.isEmpty() || attr.isEmpty()) return jsonError(400, "缺少参数");
        return executeScript("(function(){var e=document.querySelector('" + selector + "');return e?e.getAttribute('" + attr + "'):null})()");
    }
    
    private NanoHTTPD.Response handleAttrSet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String attr = body.optString("attr", "");
        String value = body.optString("value", "");
        if (selector.isEmpty() || attr.isEmpty()) return jsonError(400, "缺少参数");
        return executeScript("(function(){var e=document.querySelector('" + selector + "');if(e){e.setAttribute('" + attr + "','" + escapeJsString(value) + "');return true;}return false;})()");
    }
    
    private NanoHTTPD.Response handleCssGet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String property = body.optString("property", "");
        if (selector.isEmpty()) return jsonError(400, "缺少 selector");
        String script = property.isEmpty() 
            ? "(function(){var e=document.querySelector('" + selector + "');return e?JSON.stringify(getComputedStyle(e)):null})()"
            : "(function(){var e=document.querySelector('" + selector + "');return e?getComputedStyle(e)['" + property + "']:null})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleCssSet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        JSONObject styles = body.optJSONObject("styles");
        if (selector.isEmpty() || styles == null) return jsonError(400, "缺少参数");
        StringBuilder css = new StringBuilder();
        JSONArray keys = styles.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.optString(i);
                css.append("e.style['").append(k).append("']='").append(escapeJsString(styles.optString(k))).append("';");
            }
        }
        return executeScript("(function(){var e=document.querySelector('" + selector + "');if(e){" + css + "return true;}return false;})()");
    }
    
    private NanoHTTPD.Response handleTextGet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String script = selector.isEmpty() 
            ? "document.body.innerText"
            : "(function(){var e=document.querySelector('" + selector + "');return e?e.textContent:null})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleTextSet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String text = body.optString("text", "");
        return executeScript("(function(){var e=document.querySelector('" + selector + "');if(e){e.textContent='" + escapeJsString(text) + "';return true;}return false;})()");
    }
    
    private NanoHTTPD.Response handleHtmlGet(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String script = selector.isEmpty() 
            ? "document.documentElement.outerHTML"
            : "(function(){var e=document.querySelector('" + selector + "');return e?e.outerHTML:null})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleXPath(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String xpath = body.optString("xpath", "");
        String attr = body.optString("attr", "textContent");
        if (xpath.isEmpty()) return jsonError(400, "缺少 xpath");
        String script = "(function(){var r=document.evaluate('" + escapeJsString(xpath) + "',document,null,XPathResult.ORDERED_NODE_ITERATOR_TYPE,null);var a=[];var n;while(n=r.iterateNext()){a.push(n." + attr + ");}return JSON.stringify(a);})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleQueryAll(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "");
        String attr = body.optString("attr", "textContent");
        if (selector.isEmpty()) return jsonError(400, "缺少 selector");
        return executeScript("(function(){var es=document.querySelectorAll('" + selector + "');return JSON.stringify(Array.from(es).map(function(e){return e." + attr + ";}))})()");
    }
    
    private NanoHTTPD.Response handleTableExtract(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "table");
        String script = "(function(){var t=document.querySelector('" + selector + "');if(!t)return null;var d=[];t.querySelectorAll('tr').forEach(function(r){var rd=[];r.querySelectorAll('td,th').forEach(function(c){rd.push(c.textContent.trim());});if(rd.length>0)d.push(rd);});return JSON.stringify(d);})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleMeta(NanoHTTPD.IHTTPSession session) throws Exception {
        String script = "(function(){var m={};m.title=document.title;m.url=location.href;m.domain=location.hostname;document.querySelectorAll('meta').forEach(function(t){var n=t.getAttribute('name')||t.getAttribute('property');if(n)m[n]=t.getAttribute('content');});return JSON.stringify(m);})()";
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleLinks(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String selector = body.optString("selector", "a");
        return executeScript("(function(){var ls=document.querySelectorAll('" + selector + "');return JSON.stringify(Array.from(ls).map(function(a){return{text:a.textContent.trim(),href:a.href};}))})()");
    }
    
    private NanoHTTPD.Response handleImages(NanoHTTPD.IHTTPSession session) throws Exception {
        return executeScript("(function(){var is=document.querySelectorAll('img');return JSON.stringify(Array.from(is).map(function(i){return{alt:i.alt,src:i.src,width:i.width,height:i.height};}))})()");
    }
    
    private NanoHTTPD.Response handleNavigate(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String url = body.optString("url", "");
        if (url.isEmpty()) return jsonError(400, "缺少 url");
        if (currentWebView == null) return jsonError(500, "无 WebView");
        
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try { currentWebView.loadUrl(url); } catch (Exception e) {}
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("url", url));
    }
    
    private NanoHTTPD.Response handleHistory(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String action = body.optString("action", "back");
        int steps = body.optInt("steps", 1);
        
        if (currentWebView == null) return jsonError(500, "无 WebView");
        
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try {
                if ("back".equals(action)) {
                    currentWebView.goBack();
                } else if ("forward".equals(action)) {
                    currentWebView.goForward();
                } else if ("go".equals(action)) {
                    currentWebView.goBackOrForward(steps);
                }
            } catch (Exception e) {}
            latch.countDown();
        });
        latch.await(2, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true).put("action", action));
    }
    
    private NanoHTTPD.Response handleReload(NanoHTTPD.IHTTPSession session) throws Exception {
        if (currentWebView == null) return jsonError(500, "无 WebView");
        
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try { currentWebView.reload(); } catch (Exception e) {}
            latch.countDown();
        });
        latch.await(2, TimeUnit.SECONDS);
        return jsonOk(new JSONObject().put("success", true));
    }
    
    private NanoHTTPD.Response handleExecute(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String script = body.optString("script", "");
        if (script.isEmpty()) return jsonError(400, "缺少 script");
        return executeScript(script);
    }
    
    private NanoHTTPD.Response handleEvaluate(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String expr = body.optString("expression", "");
        if (expr.isEmpty()) return jsonError(400, "缺少 expression");
        return executeScript("(function(){try{return JSON.stringify(" + expr + ");}catch(e){return JSON.stringify({error:e.message});}})()");
    }
    
    private NanoHTTPD.Response executeScript(String script) {
        if (currentWebView == null) return jsonError(500, "无 WebView");
        
        AtomicReference<String> result = new AtomicReference<>("null");
        CountDownLatch latch = new CountDownLatch(1);
        
        mainHandler.post(() -> {
            try {
                currentWebView.evaluateJavascript(script, r -> {
                    result.set(r);
                    latch.countDown();
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });
        
        try {
            latch.await(10, TimeUnit.SECONDS);
            return jsonOk(new JSONObject().put("success", true).put("result", result.get()));
        } catch (Exception e) {
            return jsonError(500, "执行超时");
        }
    }
    
    private String escapeJsString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    private JSONObject parseBody(NanoHTTPD.IHTTPSession session) throws Exception {
        String body = session.getQueryParameterString();
        if (body == null || body.isEmpty()) {
            java.util.Map<String, String> files = new java.util.HashMap<>();
            session.parseBody(files);
            body = files.get("postData");
        }
        return body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
    }
    
    private NanoHTTPD.Response jsonOk(JSONObject data) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", data.toString());
    }
    
    private NanoHTTPD.Response jsonError(int code, String msg) {
        JSONObject obj = new JSONObject();
        try { obj.put("success", false).put("error", msg).put("code", code); } catch (Exception e) {}
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", obj.toString());
    }
}