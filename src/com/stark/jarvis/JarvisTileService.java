package com.stark.jarvis;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class JarvisTileService extends TileService {
    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        boolean running = JarvisService.isRunning();
        if (running) {
            JarvisService.requestStop(getApplicationContext(), "Standing down. Do call when you need me, sir.");
        } else {
            // Tiles may not start mic foreground services directly; hand the
            // interaction to the HUD which starts it from the foreground.
            Intent i = new android.content.Intent(this, MainActivity.class)
                    .setAction("com.stark.jarvis.WIDGET_TALK")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivityAndCollapse(i);
            } catch (Exception e) {
                startActivity(i);
            }
        }
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean running = JarvisService.isRunning();
        t.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
