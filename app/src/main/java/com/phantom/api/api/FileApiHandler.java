package com.phantom.api.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.phantom.api.engine.HttpServerEngine;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import fi.iki.elonen.NanoHTTPD;

/**
 * 文件操作 API 处理器
 * 借鉴自 MobiAgent, AutoJs6
 * 
 * 端点：
 * /api/file/read - 读取文件
 * /api/file/write - 写入文件
 * /api/file/append - 追加内容
 * /api/file/delete - 删除文件
 * /api/file/exists - 检查文件是否存在
 * /api/file/list - 列出目录
 * /api/file/copy - 复制文件
 * /api/file/move - 移动文件
 * /api/file/mkdir - 创建目录
 * /api/file/download - 下载文件
 * /api/file/upload - 上传文件
 */
public class FileApiHandler implements HttpServerEngine.ApiHandler {
    private static final String TAG = "PhantomAPI-File";
    private final Context context;
    
    public FileApiHandler(Context context) {
        this.context = context;
    }
    
    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) throws Exception {
        String uri = session.getUri();
        
        switch (uri) {
            case "/api/file/read": return handleRead(session);
            case "/api/file/write": return handleWrite(session);
            case "/api/file/append": return handleAppend(session);
            case "/api/file/delete": return handleDelete(session);
            case "/api/file/exists": return handleExists(session);
            case "/api/file/list": return handleList(session);
            case "/api/file/copy": return handleCopy(session);
            case "/api/file/move": return handleMove(session);
            case "/api/file/mkdir": return handleMkdir(session);
            case "/api/file/download": return handleDownload(session);
            default: return jsonError(404, "Unknown: " + uri);
        }
    }
    
    private NanoHTTPD.Response handleRead(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        String encoding = body.optString("encoding", "UTF-8");
        int maxSize = body.optInt("maxSize", 1024 * 1024); // 默认最大 1MB
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File file = new File(path);
            if (!file.exists()) {
                return jsonError(404, "文件不存在: " + path);
            }
            if (!file.canRead()) {
                return jsonError(403, "无法读取文件: " + path);
            }
            if (file.length() > maxSize) {
                return jsonError(413, "文件过大: " + file.length() + " > " + maxSize);
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            return jsonOk(new JSONObject()
                .put("success", true)
                .put("content", content.toString())
                .put("size", file.length()));
        } catch (Exception e) {
            return jsonError(500, "读取失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleWrite(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        String content = body.optString("content", "");
        String encoding = body.optString("encoding", "UTF-8");
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes(encoding));
            }
            
            return jsonOk(new JSONObject()
                .put("success", true)
                .put("path", path)
                .put("size", file.length()));
        } catch (Exception e) {
            return jsonError(500, "写入失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleAppend(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        String content = body.optString("content", "");
        String encoding = body.optString("encoding", "UTF-8");
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File file = new File(path);
            
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(content.getBytes(encoding));
            }
            
            return jsonOk(new JSONObject()
                .put("success", true)
                .put("path", path)
                .put("size", file.length()));
        } catch (Exception e) {
            return jsonError(500, "追加失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleDelete(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        boolean recursive = body.optBoolean("recursive", false);
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File file = new File(path);
            if (!file.exists()) {
                return jsonError(404, "文件不存在: " + path);
            }
            
            boolean deleted;
            if (recursive && file.isDirectory()) {
                deleted = deleteRecursive(file);
            } else {
                deleted = file.delete();
            }
            
            return jsonOk(new JSONObject().put("success", deleted).put("path", path));
        } catch (Exception e) {
            return jsonError(500, "删除失败: " + e.getMessage());
        }
    }
    
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
    
    private NanoHTTPD.Response handleExists(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        File file = new File(path);
        return jsonOk(new JSONObject()
            .put("success", true)
            .put("exists", file.exists())
            .put("isFile", file.isFile())
            .put("isDirectory", file.isDirectory()));
    }
    
    private NanoHTTPD.Response handleList(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        boolean showHidden = body.optBoolean("showHidden", false);
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                return jsonError(404, "目录不存在: " + path);
            }
            if (!dir.isDirectory()) {
                return jsonError(400, "不是目录: " + path);
            }
            
            org.json.JSONArray list = new org.json.JSONArray();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!showHidden && file.getName().startsWith(".")) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("name", file.getName());
                    item.put("path", file.getAbsolutePath());
                    item.put("isFile", file.isFile());
                    item.put("isDirectory", file.isDirectory());
                    item.put("size", file.length());
                    item.put("lastModified", file.lastModified());
                    item.put("canRead", file.canRead());
                    item.put("canWrite", file.canWrite());
                    list.put(item);
                }
            }
            
            return jsonOk(new JSONObject().put("success", true).put("path", path).put("files", list));
        } catch (Exception e) {
            return jsonError(500, "列出目录失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleCopy(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String src = body.optString("src", "");
        String dst = body.optString("dst", "");
        
        if (src.isEmpty() || dst.isEmpty()) {
            return jsonError(400, "缺少 src 或 dst 参数");
        }
        
        try {
            File srcFile = new File(src);
            File dstFile = new File(dst);
            
            if (!srcFile.exists()) {
                return jsonError(404, "源文件不存在: " + src);
            }
            
            copyFile(srcFile, dstFile);
            
            return jsonOk(new JSONObject().put("success", true).put("src", src).put("dst", dst));
        } catch (Exception e) {
            return jsonError(500, "复制失败: " + e.getMessage());
        }
    }
    
    private void copyFile(File src, File dst) throws Exception {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
    
    private NanoHTTPD.Response handleMove(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String src = body.optString("src", "");
        String dst = body.optString("dst", "");
        
        if (src.isEmpty() || dst.isEmpty()) {
            return jsonError(400, "缺少 src 或 dst 参数");
        }
        
        try {
            File srcFile = new File(src);
            File dstFile = new File(dst);
            
            if (!srcFile.exists()) {
                return jsonError(404, "源文件不存在: " + src);
            }
            
            boolean moved = srcFile.renameTo(dstFile);
            
            return jsonOk(new JSONObject().put("success", moved).put("src", src).put("dst", dst));
        } catch (Exception e) {
            return jsonError(500, "移动失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleMkdir(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String path = body.optString("path", "");
        boolean parents = body.optBoolean("parents", true);
        
        if (path.isEmpty()) {
            return jsonError(400, "缺少 path 参数");
        }
        
        try {
            File dir = new File(path);
            boolean created;
            
            if (parents) {
                created = dir.mkdirs();
            } else {
                created = dir.mkdir();
            }
            
            return jsonOk(new JSONObject().put("success", created || dir.exists()).put("path", path));
        } catch (Exception e) {
            return jsonError(500, "创建目录失败: " + e.getMessage());
        }
    }
    
    private NanoHTTPD.Response handleDownload(NanoHTTPD.IHTTPSession session) throws Exception {
        JSONObject body = parseBody(session);
        String url = body.optString("url", "");
        String path = body.optString("path", "");
        
        if (url.isEmpty() || path.isEmpty()) {
            return jsonError(400, "缺少 url 或 path 参数");
        }
        
        try {
            File outputFile = new File(path);
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            URL downloadUrl = new URL(url);
            URLConnection conn = downloadUrl.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }
            
            return jsonOk(new JSONObject()
                .put("success", true)
                .put("path", path)
                .put("size", outputFile.length()));
        } catch (Exception e) {
            return jsonError(500, "下载失败: " + e.getMessage());
        }
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