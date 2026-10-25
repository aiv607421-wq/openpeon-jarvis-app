package com.stark.jarvis;

import android.content.Context;

import org.json.JSONArray;

/**
 * Voiceprint lock: stores the enrolled speaker embeddings (mean vector from
 * Vosk's speaker model) and validates future wake words against it.
 * Vector + threshold live only on this phone.
 */
public class Voiceprint {
    public static final float COSINE_THRESHOLD = 0.55f;
    private static final String KEY_VECTOR = "voiceprint_mean";
    private static final String KEY_SAMPLES = "voiceprint_n";

    public static class Check {
        public final boolean accepted;
        public final float similarity;
        Check(boolean a, float s) { accepted = a; similarity = s; }
    }

    public static boolean isEnrolled(Context c) {
        return Prefs.get(c).raw().contains(KEY_VECTOR);
    }

    /** Average a freshly captured vector into the profile. */
    public static void enrollVector(Context c, float[] vec) {
        android.content.SharedPreferences sp = Prefs.get(c).raw();
        try {
            JSONArray old;
            int n;
            try {
                old = new JSONArray(sp.getString(KEY_VECTOR, "[]"));
                n = sp.getInt(KEY_SAMPLES, 0);
            } catch (Exception e) { old = new JSONArray(); n = 0; }

            if (n == 0 || old.length() != vec.length) {
                JSONArray fresh = new JSONArray();
                for (float v : vec) fresh.put((double) v);
                old = fresh;
                n = 1;
            } else {
                // running mean
                for (int i = 0; i < vec.length; i++) old.put(i, (old.optDouble(i, 0) * n + vec[i]) / (n + 1));
                n++;
            }
            sp.edit().putString(KEY_VECTOR, old.toString()).putInt(KEY_SAMPLES, n).apply();
        } catch (Exception ignored) { }
    }

    /** Cosine similarity against the stored profile; null when unenrolled. */
    public static Check verify(Context c, float[] vec) {
        android.content.SharedPreferences sp = Prefs.get(c).raw();
        String json = sp.getString(KEY_VECTOR, null);
        if (json == null || vec == null) return null;
        try {
            JSONArray a = new JSONArray(json);
            double dot = 0, na = 0, nb = 0;
            int m = Math.min(a.length(), vec.length);
            for (int i = 0; i < m; i++) {
                double x = a.optDouble(i, 0), y = vec[i];
                dot += x * y; na += x * x; nb += (double) y * y;
            }
            float sim = (float) (dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9));
            return new Check(sim >= COSINE_THRESHOLD, sim);
        } catch (Exception e) {
            return null;
        }
    }

    public static void reset(Context c) {
        Prefs.get(c).raw().edit().remove(KEY_VECTOR).remove(KEY_SAMPLES).apply();
    }

    public static int sampleCount(Context c) {
        return Prefs.get(c).raw().getInt(KEY_SAMPLES, 0);
    }
}
