package com.stark.jarvis;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

/**
 * J.A.R.V.I.S. Voice Core.
 * A microphone foreground service that keeps a speech recognizer running in a loop.
 * While the wake word is required, phrases are only executed once "Jarvis" is heard;
 * the rest of the phrase becomes the command.
 */
public class JarvisService extends Service {

    public interface Listener {
        void onStatus(String s);
        void onTranscript(String s);
        void onReply(String s);
        void onLog(String s);
    }

    public static Listener uiListener;

    private static JarvisService sInstance;
    private SpeechRecognizer recognizer;
    private boolean armed = false;               // listening loop live
    private boolean recognizing = false;
    private boolean awaitingCommand = false;     // woken, capturing the actual command
    private boolean voskActive = false;

    public static boolean isRunning() { return sInstance != null; }

    /** Temporarily silence the offline waker (e.g. during voiceprint enrollment). */
    public static void requestWakerPause(Context c) {
        JarvisService s = sInstance;
        if (s != null && s.voskActive) VoskWakeEngine.get(c).pauseForCapture();
    }

    public static void requestWakerResume(Context c) {
        JarvisService s = sInstance;
        if (s != null && s.voskActive) VoskWakeEngine.get(c).resume();
    }

    public static void start(Context c) {
        Intent i = new Intent(c, JarvisService.class).setAction("com.stark.jarvis.START");
        c.startForegroundService(i);
    }

    public static void requestStop(Context c, String line) {
        if (line != null) {
            VoiceFeedback.voiceOf(c).speak(line);
            // let TTS breathe briefly before the service dies
            getMainHandler().postDelayed(() -> {
                if (sInstance != null) sInstance.selfStop();
            }, line.length() * 70L + 1200);
        } else if (sInstance != null) {
            sInstance.selfStop();
        }
    }

    private static final Object HANDLER_LOCK = new Object();
    private static android.os.Handler sMainHandler;

    private static android.os.Handler getMainHandler() {
        synchronized (HANDLER_LOCK) {
            if (sMainHandler == null) sMainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        return sMainHandler;
    }

    private void mainPost(long delay, Runnable r) { getMainHandler().postDelayed(r, delay); }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Alerts.channels(this);
        // When JARVIS finishes speaking, hand the mic back to the offline waker.
        VoiceFeedback.voiceOf(this).setIdleCallback(() -> {
            if (voskActive && armed && !recognizing) {
                VoskWakeEngine.get(this).resume();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "com.stark.jarvis.START" : intent.getAction();
        Notification n = Alerts.serviceNotification(this, statusText());
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(42, n);
        }

        if ("com.stark.jarvis.STOP".equals(action)) {
            selfStop();
            return START_NOT_STICKY;
        }
        if ("com.stark.jarvis.TALK".equals(action)) {
            if (!armed) arm();
            // one-shot capture, then return to wake mode
            if (voskActive) VoskWakeEngine.get(this).pauseForCapture();
            beginRecognition(true);
            return START_NOT_STICKY;
        }
        if (!armed && hasMicPermission()) {
            arm();
        } else if (!hasMicPermission()) {
            updateUi("Mic permission missing — open the app to grant it.");
        }
        return START_STICKY;
    }

    private String statusText() {
        Prefs p = Prefs.get(this);
        return p.wakeOnly ? "Listening for the wake word…" : "Listening for commands…";
    }

    private boolean hasMicPermission() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void arm() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateUi("No speech recognizer on this device, sir.");
            return;
        }
        armed = true;
        updateUi(statusText());
        // Prefer the fully offline wake-word engine (Vosk) when bundled and enabled.
        if (Prefs.get(this).bool(Prefs.VOSK_ENABLED, true) && VoskWakeEngine.isAvailable(this)) {
            voskActive = true;
            VoskWakeEngine.get(this).start(this, wakeCallback);
            updateUi("Arming offline waker (no internet needed)…");
        } else {
            voskActive = false;
            mainPost(400, () -> beginRecognition(false));
        }
        if (Prefs.get(this).bool(Prefs.CAR_MODE, false)) {
            HeadsetRouter.routeForVoice(this);
        }
    }

    private final VoskWakeEngine.Callback wakeCallback = new VoskWakeEngine.Callback() {
        @Override public void onWake() { handleWake(); }

        @Override public void onState(boolean running, String detail) {
            if (running) {
                updateUi("Offline waker armed — say \"Jarvis\". (No internet used.)");
            } else {
                updateLog("vosk: " + detail);
                // fall back to the recogniser loop
                if (voskActive) {
                    voskActive = false;
                    VoskWakeEngine.get(JarvisService.this).stop();
                    updateUi("Offline waker unavailable — using recognizer loop.");
                    beginRecognition(false);
                }
            }
        }
    };

    private void handleWake() {
        if (!armed) return;
        if (recognizing || awaitingCommand) return;
        updateLog("wake: jarvis (offline)");
        mainPost(400, () -> beginRecognition(true));
    }

