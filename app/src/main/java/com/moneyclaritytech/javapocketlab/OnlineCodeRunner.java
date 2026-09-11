package com.moneyclaritytech.pocketforge;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Executes selected languages through PocketForge's explicit online-runner mode. */
public final class OnlineCodeRunner {
    private static final String ENDPOINT = "https://emkc.org/api/v2/piston/execute";
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_RESPONSE_BYTES = 120000;

    public interface Callback {
        void complete(boolean success, String output, int exitCode);
    }

    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static boolean supports(String languageId) {
        return "python".equals(languageId) || "cpp".equals(languageId) || "javascript".equals(languageId);
    }

    public void run(String languageId, String source, String stdin, Callback callback) {
        worker.submit(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("language", pistonLanguage(languageId));
                request.put("version", pistonVersion(languageId));
                request.put("stdin", stdin == null ? "" : stdin);
                request.put("compile_timeout", 10000);
                request.put("run_timeout", 5000);
                request.put("compile_cpu_time", 10000);
                request.put("run_cpu_time", 5000);

                JSONObject file = new JSONObject();
                file.put("name", fileName(languageId));
                file.put("content", source == null ? "" : source);
                JSONArray files = new JSONArray();
                files.put(file);
                request.put("files", files);

                HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "PocketForge/0.1");

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
                int status = connection.getResponseCode();
                InputStream response = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                String raw = readLimited(response);
                connection.disconnect();
                if (status < 200 || status >= 300) {
                    finish(callback, false, "Online runner returned HTTP " + status + "\n" + raw, 125);
                    return;
                }

                JSONObject result = new JSONObject(raw);
                JSONObject compile = result.optJSONObject("compile");
                JSONObject run = result.optJSONObject("run");
                StringBuilder text = new StringBuilder();
                if (compile != null) appendPhase(text, "Compile", compile);
                if (run != null) appendPhase(text, "Run", run);
                if (text.length() == 0) text.append(result.optString("message", raw));
                int exitCode = run == null ? 1 : run.optInt("code", 1);
                finish(callback, exitCode == 0, text.toString(), exitCode);
            } catch (Exception e) {
                finish(callback, false, "Could not contact the online runner: " + safeMessage(e), 125);
            }
        });
    }

    private static void appendPhase(StringBuilder out, String label, JSONObject phase) {
        String text = phase.optString("output", "");
        if (text.isEmpty()) text = phase.optString("stdout", "") + phase.optString("stderr", "");
        if (!text.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            if (phase.optInt("code", 0) != 0) out.append(label).append(":\n");
            out.append(text);
        }
    }

    private static String pistonLanguage(String id) {
        return "cpp".equals(id) ? "c++" : "javascript".equals(id) ? "javascript" : "python";
    }

    private static String pistonVersion(String id) {
        return "cpp".equals(id) ? "10.2.0" : "javascript".equals(id) ? "18.15.0" : "3.10.0";
    }

    private static String fileName(String id) {
        return "cpp".equals(id) ? "main.cpp" : "javascript".equals(id) ? "main.js" : "main.py";
    }

    private static String readLimited(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                int remaining = MAX_RESPONSE_BYTES - out.size();
                if (remaining <= 0) break;
                out.write(buffer, 0, Math.min(count, remaining));
            }
            String result = out.toString(StandardCharsets.UTF_8.name());
            return out.size() >= MAX_RESPONSE_BYTES ? result + "\n[response truncated]" : result;
        }
    }

    private void finish(Callback callback, boolean success, String output, int exitCode) {
        if (callback != null) main.post(() -> callback.complete(success, output == null ? "" : output, exitCode));
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
