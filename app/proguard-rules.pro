# PhantomAPI ProGuard 规则

# 保留应用主类
-keep public class com.phantom.api.** { *; }

# 保留 NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# 保留 Java-WebSocket
-keep class org.java_websocket.** { *; }

# 保留 JSON 相关
-keep class org.json.** { *; }

# 保留反射调用的类和方法
-keep class android.hardware.input.InputManager { *; }
-keep class android.view.accessibility.AccessibilityNodeInfo { *; }

# 保留 Xposed 相关
-keep class de.robv.android.xposed.** { *; }
-keep class com.phantom.api.hook.** { *; }

# 移除日志 (发布时启用)
# -assumenosideeffects class android.util.Log {
#     public static int v(...);
#     public static int d(...);
#     public static int i(...);
# }
