package com.stark.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateUtils;

import java.util.Calendar;

/** Reminder scheduling: "remind me to X in N minutes | at H(:MM) [am|pm]". */
public class Reminders {

    /** @return user-facing confirmation, or null if no reminder pattern matched. */
    public static String trySet(Context c, String text) {
        String t = text.toLowerCase().trim();
        if (!t.contains("remind")) return null;
        if (!(t.contains("remind me") || t.startsWith("remind"))) return null;

        String rest = t.replaceFirst("^.*(remind me|remind)", "").trim();
        rest = rest.replaceFirst("^(sir|to|that|about)\\s+", "");

        Long delay = null; String title;
        java.util.regex.Matcher mIn = java.util.regex.Pattern
                .compile("^(.*)\\s+in\\s+(\\d+)\\s+(second|seconds|minute|minutes|hour|hours)\\b.*$").matcher(rest);
        java.util.regex.Matcher mAt = java.util.regex.Pattern
                .compile("^(.*)\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|a\\.?m\\.?|pm|p\\.?m\\.?)?\\b.*$").matcher(rest);

        if (mIn.matches()) {
            title = mIn.group(1).trim();
            long n = parseNumber(mIn.group(2));
            String unit = mIn.group(3);
            long mult = unit.startsWith("second") ? 1000L : unit.startsWith("minute") ? 60_000L : 3_600_000L;
            delay = n * mult;
        } else if (mAt.matches()) {
            title = mAt.group(1).trim();
            int hour = Integer.parseInt(mAt.group(2));
            int min = mAt.group(3) != null ? Integer.parseInt(mAt.group(3)) : 0;
            String ap = mAt.group(4);
            if (ap != null) {
                ap = ap.replace(".", "");
                if (ap.equals("pm") && hour < 12) hour += 12;
                if (ap.equals("am") && hour == 12) hour = 0;
            }
            Calendar cal = Calendar.getInstance();
            Calendar due = (Calendar) cal.clone();
            due.set(Calendar.HOUR_OF_DAY, hour);
            due.set(Calendar.MINUTE, min);
            due.set(Calendar.SECOND, 0);
            if (due.before(cal)) due.add(Calendar.DAY_OF_YEAR, 1);
            delay = due.getTimeInMillis() - cal.getTimeInMillis();
        } else {
            return null;
        }

        if (title.isEmpty()) return null;
        schedule(c, title, delay);
        return "Reminder set" + (title.isEmpty() ? "." : ": " + capitalize(title)) + whenPhrase(delay);
    }

    private static long parseNumber(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 1; }
    }

    private static String whenPhrase(long delayMs) {
        if (delayMs < DateUtils.MINUTE_IN_MILLIS) return " in " + (delayMs / 1000) + " seconds.";
        if (delayMs < DateUtils.HOUR_IN_MILLIS) return " in " + (delayMs / DateUtils.MINUTE_IN_MILLIS) + " minutes.";
        return " in " + String.format(java.util.Locale.US, "%.1f", delayMs / 3600000.0) + " hours.";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void schedule(Context c, String title, long delayMs) {
        long when = System.currentTimeMillis() + delayMs;
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("title", title);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int) (System.nanoTime() & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        boolean exact = Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms();
        if (exact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return;
            } catch (SecurityException ignored) { }
        }
        am.setWindow(AlarmManager.RTC_WAKEUP, when, 60_000, pi);
    }
}
