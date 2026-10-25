package com.stark.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** J.A.R.V.I.S. HUD: onboarding, live transcript, and the reactor button. */
public class MainActivity extends Activity implements JarvisService.Listener {

    private static final int PERMS_FIRST = 1;

    private android.widget.TextView status, transcript, reply, log;
    private android.widget.ImageButton reactor;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final StringBuilder logLines = new StringBuilder();
    private int logCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Alerts.channels(this);
        Prefs prefs = Prefs.get(this);
        // Sensible default brain endpoint (Freebuff's OpenAI-compatible API); user can change it.
        if (prefs.aiBaseUrl.trim().isEmpty()) prefs.put(Prefs.AI_BASE, "https://freebuff.com/api/v1/paid");
        maybeWidgetIntent(getIntent());

        status = findViewById(R.id.status);
        transcript = findViewById(R.id.transcript);
        reply = findViewById(R.id.reply);
        log = findViewById(R.id.log);
        reactor = findViewById(R.id.reactor);

        reactor.setOnClickListener(v -> {
            if (jarvisArmed()) {
                JarvisService.start(this);
                talkNow();
            } else {
                requestEssentialPerms();
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_stop).setOnClickListener(v ->
                JarvisService.requestStop(this, "Standing down. Do call when you need me, sir."));

        logLine("core online — Mk VII");
    }

    private boolean jarvisArmed() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Home-screen widget / quick-settings tile launch here to summon me. */
    private void maybeWidgetIntent(Intent i) {
        if (i == null || !"com.stark.jarvis.WIDGET_TALK".equals(i.getAction())) return;
        if (jarvisArmed()) {
            JarvisService.start(this);
            talkNow();
        } else {
            requestEssentialPerms();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        maybeWidgetIntent(intent);
    }

    private void talkNow() {
        Intent i = new Intent(this, JarvisService.class).setAction("com.stark.jarvis.TALK");
        startService(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        JarvisService.uiListener = this;
        VoiceFeedback.voiceOf(this);
        boolean mic = jarvisArmed();
        if (!mic) {
            status.setText("Permissions required, sir.");
            requestEssentialPerms();
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMS_FIRST + 2);
        }
        // optional but useful: contacts, calls, and Bluetooth routing for car/headset mode
        List<String> optional = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            optional.add(Manifest.permission.READ_CONTACTS);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            optional.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            optional.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!optional.isEmpty()) {
            requestPermissions(optional.toArray(new String[0]), PERMS_FIRST + 3);
        }
        JarvisService.start(this);
    }

    @Override
    protected void onPause() {
        JarvisService.uiListener = null;
        super.onPause();
    }

    private void requestEssentialPerms() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (need.isEmpty()) {
            JarvisService.start(this);
            return;
        }
        requestPermissions(need.toArray(new String[0]), PERMS_FIRST);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = false;
        for (int i = 0; i < perms.length; i++) {
            if (perms[i].equals(Manifest.permission.RECORD_AUDIO) && results[i] == PackageManager.PERMISSION_GRANTED)
                granted = true;
        }
        if (code == PERMS_FIRST && granted) {
            if (results.length >= 2 && perms.length == results.length
                    && Arrays.asList(perms).contains(Manifest.permission.RECORD_AUDIO)) {
                grantOptionalContacts();
            }
            JarvisService.start(this);
            status.setText("Mic armed. Say \"Jarvis\".");
            logLine("mic permission granted");
        } else if (code == PERMS_FIRST && !granted) {
            status.setText("I need the microphone to function, sir.");
            logLine("mic permission denied");
        }
    }

    private void grantOptionalContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE},
                    PERMS_FIRST + 3);
        }
    }

    // ---- JarvisService.Listener ----
    @Override public void onStatus(String s) { status.setText(s); }
    @Override public void onTranscript(String s) { transcript.setText(s); }
    @Override public void onReply(String s) { reply.setText(s); }
    @Override public void onLog(String s) { logLine(s); }

    private void logLine(String s) {
        logCount++;
        if (logCount > 40) { logLines.setLength(0); logCount = 1; }
        String stamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new Date());
        logLines.append(stamp).append("  ").append(s).append('\n');
        log.setText(logLines.toString().trim());
    }
}
