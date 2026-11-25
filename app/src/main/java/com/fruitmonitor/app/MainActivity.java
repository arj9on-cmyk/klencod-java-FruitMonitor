package com.fruitmonitor.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

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

        // إعداد WebView لعرض لوحة التحكم
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        
        // ربط Java بـ JavaScript
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String getJson() {
                return db != null ? db.getAllJson() : "[]";
            }
        }, "AndroidBridge");

        // تحميل ملف HTML من مجلد assets
        webView.loadUrl("file:///android_asset/dashboard.html");

        // زر الذهاب لإعدادات إمكانية الوصول
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
        // تحديث البيانات عند العودة للتطبيق
        if (webView != null) {
            webView.post(() -> webView.loadUrl("javascript:load()"));
        }
    }

    // دالة للتحقق مما إذا كانت الخدمة مفعلة
    private void updateStatus(TextView tv) {
        String serviceName = getPackageName() + "/" + FruitService.class.getName();
        String enabled = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        
        boolean isActive = enabled != null && enabled.contains(serviceName);
        
        if (isActive) {
            tv.setText("✅ المراقب يعمل ويقرأ الشاشة");
            tv.setTextColor(0xFF4CAF50); // أخضر
        } else {
            tv.setText("⚠️ اضغط الزر أدناه لتفعيل خدمة المراقبة");
            tv.setTextColor(0xFFFF9800); // برتقالي
        }
    }

    // دالة يستدعيها الـ Service لتحديث الشاشة تلقائياً عند تسجيل جولة جديدة
    public static void refreshDashboard() {
        if (webView != null) {
            webView.post(() -> webView.loadUrl("javascript:load()"));
        }
    }
}