package com.stark.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After reboot the core comes back up; tap the notification (or open the app once) to re-arm the mic. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (a != null && (a.equals(Intent.ACTION_BOOT_COMPLETED) || a.equals("android.intent.action.QUICKBOOT_POWERON"))) {
            Intent svc = new Intent(context, JarvisService.class).setAction("com.stark.jarvis.BOOT");
            context.startForegroundService(svc);
        }
    }
}
