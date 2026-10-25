package com.stark.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny preference wrapper. All values live only on the phone. */
public class Prefs {
    public static final String WAKE_ONLY = "wake_only";
    public static final String VOSK_ENABLED = "vosk_offline_waker";
    public static final String VOICEPRINT_LOCK = "voiceprint_lock";
    public static final String CAR_MODE = "car_mode";
    public static final String TTS_ENABLED = "tts_enabled";
    public static final String SOUND_PACK_ENABLED = "sound_pack";
    public static final String LOG_TOASTS = "log_toasts";
    public static final String AI_BASE = "ai_base";
    public static final String AI_KEY = "ai_key";
    public static final String AI_MODEL = "ai_model";
    public static final String WAKE_WORD = "wake_word";

    private static Prefs sInstance;
    public String wakeWord = "jarvis";
    public String aiBaseUrl = "";
    public String aiApiKey = "";
    public String aiModel = "gpt-4o-mini";

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("jarvis", Context.MODE_PRIVATE);
        load();
    }

    public static Prefs get(Context c) {
        if (sInstance == null) sInstance = new Prefs(c);
        return sInstance;
    }

    private void load() {
        wakeOnly(sp.getBoolean(WAKE_ONLY, true));
        wakeWord = sp.getString(WAKE_WORD, "jarvis");
        aiBaseUrl = sp.getString(AI_BASE, "");
        aiApiKey = sp.getString(AI_KEY, "");
        aiModel = sp.getString(AI_MODEL, "gpt-4o-mini");
    }

    public boolean wakeOnly = true;

    public android.content.SharedPreferences raw() { return sp; }

    public void wakeOnly(boolean b) { wakeOnly = b; }

    public boolean bool(String key, boolean def) { return sp.getBoolean(key, def); }

    public void put(String key, boolean v) {
        sp.edit().putBoolean(key, v).apply();
        if (WAKE_ONLY.equals(key)) wakeOnly = v;
    }

    public void put(String key, String v) {
        sp.edit().putString(key, v).apply();
        if (WAKE_WORD.equals(key)) wakeWord = v;
        else if (AI_BASE.equals(key)) aiBaseUrl = v;
        else if (AI_KEY.equals(key)) aiApiKey = v;
        else if (AI_MODEL.equals(key)) aiModel = v;
    }

    public void save() { /* writes are immediate via apply(); kept for readability */ }
}
