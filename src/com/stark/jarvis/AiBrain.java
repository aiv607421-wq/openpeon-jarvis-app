package com.stark.jarvis;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Optional "brain": any OpenAI-compatible chat endpoint.
 * The key lives only in JARVIS settings, on this phone.
 */
public class AiBrain {

    public interface Listener { void onAnswer(String answer); }

    /** @return true if a round-trip was started. */
    public static boolean ask(final Context c, String question, final Listener cb) {
        final String key = Prefs.get(c).aiApiKey;
        final String base = Prefs.get(c).aiBaseUrl;
        final String model = Prefs.get(c).aiModel;
        if (key == null || key.trim().isEmpty() || base == null || base.trim().isEmpty()) return false;
        askWithKey(base.trim(), key.trim(), model == null || model.trim().isEmpty() ? "gpt-4o-mini" : model.trim(),
                question, cb);
        return true;
    }

    public static void askWithKey(final String baseUrl, final String apiKey, final String model,
                                  final String question, final Listener cb) {
        new Thread(() -> {
            String err = null; String answer = null;
            try {
                URL url = new URL(stripSuffix(baseUrl) + "/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");

                String system = "You are JARVIS, Tony Stark's AI butler: composed, dry British wit, address the user as 'sir'. "
                        + "Keep replies under 60 words — they will be spoken aloud on a phone.";
                JSONObject sys = new JSONObject().put("role", "system").put("content", system);
                JSONObject usr = new JSONObject().put("role", "user").put("content", question);
                JSONObject body = new JSONObject()
                        .put("model", model)
                        .put("messages", new JSONArray().put(sys).put(usr))
                        .put("max_tokens", 160);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code >= 200 && code < 300) {
                    JSONObject j = new JSONObject(sb.toString());
                    answer = j.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim();
                } else {
                    err = "Brain end-point answered HTTP " + code + ".";
                }
            } catch (Exception e) {
                err = "The brain is unreachable, sir.";
            }
            final String a = answer, e2 = err;
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> cb.onAnswer(a != null ? a : e2));
        }, "jarvis-ai").start();
    }

    private static String stripSuffix(String base) {
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/v1")) base = base.substring(0, base.length() - 3);
        if (base.endsWith("/chat/completions")) return base.substring(0, base.length() - "/chat/completions".length());
        return base;
    }
}
