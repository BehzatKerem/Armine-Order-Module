package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GET /api/FrogMobileRFIDHandheldCounting/{id}
 * POST /api/FrogMobileRFIDHandheldCounting/Save
 * Authorization: Bearer {token}
 */
public final class RfidHandheldCountingApiClient {

    private static final String PATH_PREFIX = "/api/FrogMobileRFIDHandheldCounting/";
    private static final String SAVE_PATH = "/api/FrogMobileRFIDHandheldCounting/Save";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private RfidHandheldCountingApiClient() {
    }

    public interface CallbackResult {
        void onSuccess(RfidHandheldCounting counting);

        void onError(String message);
    }

    public interface SendCallback {
        void onSuccess();

        void onError(String message);
    }

    public static void fetchByCode(String serverUrl, String token, String countingCode,
                                   CallbackResult callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return;
        }
        if (TextUtils.isEmpty(countingCode)) {
            mainHandler.post(() -> callback.onError("Sayim kodu gerekli."));
            return;
        }

        String trimmedCode = countingCode.trim();
        Request request;
        try {
            String url = buildUrl(serverUrl, trimmedCode);
            request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + token)
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

                if (response.code() == 401 || response.code() == 403) {
                    mainHandler.post(() -> callback.onError("Oturum gecersiz. Tekrar giris yapin."));
                    return;
                }

                ParsedResponse parsed = parseResponse(response.code(), bodyString, trimmedCode);
                mainHandler.post(() -> {
                    if (parsed.success && parsed.counting != null) {
                        callback.onSuccess(parsed.counting);
                    } else {
                        callback.onError(parsed.message);
                    }
                });
            }
        });
    }

    public static void sendChunk(String serverUrl, String token, String countingCode,
                                 JSONObject chunkJson, SendCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return;
        }
        if (TextUtils.isEmpty(countingCode) || chunkJson == null) {
            mainHandler.post(() -> callback.onError("Gonderim verisi eksik."));
            return;
        }

        String trimmedCode = countingCode.trim();
        Request request;
        try {
            String url = buildSaveUrl(serverUrl);
            RequestBody body = RequestBody.create(chunkJson.toString(), JSON_MEDIA_TYPE);
            request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + token)
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

                if (response.code() == 401 || response.code() == 403) {
                    mainHandler.post(() -> callback.onError("Oturum gecersiz. Tekrar giris yapin."));
                    return;
                }

                String error = parseSendResponse(response.code(), bodyString);
                mainHandler.post(() -> {
                    if (error == null) {
                        callback.onSuccess();
                    } else {
                        callback.onError(error);
                    }
                });
            }
        });
    }

    private static String buildUrl(String serverUrl, String countingCode) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + PATH_PREFIX + countingCode;
    }

    private static String buildSaveUrl(String serverUrl) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + SAVE_PATH;
    }

    private static ParsedResponse parseResponse(int httpCode, String body, String countingCode) {
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
            parsed.success = json.optBoolean("success", httpCode >= 200 && httpCode < 300);
            parsed.message = json.optString("message", "");

            if (!parsed.success) {
                if (TextUtils.isEmpty(parsed.message)) {
                    parsed.message = "Sayim detaylari alinamadi.";
                }
                return parsed;
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                parsed.success = false;
                parsed.message = TextUtils.isEmpty(parsed.message)
                        ? "Sayim detayi bulunamadi."
                        : parsed.message;
                return parsed;
            }

            parsed.counting = RfidHandheldCounting.fromApiData(data, countingCode);
            if (TextUtils.isEmpty(parsed.counting.countingCode)) {
                parsed.counting.countingCode = countingCode;
            }
            if (parsed.counting.id <= 0) {
                try {
                    parsed.counting.id = Integer.parseInt(countingCode);
                } catch (NumberFormatException ignored) {
                    // kod sayisal olmayabilir
                }
            }
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Sayim detaylari alinamadi.";
        }

        return parsed;
    }

    /** Basariliysa null, degilse hata mesaji. */
    private static String parseSendResponse(int httpCode, String body) {
        boolean httpOk = httpCode >= 200 && httpCode < 300;
        if (TextUtils.isEmpty(body)) {
            return httpOk ? null : "Sunucu hatasi (" + httpCode + ")";
        }
        try {
            JSONObject json = new JSONObject(body);
            boolean success = json.optBoolean("success", httpOk);
            String message = json.optString("message", "");
            if (success) {
                return null;
            }
            return TextUtils.isEmpty(message) ? "Parca gonderilemedi." : message;
        } catch (JSONException e) {
            return httpOk ? null : "Sunucu yaniti anlasilamadi.";
        }
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        RfidHandheldCounting counting;
    }
}
