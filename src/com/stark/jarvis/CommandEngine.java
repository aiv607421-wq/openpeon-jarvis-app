package com.stark.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JARVIS command rules: parses the recognized phrase and returns a Result
 * { reply, sound category } plus side-effects on the device.
 */
public class CommandEngine {

    public static class Result {
        public final String reply;
        public final String sound;   // voice-pack category or null
        public final boolean isQuestion; // route to AI brain if it answers questions
        public Result(String reply, String sound) { this(reply, sound, false); }
        public Result(String reply, String sound, boolean question) {
            this.reply = reply; this.sound = sound; this.isQuestion = question;
        }
    }

    private static String lastRepeatKey = "";
    private static long lastRepeatAt = 0;

    public static Result handle(Context c, String raw) {
        String text = raw.trim();
        String low = normalize(text);

        // Anti-double-trigger (recognizers occasionally fire twice).
        String key = low;
        long now = System.currentTimeMillis();
        if (key.equals(lastRepeatKey) && now - lastRepeatAt < 2500) {
            lastRepeatAt = now;
            return new Result(null, "spam");
        }
        lastRepeatKey = key; lastRepeatAt = now;

        if (low.isEmpty()) return new Result("I didn't catch that, sir.", "input");

        // --- shutdown / status of self ------------------------------------------
        if (matches(low, "stop listening", "go to sleep", "stand down", "that will be all", "goodnight", "good night")) {
            String line = pick(new String[]{"Very good, sir. Powering down.",
                    "As you wish. Do let me know when you need me."});
            JarvisService.requestStop(c, line);
            return new Result(null, null);
        }
        if (matches(low, "how are you", "status check", "self diagnostic", "run diagnostics")) {
            String reply = "All systems nominal, sir. " + DeviceTools.batteryLine(c);
            if (!granted(c, Manifest.permission.RECORD_AUDIO)) reply += " Though you have not yet granted me ears.";
            return new Result(reply, null);
        }
        if (matches(low, "who are you", "introduce yourself", "what is your name")) {
            return new Result("I am JARVIS. Just A Rather Very Intelligent System, at your service, sir.", "ack");
        }
        if (matches(low, "hello", "hey jarvis", "hi jarvis", "good morning", "good evening", "good afternoon")) {
            return new Result(pick(new String[]{"Good to see you, sir.", "At your service, sir.",
                    "Welcome home, sir."}), "ack");
        }
        if (matches(low, "thank you", "thanks")) {
            return new Result(pick(new String[]{"Always a pleasure, sir.", "Think nothing of it, sir.",
                    "Of course, sir."}), "ack");
        }

        // --- flashlight ----------------------------------------------------------
        if (low.contains("flashlight") || low.contains("torch")) {
            boolean on = low.contains("on") || low.contains("up");
            boolean off = low.contains("off") || low.contains("close");
            if (on == off) return new Result("On or off, sir?", "input");
            boolean ok = DeviceTools.torch(c, on);
            return ok ? new Result("Torch " + (on ? "on." : "off."), "complete")
                      : new Result("The torch refuses to cooperate, sir.", "error");
        }

        // --- volume --------------------------------------------------------------
        if (low.contains("volume")) {
            if (low.contains("up")) return ok(DeviceTools.volumeUp(c), "Volume up.");
            if (low.contains("down")) return ok(DeviceTools.volumeDown(c), "Volume down.");
            if (low.contains("max") || low.contains("full")) return ok(DeviceTools.volumeMax(c), "Volume at maximum.");
            if (low.contains("mute") && !low.contains("un")) return ok(DeviceTools.volumeMute(c), "Muted.");
            if (low.contains("unmute")) return ok(DeviceTools.volumeUnmute(c), "Unmuted.");
            AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            return new Result("Media volume at " + a.getStreamVolume(AudioManager.STREAM_MUSIC)
                    + " of " + a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) + ".", null);
        }

        // --- brightness ----------------------------------------------------------
        if (low.contains("brightness") || low.contains("dim the screen")) {
            boolean up = low.contains("up") || low.contains("high") || low.contains("increase");
            boolean down = low.contains("down") || low.contains("lower") || low.contains("dim")
                    || low.contains("decrease");
            if (up == down) {
                int pct = parsePercent(low);
                if (pct >= 0 && DeviceTools.setBrightnessPercent(c, pct))
                    return new Result("Brightness at " + pct + " percent.", "complete");
                return new Result("Which way, sir — up, down, or a percentage?", "input");
            }
            boolean ok = DeviceTools.brightnessNudge(c, up);
            return ok ? new Result("Brightness " + (up ? "raised." : "lowered."), "complete")
                      : new Result("I need modify-settings permission for that, sir.", "error");
        }

