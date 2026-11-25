package com.fruitmonitor.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    static WebView webView;
    static FruitService.DBHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView status = findViewById(R.id.statusText);
        Button btn = findViewById(R.id.btnEnable);
        webView = findViewById(R.id.webView);

        // تهيئة قاعدة البيانات
        db = new FruitService.DBHelper(this);

        // إعداد WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        
        // ربط Java بـ JavaScript
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String getJson() {
                return db != null ? db.getAllJson() : "[]";
            }
        }, "AndroidBridge");

        // تحميل Dashboard
        webView.loadUrl("file:///android_asset/dashboard.html");

        // زر تفعيل الخدمة
        btn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        updateStatus(status);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView status = findViewById(R.id.statusText);
        updateStatus(status);
        if (webView != null) {
            webView.post(() -> webView.loadUrl("javascript:load()"));
        }
    }

    private void updateStatus(TextView tv) {
        String serviceName = getPackageName() + "/" + FruitService.class.getName();
        String enabled = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        
        boolean isActive = enabled != null && enabled.contains(serviceName);
        
        if (isActive) {
            tv.setText("✅ المراقب يعمل ويقرأ الشاشة");
            tv.setTextColor(0xFF4CAF50);
        } else {
            tv.setText("⚠️ اضغط الزر أدناه لتفعيل خدمة المراقبة");
            tv.setTextColor(0xFFFF9800);
        }
    }

    public static void refreshDashboard() {
        if (webView != null) {
            webView.post(() -> webView.loadUrl("javascript:load()"));
        }
    }
}