package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * POST /api/FrogMobileHandheldWholesaleOrder/WholesaleCreate
 * OrderGuid ile idempotent olusturma / Nebim aktarimi.
 *
 * Basari (HTTP 200): success=true, data.isNebimSend=true, data.orderGuid dolu.
 * Hata (HTTP 400/500): success=false, message; ayni OrderGuid ile tekrar denenebilir.
 */
public final class WholesaleOrderCreateApiClient {

    private static final String TAG = "WholesaleCreateApi";
    private static final String CREATE_PATH =
            "/api/FrogMobileHandheldWholesaleOrder/WholesaleCreate";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private WholesaleOrderCreateApiClient() {
    }

    public static final class CreateResult {
        public final String message;
        public final String description;
        public final String orderGuid;
        public final int headerId;
        public final boolean isNebimSend;

        public CreateResult(
                String message,
                String description,
                String orderGuid,
                int headerId,
                boolean isNebimSend) {
            this.message = message != null ? message : "";
            this.description = description != null ? description : "";
            this.orderGuid = orderGuid != null ? orderGuid : "";
            this.headerId = headerId;
            this.isNebimSend = isNebimSend;
        }
    }

    public interface Callback {
        void onSuccess(CreateResult result);

        void onError(String message);
    }

    public static void create(String serverUrl, String token, JSONObject requestJson, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return;
        }
        if (requestJson == null) {
            mainHandler.post(() -> callback.onError("Gonderim verisi eksik."));
            return;
        }

        Request request;
        try {
            String url = buildUrl(serverUrl);
            String bodyText = requestJson.toString();
            RequestBody body = RequestBody.create(bodyText, JSON_MEDIA_TYPE);
            request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            Log.d(TAG, "WholesaleCreate POST " + url);
            Log.d(TAG, "WholesaleCreate body=" + bodyText);
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return;
        }

        ApiHttpClient.get().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "WholesaleCreate network fail", e);
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

                Log.d(TAG, "WholesaleCreate HTTP " + response.code()
                        + " body=" + truncate(bodyString, 1500));

                if (response.code() == 401 || response.code() == 403) {
                    mainHandler.post(() -> callback.onError("Oturum gecersiz. Tekrar giris yapin."));
                    return;
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

    private static String buildUrl(String serverUrl) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + CREATE_PATH;
    }

    /**
     * Basari: HTTP 2xx + success=true + data + isNebimSend=true.
     * Aksi halde message ile hata (400 validasyon/Nebim, 500 sunucu).
     */
    private static ParsedResponse parseResponse(int httpCode, String body) {
        ParsedResponse parsed = new ParsedResponse();
        boolean httpOk = httpCode >= 200 && httpCode < 300;

        if (TextUtils.isEmpty(body)) {
            parsed.success = false;
            parsed.message = httpOk
                    ? "Sunucudan bos yanit alindi."
                    : defaultHttpError(httpCode);
            return parsed;
        }

        try {
            JSONObject json = new JSONObject(body);
            String message = firstNonEmpty(
                    json.optString("message", ""),
                    json.optString("Message", ""));

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                data = json.optJSONObject("Data");
            }

            boolean flagSuccess = json.optBoolean("success",
                    json.optBoolean("Success", false));

            if (!httpOk || !flagSuccess || data == null) {
                parsed.success = false;
                if (!TextUtils.isEmpty(message)) {
                    parsed.message = message;
                } else if (data == null && json.has("data")) {
                    // 400 schema: data string olabilir
                    String dataStr = json.optString("data", "");
                    parsed.message = !TextUtils.isEmpty(dataStr)
                            ? dataStr
                            : defaultHttpError(httpCode);
                } else {
                    parsed.message = defaultHttpError(httpCode);
                }
                return parsed;
            }

            boolean isNebimSend = data.optBoolean("isNebimSend",
                    data.optBoolean("IsNebimSend", false));
            String orderGuid = firstNonEmpty(
                    data.optString("orderGuid", ""),
                    data.optString("OrderGuid", ""));
            String description = firstNonEmpty(
                    data.optString("description", ""),
                    data.optString("Description", ""));
            int headerId = data.has("headerId")
                    ? data.optInt("headerId", 0)
                    : data.optInt("HeaderId", 0);

            if (!isNebimSend) {
                parsed.success = false;
                parsed.message = !TextUtils.isEmpty(message)
                        ? message
                        : "Siparis Nebim'e gonderilemedi.";
                return parsed;
            }

            parsed.success = true;
            parsed.result = new CreateResult(message, description, orderGuid, headerId, true);
            parsed.message = message;
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = httpOk
                    ? "Sunucu yaniti anlasilamadi."
                    : defaultHttpError(httpCode);
        }

        return parsed;
    }

    private static String defaultHttpError(int httpCode) {
        if (httpCode == 400) {
            return "Siparis gonderilemedi (HTTP 400).";
        }
        if (httpCode >= 500) {
            return "Sunucu hatasi (" + httpCode + ")";
        }
        return "Siparis gonderilemedi (HTTP " + httpCode + ").";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private static String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) {
            return a.trim();
        }
        if (!TextUtils.isEmpty(b)) {
            return b.trim();
        }
        return "";
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        CreateResult result;
    }
}
