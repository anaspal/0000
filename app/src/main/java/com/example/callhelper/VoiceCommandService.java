package com.example.callhelper;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class VoiceCommandService extends Service {
    private static final String TAG = "VoiceSvc";
    private static final String CH_ID = "callhelper";
    private static final int NOTIF_ID = 1;
    private static final float MIN_RMS = 900f;
    private static final int MIN_TEMPLATES = 2;

    private final Object lock = new Object();
    private volatile float[][][] templates = new float[0][][];
    private volatile float threshold = 0f;
    private volatile AudioRecord audioRecord;
    private volatile boolean capturing = false;
    private boolean foregroundOk = false;

    private TelephonyManager telephonyManager;
    private PhoneStateListener legacyListener;   // يجب حفظه في حقل (TelephonyManager يحتفظ به بمرجع ضعيف)
    private Object modernCallback;               // TelephonyCallback على أندرويد 12+

    @TargetApi(31)
    private class CallCb extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override public void onCallStateChanged(int state) { handleCallState(state); }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            startForegroundCompat(buildNotification());
            foregroundOk = true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            return;
        }
        loadTemplates();
        setupPhoneListener();
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(NOTIF_ID, n);
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH_ID, "CallHelper", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CH_ID)
                .setContentTitle("CallHelper شغال")
                .setContentText("جاهز لاستقبال المكالمات")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
                .build();
    }

    private void loadTemplates() {
        List<float[][]> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            File f = new File(getFilesDir(), "template_" + i + ".mfcc");
            if (!f.exists()) continue;
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Object o = ois.readObject();
                if (o instanceof float[][]) {
                    float[][] m = (float[][]) o;
                    if (m.length > 0) list.add(m);
                }
            } catch (Exception e) { Log.e(TAG, "load " + i, e); }
        }
        float[][][] arr = list.toArray(new float[0][][]);
        templates = arr;
        threshold = DtwMatcher.autoThreshold(arr);
        Log.d(TAG, "templates=" + arr.length + " threshold=" + threshold);
    }

    private void setupPhoneListener() {
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm == null) return;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE not granted");
            return;
        }
        telephonyManager = tm;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                CallCb cb = new CallCb();
                tm.registerTelephonyCallback(getMainExecutor(), cb);
                modernCallback = cb;
            } else {
                legacyListener = new PhoneStateListener() {
                    @Override public void onCallStateChanged(int state, String number) { handleCallState(state); }
                };
                tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        } catch (Exception e) { Log.e(TAG, "phone listener", e); }
    }

    private void handleCallState(int state) {
        CallController.setCallState(state);
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            loadTemplates(); // لالتقاط أي تسجيل جديد دون إعادة تشغيل الخدمة
            if (templates.length >= MIN_TEMPLATES) startCapture();
        } else {
            stopCapture();
        }
    }

    private void startCapture() {
        synchronized (lock) {
            if (capturing) return;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
            final int sr = MfccExtractor.SAMPLE_RATE;
            try {
                int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bufBytes = Math.max(min, sr * 2 * 2);
                final AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes);
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) { ar.release(); return; }
                audioRecord = ar;
                capturing = true;
                new Thread(() -> captureLoop(ar), "voice-capture").start();
            } catch (Exception e) {
                Log.e(TAG, "start", e);
                capturing = false;
                audioRecord = null;
            }
        }
    }

    private void captureLoop(AudioRecord ar) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        final int sr = MfccExtractor.SAMPLE_RATE;
        short[] chunk = new short[sr / 4];
        short[] window = new short[sr * 2];
        int filled = 0;
        try {
            ar.startRecording();
            while (capturing && audioRecord == ar) {
                int r = ar.read(chunk, 0, chunk.length);
                if (r < 0) { Log.e(TAG, "read error " + r); break; }
                if (r == 0) continue;
                // نافذة منزلقة: ندفع القديم لليسار ونضع الجديد في النهاية
                System.arraycopy(window, r, window, 0, window.length - r);
                System.arraycopy(chunk, 0, window, window.length - r, r);
                filled = Math.min(window.length, filled + r);
                if (filled < window.length) continue;
                if (rms(window) < MIN_RMS) continue;

                float[][] test = MfccExtractor.extract(window);
                float thr = threshold;
                float dist = DtwMatcher.minDistance(test, templates);
                Log.d(TAG, "dist=" + dist + " thr=" + thr);
                if (dist < thr) {
                    capturing = false;
                    vibrate(120);
                    CallController.answerCall(VoiceCommandService.this);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "loop", e);
        } finally {
            try { ar.stop(); } catch (Exception ignored) {}
            try { ar.release(); } catch (Exception ignored) {}
            synchronized (lock) {
                if (audioRecord == ar) { audioRecord = null; capturing = false; }
            }
        }
    }

    private void stopCapture() {
        AudioRecord ar;
        synchronized (lock) { capturing = false; ar = audioRecord; }
        if (ar != null) { try { ar.stop(); } catch (Exception ignored) {} }
    }

    private float rms(short[] s) {
        double sum = 0;
        for (short v : s) sum += (double) v * v;
        return (float) Math.sqrt(sum / s.length);
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        if (!foregroundOk) { stopSelf(); return START_NOT_STICKY; }
        loadTemplates();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        stopCapture();
        if (telephonyManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 31 && modernCallback != null) {
                    telephonyManager.unregisterTelephonyCallback((TelephonyCallback) modernCallback);
                } else if (legacyListener != null) {
                    telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_NONE);
                }
            } catch (Exception e) { Log.w(TAG, "unregister", e); }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
