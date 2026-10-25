package com.stark.jarvis;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Voice output for JARVIS:
 *  - Plays one of the 45 canonical voice-pack MP3s under assets/sounds for events.
 *  - Speaks dynamic replies through TTS, tuned toward a British butler voice.
 */
public class VoiceFeedback {

    private static VoiceFeedback sInstance;

    public static synchronized VoiceFeedback voiceOf(Context c) {
        if (sInstance == null) sInstance = new VoiceFeedback(c.getApplicationContext());
        return sInstance;
    }

    private final Context ctx;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean soundPack = true;
    private boolean ttsEnabled = true;
    public volatile boolean speakingNow = false;
    private final Random rnd = new Random();
    private SoundListener listener;

    public interface SoundListener { void onSoundFinished(); }

    /** Called once every voice output (pack line + TTS chain) has finished. */
    public interface IdleCallback { void onIdle(); }

    private IdleCallback idle;

    private VoiceFeedback(Context c) {
        ctx = c;
        Prefs p = Prefs.get(c);
        soundPack = p.bool(Prefs.SOUND_PACK_ENABLED, true);
        ttsEnabled = p.bool(Prefs.TTS_ENABLED, true);
        initTts();
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(ctx, status -> {
                ttsReady = status == TextToSpeech.SUCCESS && tts != null;
                if (ttsReady) {
                    try {
                        // Prefer a British male-ish google voice: closest open butler.
                        android.speech.tts.Voice best = null;
                        try {
                            for (android.speech.tts.Voice v : tts.getVoices()) {
                                if (v.getLocale().getLanguage().equals("en")) {
                                    String n = v.getName().toLowerCase(Locale.US);
                                    if (n.contains("gb") || n.contains("uk")) {
                                        if (best == null || n.contains("male")) best = v;
                                    }
                                }
                            }
                        } catch (Exception ignored) { }
                        if (best != null) tts.setVoice(best);
                        else tts.setLanguage(Locale.UK);
                        tts.setPitch(0.95f);
                        tts.setSpeechRate(1.0f);
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override public void onStart(String id) { speakingNow = true; }
                            @Override public void onDone(String id) { speakingNow = false; notifySoundDone(); }
                            @Override public void onError(String id) { speakingNow = false; notifySoundDone(); }
                        });
                    } catch (Exception ignored) { }
                }
            });
        } catch (Exception e) {
            ttsReady = false;
        }
    }

    private void notifySoundDone() {
        SoundListener l = listener;
        if (l != null) l.onSoundFinished();
        IdleCallback i = idle;
        if (i != null && !speakingNow) i.onIdle();
    }

    public void setSoundListener(SoundListener l) { listener = l; }

    public void setIdleCallback(IdleCallback cb) { idle = cb; }

    public void setSoundPack(boolean on) { soundPack = on; }
    public void setTts(boolean on) { ttsEnabled = on; }
    public boolean ttsReady() { return ttsReady; }

    /** Play one random line from the given voice-pack category (e.g. "acknowledge"). */
    public boolean playCategory(String category) {
        if (!soundPack) return false;
        try {
            android.content.res.AssetManager am = ctx.getAssets();
            String[] all = am.list("sounds");
            if (all == null || all.length == 0) return false;
            String prefix = prefixFor(category);
            if (prefix == null) return false;
            List<String> matches = new ArrayList<>();
            for (String a : all) if (a.startsWith(prefix)) matches.add(a);
            if (matches.isEmpty()) return false;
            String pick = matches.get(rnd.nextInt(matches.size()));
            return playAsset(pick);
        } catch (Exception e) {
            return false;
        }
    }

    private String prefixFor(String category) {
        switch (category) {
            case "session_start": case "start": return "session_start_";
            case "acknowledge": case "ack": case "wake": return "acknowledge_";
            case "complete": case "done": return "complete_";
            case "error": case "fail": return "error_";
            case "input": case "prompt": return "input_";
            case "resource": case "limit": return "resource_";
            case "spam": case "repeat": return "spam_";
            default: return null;
        }
    }

    private boolean playAsset(String name) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            AssetFileDescriptor fd = ctx.getAssets().openFd("sounds/" + name);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            mp.setOnCompletionListener(m -> { m.release(); notifySoundDone(); });
            mp.setOnErrorListener((m, w, e) -> { m.release(); notifySoundDone(); return true; });
            mp.prepare();
            mp.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Speak a dynamic line with TTS (category optional: plays a pack line first when it fits). */
    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, final String[] categoryThenText) {
        if (text == null || text.trim().isEmpty()) return;
        if (categoryThenText != null && categoryThenText.length == 2 && soundPack) {
            // Play the pack line, then follow with TTS text when it finishes.
            if (playCategory(categoryThenText[0])) {
                speakingNow = true;
                final String followUp = categoryThenText[1];
                setSoundListener(new SoundListener() {
                    @Override public void onSoundFinished() {
                        listener = null;
                        if (ttsEnabled && ttsReady) say(followUp); else notifySoundDone();
                    }
                });
                return;
            }
        }
        if (ttsEnabled && ttsReady) say(text);
        else notifySoundDone();
    }

    private void say(String text) {
        try {
            speakingNow = true;
            String id = "j" + System.nanoTime();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        } catch (Exception e) {
            speakingNow = false;
            notifySoundDone();
        }
    }

    public boolean isBusy() {
        return speakingNow;
    }

    public void shutdown() {
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); }
        } catch (Exception ignored) { }
        ttsReady = false;
        speakingNow = false;
    }
}
