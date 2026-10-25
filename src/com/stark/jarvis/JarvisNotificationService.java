package com.stark.jarvis;

import android.app.Notification;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures incoming notifications (WhatsApp, SMS, everything) so JARVIS can read
 * them aloud on request: "Jarvis, any messages?".
 * Requires the user to grant Notification access (SYSTEMS -> REQUEST NOTIFICATION ACCESS).
 */
public class JarvisNotificationService extends NotificationListenerService {

    public static class Item {
        public final String pkg;
        public final String title;
        public final String text;
        public final long when;
        Item(String pkg, String title, String text, long when) {
            this.pkg = pkg; this.title = title; this.text = text; this.when = when;
        }
        String app() { return appName(pkg); }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static final int MAX = 25;
    private static JarvisNotificationService sInstance;

    public static boolean isActive() { return sInstance != null; }

    public static List<Item> items() {
        synchronized (ITEMS) { return new ArrayList<>(ITEMS); }
    }

    public static String appName(String pkg) {
        if (pkg == null) return "";
        switch (pkg) {
            case "com.whatsapp": case "com.whatsapp.w4b": return "WhatsApp";
            case "com.google.android.apps.messaging": return "Messages";
            case "com.android.messaging": case "com.android.mms": return "SMS";
            case "com.instagram.android": return "Instagram";
            case "org.telegram.messenger": return "Telegram";
            case "com.android.dialer": return "Dialer";
            case "com.google.android.gm": return "Gmail";
            default:
                int d = pkg.lastIndexOf('.');
                return d >= 0 && d < pkg.length() - 1 ? pkg.substring(d + 1) : pkg;
        }
    }

    public static String readout() {
        List<Item> it = items();
        if (it.isEmpty()) return "All quiet, sir. Not a peep.";
        StringBuilder sb = new StringBuilder("You have " + it.size() + " messages, sir. Latest first: ");
        int count = 0;
        for (Item i : it) {
            if (count++ >= 4) break;
            String body = i.text == null ? "" : i.text.replace("\n", " ").trim();
            if (body.length() > 90) body = body.substring(0, 90) + "…";
            sb.append(" ").append(i.app());
            if (i.title != null && !i.title.isEmpty()) sb.append(" from ").append(i.title);
            sb.append(": ").append(body.isEmpty() ? "(no text)" : "\"" + body + ".\"");
        }
        if (it.size() > 4) sb.append(" Plus ").append(it.size() - 4).append(" more I can read later.");
        return sb.toString();
    }

    public static void clearAll(Context ignored) {
        JarvisNotificationService s = sInstance;
        if (s != null) {
            try { s.cancelAllNotifications(); } catch (Exception ignored2) { }
            synchronized (ITEMS) { ITEMS.clear(); }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        // skip rolling group summaries and ongoing annoyances
        boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (summary || ongoing) return;

        CharSequence titleC = null, textC = null;
        android.os.Bundle ex = n.extras;
        if (ex != null) {
            titleC = ex.getCharSequence(Notification.EXTRA_TITLE_BIG);
            if (titleC == null) titleC = ex.getCharSequence(Notification.EXTRA_TITLE);
            textC = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (textC == null || textC.length() == 0) textC = ex.getCharSequence(Notification.EXTRA_TEXT);
        }
        String title = titleC == null ? "" : titleC.toString();
        String text = textC == null ? "" : textC.toString();
        if (title.equals("JARVIS") || (text.isEmpty() && title.isEmpty())) return;
        // skip our own notifications
        if (getPackageName().equals(sbn.getPackageName())) return;

        Item item = new Item(sbn.getPackageName(), title, text,
                sbn.getNotification().when > 0 ? sbn.getNotification().when : System.currentTimeMillis());
        synchronized (ITEMS) {
            // de-dupe identical posts
            for (int i = 0; i < ITEMS.size(); i++) {
                Item old = ITEMS.get(i);
                if (old.pkg.equals(item.pkg) && nulEq(old.title, item.title) && nulEq(old.text, item.text)) {
                    ITEMS.remove(i);
                    break;
                }
            }
            ITEMS.add(0, item);
            while (ITEMS.size() > MAX) ITEMS.remove(ITEMS.size() - 1);
        }
    }

    private static boolean nulEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
