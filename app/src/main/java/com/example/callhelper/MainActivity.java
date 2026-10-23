package com.example.callhelper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int p = 40; ll.setPadding(p, p, p, p);
        TextView tv = new TextView(this);
        tv.setText("CallHelper\n\n1) امنح الأذونات\n2) سجّل صوتك\n3) فعّل Accessibility\n4) شغّل الخدمة");
        tv.setTextSize(16f);
        ll.addView(tv);

        Button perm = new Button(this);
        perm.setText("1️⃣ منح الأذونات");
        perm.setOnClickListener(v -> requestAll());
        ll.addView(perm);

        Button train = new Button(this);
        train.setText("2️⃣ 🎤 سجّل صوتك");
        train.setOnClickListener(v -> startActivity(new Intent(this, VoiceTrainerActivity.class)));
        ll.addView(train);

        Button a11y = new Button(this);
        a11y.setText("3️⃣ فتح Accessibility");
        a11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ll.addView(a11y);

        Button start = new Button(this);
        start.setText("4️⃣ ▶️ تشغيل الخدمة");
        start.setOnClickListener(v -> startService());
        ll.addView(start);

        Button stop = new Button(this);
        stop.setText("⏹️ إيقاف الخدمة");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, VoiceCommandService.class));
            Toast.makeText(this, "تم إيقاف الخدمة", Toast.LENGTH_SHORT).show();
        });
        ll.addView(stop);

        ScrollView sv = new ScrollView(this);
        sv.addView(ll);
        setContentView(sv);
    }

    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void startService() {
        if (!has(Manifest.permission.RECORD_AUDIO) || !has(Manifest.permission.READ_PHONE_STATE)
                || !has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            Toast.makeText(this, "امنح الأذونات أولاً (الخطوة 1)", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startForegroundService(new Intent(this, VoiceCommandService.class));
            Toast.makeText(this, "الخدمة اشتغلت", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر تشغيل الخدمة: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestAll() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        perms.add(Manifest.permission.READ_PHONE_STATE);
        perms.add(Manifest.permission.ANSWER_PHONE_CALLS);
        if (Build.VERSION.SDK_INT >= 33) perms.add("android.permission.POST_NOTIFICATIONS");
        requestPermissions(perms.toArray(new String[0]), 100);
    }
}
