package com.stark.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully offline wake-word engine (Vosk + a 40 MB on-device model).
 * Listens for "Jarvis" (and common mishearings) with zero internet.
 * Optional voiceprint lock: when a speaker model is bundled and the user is
 * enrolled, wake words are checked against their speaker embedding.
 */
public class VoskWakeEngine {
    private static final String MODEL_ASSET = "vosk-model-small-en-us-0.15";
    private static final String MODEL_DIR = "vosk-en-us-small";
    private static final String SPK_ASSET = "vosk-model-spk-0.4";
    private static final String SPK_DIR = "vosk-spk-0.4";
    private static VoskWakeEngine sInstance;
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onWake();
        void onState(boolean running, String detail);
    }

    public interface EnrollCallback {
        /** progress 0..3 while collecting; done=true finished; msg human feedback. */
        void onProgress(int samples, boolean done, String msg);
    }

    private SpeechService service;
    private volatile boolean ready = false;
    private Context ctx;
    private Callback cb;
    private SpeakerModel spkModel;
    private volatile float[] lastEmbedding;
    private final List<SpeechService> enrollServices = new ArrayList<>();

    public static synchronized VoskWakeEngine get(Context c) {
        if (sInstance == null) sInstance = new VoskWakeEngine();
        return sInstance;
    }

    /** @return true when the offline model is bundled in the APK. */
    public static boolean isAvailable(Context c) {
        try {
            c.getAssets().open(MODEL_ASSET + "/README").close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean speakerModelAvailable(Context c) {
        try {
            c.getAssets().open(SPK_ASSET + "/mean.vec").close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void start(Context context, Callback callback) {
        ctx = context.getApplicationContext();
        cb = callback;
        File dir = new File(ctx.getFilesDir(), MODEL_DIR);
        if (new File(dir, "conf/model.conf").exists()) {
            boot(dir.getAbsolutePath());
            return;
        }
        new Thread(() -> {
            try {
                String path = StorageService.sync(ctx, MODEL_ASSET, dir.getAbsolutePath());
                boot(path);
            } catch (Exception e) {
                report(false, "Model unpack failed: " + e.getMessage());
            }
        }, "jarvis-vosk-unpack").start();
    }

    private SpeakerModel loadSpeakerModel() {
        if (spkModel != null) return spkModel;
        if (!speakerModelAvailable(ctx)) return null;
        if (!Prefs.get(ctx).bool(Prefs.VOICEPRINT_LOCK, false)) return null;
        try {
            File dir = new File(ctx.getFilesDir(), SPK_DIR);
            if (new File(dir, "final.ext.raw").exists()) {
                spkModel = new SpeakerModel(dir.getAbsolutePath());
            } else {
                String p = StorageService.sync(ctx, SPK_ASSET, dir.getAbsolutePath());
                spkModel = new SpeakerModel(p);
            }
            return spkModel;
        } catch (Exception e) {
            return null;
        }
    }

    private void boot(String modelPath) {
        new Thread(() -> {
            try {
                Model model = new Model(modelPath);
                SpeakerModel sm = loadSpeakerModel();
                Recognizer rec = new Recognizer(model, 16000.0f);
                if (sm != null) {
                    try { rec.setSpeakerModel(sm); } catch (Exception ignored) { }
                }
                try { rec.setGrammar(wakeGrammar()); } catch (Exception ignored) { }
                SpeechService svc = new SpeechService(rec, 16000.0f);
                service = svc;
                svc.startListening(new Ears());
                ready = true;
                report(true, sm != null ? "Offline waker ready (voiceprint on)" : "Offline waker ready");
            } catch (Exception e) {
                report(false, "Offline waker failed: " + e.getMessage());
            }
        }, "jarvis-vosk-boot").start();
    }

    private String wakeGrammar() {
        return "[\"jarvis\",\"jervis\",\"javis\",\"servis\",\"jarves\",\"service\",\"jasper\",\"jervise\"]";
    }

    private void report(boolean running, String detail) {
        main.post(() -> {
            Callback l = cb;
            if (l != null) l.onState(running, detail);
        });
    }

    public void pauseForCapture() {
        if (service != null) {
            try { service.setPause(true); } catch (Exception ignored) { }
        }
    }

    public void resume() {
        if (service != null) {
            try {
                service.reset();
                service.setPause(false);
            } catch (Exception ignored) { }
        }
    }

    public boolean isReady() { return ready; }

    public void stop() {
        try {
            if (service != null) { service.stop(); service.shutdown(); }
        } catch (Exception ignored) { }
        service = null;
        ready = false;
        stopEnrollers();
    }

    // ---------------- wake verification ----------------

    private class Ears implements org.vosk.android.RecognitionListener {
        private boolean pendingWake = false;
        private boolean firedThisUtterance = false;

        @Override public void onPartialResult(String hypothesis) {
            if (!pendingWake && containsWakeword(hypothesis)) {
                pendingWake = true;
                // small model endpointers settle within ~1s; wait for the final
                // result so we can grab the speaker embedding. Give it a safety
                // window in case no final arrives.
                main.postDelayed(() -> {
                    if (pendingWake && !firedThisUtterance) {
                        pendingWake = false;
                        firedThisUtterance = true;
                        fire(null); // speak up without verification rather than go mute
                    }
                }, 1500);
            }
        }

        @Override public void onResult(String hypothesis) { judgeFinal(hypothesis); }
        @Override public void onFinalResult(String hypothesis) { judgeFinal(hypothesis); }

        private void judgeFinal(String json) {
            boolean wake = containsWakeword(json);
            float[] spk = extractSpeaker(json);
            if (spk != null) lastEmbedding = spk;
            if (pendingWake && wake && !firedThisUtterance) {
                pendingWake = false;
                firedThisUtterance = true;
                if (voiceprintLockOn()) {
                    Voiceprint.Check chk = Voiceprint.verify(ctx, spk);
                    if (chk == null) {
                        fire(null); // no embedding this round; don't drop a valid wake
                        return;
                    }
                    if (!chk.accepted) {
                        // someone else's voice: stay silent, reset, keep listening
                        pauseForCapture();
                        main.postDelayed(() -> {
                            try {
                                if (service != null) service.reset();
                            } catch (Exception ignored) { }
                            firedThisUtterance = false;
                            pendingWake = false;
                            resume();
                        }, 400);
                        return;
                    }
                }
                fire(spk);
            } else if (!wake) {
                firedThisUtterance = false;
                pendingWake = false;
            }
        }

        @Override public void onError(Exception e) { report(false, "waker error: " + e.getMessage()); }
        @Override public void onTimeout() { }
    }

    private boolean voiceprintLockOn() {
        return Prefs.get(ctx).bool(Prefs.VOICEPRINT_LOCK, false) && Voiceprint.isEnrolled(ctx);
    }

    private void fire(float[] spk) {
        if (!ready) return;
        pauseForCapture();
        main.post(() -> {
            Callback l = cb;
            if (l != null) l.onWake();
        });
    }

    private static boolean containsWakeword(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String text = o.optString("partial", o.optString("text", "")).toLowerCase();
            for (String w : new String[]{"jarvis", "jervis", "javis", "servis", "jarves", "jasper"}) {
                if (text.contains(w)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static float[] extractSpeaker(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONArray a = o.optJSONArray("spk");
            if (a == null) {
                JSONObject spkObj = o.optJSONObject("spk");
                if (spkObj != null) a = spkObj.optJSONArray("spk");
            }
            if (a != null && a.length() > 0) {
                float[] v = new float[a.length()];
                for (int i = 0; i < v.length; i++) v[i] = (float) a.optDouble(i, 0);
                return v;
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------------- voiceprint enrollment ----------------

    /**
     * Capture 3 utterances of "Jarvis" into the speaker profile.
     * Call with the regular waker paused (JarvisService.requestWakerPause()).
     */
    public void enroll(final Context context, final EnrollCallback cbE) {
        final Context c = context.getApplicationContext();
        new Thread(() -> {
            try {
                Model model;
                File dir = new File(c.getFilesDir(), MODEL_DIR);
                if (new File(dir, "conf/model.conf").exists()) {
                    model = new Model(dir.getAbsolutePath());
                } else {
                    String p = StorageService.sync(c, MODEL_ASSET,
                            new File(c.getFilesDir(), MODEL_DIR).getAbsolutePath());
                    model = new Model(p);
                }
                SpeakerModel sm = loadSpeakerModel();
                if (sm == null && speakerModelAvailable(c)) {
                    File d2 = new File(c.getFilesDir(), SPK_DIR);
                    String p2 = StorageService.sync(c, SPK_ASSET, d2.getAbsolutePath());
                    sm = new SpeakerModel(p2);
                }
                if (sm == null) {
                    main.post(() -> cbE.onProgress(0, true, "Speaker model unavailable."));
                    return;
                }
                final Recognizer rec = new Recognizer(model, 16000.0f, sm);
                try { rec.setGrammar(wakeGrammar()); } catch (Exception ignored) { }
                final SpeechService svc = new SpeechService(rec, 16000.0f);
                synchronized (enrollServices) { enrollServices.add(svc); }

                final List<float[]> samples = new ArrayList<>();
                svc.startListening(new org.vosk.android.RecognitionListener() {
                    @Override public void onPartialResult(String h) { }
                    @Override public void onResult(String h) { judge(h); }
                    @Override public void onFinalResult(String h) { judge(h); }
                    @Override public void onError(Exception e) {
                        main.post(() -> cbE.onProgress(samples.size(), true, "Mic busy: " + e.getMessage()));
                    }
                    @Override public void onTimeout() { }

                    private void judge(String json) {
                        float[] v = extractSpeaker(json);
                        if (v == null || !containsWakeword(json)) {
                            main.post(() -> cbE.onProgress(samples.size(), false,
                                    "I heard nothing usable — say \"Jarvis\". (" + samples.size() + "/3)"));
                            return;
                        }
                        Voiceprint.enrollVector(c, v);
                        samples.add(v);
                        main.post(() -> cbE.onProgress(samples.size(), false,
                                "Captured " + samples.size() + "/3 voiceprints."));
                        if (samples.size() >= 3) {
                            try { svc.stop(); svc.shutdown(); } catch (Exception ignored) { }
                            synchronized (enrollServices) { enrollServices.remove(svc); }
                            Voiceprint.Check chk = Voiceprint.verify(c, samples.get(0));
                            final String msg = "Voiceprint saved. Match quality "
                                    + (chk == null ? "?" : String.format(" %.2f", chk.similarity))
                                    + " (needs >= " + Voiceprint.COSINE_THRESHOLD + "). Turn the lock on now.";
                            main.post(() -> cbE.onProgress(3, true, msg));
                        }
                    }
                });
            } catch (Exception e) {
                main.post(() -> cbE.onProgress(0, true, "Enrollment failed: " + e.getMessage()));
            }
        }, "jarvis-enroll").start();
    }

    private void stopEnrollers() {
        synchronized (enrollServices) {
            for (SpeechService s : enrollServices) {
                try { s.stop(); s.shutdown(); } catch (Exception ignored) { }
            }
            enrollServices.clear();
        }
    }
}
