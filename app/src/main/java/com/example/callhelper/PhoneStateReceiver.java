package com.example.callhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

/**
 * يحدّث حالة المكالمة فقط. لا يشغّل VoiceCommandService من الخلفية: ميكروفون
 * الـ foreground service لا يعمل إذا بدأت الخدمة من الخلفية (أندرويد 11+)،
 * وتشغيلها من الخلفية يرمي استثناء في أندرويد 12+. الخدمة تُشغَّل من MainActivity.
 */
public class PhoneStateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) CallController.setCallState(TelephonyManager.CALL_STATE_RINGING);
        else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) CallController.setCallState(TelephonyManager.CALL_STATE_OFFHOOK);
        else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) CallController.setCallState(TelephonyManager.CALL_STATE_IDLE);
    }
}
