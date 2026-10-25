package com.stark.jarvis;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Notes live in the app's private storage. Nothing leaves the phone. */
public class NotesStore {

    private static File file(Context c) { return new File(c.getFilesDir(), "notes.txt"); }

    public static void add(Context c, String note) {
        String stamp = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.US).format(new Date());
        try (FileOutputStream out = new FileOutputStream(file(c), true)) {
            out.write(("[" + stamp + "] " + note + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public static String readAll(Context c) {
        try (FileInputStream in = new FileInputStream(file(c))) {
            byte[] b = new byte[(int) file(c).length()];
            int r = in.read(b);
            return r > 0 ? new String(b, 0, r, StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void clear(Context c) { file(c).delete(); }
}