        // --- connectivity panels ---------------------------------------------------
        if (low.contains("wifi") || low.contains("wi-fi") || low.contains("wireless")
                || low.contains("bluetooth") || low.contains("airplane") || low.contains("mobile data")) {
            DeviceTools.openConnectivityPanel(c);
            return new Result("Connectivity panel ready, sir — confirm it there.", "ack");
        }

        // --- time & date ------------------------------------------------------------
        if (matches(low, "what time is it", "tell me the time", "the time")) {
            String t = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
            return new Result("It is " + t + ", sir.", null);
        }
        if (matches(low, "what is the date", "what date is it", "today date", "what day is it")) {
            String d = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(new Date());
            return new Result("Today is " + d + ", sir.", null);
        }

        // --- battery ------------------------------------------------------------------
        if (low.contains("battery") && (low.contains("status") || low.contains("level")
                || low.contains("how much") || low.equals("battery"))) {
            return new Result(DeviceTools.batteryLine(c),
                    DeviceTools.batteryPercent(c) <= 15 ? "resource" : null);
        }
        if (low.contains("phone status") || low.contains("system status") || low.contains("device status")
                || low.contains("storage")) {
            return new Result(DeviceTools.deviceLine(c), null);
        }

        // --- alarm / timer -----------------------------------------------------------
        Integer[] t = parseNaturalTime(text);
        if (t != null && (low.contains("alarm") || low.contains("wake me"))) {
            DeviceTools.setAlarm(c, t[0], t[1], "On your orders, sir");
            return new Result("Alarm set for " + describe(t), "complete");
        }
        long seconds = parseTimerSeconds(text);
        if (seconds > 0 && low.contains("timer")) {
            DeviceTools.setTimer(c, (int) seconds, "JARVIS timer");
            return new Result("Timer armed for " + describeSeconds(seconds) + ".", "complete");
        }

        // --- reminders -----------------------------------------------------------------
        String reminderReply = Reminders.trySet(c, text);
        if (reminderReply != null) return new Result(reminderReply, "complete");

        // --- notes -----------------------------------------------------------------------
        if (low.startsWith("take a note") || low.startsWith("make a note") || low.startsWith("note that")
                || low.startsWith("note to self") || low.startsWith("write a note")) {
            String note = stripPrefix(text, "take a note|take note|make a note|make a note that|note that|note to self|write a note");
            if (note.trim().isEmpty()) return new Result("What shall I note, sir?", "input");
            NotesStore.add(c, note);
            return new Result("Noted.", "complete");
        }
        if (low.contains("read my notes") || low.contains("my notes") || low.contains("read the note")) {
            String notes = NotesStore.readAll(c).trim();
            return notes.isEmpty()
                    ? new Result("Your notebook is empty, sir.", null)
                    : new Result("Your notes: " + notes.replace("\n", " … "), null);
        }
        if (low.contains("clear my notes") || low.contains("delete my notes")) {
            NotesStore.clear(c);
            return new Result("Notebook wiped.", "complete");
        }

        // --- notifications (read out on request) ---------------------------------------
        boolean wantsMessages = (low.contains("message") || low.contains("notification") || low.contains("whatsapp")
                || low.contains("who messaged") || low.contains("messages"))
                && (low.contains("any") || low.contains("check") || low.contains("read") || low.contains("who")
                    || low.contains("show") || low.contains("tell"));
        boolean wantsClear = low.contains("clear notification") || low.contains("dismiss notification")
                || low.contains("clear all notifications") || low.contains("clear my notifications");
        if (wantsClear) {
            JarvisNotificationService.clearAll(c);
            return new Result("Notifications cleared, sir.", "complete");
        }
        if (wantsMessages) {
            if (!JarvisNotificationService.isActive()) {
                return new Result("I have no notification access, sir — grant it in Systems.", "error");
            }
            return new Result(JarvisNotificationService.readout(), null);
        }

