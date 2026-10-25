package com.stark.jarvis;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/**
 * Car / headset mode: routes voice recognition and TTS through a connected
 * Bluetooth headset (SCO). Wired headsets don't need any explicit routing.
 */
public class HeadsetRouter {

    public enum Kind { NONE, BT_SCO, WIRED }

    /** What's attached right now. */
    public static Kind headsetKind(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return Kind.NONE;
        AudioDeviceInfo[] outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo d : outs) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return Kind.BT_SCO;
            if (d.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || d.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || d.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) return Kind.WIRED;
        }
        return Kind.NONE;
    }

    public static boolean bluetoothConnected(Context c) {
        try {
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            return ad != null && ad.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)
                    == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
        } catch (SecurityException se) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Call right before recognition or TTS when car mode is on: open the SCO
     * link so the headset mic/speaker are used.
     */
    public static void routeForVoice(Context c) {
        if (!Prefs.get(c).bool(Prefs.CAR_MODE, false)) return;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || headsetKind(c) != Kind.BT_SCO && !bluetoothConnected(c)) return;
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31) {
                AudioDeviceInfo sco = null;
                for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) { sco = d; break; }
                }
                if (sco != null) am.setCommunicationDevice(sco);
            } else {
                am.startBluetoothSco();
                am.setBluetoothScoOn(true);
            }
        } catch (Exception ignored) { }
    }

    public static void release(Context c) {
        if (!Prefs.get(c).bool(Prefs.CAR_MODE, false)) return;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice();
            } else {
                am.stopBluetoothSco();
                am.setBluetoothScoOn(false);
            }
        } catch (Exception ignored) { }
        try { am.setMode(AudioManager.MODE_NORMAL); } catch (Exception ignored) { }
    }
}
