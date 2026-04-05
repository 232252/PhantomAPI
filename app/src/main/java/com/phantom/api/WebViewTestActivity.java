package com.phantom.api;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.util.Log;

/**
 * WebView 测试 Activity
 */
public class WebViewTestActivity extends Activity {
    private static final String TAG = "WebViewTest";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        
        // 必须设置 WebChromeClient 才能触发 onJsPrompt
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                Log.i(TAG, ">>> onJsPrompt called: " + message);
                // 不拦截，让 LSPosed hook 处理
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        );
        layout.addView(webView, params);
        
        setContentView(layout);
        
        // 加载测试页面
        webView.loadUrl("https://www.baidu.com");
    }
}