        // --- calls -----------------------------------------------------------------------
        if (low.startsWith("call ")) {
            String who = stripPrefix(text, "call");
            boolean phonePerm = granted(c, Manifest.permission.CALL_PHONE);
            boolean contactsPerm = granted(c, Manifest.permission.READ_CONTACTS);
            String number = extractNumber(who);
            if (number != null) {
                placeCall(c, number, phonePerm);
                return new Result("Calling " + number + ".", "ack");
            }
            String contact = contactsPerm ? lookupContact(c, who) : null;
            if (contact != null) {
                placeCall(c, contact, phonePerm);
                return new Result("Calling " + who.trim() + ".", "ack");
            }
            return new Result(contactsPerm
                    ? "No contact matches " + who.trim() + ", sir."
                    : "Grant me contacts and phone permission and I will make the call, sir.", "error");
        }

        // --- texts -------------------------------------------------------------------------
        if (low.startsWith("text ") || low.startsWith("send a message") || low.startsWith("message ")) {
            String who = stripPrefix(text, "text|send a message|send a text|message");
            java.util.regex.Matcher sm = java.util.regex.Pattern
                    .compile(".*\\s(saying|about|that)\\s+(.+)$").matcher(who);
            String body = who;
            String to = "";
            if (sm.matches()) { body = sm.group(2).trim(); to = who.substring(0, sm.start(1)).replace("to", "").trim(); }
            boolean contactsPerm = granted(c, Manifest.permission.READ_CONTACTS);
            String number = to.isEmpty() ? null : (extractNumber(to) != null ? extractNumber(to) : (contactsPerm ? lookupContact(c, to) : null));
            try {
                String data = (number != null && !number.isEmpty()) ? ("smsto:" + number) : "smsto:";
                Intent i = new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(data));
                i.putExtra("sms_body", body.isEmpty() ? "" : capitalizeFirst(body));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return new Result("Message drafted" + (number != null && !number.isEmpty() ? " for " + to + "." : "."), "ack");
            } catch (Exception e) {
                return new Result("No messaging app found, sir.", "error");
            }
        }

        // --- open apps -------------------------------------------------------------------------
        if (low.startsWith("open ") || low.startsWith("launch ") || low.startsWith("start ")) {
            String thing = stripPrefix(text, "open|launch|start").trim();
            if (low.contains("www.") || low.contains("http") || low.contains(".com")) {
                String url = thing.startsWith("http") ? thing : ("https://" + thing);
                DeviceTools.openUrl(c, url);
                return new Result("Opening.", "ack");
            }
            if (thing.matches("settings|system settings")) {
                c.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return new Result("Opening settings.", "ack");
            }
            if (thing.matches("camera|the camera")) {
                try {
                    c.startActivity(new Intent("android.media.action.IMAGE_CAPTURE")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return new Result("Camera ready.", "ack");
                } catch (Exception e) { /* fall through to app search */ }
            }
            String pkg = DeviceTools.findApp(c, thing);
            if (pkg != null && DeviceTools.openApp(c, pkg)) {
                String label = stripPrefix(text, "open|launch|start").trim();
                return new Result("Opening " + label + ", sir.", "ack");
            }
            return new Result("I cannot find anything called " + thing + ", sir.", "error");
        }

        // --- search / play -------------------------------------------------------------------------
        if (low.startsWith("search for ") || low.startsWith("search ") || low.startsWith("google ")
                || low.contains("search youtube") || low.contains("search the web")) {
            String q = stripPrefix(text, "search for|search the web for|search the web|search|google|search youtube for|search youtube");
            boolean yt = low.contains("youtube");
            if (yt) DeviceTools.youtubeSearch(c, q.isEmpty() ? "" : q);
            else DeviceTools.webSearch(c, q.isEmpty() ? stripPrefix(raw, "search|google|search for") : q);
            return new Result("Searching the web, sir.", "ack");
        }
        if (low.startsWith("play ")) {
            String q = stripPrefix(text, "play");
            DeviceTools.youtubeSearch(c, q.trim());
            return new Result("Queuing " + q.trim() + ", sir.", "ack");
        }

        // --- weather ----------------------------------------------------------------------------------
        if (low.contains("weather")) {
            boolean fine = granted(c, Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarse = granted(c, Manifest.permission.ACCESS_COARSE_LOCATION);
            DeviceTools.weatherAction(c, fine, coarse);
            return new Result((fine || coarse) ? "Checking the weather, sir." :
                    "Without location permission I shall show the general forecast, sir.", "ack");
        }

        // --- math --------------------------------------------------------------------------------------
        IntAndRest calc = extractCalculation(text);
        if (calc != null) {
            return new Result("That comes to " + formatCalc(calc), "complete");
        }

        // --- AI brain / web fallback ---------------------------------------------------------------------
        boolean aiAvailable = !Prefs.get(c).aiApiKey.trim().isEmpty() && !Prefs.get(c).aiBaseUrl.trim().isEmpty();
        if (aiAvailable) {
            return new Result(null, "thinking", true);
        }
        DeviceTools.webSearch(c, text);
        return new Result("That one is beyond my local reach, sir — I have searched the web.", "error");
    }

    public static Result ok(boolean done, String line) {
        return done ? new Result(line, "complete") : new Result(line + " Failed.", "error");
    }

    // ---------------- helpers ----------------

    /** Removes the heard wake word (and filler words) from the front of the raw phrase. */
    public static String fuzzyTrim(String raw, String prefix) {
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length == 0) return "";
        String probe = prefix.length() > 3 ? prefix.substring(0, 4) : prefix;
        if (!tokens[0].toLowerCase(Locale.US).startsWith(probe)) return raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) sb.append(tokens[i]).append(' ');
        String out = sb.toString().trim();
        out = out.replaceFirst("(?i)^(please|would you|could you|can you|kindly)\\s+", "").trim();
        return out;
    }

    public static String normalize(String s) {
        return s.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim()
                .replace("?", "").replace(".", "").replace(",", "").replace("!", "");
    }

    private static boolean matches(String low, String... phrases) {
        for (String p : phrases) if (low.equals(p) || low.startsWith(p + " ") || (low.contains(p) && p.length() > 8)) return true;
        return false;
    }

    private static String stripPrefix(String s, String prefixes) {
        for (String p : prefixes.split("\\|")) {
            String low = s.toLowerCase(Locale.US);
            if (low.startsWith(p)) {
                String out = s.substring(p.length());
                return out.replaceFirst("^(\\s+)(?i)(a\\s+)?", "").trim();
            }
        }
        return s;
    }

    private static String pick(String[] arr) { return arr[new java.util.Random().nextInt(arr.length)]; }

    private static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static String capitalizeFirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- time parsing ----
    /** Returns [hour, minute] from natural phrases like "at 7", "at 7:30 pm". */
    private static Integer[] parseNaturalTime(String text) {
        String low = normalize(text);
        if (!low.contains(" at ") && !low.contains("wake me") && !low.contains("alarm")) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:at|wake me at|alarm at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|a\\.?m\\.?|pm|p\\.?m\\.?|in the (?:morning|evening|afternoon))?").matcher(low);
        if (!m.find()) return null;
        try {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ap = m.group(3);
            if (ap != null) {
                ap = ap.replace(".", "");
                if (ap.contains("pm") && h < 12) h += 12;
                if (ap.startsWith("am") && h == 12) h = 0;
            }
            if (h > 23 || min > 59) return null;
            return new Integer[]{h, min};
        } catch (Exception e) { return null; }
    }

    private static String describe(Integer[] hm) {
        int h = hm[0], m = hm[1];
        String ap = h < 12 ? "a m" : "p m";
        int hh = h % 12 == 0 ? 12 : h % 12;
        return hh + ":" + String.format(Locale.US, "%02d", m) + " " + ap;
    }

    private static long parseTimerSeconds(String text) {
        String low = normalize(text);
        long total = 0; boolean any = false;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*(seconds?|minutes?|hours?)").matcher(low);
        while (m.find()) {
            try {
                long n = Long.parseLong(m.group(1));
                String u = m.group(2);
                if (u.startsWith("h")) total += n * 3600;
                else if (u.startsWith("m")) total += n * 60;
                else total += n;
                any = true;
            } catch (Exception ignored) { }
        }
        return any ? total : 0;
    }

    private static String describeSeconds(long s) {
        if (s < 60) return s + " seconds";
        if (s % 60 == 0 && s < 3600) return (s / 60) + " minutes";
        if (s % 3600 == 0) return (s / 3600) + " hours";
        return String.format(Locale.US, "%.1f minutes", s / 60.0);
    }

    // ---- numbers ----
    private static int parsePercent(String low) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*(%|percent)?").matcher(low);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                return (v >= 0 && v <= 100) ? v : -1;
            } catch (Exception ignored) { }
        }
        return -1;
    }

    // ---- calculator ----
    private static class IntAndRest { double value; }
    private static final Map<String, String> NUM_WORDS = new HashMap<>();
    static {
        NUM_WORDS.put("zero", "0"); NUM_WORDS.put("one", "1"); NUM_WORDS.put("two", "2");
        NUM_WORDS.put("three", "3"); NUM_WORDS.put("four", "4"); NUM_WORDS.put("five", "5");
        NUM_WORDS.put("six", "6"); NUM_WORDS.put("seven", "7"); NUM_WORDS.put("eight", "8");
        NUM_WORDS.put("nine", "9"); NUM_WORDS.put("ten", "10");
        NUM_WORDS.put("eleven", "11"); NUM_WORDS.put("twelve", "12"); NUM_WORDS.put("twenty", "20");
        NUM_WORDS.put("thirty", "30"); NUM_WORDS.put("forty", "40"); NUM_WORDS.put("fifty", "50");
        NUM_WORDS.put("sixty", "60"); NUM_WORDS.put("seventy", "70"); NUM_WORDS.put("eighty", "80");
        NUM_WORDS.put("ninety", "90"); NUM_WORDS.put("hundred", "100"); NUM_WORDS.put("minus", "-");
    }

    private static String spokenToDigits(String low) {
        for (Map.Entry<String, String> e : NUM_WORDS.entrySet()) {
            if (low.contains(" " + e.getKey() + " ") || low.startsWith(e.getKey() + " ")) {
                low = low.replace(" " + e.getKey(), " " + e.getValue());
                if (low.startsWith(e.getKey())) low = low.replaceFirst(e.getKey(), e.getValue());
            }
        }
        return low;
    }

    private static IntAndRest extractCalculation(String text) {
        String low = spokenToDigits(normalize(text));
        if (!low.startsWith("calculate") && !low.startsWith("what is") && !low.startsWith("how much is")) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(times|multiplied by|plus|and|minus|less|divided by|over|percent of|x)\\s*(\\d+(?:\\.\\d+)?)").matcher(low);
        if (!m.find()) return null;
        try {
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(3));
            String op = m.group(2);
            IntAndRest r = new IntAndRest();
            r.value = op.startsWith("times") || op.equals("x") || op.contains("multiplied") ? a * b
                    : op.equals("plus") || op.equals("and") ? a + b
                    : op.equals("minus") || op.equals("less") ? a - b
                    : op.contains("divided") || op.equals("over") ? a / b
                    : a * b / 100.0; // percent of
            return r;
        } catch (Exception e) { return null; }
    }

    private static String formatCalc(IntAndRest r) {
        if (r.value == Math.floor(r.value) && !Double.isInfinite(r.value))
            return String.valueOf((long) r.value);
        return String.valueOf(r.value);
    }

    // ---- calls & contacts ----
    private static String extractNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\+?\\d[\\d\\- ]{7,}").matcher(s);
        if (m.find()) return m.group().replaceAll("[ -]", "");
        return null;
    }

    public static String lookupContact(Context c, String name) {
        try {
            android.database.Cursor cur = c.getContentResolver().query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    null, null, null);
            if (cur == null) return null;
            String best = null; int bestScore = 0;
            String q = normalize(name);
            while (cur.moveToNext()) {
                String n = cur.getString(1);
                if (n == null) continue;
                String ln = normalize(n);
                int score = 0;
                if (ln.equals(q)) score = 4; else if (ln.startsWith(q)) score = 3;
                else if (ln.contains(q)) score = 2;
                for (String tok : q.split(" ")) if (!tok.isEmpty() && ln.contains(tok)) score = Math.max(score, 2);
                if (score > bestScore) { bestScore = score; best = cur.getString(0); }
            }
            cur.close();
            return best;
        } catch (Exception e) { return null; }
    }

    private static void placeCall(Context c, String number, boolean callPerm) {
        try {
            Intent i;
            if (callPerm) {
                i = new Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:" + number));
            } else {
                i = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + number));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) { }
    }
}
