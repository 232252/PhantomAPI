package com.phantom.api.engine;

import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.KeyEvent;

import java.lang.reflect.Method;

/**
 * 底层事件注入引擎
 * 
 * 核心能力：
 * 直接调用隐藏 API InputManager.injectInputEvent()
 * 替代慢速的 adb shell input
 * 延迟 < 5ms
 * 
 * 权限要求：
 * - android.permission.INJECT_EVENTS
 * - 系统 App (priv-app 或 sharedUserId="android.uid.system")
 */
public class InputInjectionEngine {
    private static final String TAG = "InputInjection";
    
    // InputManager 实例
    private Object inputManager;
    private Method injectInputEventMethod;
    
    // 注入模式
    private static final int INJECT_MODE_ASYNC = 0;      // 异步，最快
    private static final int INJECT_MODE_WAIT_FOR_DISPATCH = 1;  // 等待分发
    private static final int INJECT_MODE_WAIT_FOR_RESULT = 2;     // 等待结果
    
    private boolean initialized = false;
    
    public InputInjectionEngine() {
        init();
    }
    
    /**
     * 初始化 InputManager
     */
    private void init() {
        try {
            // 通过反射获取 InputManager 单例
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Method getInstanceMethod = inputManagerClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            inputManager = getInstanceMethod.invoke(null);
            
            // 获取 injectInputEvent 方法
            injectInputEventMethod = inputManagerClass.getDeclaredMethod(
                "injectInputEvent",
                InputEvent.class,
                int.class
            );
            injectInputEventMethod.setAccessible(true);
            
            initialized = true;
            Log.i(TAG, "InputManager 初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "InputManager 初始化失败", e);
            initialized = false ;
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 注入触摸事件
     */
    private boolean injectMotionEvent(MotionEvent event) {
        if (!initialized) {
            Log.e(TAG, "InputManager 未初始化");
            return false;
        }
        
        try {
            injectInputEventMethod.invoke(inputManager, event, INJECT_MODE_ASYNC);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "注入触摸事件失败", e);
            return false;
        }
    }
    
    /**
     * 注入按键事件
     */
    private boolean injectKeyEvent(KeyEvent event) {
        if (!initialized) {
            Log.e(TAG, "InputManager 未初始化");
            return false ;
        }
        
        try {
            injectInputEventMethod.invoke(inputManager, event, INJECT_MODE_ASYNC);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "注入按键事件失败", e);
            return false;
        }
    }
    
    /**
     * 点击指定坐标
     * @param x X 坐标
     * @param y Y 坐标
     * @return 是否成功
     */
    public boolean tap(int x, int y) {
        return tap(x, y, 50); // 默认 50ms 按压时长
    }
    
    /**
     * 点击指定坐标
     * @param x X 坐标
     * @param y Y 坐标
     * @param duration 按压时长 (ms)
     */
    public boolean tap(int x, int y, long duration) {
        long downTime = SystemClock.uptimeMillis();
        
        // 创建按下事件
        MotionEvent downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        );
        
        // 创建抬起事件
        MotionEvent upEvent = MotionEvent.obtain(
            downTime,
            downTime + duration,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        );
        
        try {
            // 注入按下事件
            if (!injectMotionEvent(downEvent)) {
                return false;
            }
            
            // 等待
            SystemClock.sleep(duration);
            
            // 注入抬起事件
            return injectMotionEvent(upEvent);
        } finally {
            downEvent.recycle();
            upEvent.recycle();
        }
    }
    
    /**
     * 长按指定坐标
     */
    public boolean longPress(int x, int y) {
        return longPress(x, y, 500); // 默认 500ms
    }
    
    /**
     * 长按指定坐标
     */
    public boolean longPress(int x, int y, long duration) {
        return tap(x, y, duration);
    }
    
    /**
     * 滑动
     * @param startX 起始 X
     * @param startY 起始 Y
     * @param endX 结束 X
     * @param endY 结束 Y
     * @param duration 持续时间 (ms)
     */
    public boolean swipe(int startX, int startY, int endX, int endY, long duration) {
        long downTime = SystemClock.uptimeMillis();
        
        // 创建按下事件
        MotionEvent downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            startX,
            startY,
            0
        );
        
        // 创建移动事件
        MotionEvent moveEvent = MotionEvent.obtain(
            downTime,
            downTime + duration / 2,
            MotionEvent.ACTION_MOVE,
            endX,
            endY,
            0
        );
        
        // 创建抬起事件
        MotionEvent upEvent = MotionEvent.obtain(
            downTime,
            downTime + duration,
            MotionEvent.ACTION_UP,
            endX,
            endY,
            0
        );
        