    private synchronized void beginRecognition(final boolean oneShot) {
        HeadsetRouter.routeForVoice(this);
        if (!armed || recognizing || VoiceFeedback.voiceOf(this).isBusy()) {
            mainPost(500, () -> beginRecognition(oneShot));
            return;
        }
        recognizing = true;
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Ears(oneShot));
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toString());
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizer.startListening(i);
        updateUi(oneShot ? "Listening — go ahead, sir." : statusText());
    }

    private void restartLoop() {
        recognizing = false;
        Prefs p = Prefs.get(this);
        if (!armed) return;
        if (voskActive) {
            // hand the mic back to the offline waker
            mainPost(600, () -> VoskWakeEngine.get(this).resume());
            return;
        }
        // delay while TTS/pack sounds are speaking to avoid listening to ourselves
        if (VoiceFeedback.voiceOf(this).isBusy()) {
            mainPost(600, () -> { recognizing = false; beginRecognition(false); });
            return;
        }
        mainPost(250, () -> beginRecognition(false));
    }

    private void updateUi(String status) {
        Listener l = uiListener;
        if (l != null) l.onStatus(status);
    }
    private void updateTranscript(String t) {
        Listener l = uiListener;
        if (l != null) l.onTranscript(t);
    }
    private void updateReply(String r) {
        Listener l = uiListener;
        if (l != null) l.onReply(r);
    }
    private void updateLog(String s) {
        Listener l = uiListener;
        if (l != null) l.onLog(s);
    }

    private class Ears implements RecognitionListener {
        private final boolean oneShot;
        Ears(boolean oneShot) { this.oneShot = oneShot; }

        @Override public void onReadyForSpeech(Bundle params) {
            if (oneShot) updateUi("Listening — go ahead, sir.");
        }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float r) { }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onPartialResults(Bundle partial) {
            try {
                ArrayList<String> out = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (out != null && !out.isEmpty()) updateTranscript("… " + out.get(0));
            } catch (Exception ignored) { }
        }
        @Override public void onEvent(int t, Bundle p) { }

        @Override
        public void onResults(Bundle results) {
            String text = null;
            try {
                ArrayList<String> out = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (out != null && !out.isEmpty()) text = out.get(0);
            } catch (Exception ignored) { }
            recognizing = false;
            if (text != null && !text.trim().isEmpty()) {
                updateTranscript(text);
                if (oneShot || awaitingCommand) {
                    awaitingCommand = false;
                    execute(text);
                } else {
                    final String after = stripWakeWord(text);
                    if (after != null) {
                        if (after.trim().isEmpty()) {
                            // "Jarvis!" alone: ask for the command
                            VoiceFeedback.voiceOf(JarvisService.this).speak("Sir?", null);
                            awaitingCommand = true;
                            mainPost(700, () -> { recognizing = false; beginRecognition(false); });
                        } else {
                            execute(after);
                        }
                    } else if (!Prefs.get(JarvisService.this).wakeOnly) {
                        execute(text);
                    }
                }
            }
            restartLoop();
        }

        @Override
        public void onError(int code) {
            recognizing = false;
            String msg;
            switch (code) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    msg = null; break; // silence: nobody spoke
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    msg = "Recognizer busy"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    msg = "Mic permission revoked"; break;
                default:
                    msg = "Recognizer error " + code; break;
            }
            if (msg != null) {
                updateLog("warn: " + msg);
                if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    armed = false;
                    updateUi("Mic permission lost — reopen the app to re-arm.");
                    return;
                }
                if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    // destroy and rebuild the recognizer
                    try { if (recognizer != null) { recognizer.destroy(); recognizer = null; } } catch (Exception ignored) { }
                }
            }
            restartLoop();
        }
    }

    /** Strips the wake word ("jarvis", common mishearings) from the start of a phrase. */
    private String stripWakeWord(String raw) {
        String wan = Prefs.get(this).wakeWord.toLowerCase(Locale.US).trim();
        String low = CommandEngine.normalize(raw);
        String[] cands = {wan, "jarvis", "jervis", "javis", "javus", "servis", "service", "jasper", "javis", "jarvis"};
        for (String cand : cands) {
            if (cand == null || cand.isEmpty()) continue;
            if (low.startsWith(cand + " ")) {
                return CommandEngine.fuzzyTrim(raw, cand);
            }
            if (low.startsWith(cand)) {
                // "Jarvis" spoken alone
                if (low.length() == cand.length()) return "";
                return CommandEngine.fuzzyTrim(raw, cand);
            }
        }
        return null;
    }

    private void execute(String text) {
        if (text == null || text.trim().isEmpty()) return;
        updateLog("cmd: " + text);
        try {
            final CommandEngine.Result r = CommandEngine.handle(this, text.trim());
            if (r == null) { restartLoop(); return; }
            VoiceFeedback vf = VoiceFeedback.voiceOf(this);
            if (r.reply != null) updateReply(r.reply);
            if (r.isQuestion) {
                updateReply("Querying the AI brain…");
                boolean started = AiBrain.ask(this, text.trim(), answer -> {
                    updateReply(answer);
                    vf.speak(answer);
                    mainPost(800, () -> { recognizing = false; beginRecognition(false); });
                });
                if (!started) {
                    String line = "No brain configured, sir. Try setting one in Systems.";
                    updateReply(line);
                    vf.speak(line);
                    restartLoop();
                }
                return; // loop resumes from the AI callback
            }
            if (r.sound != null) vf.playCategory(r.sound);
            if (r.reply != null) vf.speak(r.reply);
        } catch (Exception e) {
            updateLog("err: " + e);
            VoiceFeedback.voiceOf(this).speak("That one failed, sir.", null);
        }
    }

    private void selfStop() {
        armed = false;
        HeadsetRouter.release(this);
        try { VoskWakeEngine.get(this).stop(); } catch (Exception ignored) { }
        try { if (recognizer != null) { recognizer.destroy(); recognizer = null; } } catch (Exception ignored) { }
        try { VoiceFeedback.voiceOf(this).shutdown(); } catch (Exception ignored) { }
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) { }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        armed = false;
        try { VoskWakeEngine.get(this).stop(); } catch (Exception ignored) { }
        try { if (recognizer != null) { recognizer.destroy(); recognizer = null; } } catch (Exception ignored) { }
        sInstance = null;
        super.onDestroy();
    }
}
