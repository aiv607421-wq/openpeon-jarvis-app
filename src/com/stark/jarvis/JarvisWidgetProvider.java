package com.stark.jarvis;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class JarvisWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_jarvis);
            Intent i = new Intent(context, MainActivity.class)
                    .setAction("com.stark.jarvis.WIDGET_TALK")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(context, id, i,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, v);
        }
    }
}
