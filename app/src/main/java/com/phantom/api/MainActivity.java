package com.phantom.api;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.phantom.api.service.PhantomAccessibilityService;
import com.phantom.api.service.PhantomHttpService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 主界面 Activity
 * 用于显示服务状态和配置
 */
public class MainActivity extends Activity {
    private static final String TAG = "PhantomAPI";
    
    private TextView tvStatus;
    private TextView tvIp;
    private TextView tvPort;
    private TextView tvLog;
    private Button btnStart;
    private Button btnOpenAccessibility;
    private ScrollView scrollView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        updateStatus();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvIp = findViewById(R.id.tv_ip);
        tvPort = findViewById(R.id.tv_port);
        tvLog = findViewById(R.id.tv_log);
        btnStart = findViewById(R.id.btn_start);
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        scrollView = (ScrollView) tvLog.getParent();
        
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(this, PhantomHttpService.class);
            startService(intent);
            appendLog("服务启动命令已发送");
            updateStatus();
        });
        
        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        
        // 显示 IP 地址
        String ip = getLocalIpAddress();
        tvIp.setText("IP: " + ip);
        tvPort.setText("端口: 9999");
        
        appendLog("PhantomAPI 已启动");
        appendLog("局域网访问: http://" + ip + ":9999");
        appendLog("API 文档: http://" + ip + ":9999/api/docs");
    }
    
    private void updateStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean serviceRunning = PhantomHttpService.isRunning();
        
        StringBuilder status = new StringBuilder();
        status.append("HTTP 服务: ").append(serviceRunning ? "运行中 ✓" : "已停止 ✗").append("\n");
        status.append("无障碍服务: ").append(accessibilityEnabled ? "已启用 ✓" : "未启用 ✗");
        
        tvStatus.setText(status.toString());
        
        if (accessibilityEnabled && serviceRunning) {
            tvStatus.setTextColor(0xFF4CAF50); // 绿色
        } else {
            tvStatus.setTextColor(0xFFFF5722); // 橙色
        }
    }
    
    private boolean isAccessibilityServiceEnabled() {
        int enabled = 0;
        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.packageName = getPackageName();
            serviceInfo.name = PhantomAccessibilityService.class.getName();
            
            String serviceId = serviceInfo.packageName + "/" + serviceInfo.name;
            
            String settingValue = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            
            if (settingValue != null) {
                return settingValue.contains(serviceId);
            }
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍服务状态失败", e);
        }
        return false;
    }
    
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip != null && !ip.contains(":")) { // IPv4
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 IP 失败", e);
        }
        return "127.0.0.1";
    }
    
    private void appendLog(String message) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date());
        tvLog.append("[" + time + "] " + message + "\n");
        // 滚动到底部
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
