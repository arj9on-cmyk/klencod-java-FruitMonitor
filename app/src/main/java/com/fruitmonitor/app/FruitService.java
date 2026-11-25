package com.fruitmonitor.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FruitService extends AccessibilityService {

    private DBHelper db;
    private int lastRound = -1;
    private String lastFruit = "";

    // أنماط القراءة من الشاشة
    private static final Pattern ROUND_P = Pattern.compile("الجولة\\s+(\\d+)\\s+اليوم");
    private static final Pattern FRUIT_P = Pattern.compile("الفاكهة الفائزة[:：]\\s*(.)");

    // خريطة الإيموجي → الاسم
    private static final Map<String, String> FRUIT_MAP = new HashMap<>();
    static {
        FRUIT_MAP.put("🍊", "orange");
        FRUIT_MAP.put("🍋", "lemon");
        FRUIT_MAP.put("🍎", "apple");
        FRUIT_MAP.put("🍒", "cherry");
        FRUIT_MAP.put("🍌", "banana");
        FRUIT_MAP.put("🥑", "avocado");
        FRUIT_MAP.put("🍉", "watermelon");
        FRUIT_MAP.put("🍇", "grape");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        db = new DBHelper(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String text = extractText(root);
        if (text == null || text.isEmpty()) return;

        // استخراج رقم الجولة
        Matcher rm = ROUND_P.matcher(text);
        if (!rm.find()) return;
        int round;
        try { round = Integer.parseInt(rm.group(1)); }
        catch (NumberFormatException e) { return; }

        // استخراج الفاكهة الفائزة
        Matcher fm = FRUIT_P.matcher(text);
        String fruit = null;
        if (fm.find()) {
            String emoji = fm.group(1);
            fruit = FRUIT_MAP.get(emoji);
        }

        // التسجيل فقط عند وجود فاكهة وتغير الجولة
        if (fruit != null && round != lastRound) {
            boolean saved = db.insertRound(round, fruit);
            if (saved) {
                lastRound = round;
                lastFruit = fruit;
                MainActivity.refreshDashboard();
            }
        }
    }

    private String extractText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText()).append(" ");
        if (node.getContentDescription() != null)
            sb.append(node.getContentDescription()).append(" ");

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(extractText(child));
            }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (db != null) db.close();
        return super.onUnbind(intent);
    }

    // ============================================================
    // قاعدة البيانات (Static Nested Class)
    // ============================================================
    public static class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context) {
            super(context.getApplicationContext(), "fruit.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE rounds(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "roundNum INTEGER," +
                "winner TEXT," +
                "date TEXT," +
                "time TEXT)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            db.execSQL("DROP TABLE IF EXISTS rounds");
            onCreate(db);
        }

        /**
         * تسجيل جولة جديدة — يرجع true إذا تم الحفظ، false إذا كان مكرراً
         */
        public boolean insertRound(int roundNum, String winner) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

            SQLiteDatabase r = getReadableDatabase();
            Cursor c = r.rawQuery(
                "SELECT id FROM rounds WHERE roundNum=? AND date=?",
                new String[]{String.valueOf(roundNum), today}
            );
            boolean exists = c.getCount() > 0;
            c.close();

            if (exists) return false;

            ContentValues v = new ContentValues();
            v.put("roundNum", roundNum);
            v.put("winner", winner);
            v.put("date", today);
            v.put("time", now);
            getWritableDatabase().insert("rounds", null, v);
            return true;
        }

        /**
         * إرجاع كل البيانات كـ JSON (يُستهلك من WebView)
         */
        public String getAllJson() {
            SQLiteDatabase r = getReadableDatabase();
            Cursor c = r.rawQuery("SELECT * FROM rounds ORDER BY id DESC LIMIT 5000", null);
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (c.moveToNext()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{")
                  .append("\"roundNum\":").append(c.getInt(c.getColumnIndexOrThrow("roundNum"))).append(",")
                  .append("\"winner\":\"").append(c.getString(c.getColumnIndexOrThrow("winner"))).append("\",")
                  .append("\"date\":\"").append(c.getString(c.getColumnIndexOrThrow("date"))).append("\",")
                  .append("\"time\":\"").append(c.getString(c.getColumnIndexOrThrow("time"))).append("\"")
                  .append("}");
            }
            c.close();
            return sb.append("]").toString();
        }
    }
}