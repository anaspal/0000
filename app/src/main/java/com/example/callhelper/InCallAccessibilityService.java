package com.example.callhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class InCallAccessibilityService extends AccessibilityService {
    private boolean consumedUp = false;
    private boolean consumedDown = false;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override public boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyEvent(event);
        boolean up = (code == KeyEvent.KEYCODE_VOLUME_UP);

        // ACTION_UP والتكرار (الضغط المطوّل): نبتلعها فقط إذا ابتلعنا الـ DOWN الأصلي
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean was = up ? consumedUp : consumedDown;
            if (up) consumedUp = false; else consumedDown = false;
            return was;
        }
        if (event.getRepeatCount() > 0) return up ? consumedUp : consumedDown;

        boolean ringing = CallController.ringing(this);
        boolean inCall = CallController.inCall(this);
        if (!ringing && !inCall) return super.onKeyEvent(event);

        if (up) {
            if (ringing) { CallController.answerCall(this); vibrate(80); }
            else { CallController.toggleSpeaker(this); vibrate(40); }
            consumedUp = true;
        } else {
            if (inCall) { CallController.endCall(this); vibrate(80); }
            else { vibrate(40); }
            consumedDown = true;
        }
        return true;
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignored) {}
    }
}
