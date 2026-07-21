package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class LoginApiClient {

    private static final String LOGIN_PATH = "/api/FrogMobileHandheld/Login";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private LoginApiClient() {
    }

    public interface LoginCallback {
        void onSuccess(LoginResult result);

        void onError(String message);
    }

    public static void login(String serverUrl, String username, String password, LoginCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("userName", username);
            requestJson.put("password", password);
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Istek olusturulamadi."));
            return;
        }

        Request request;
        try {
            String url = buildLoginUrl(serverUrl);
            RequestBody body = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return;
        }

        ApiHttpClient.get().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(
                        "Sunucuya baglanilamadi. Internet baglantinizi ve sunucu adresini kontrol edin."));
            }

            @Override
            public void onResponse(Call call, Response response) {
                String bodyString;
                try {
                    bodyString = response.body() != null ? response.body().string() : "";
                } catch (IOException e) {
                    mainHandler.post(() -> callback.onError("Sunucu yaniti okunamadi."));
                    return;
                } finally {
                    response.close();
                }

                ParsedResponse parsed = parseResponse(response.code(), bodyString);
                mainHandler.post(() -> {
                    if (parsed.success && parsed.result != null) {
                        callback.onSuccess(parsed.result);
                    } else {
                        callback.onError(parsed.message);
                    }
                });
            }
        });
    }

    private static String buildLoginUrl(String serverUrl) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + LOGIN_PATH;
    }

    private static ParsedResponse parseResponse(int httpCode, String body) {
        ParsedResponse parsed = new ParsedResponse();

        if (TextUtils.isEmpty(body)) {
            parsed.success = false;
            parsed.message = (httpCode >= 200 && httpCode < 300)
                    ? "Sunucudan bos yanit alindi."
                    : "Sunucu hatasi (" + httpCode + ")";
            return parsed;
        }

        try {
            JSONObject json = new JSONObject(body);
            parsed.success = json.optBoolean("success", false);
            parsed.message = json.optString("message", "");

            if (parsed.success) {
                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    parsed.success = false;
                    parsed.message = TextUtils.isEmpty(parsed.message)
                            ? "Sunucu yaniti eksik."
                            : parsed.message;
                    return parsed;
                }

                String token = data.optString("token", "");
                int expiresInMinutes = data.optInt("expiresInMinutes", 0);
                String expiresAt = data.optString("expiresAt", "");
                String authSource = data.optString("authSource", "");

                JSONObject user = data.optJSONObject("user");
                String userId = user != null ? user.optString("id", "") : "";
                String userName = user != null ? user.optString("userName", "") : "";
                String email = user != null ? user.optString("email", "") : "";
                String defaultDatabase = user != null ? user.optString("defaultDatabase", "") : "";

                if (TextUtils.isEmpty(token)) {
                    parsed.success = false;
                    parsed.message = "Sunucu gecerli bir oturum anahtari dondurmedi.";
                    return parsed;
                }

                long expiresAtMillis = parseExpiresAt(expiresAt, expiresInMinutes);
                parsed.result = new LoginResult(token, expiresInMinutes, expiresAtMillis,
                        authSource, userId, userName, email, defaultDatabase);
            }
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Giris basarisiz. Bilgilerinizi kontrol edin.";
        }

        return parsed;
    }

    private static long parseExpiresAt(String isoDate, int fallbackMinutes) {
        if (!TextUtils.isEmpty(isoDate)) {
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ssXXX"
            };
            for (String pattern : patterns) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date = sdf.parse(isoDate);
                    if (date != null) {
                        return date.getTime();
                    }
                } catch (ParseException ignored) {
                    // Sonraki pattern denenir.
                }
            }
        }
        if (fallbackMinutes > 0) {
            return System.currentTimeMillis() + fallbackMinutes * 60_000L;
        }
        return 0L;
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        LoginResult result;
    }
}
