package com.example.callhelper;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public class VoiceTrainerActivity extends Activity {
    private static final String TAG = "Trainer";
    private volatile int count = 0;
    private TextView status;
    private Button recBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int p = 40; ll.setPadding(p, p, p, p);
        status = new TextView(this); status.setTextSize(15f); ll.addView(status);
        recBtn = new Button(this); recBtn.setText("🎙️ سجّل عينة (3 ثواني)"); recBtn.setOnClickListener(v -> record()); ll.addView(recBtn);
        Button reset = new Button(this); reset.setText("🗑️ حذف كل التسجيلات"); reset.setOnClickListener(v -> resetAll()); ll.addView(reset);
        setContentView(ll);
        refreshCount();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private File templateFile(int i) { return new File(getFilesDir(), "template_" + i + ".mfcc"); }

    private void refreshCount() {
        int c = 0;
        for (int i = 1; i <= 3; i++) if (templateFile(i).exists()) c++;
        count = c;
        status.setText("سجّل عبارتك 3 مرات (مثال: \"رد على المكالمة\")\n" + "بنفس النبرة والسرعة كل مرة\n\n" + "المسجل حالياً: " + c + "/3");
        recBtn.setEnabled(c < 3);
    }

    private void resetAll() {
        for (int i = 1; i <= 3; i++) templateFile(i).delete();
        refreshCount();
        Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
    }

    private void record() {
        if (count >= 3) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            Toast.makeText(this, "امنح إذن الميكروفون ثم أعد المحاولة", Toast.LENGTH_SHORT).show();
            return;
        }
        recBtn.setEnabled(false);
        status.setText("🎤 قل العبارة الآن...");
        new Thread(this::doRecord).start();
    }

    private void doRecord() {
        final int sr = MfccExtractor.SAMPLE_RATE;
        final short[] recorded = new short[sr * 3];
        int pos = 0;
        AudioRecord ar = null;
        try {
            int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, sr * 2 * 3));
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("init failed");
            ar.startRecording();
            short[] buf = new short[sr / 10];
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end && pos < recorded.length) {
                int r = ar.read(buf, 0, buf.length);
                if (r < 0) throw new IllegalStateException("read error " + r);
                if (r > 0) {
                    int n = Math.min(r, recorded.length - pos);
                    System.arraycopy(buf, 0, recorded, pos, n);
                    pos += n;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "rec", e);
            final String msg = String.valueOf(e.getMessage());
            runOnUiThread(() -> { refreshCount(); status.setText("❌ فشل: " + msg); });
            return;
        } finally {
            if (ar != null) {
                try { ar.stop(); } catch (Exception ignored) {}
                try { ar.release(); } catch (Exception ignored) {}
            }
        }
        save(recorded, pos);
    }

    private void save(short[] pcm, int len) {
        short[] trimmed = MfccExtractor.trimSilence(Arrays.copyOf(pcm, len));
        if (trimmed == null) {
            runOnUiThread(() -> { refreshCount(); status.setText("❌ ما انسمع صوت واضح، حاول مرة ثانية وتكلم أقرب"); });
            return;
        }
        float[][] mfcc = MfccExtractor.extract(trimmed);
        if (mfcc.length == 0) {
            runOnUiThread(() -> { refreshCount(); status.setText("❌ قصير جداً"); });
            return;
        }
        int idx = 1;
        while (idx <= 3 && templateFile(idx).exists()) idx++;   // أول خانة فارغة (لا نكتب فوق عينة موجودة)
        if (idx > 3) { runOnUiThread(this::refreshCount); return; }
        File f = templateFile(idx);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
            oos.writeObject(mfcc);
        } catch (Exception e) {
            Log.e(TAG, "save", e);
            f.delete();
            runOnUiThread(() -> { refreshCount(); status.setText("❌ فشل الحفظ"); });
            return;
        }
        runOnUiThread(() -> {
            refreshCount();
            Toast.makeText(this, "تم حفظ العينة", Toast.LENGTH_SHORT).show();
        });
    }
}
