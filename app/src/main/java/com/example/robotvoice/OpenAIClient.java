package com.example.robotvoice;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Direct OpenRouter client.
 *
 * This university-project build talks directly to OpenRouter, so no local
 * Node.js/Python backend or computer is required at runtime.
 *
 * WARNING: The API key is intentionally embedded because this is a demo/
 * university project. It can be extracted from a released APK.
 */
public final class OpenAIClient {
    public interface Callback {
        void onSuccess(String answer);
        void onError(String message);
    }

    private static final String OPENROUTER_URL =
            "https://openrouter.ai/api/v1/chat/completions";

    // Intentionally embedded per the project requirement.
    private static final String OPENROUTER_API_KEY =
            "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";

    // OpenRouter's free router automatically selects an available free model.
    private static final String MODEL = "openrouter/free";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    private OpenAIClient() {}

    public static void send(String ignoredBaseUrl, String text, Callback callback) {
        new Thread(() -> {
            try {
                String message = text == null ? "" : text.trim();
                if (message.isEmpty()) {
                    callback.onError("متن ورودی خالی است");
                    return;
                }

                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content",
                        "تو یک دستیار صوتی فارسی هستی. " +
                        "کوتاه، طبیعی و واضح به فارسی پاسخ بده. " +
                        "پاسخ باید برای تبدیل متن به گفتار مناسب باشد. " +
                        "از Markdown پیچیده، جدول و علامت‌های غیرضروری استفاده نکن.");

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", message);

                JSONArray messages = new JSONArray();
                messages.put(system);
                messages.put(user);

                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("messages", messages);
                body.put("temperature", 0.7);
                body.put("max_tokens", 500);

                Request request = new Request.Builder()
                        .url(OPENROUTER_URL)
                        .addHeader("Authorization", "Bearer " + OPENROUTER_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://github.com/")
                        .addHeader("X-Title", "Robot Voice Assistant - University Project")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                try (Response response = CLIENT.newCall(request).execute()) {
                    String raw = response.body() == null ? "" : response.body().string();

                    if (!response.isSuccessful()) {
                        callback.onError(parseError(raw, response.code()));
                        return;
                    }

                    JSONObject result = new JSONObject(raw);
                    JSONArray choices = result.optJSONArray("choices");

                    if (choices == null || choices.length() == 0) {
                        callback.onError("OpenRouter پاسخی برنگرداند");
                        return;
                    }

                    JSONObject choice = choices.optJSONObject(0);
                    JSONObject messageObject = choice == null
                            ? null : choice.optJSONObject("message");
                    String answer = messageObject == null
                            ? "" : messageObject.optString("content", "").trim();

                    if (answer.isEmpty()) {
                        callback.onError("پاسخ متنی از OpenRouter دریافت نشد");
                        return;
                    }

                    callback.onSuccess(answer);
                }
            } catch (Exception e) {
                String message = e.getMessage();
                callback.onError(message == null ? "خطا در ارتباط با OpenRouter" : message);
            }
        }, "OpenRouter-Request").start();
    }

    private static String parseError(String raw, int code) {
        try {
            JSONObject object = new JSONObject(raw);
            JSONObject error = object.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return "OpenRouter (" + code + "): " + message;
                }
            }
        } catch (Exception ignored) {
            // Keep raw response below.
        }

        if (raw == null || raw.trim().isEmpty()) {
            return "OpenRouter خطای HTTP " + code + " برگرداند";
        }

        return "OpenRouter (" + code + "): " + raw;
    }
}