        try {
            // 注入按下
            if (!injectMotionEvent(downEvent)) {
                return false;
            }
            
            // 等待一半时间
            SystemClock.sleep(duration / 2);
            
            // 注入移动
            injectMotionEvent(moveEvent);
            
            // 等待剩余时间
            SystemClock.sleep(duration / 2);
            
            // 注入抬起
            return injectMotionEvent(upEvent);
        } finally {
            downEvent.recycle();
            moveEvent.recycle();
            upEvent.recycle();
        }
    }
    
    /**
     * 注入按键
     * @param keyCode 按键码 (KeyEvent.KEYCODE_*)
     */
    public boolean pressKey(int keyCode) {
        long downTime = SystemClock.uptimeMillis();
        
        KeyEvent downEvent = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent upEvent = new KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0);
        
        try {
            if (!injectKeyEvent(downEvent)) {
                return false;
            }
            SystemClock.sleep(50);
            return injectKeyEvent(upEvent);
        } finally {
            // KeyEvent 不需要 recycle
        }
    }
    
    /**
     * 返回键
     */
    public boolean pressBack() {
        return pressKey(KeyEvent.KEYCODE_BACK);
    }
    
    /**
     * Home 键
     */
    public boolean pressHome() {
        return pressKey(KeyEvent.KEYCODE_HOME);
    }
    
    /**
     * 菜单键
     */
    public boolean pressMenu() {
        return pressKey(KeyEvent.KEYCODE_MENU);
    }
    
    /**
     * 输入文本 (通过按键事件模拟)
     * 注意：此方法较慢，建议使用 AccessibilityService 的 setText
     */
    public boolean typeText(String text) {
        for (char c : text.toCharArray()) {
            int keyCode = charToKeyCode(c);
            if (keyCode != 0) {
                if (!pressKey(keyCode)) {
                    return false;
                }
                SystemClock.sleep(20); // 字符间隔
            }
        }
        return true;
    }
    
    /**
     * 字符转按键码
     */
    private int charToKeyCode(char c) {
        switch (c) {
            case 'a': case 'A': return KeyEvent.KEYCODE_A;
            case 'b': case 'B': return KeyEvent.KEYCODE_B;
            case 'c': case 'C': return KeyEvent.KEYCODE_C;
            case 'd': case 'D': return KeyEvent.KEYCODE_D;
            case 'e': case 'E': return KeyEvent.KEYCODE_E;
            case 'f': case 'F': return KeyEvent.KEYCODE_F;
            case 'g': case 'G': return KeyEvent.KEYCODE_G;
            case 'h': case 'H': return KeyEvent.KEYCODE_H;
            case 'i': case 'I': return KeyEvent.KEYCODE_I;
            case 'j': case 'J': return KeyEvent.KEYCODE_J;
            case 'k': case 'K': return KeyEvent.KEYCODE_K;
            case 'l': case 'L': return KeyEvent.KEYCODE_L;
            case 'm': case 'M': return KeyEvent.KEYCODE_M;
            case 'n': case 'N': return KeyEvent.KEYCODE_N;
            case 'o': case 'O': return KeyEvent.KEYCODE_O;
            case 'p': case 'P': return KeyEvent.KEYCODE_P;
            case 'q': case 'Q': return KeyEvent.KEYCODE_Q;
            case 'r': case 'R': return KeyEvent.KEYCODE_R;
            case 's': case 'S': return KeyEvent.KEYCODE_S;
            case 't': case 'T': return KeyEvent.KEYCODE_T;
            case 'u': case 'U': return KeyEvent.KEYCODE_U;
            case 'v': case 'V': return KeyEvent.KEYCODE_V;
            case 'w': case 'W': return KeyEvent.KEYCODE_W;
            case 'x': case 'X': return KeyEvent.KEYCODE_X;
            case 'y': case 'Y': return KeyEvent.KEYCODE_Y;
            case 'z': case 'Z': return KeyEvent.KEYCODE_Z;
            case '0': return KeyEvent.KEYCODE_0;
            case '1': return KeyEvent.KEYCODE_1;
            case '2': return KeyEvent.KEYCODE_2;
            case '3': return KeyEvent.KEYCODE_3;
            case '4': return KeyEvent.KEYCODE_4;
            case '5': return KeyEvent.KEYCODE_5;
            case '6': return KeyEvent.KEYCODE_6;
            case '7': return KeyEvent.KEYCODE_7;
            case '8': return KeyEvent.KEYCODE_8;
            case '9': return KeyEvent.KEYCODE_9;
            case ' ': return KeyEvent.KEYCODE_SPACE;
            case '\n': return KeyEvent.KEYCODE_ENTER;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case ',': return KeyEvent.KEYCODE_COMMA;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case '=': return KeyEvent.KEYCODE_EQUALS;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case '*': return KeyEvent.KEYCODE_STAR;
            case '#': return KeyEvent.KEYCODE_POUND;
            case '@': return KeyEvent.KEYCODE_AT;
            case '+': return KeyEvent.KEYCODE_PLUS;
            default: return 0;
        }
    }
}
