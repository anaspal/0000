package com.example.callhelper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CallController {
    private static final String TAG = "CallCtrl";

    // آخر حالة مكالمة معروفة (يحدّثها PhoneStateReceiver و VoiceCommandService)
    private static volatile int sCallState = TelephonyManager.CALL_STATE_IDLE;

    public static void setCallState(int state) { sCallState = state; }

    public static void answerCall(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) tm.acceptRingingCall();
        } catch (Exception e) { Log.e(TAG, "answer", e); }
    }

    public static void endCall(Context ctx) {
        if (Build.VERSION.SDK_INT < 28) return;
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) tm.endCall();
        } catch (Exception e) { Log.e(TAG, "end", e); }
    }

    public static void toggleSpeaker(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try { am.setSpeakerphoneOn(!am.isSpeakerphoneOn()); } catch (Exception e) { Log.e(TAG, "spk", e); }
    }

    public static boolean inCall(Context ctx) {
        if (sCallState == TelephonyManager.CALL_STATE_OFFHOOK) return true;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        // MODE_IN_COMMUNICATION (واتساب/VoIP) مستبعد عمداً حتى لا نعترض أزرار الصوت فيها
        return am != null && am.getMode() == AudioManager.MODE_IN_CALL;
    }

    public static boolean ringing(Context ctx) {
        if (sCallState == TelephonyManager.CALL_STATE_RINGING) return true;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        // الثابت الصحيح هو MODE_RINGTONE (MODE_RINGING غير موجود)
        return am != null && am.getMode() == AudioManager.MODE_RINGTONE;
    }
}
