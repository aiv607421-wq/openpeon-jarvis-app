package com.stark.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.Settings;

import java.util.List;
import java.util.Locale;

/** Hardware + device actions JARVIS can control: torch, volume, brightness, apps, clocks, status. */
public class DeviceTools {

    private static boolean torchOn = false;

    // ---- Torch ---------------------------------------------------------------
    public static boolean torch(Context c, boolean on) {
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cm.setTorchMode(id, on);
                    torchOn = on;
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static boolean isTorchOn() { return torchOn; }

    // ---- Volume --------------------------------------------------------------
    private static AudioManager audio(Context c) { return (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }

    public static boolean volumeUp(Context c) { return adjust(c, AudioManager.ADJUST_RAISE); }
    public static boolean volumeDown(Context c) { return adjust(c, AudioManager.ADJUST_LOWER); }
    public static boolean volumeMute(Context c) {
        try { audio(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0); return true; }
        catch (Exception e) { return false; }
    }
    public static boolean volumeUnmute(Context c) {
        try { audio(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0); return true; }
        catch (Exception e) { return false; }
    }
    public static boolean volumeMax(Context c) {
        try {
            AudioManager a = audio(c);
            a.setStreamVolume(AudioManager.STREAM_MUSIC,
                    a.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            return true;
        } catch (Exception e) { return false; }
    }
    private static boolean adjust(Context c, int dir) {
        try { audio(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0); return true; }
        catch (Exception e) { return false; }
    }

    // ---- Brightness (needs WRITE_SETTINGS) -----------------------------------
    public static int brightnessPercent(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && Settings.System.getFloat(c.getContentResolver(), "screen_brightness_float", -1f) >= 0f) {
                float f = Settings.System.getFloat(c.getContentResolver(), "screen_brightness_float", 0.5f);
                return Math.round(f * 100f);
            }
            int b = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return Math.round(b / 255f * 100f);
        } catch (Exception e) { return -1; }
    }

    public static boolean setBrightnessPercent(Context c, int percent) {
        if (!Settings.System.canWrite(c)) return false;
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    Settings.System.putFloat(c.getContentResolver(), "screen_brightness_float", percent / 100f);
                    return true;
                } catch (Exception ignored) { }
            }
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.round(percent / 100f * 255f));
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean brightnessNudge(Context c, boolean up) {
        int cur = Math.max(5, brightnessPercent(c) < 0 ? 50 : brightnessPercent(c));
        return setBrightnessPercent(c, cur + (up ? 15 : -15));
    }

    // ---- Status --------------------------------------------------------------
    public static String batteryLine(Context c) {
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int charge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            boolean charging = charge == BatteryManager.BATTERY_STATUS_CHARGING
                    || charge == BatteryManager.BATTERY_STATUS_FULL;
            return "Battery at " + pct + " percent" + (charging ? ", charging." :
                    pct <= 15 ? ". Running low, sir." : ".");
        } catch (Exception e) { return "I cannot read the battery, sir."; }
    }

    public static int batteryPercent(Context c) {
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) { return -1; }
    }

    public static String deviceLine(Context c) {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long free = s.getAvailableBlocksLong() * (long) s.getBlockSizeLong();
            long total = s.getBlockCountLong() * (long) s.getBlockSizeLong();
            return String.format(Locale.US,
                    "Model %s %s, Android %d, storage %s free of %s, battery %d percent.",
                    Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
                    human(free), human(total), batteryPercent(c));
        } catch (Exception e) {
            return "Model " + Build.MODEL + ", Android " + Build.VERSION.SDK_INT + ".";
        }
    }

    private static String human(long bytes) {
        double v = bytes;
        for (String u : new String[]{"B", "KB", "MB", "GB"}) {
            if (v < 1024) return String.format(Locale.US, "%.0f%s", v, u);
            v /= 1024;
        }
        return String.format(Locale.US, "%.1fTB", v);
    }

    // ---- App launching -------------------------------------------------------
    public static String findApp(Context c, String query) {
        try {
            PackageManager pm = c.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String q = query.toLowerCase(Locale.US).trim();
            String best = null; int bestScore = -1;
            for (ApplicationInfo ai : apps) {
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                String label = pm.getApplicationLabel(ai).toString().toLowerCase(Locale.US);
                if (label.equals(q)) return ai.packageName;
                if (label.startsWith(q) && bestScore < 3) { best = ai.packageName; bestScore = 3; }
                else if (label.contains(q) && bestScore < 2) { best = ai.packageName; bestScore = 2; }
                else {
                    // match on package name too ("insta", "whatsapp")
                    String pn = ai.packageName.toLowerCase(Locale.US);
                    if (pn.contains(q.replaceAll("\\s+", "")) && bestScore < 1) { best = ai.packageName; bestScore = 1; }
                }
            }
            return best;
        } catch (Exception e) { return null; }
    }

    public static boolean openApp(Context c, String pkg) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- Web -----------------------------------------------------------------
    public static void openUrl(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) { }
    }

    public static void webSearch(Context c, String query) {
        openUrl(c, "https://www.google.com/search?q=" + Uri.encode(query == null ? "" : query));
    }

    public static void youtubeSearch(Context c, String query) {
        try {
            Intent i = new Intent(Intent.ACTION_SEARCH);
            i.setPackage("com.google.android.youtube");
            i.putExtra("query", query);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            openUrl(c, "https://www.youtube.com/results?search_query=" + Uri.encode(query));
        }
    }

    // ---- Clock ----------------------------------------------------------------
    public static void setAlarm(Context c, int hour, int minute, String label) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "JARVIS" : label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) { }
    }

    public static void setTimer(Context c, int seconds, String label) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "JARVIS timer" : label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) { }
    }

    // ---- Location (for weather) -----------------------------------------------
    public interface LocationCallback { void onLocation(String place); }

    public static void weatherAction(Context c, boolean hasFine, boolean hasCoarse) {
        if (hasFine || hasCoarse) {
            fetchPlace(c, place -> openUrl(c, "https://www.google.com/search?q=weather+" + Uri.encode(place)));
        } else {
            openUrl(c, "https://www.google.com/search?q=weather");
        }
    }

    private static void fetchPlace(Context c, LocationCallback cb) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = lm.getProviders(true);
            Location best = null;
            for (String p : providers) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) { }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 10 * 60_000L) {
                cb.onLocation(latLon(best.getLatitude(), best.getLongitude()));
                return;
            }
            // one-shot network update, 6s budget
            final String provider = LocationManager.NETWORK_PROVIDER;
            android.os.Handler h = new android.os.Handler(c.getMainLooper());
            LocationListener ll = new LocationListener() {
                public void onLocationChanged(Location l) { cb.onLocation(latLon(l.getLatitude(), l.getLongitude())); }
                @Deprecated public void onStatusChanged(String x, int i, Bundle b) { }
                public void onProviderEnabled(String p) { }
                public void onProviderDisabled(String p) { }
            };
            lm.requestSingleUpdate(provider, ll, c.getMainLooper());
            h.postDelayed(() -> {
                try { lm.removeUpdates(ll); } catch (Exception ignored) { }
                cb.onLocation("");
            }, 6000);
        } catch (Exception e) {
            cb.onLocation("");
        }
    }

    private static String latLon(double lat, double lon) {
        return String.format(Locale.US, "%.2f,%.2f", lat, lon);
    }

    // ---- Connectivity panels (API 29+ quick panels) ----------------------------
    public static void openConnectivityPanel(Context c) {
        try {
            Intent i = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception ignored) { }
        }
    }
}
