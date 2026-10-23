package com.example.callhelper;

import java.util.Arrays;

public class DtwMatcher {
    private static final float THRESHOLD_FACTOR = 1.8f;
    private static final float INF = Float.MAX_VALUE / 4;

    /**
     * Subsequence DTW: القالب (template) يُطابَق كاملاً، لكن يمكن أن يبدأ وينتهي
     * في أي مكان داخل مقطع الاختبار (test). مناسب لأن نافذة الميكروفون (2 ثانية)
     * فيها الكلام + صمت/ضوضاء، بينما القالب هو العبارة فقط.
     */
    public static float distance(float[][] template, float[][] test) {
        if (template == null || test == null || template.length == 0 || test.length == 0) return Float.MAX_VALUE;
        if (template[0].length != test[0].length) return Float.MAX_VALUE;
        int n = template.length, m = test.length;
        float[] prev = new float[m + 1];
        float[] cur = new float[m + 1];
        Arrays.fill(prev, 0f); // بداية حرة داخل test
        for (int i = 1; i <= n; i++) {
            cur[0] = INF;
            for (int j = 1; j <= m; j++) {
                float cost = 0;
                for (int c = 0; c < template[0].length; c++) {
                    float diff = template[i - 1][c] - test[j - 1][c];
                    cost += diff * diff;
                }
                cost = (float) Math.sqrt(cost);
                cur[j] = cost + Math.min(prev[j], Math.min(cur[j - 1], prev[j - 1]));
            }
            float[] t = prev; prev = cur; cur = t;
        }
        float best = INF;
        for (int j = 1; j <= m; j++) if (prev[j] < best) best = prev[j];
        return best / n;
    }

    public static float minDistance(float[][] test, float[][][] templates) {
        float best = Float.MAX_VALUE;
        if (templates == null) return best;
        for (float[][] t : templates) {
            float dd = distance(t, test);
            if (dd < best) best = dd;
        }
        return best;
    }

    /** عتبة تلقائية من المسافات بين العينات المسجّلة. أقل من عينتين = 0 (لا مطابقة أبداً). */
    public static float autoThreshold(float[][][] templates) {
        if (templates == null || templates.length < 2) return 0f;
        float sum = 0; int cnt = 0;
        for (int i = 0; i < templates.length; i++) {
            for (int j = i + 1; j < templates.length; j++) {
                float d1 = distance(templates[i], templates[j]);
                float d2 = distance(templates[j], templates[i]);
                if (d1 >= INF || d2 >= INF) continue;
                sum += (d1 + d2) / 2f; cnt++;
            }
        }
        if (cnt == 0) return 0f;
        return (sum / cnt) * THRESHOLD_FACTOR;
    }
}
