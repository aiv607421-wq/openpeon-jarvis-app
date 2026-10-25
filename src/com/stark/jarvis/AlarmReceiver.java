package com.stark.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        if (title == null || title.isEmpty()) title = "You asked to be reminded.";
        Alerts.channels(context);
        Alerts.reminder(context, title);
    }
}
