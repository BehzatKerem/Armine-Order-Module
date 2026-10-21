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
 * POST /api/FrogMobileHandheldWarehouse/CreateWarehouseTransfer
 * TransferGuid ile tekrar gonderim ayni islemi ayirt eder.
 *
 * Basari (HTTP 200): success=true ve data.isNebimSend veya data.alreadySent.
 * Hata (HTTP 400/500): success=false, message; ayni TransferGuid ile tekrar denenebilir.
 */
public final class WarehouseTransferCreateApiClient {

    private static final String TAG = "WarehouseTransferApi";
    private static final String CREATE_PATH =
            "/api/FrogMobileHandheldWarehouse/CreateWarehouseTransfer";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private WarehouseTransferCreateApiClient() {
    }

    public static final class CreateResult {
        public final String message;
        public final String transferGuid;
        public final int headerId;
        public final boolean isNebimSend;
        public final boolean alreadySent;

        public CreateResult(String message, String transferGuid, int headerId,
                            boolean isNebimSend, boolean alreadySent) {
            this.message = message != null ? message : "";
            this.transferGuid = transferGuid != null ? transferGuid : "";
            this.headerId = headerId;
            this.isNebimSend = isNebimSend;
            this.alreadySent = alreadySent;
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
            Log.d(TAG, "CreateWarehouseTransfer POST " + url);
            Log.d(TAG, "CreateWarehouseTransfer body=" + bodyText);
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return;
        }

        ApiHttpClient.get().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "CreateWarehouseTransfer network fail", e);
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

                Log.d(TAG, "CreateWarehouseTransfer HTTP " + response.code()
                        + " body=" + truncate(bodyString, 1500));

                if (response.code() == 401 || response.code() == 403) {
                    mainHandler.post(() -> callback.onError(
                            "Bu islem yalnizca el terminali oturumu icindir. Tekrar giris yapin."));
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
                } else if (data == null && (json.has("data") || json.has("Data"))) {
                    String dataStr = firstNonEmpty(
                            json.optString("data", ""),
                            json.optString("Data", ""));
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
            boolean alreadySent = data.optBoolean("alreadySent",
                    data.optBoolean("AlreadySent", false));
            String transferGuid = firstNonEmpty(
                    data.optString("transferGuid", ""),
                    data.optString("TransferGuid", ""));
            int headerId = data.has("headerId")
                    ? data.optInt("headerId", 0)
                    : data.optInt("HeaderId", 0);

            if (!isNebimSend && !alreadySent) {
                parsed.success = false;
                parsed.message = !TextUtils.isEmpty(message)
                        ? message
                        : "Depo transferi Nebim'e gonderilemedi.";
                return parsed;
            }

            parsed.success = true;
            parsed.result = new CreateResult(message, transferGuid, headerId, isNebimSend, alreadySent);
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
            return "Depo transferi gonderilemedi (HTTP 400).";
        }
        if (httpCode >= 500) {
            return "Sunucu hatasi (" + httpCode + ")";
        }
        return "Depo transferi gonderilemedi (HTTP " + httpCode + ").";
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
