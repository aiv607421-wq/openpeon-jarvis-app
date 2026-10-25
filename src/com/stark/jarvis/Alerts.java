package com.stark.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Notification plumbing. */
public class Alerts {

    public static final String CH_SERVICE = "jarvis_service";
    public static final String CH_REMIND = "jarvis_reminders";
    private static int remindId = 100;

    public static void channels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel svc = new NotificationChannel(CH_SERVICE, c.getString(R.string.channel_service),
                NotificationManager.IMPORTANCE_LOW);
        svc.setDescription("Voice core status");
        nm.createNotificationChannel(svc);
        NotificationChannel rem = new NotificationChannel(CH_REMIND, c.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH);
        rem.setDescription("Voice-set reminders");
        nm.createNotificationChannel(rem);
    }

    public static Notification serviceNotification(Context c, String text) {
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CH_SERVICE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c.getString(R.string.service_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true);
        // "Talk" action: a one-shot capture session straight from the notification.
        Intent talk = new Intent(c, JarvisService.class).setAction("com.stark.jarvis.TALK");
        b.addAction(new Notification.Action.Builder(null, "Talk",
                PendingIntent.getService(c, 1, talk, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        Intent stop = new Intent(c, JarvisService.class).setAction("com.stark.jarvis.STOP");
        b.addAction(new Notification.Action.Builder(null, "Stand down",
                PendingIntent.getService(c, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        return b.build();
    }

    /** Friendly reminder pop, fired by AlarmReceiver. */
    public static void reminder(Context c, String title) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Reminder, sir")
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(remindId++, n);
        VoiceFeedback.voiceOf(c).speak("Sir, " + title, null);
    }
}
