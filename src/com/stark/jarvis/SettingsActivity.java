package com.stark.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Settings screen: toggles, permissions helpers, AI brain link and the command manual. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildManualUi());
    }

    private android.view.View buildManualUi() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(0xFF05070B);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(box);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("SYSTEMS");
        title.setTextColor(0xFF00D4FF);
        title.setTextSize(26);
        box.addView(title);

        // --- Toggles ---
        final Prefs p = Prefs.get(this);
        box.addView(toggle(p, Prefs.WAKE_ONLY, "Wake word required (say \u201cJarvis\u201d first)", true));
        box.addView(toggle(p, Prefs.VOSK_ENABLED, "Offline wake word \u2014 works with no internet", true));
        box.addView(toggle(p, Prefs.TTS_ENABLED, "Speak replies (TTS)", true));
        box.addView(toggle(p, Prefs.SOUND_PACK_ENABLED, "Voice pack lines (your 45 MP3s)", true));
        box.addView(toggle(p, Prefs.LOG_TOASTS, "Verbose event log", false));

        box.addView(divider());

        // --- Wake word input ---
        box.addView(labelBox("WAKE WORD"));
        final android.widget.EditText wake = new android.widget.EditText(this);
        wake.setText(p.wakeWord);
        wake.setHintTextColor(0xFF0A7EA6);
        wake.setTextColor(0xFFC9EAF6);
        wake.setBackground(null);
        wake.setHint("jarvis");
        wake.setSingleLine(true);
        wake.setTextSize(16);
        wake.setPadding(0, dp(4), 0, dp(10));
        wake.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                p.wakeWord = s.toString().trim().toLowerCase();
                p.save();
            }
        });
        box.addView(wake);

        box.addView(divider());

        // --- AI brain ---
        box.addView(labelBox("AI BRAIN (optional)"));
        android.widget.TextView aiInfo = new android.widget.TextView(this);
        aiInfo.setTextColor(0xFF123B4E);
        aiInfo.setTextSize(12);
        aiInfo.setText("Point JARVIS at any OpenAI-compatible endpoint to answer anything\noffline commands can't. Leave blank for voice-only mode.");
        aiInfo.setPadding(0, 0, 0, dp(8));
        box.addView(aiInfo);
        final android.widget.EditText aiBase = field(p.aiBaseUrl, "https://freebuff.com/api/v1/paid");
        box.addView(aiBase);
        final android.widget.EditText aiKey = field(p.aiApiKey, "API key (stored on this device only)");
        aiKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(aiKey);
        final android.widget.EditText aiModel = field(p.aiModel, "model (e.g. gpt-4o-mini)");
        box.addView(aiModel);
        aiBase.addTextChangedListener(watcher(p, Prefs.AI_BASE));
        aiKey.addTextChangedListener(watcher(p, Prefs.AI_KEY));
        aiModel.addTextChangedListener(watcher(p, Prefs.AI_MODEL));

        final android.widget.TextView aiTest = new android.widget.TextView(this);
        aiTest.setTextColor(0xFF0A7EA6);
        aiTest.setTextSize(12);
        aiTest.setPadding(0, dp(6), 0, 0);
        box.addView(aiTest);
        box.addView(action("TEST CONNECTION", () -> {
            boolean cfg = !aiBase.getText().toString().trim().isEmpty()
                    && !aiKey.getText().toString().trim().isEmpty();
            aiTest.setText("Testing...");
            if (!cfg) {
                aiTest.setText("Base URL and API key are both required, sir.");
                return;
            }
            AiBrain.askWithKey(aiBase.getText().toString().trim(), aiKey.getText().toString().trim(),
                    aiModel.getText().toString().trim().isEmpty() ? "gpt-4o-mini" : aiModel.getText().toString().trim(),
                    "This is JARVIS' connectivity test. Reply with exactly: systems nominal.",
                    answer -> aiTest.setText(answer == null ? "(no response)" : answer));
        }));
        android.widget.TextView keyNote = new android.widget.TextView(this);
        keyNote.setText("Key lives only on this phone \u2014 never paste API keys into chats.\nFreebuff key: Account \u2192 Developer API (freebuff.com/account/api).");
        keyNote.setTextColor(0xFF123B4E);
        keyNote.setTextSize(11);
        keyNote.setPadding(0, dp(6), 0, dp(4));
        box.addView(keyNote);

        box.addView(divider());

        // --- Voiceprint lock ---
        box.addView(labelBox("VOICEPRINT LOCK (BETA)"));
        android.widget.TextView vpInfo = new android.widget.TextView(this);
        vpInfo.setTextColor(0xFF123B4E);
        vpInfo.setTextSize(11);
        vpInfo.setText("Only your voice may wake JARVIS.\nEnroll by saying \u201cJarvis\u201d three times, clearly, then enable the lock.");
        vpInfo.setPadding(0, 0, 0, dp(8));
        box.addView(vpInfo);
        box.addView(toggle(p, Prefs.VOICEPRINT_LOCK, "Wake word voice check (requires enrollment)", false));
        final android.widget.TextView vpStatus = new android.widget.TextView(this);
        vpStatus.setTextColor(0xFF0A7EA6);
        vpStatus.setTextSize(12);
        vpStatus.setPadding(0, dp(4), 0, dp(4));
        vpStatus.setText(Voiceprint.isEnrolled(this)
                ? "Profile: enrolled (" + Voiceprint.sampleCount(this) + " samples)."
                : "Profile: not enrolled.");
        box.addView(vpStatus);
        box.addView(action("ENROLL VOICEPRINT \u2014 say \u201cJarvis\u201d 3 times", () -> {
            JarvisService.requestWakerPause(this);
            VoskWakeEngine.get(this).enroll(this, (samples, done, msg) -> {
                vpStatus.setText(msg);
                if (done) JarvisService.requestWakerResume(this);
                vpStatus.setText(vpStatus.getText() + "  |  profile: "
                        + (Voiceprint.isEnrolled(this)
                           ? "enrolled (" + Voiceprint.sampleCount(this) + ")" : "not enrolled"));
            });
        }));
        box.addView(action("Reset voiceprint", () -> {
            Voiceprint.reset(this);
            vpStatus.setText("Profile: cleared. Enroll again to use the lock.");
        }));

        box.addView(divider());

        // --- Car / headset mode ---
        box.addView(labelBox("CAR / HEADSET MODE"));
        box.addView(toggle(p, Prefs.CAR_MODE, "Route voice & replies through Bluetooth headset", false));
        android.widget.TextView audioNow = new android.widget.TextView(this);
        audioNow.setTextColor(0xFF123B4E);
        audioNow.setTextSize(11);
        audioNow.setText("Now attached: " + describeHeadset());
        audioNow.setPadding(0, 0, 0, dp(6));
        box.addView(audioNow);

        box.addView(divider());

        // --- Notifications access ---
        box.addView(labelBox("MESSAGE FEEDS"));
        android.widget.TextView notifState = new android.widget.TextView(this);
        notifState.setTextColor(0xFF123B4E);
        notifState.setTextSize(11);
        notifState.setText("Notification access: "
                + (JarvisNotificationService.isActive() ? "granted, listening." : "not granted."));
        notifState.setPadding(0, 0, 0, dp(6));
        box.addView(notifState);
        box.addView(action("REQUEST NOTIFICATION ACCESS (to enable 'any messages?')",
                () -> { try {
                            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Exception ignored) { }
                }));

        box.addView(divider());

        // --- Diagnostics ---
        box.addView(labelBox("DIAGNOSTICS"));
        final android.widget.TextView diag = new android.widget.TextView(this);
        diag.setTextColor(0xFFC9EAF6);
        diag.setTextSize(12);
        diag.setTypeface(android.graphics.Typeface.MONOSPACE);
        diag.setPadding(0, dp(4), 0, dp(8));
        box.addView(diag);
        box.addView(action("RUN DIAGNOSTICS", () -> diag.setText(buildReport())));
        box.addView(action("COPY REPORT (paste it to the developer chat)", () -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("jarvis-report", buildReport()));
            android.widget.Toast.makeText(this, "Report copied", android.widget.Toast.LENGTH_SHORT).show();
        }));

        box.addView(divider());

        // --- System helpers ---
        box.addView(action("Unlock precise reminders (exact alarms)",
                () -> { try {
                            startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                                    android.net.Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                }));
        box.addView(action("Disable battery optimization (keep listening after screen off)",
                () -> { try {
                            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                }));
        box.addView(action("Text-to-speech engine settings",
                () -> { try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
                        catch (Exception ignored) { }
                }));
        box.addView(action("Set up microphone hardware / voice input",
                () -> { try {
                            Intent i = new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        } catch (Exception ignored) { }
                }));

        box.addView(divider());
        box.addView(labelBox("COMMAND MANUAL"));
        for (String[] row : COMMAND_MANUAL) {
            android.widget.TextView line = new android.widget.TextView(this);
            line.setText("\u25b8 " + row[0] + "\n   " + row[1]);
            line.setTextColor(0xFFC9EAF6);
            line.setTextSize(13);
            line.setPadding(0, dp(4), 0, dp(4));
            box.addView(line);
        }

        android.widget.TextView foot = new android.widget.TextView(this);
        foot.setText("Voice pack: FlynnCruse/openpeon-jarvis (CC-BY-NC-4.0).\nJARVIS is property of Marvel/Disney. Fan project.");
        foot.setTextColor(0xFF123B4E);
        foot.setTextSize(11);
        foot.setPadding(0, dp(24), 0, 0);
        box.addView(foot);
        return scroll;
    }

    private static final String[][] COMMAND_MANUAL = {
        {"Jarvis, what time is it / what's the date", "Clock and calendar."},
        {"Jarvis, flashlight on / flashlight off", "Camera torch."},
        {"Jarvis, volume up / down / max / mute / unmute", "Media volume."},
        {"Jarvis, brightness up / down / 50%", "Needs modify-settings permission."},
        {"Jarvis, open Telegram / open camera / open settings & list apps", "Launches any installed app by name."},
        {"Jarvis, call <name or number>", "Places the call (CALL_PHONE)."},
        {"Jarvis, text <name> saying <message>", "Opens SMS composer with text."},
        {"Jarvis, search for \u2026 / google \u2026 / open youtube", "Web search."},
        {"Jarvis, play <anything>", "YouTube music search."},
        {"Jarvis, set a timer for 5 minutes / alarm at 7 am", "Clock app."},
        {"Jarvis, remind me to water plants in 2 hours", "Persistent reminder notifications."},
        {"Jarvis, take a note \u2026 / read my notes / clear my notes", "Private on-device notes."},
        {"Jarvis, weather", "Needs location permission once."},
        {"Jarvis, battery status / phone status", "Diagnostics."},
        {"Jarvis, any messages?", "Reads recent notifications aloud (needs notification access)."},
        {"Jarvis, calculate 34 times 12", "Simple math."},
        {"Jarvis, stop listening / goodnight", "Shuts the voice core down."},
        {"Jarvis, <any question>", "AI brain, if a key is set; else web search."},
    };

    private String describeHeadset() {
        HeadsetRouter.Kind k = HeadsetRouter.headsetKind(this);
        if (k == HeadsetRouter.Kind.BT_SCO) return "Bluetooth headset (SCO mic available)";
        if (k == HeadsetRouter.Kind.WIRED) return "Wired headset";
        return HeadsetRouter.bluetoothConnected(this)
                ? "Bluetooth device (no SCO audio)" : "speaker only";
    }

    private String buildReport() {
        Prefs p = Prefs.get(this);
        StringBuilder sb = new StringBuilder();
        append(sb, "MODEL", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + ", Android " + android.os.Build.VERSION.SDK_INT);
        append(sb, "APP", "v" + appVersion() + " (" + DeviceTools.batteryPercent(this) + "% battery, "
                + (HeadsetRouter.headsetKind(this) == HeadsetRouter.Kind.BT_SCO ? "BT SCO" : "internal mic") + ")");
        append(sb, "MIC PERMISSION", String.valueOf(pm("android.permission.RECORD_AUDIO")));
        append(sb, "RECOGNIZER ", String.valueOf(android.speech.SpeechRecognizer.isRecognitionAvailable(this)));
        append(sb, "OFFLINE WAKER ON", String.valueOf(VoskWakeEngine.isAvailable(this)));
        append(sb, "SPEAKER MODEL", String.valueOf(VoskWakeEngine.speakerModelAvailable(this)));
        append(sb, "VOICEPRINT ", (Voiceprint.isEnrolled(this)
                ? "enrolled(" + Voiceprint.sampleCount(this) + ")" : "not enrolled")
                + ", lock=" + p.bool(Prefs.VOICEPRINT_LOCK, false));
        append(sb, "VOICE SERVICE", (JarvisService.isRunning() ? "running" : "stopped"));
        append(sb, "LISTEN MODE", "wakeOnly=" + p.wakeOnly + " vosk=" + p.bool(Prefs.VOSK_ENABLED, true)
                + " carMode=" + p.bool(Prefs.CAR_MODE, false));
        append(sb, "VOICES / TTS", "packOn=" + p.bool(Prefs.SOUND_PACK_ENABLED, true)
                + " ttsOn=" + p.bool(Prefs.TTS_ENABLED, true)
                + " ttsReady=" + VoiceFeedback.voiceOf(this).ttsReady());
        boolean allOk = JarvisNotificationService.isActive();
        append(sb, "NOTIFICATIONS ", (allOk ? "listener active" : "no access (needed for 'any messages?')")
                + "; " + JarvisNotificationService.items().size() + " captured");
        append(sb, "AI BRAIN ", p.aiApiKey.trim().isEmpty() ? "not configured" : "configured (" + p.aiBaseUrl + ")");
        return sb.toString();
    }

    private void append(StringBuilder sb, String key, String val) {
        sb.append(key).append(": ").append(val).append('\n');
    }

    private boolean pm(String perm) {
        return checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "?"; }
    }

    private android.widget.TextView labelBox(String t) {
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(t);
        tv.setTextColor(0xFF0A7EA6);
        tv.setTextSize(11);
        tv.setLetterSpacing(0.2f);
        tv.setPadding(0, dp(18), 0, dp(4));
        return tv;
    }

    private android.view.View divider() {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        v.setBackgroundColor(0xFF123B4E);
        v.setPadding(0, dp(12), 0, dp(12));
        return v;
    }

    private android.view.View toggle(Prefs p, String key, String title, boolean def) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(title);
        sw.setTextColor(0xFFC9EAF6);
        sw.setTextSize(14);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(p.bool(key, def));
        sw.setOnCheckedChangeListener((b, on) -> p.put(key, on));
        return sw;
    }

    private android.widget.EditText field(String value, String hint) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(value == null ? "" : value);
        et.setHint(hint);
        et.setHintTextColor(0xFF0A7EA6);
        et.setTextColor(0xFFC9EAF6);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setPadding(0, dp(4), 0, dp(10));
        return et;
    }

    private android.text.TextWatcher watcher(Prefs p, String key) {
        return new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { p.put(key, s.toString()); }
        };
    }

    private android.view.View action(String title, Runnable r) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(title);
        b.setTextColor(0xFF00D4FF);
        b.setTextSize(13);
        b.setBackgroundResource(R.drawable.btn_panel);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
