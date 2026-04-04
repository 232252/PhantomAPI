package com.phantom.api.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 网络工具类
 */
public class NetworkUtils {
    private static final String TAG = "NetworkUtils";
    private static Context appContext;
    
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }
    
    /**
     * 检查网络是否连接
     */
    public static boolean isNetworkConnected() {
        if (appContext == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查网络失败", e);
        }
        return false;
    }
    
    /**
     * 检查 WiFi 是否连接
     */
    public static boolean isWifiConnected() {
        if (appContext == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查 WiFi 失败", e);
        }
        return false;
    }
    
    /**
     * 检查移动数据是否连接
     */
    public static boolean isMobileConnected() {
        if (appContext == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查移动数据失败", e);
        }
        return false;
    }
    
    /**
     * 获取本机 IP 地址
     */
    public static String getLocalIpAddress() {
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
                    if (ip != null && !ip.contains(":")) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 IP 失败", e);
        }
        return "127.0.0.1";
    }
    
    /**
     * 获取 WiFi 信息
     */
    public static JSONObject getWifiInfo() {
        JSONObject result = new JSONObject();
        try {
            if (appContext == null) {
                result.put("error", "not initialized");
                return result;
            }
            
            WifiManager wifiManager = (WifiManager) 
                appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    result.put("ssid", wifiInfo.getSSID());
                    result.put("bssid", wifiInfo.getBSSID());
                    result.put("rssi", wifiInfo.getRssi());
                    result.put("linkSpeed", wifiInfo.getLinkSpeed());
                    
                    int ipAddress = wifiInfo.getIpAddress();
                    if (ipAddress != 0) {
                        String ip = ((ipAddress & 0xff) + "." +
                                ((ipAddress >> 8) & 0xff) + "." +
                                ((ipAddress >> 16) & 0xff) + "." +
                                ((ipAddress >> 24) & 0xff));
                        result.put("ipAddress", ip);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 WiFi 信息失败", e);
        }
        return result;
    }
    
    /**
     * 检查端口是否可用
     */
    public static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
