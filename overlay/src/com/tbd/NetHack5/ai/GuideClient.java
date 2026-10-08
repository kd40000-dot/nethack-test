package com.tbd.NetHack5.ai;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Single-shot read-only guidance request. Must run off Android's UI thread. */
final class GuideClient {
    static final String MODEL = "gpt-5-mini";
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";

    private static final String INSTRUCTIONS =
        "You are an expert NetHack 5.0 tactical guide coaching a complete beginner. " +
        "The player is using NetHack for Android and wants detailed, practical help. " +
        "Base your assessment ONLY on the attached screenshot, explicitly supplied status " +
        "and messages, and known public NetHack 5.0 rules. Do not pretend to see unseen rooms, " +
        "an inventory not supplied, monster identities obscured by tiles, or items not shown. " +
        "Treat status information as more reliable than an ambiguous screenshot. " +
        "First assess immediate threats: low HP, hunger, hostile adjacency, dangerous " +
        "status effects, escaping too-deep levels. Avoid reckless advice. " +
        "Provide (1) Situation: what is happening and how certain you are; " +
        "(2) Recommended next 3-7 actions IN PRIORITY ORDER, one at a time, " +
        "with exact NetHack keys/commands and why; (3) survival and inventory checks; " +
        "(4) where to explore next and signs of stairs or escape; " +
        "(5) beginner concepts useful RIGHT NOW; (6) what information you need " +
        "to give more precise help. If multiple key bindings/command systems are " +
        "possible, explain that and name the conventional NetHack command. " +
        "Distinguish facts from educated guesses. Warn about risks before any " +
        "irreversible move. Assume normal play, no wizard mode or spoilers of " +
        "unseen map information unless the user requests spoilers. " +
        "Do not claim to execute game commands. Keep the answer specific, " +
        "thorough, and easy for someone who has never played NetHack to follow.";

    private GuideClient() {}

    static String request(String key, String question, String stateText,
                          byte[] screenshotJpeg) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", MODEL);
        payload.put("instructions", INSTRUCTIONS);
        payload.put("max_output_tokens", 2700);
        payload.put("store", false);
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "input_text").put("text",
                "PLAYER QUESTION:\n" + question +
                "\n\nLIVE VISIBLE STATE (captured at the moment help was tapped):\n" +
                stateText +
                "\n\nThe attached image is the current visible game screen. " +
                "The dialog and AI toolbar are not part of the screenshot."));
        content.put(new JSONObject().put("type", "input_image").put(
                "image_url", "data:image/jpeg;base64," +
                Base64.encodeToString(screenshotJpeg, Base64.NO_WRAP))
                .put("detail", "high"));
        payload.put("input", new JSONArray().put(
                new JSONObject().put("role", "user").put("content", content)));

        HttpURLConnection connection = (HttpURLConnection)
                new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String json = readAll(stream);
            JSONObject parsed = new JSONObject(json);
            if (status < 200 || status >= 300) {
                JSONObject error = parsed.optJSONObject("error");
                String explanation = error == null
                        ? "HTTP " + status
                        : error.optString("message", "HTTP " + status);
                if (status == 401) explanation += "\nCheck your API key with the AI key button.";
                if (status == 429) explanation += "\nCheck API credits, billing or rate limits.";
                throw new IllegalStateException(explanation);
            }
            StringBuilder result = new StringBuilder();
            JSONArray output = parsed.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type"))) continue;
                    JSONArray parts = item.optJSONArray("content");
                    if (parts == null) continue;
                    for (int j = 0; j < parts.length(); j++) {
                        JSONObject part = parts.optJSONObject(j);
                        if (part != null && "output_text".equals(part.optString("type"))) {
                            if (result.length() > 0) result.append("\n\n");
                            result.append(part.optString("text"));
                        }
                    }
                }
            }
            if (result.length() == 0) {
                JSONObject incomplete = parsed.optJSONObject("incomplete_details");
                if (incomplete != null) throw new IllegalStateException(
                        "The answer was incomplete (" +
                        incomplete.optString("reason", "unknown") + "). Please retry.");
                throw new IllegalStateException("The model returned no readable advice. Please retry.");
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "{}";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
                if (out.size() > 4000000) throw new IllegalStateException("API response too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
