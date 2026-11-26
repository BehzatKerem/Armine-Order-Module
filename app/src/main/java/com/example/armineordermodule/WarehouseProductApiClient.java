package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * POST /api/FrogMobileHandheldWarehouse/GetProductByBarcode
 * Tek barkod icin urun bilgisi ve secilen depodaki stok. JWT (FrogMobile) gerekir.
 *
 * Barkod tanimli degilse sunucu yine 200 doner, found=false gelir: bu bir hata degildir,
 * {@link Callback#onSuccess} ile {@link WarehouseProductInfo#found} false olarak iletilir.
 */
public final class WarehouseProductApiClient {

    private static final String TAG = "WarehouseProductApi";
    private static final String PATH = "/api/FrogMobileHandheldWarehouse/GetProductByBarcode";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private WarehouseProductApiClient() {
    }

    /** Sonuclar ana thread'de doner. */
    public interface Callback {
        void onSuccess(WarehouseProductInfo product);

        void onError(String message);
    }

    public static void getProductByBarcode(String serverUrl,
                                           String token,
                                           String databaseName,
                                           String barcode,
                                           String officeCode,
                                           String warehouseCode,
                                           Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            mainHandler.post(() -> callback.onError("Varsayilan veritabani bilgisi eksik."));
            return;
        }

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("databaseName", databaseName);
            requestJson.put("barcode", barcode != null ? barcode : "");
            requestJson.put("officeCode", officeCode != null ? officeCode : "");
            requestJson.put("warehouseCode", warehouseCode != null ? warehouseCode : "");
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Istek olusturulamadi."));
            return;
        }

        Request request;
        try {
            String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
            if (TextUtils.isEmpty(base)) {
                throw new IllegalArgumentException("Empty server url");
            }
            request = new Request.Builder()
                    .url(base + PATH)
                    .post(RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return;
        }

        ApiHttpClient.get().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "GetProductByBarcode baglanti hatasi: " + barcode, e);
                mainHandler.post(() -> callback.onError(
                        "Sunucuya baglanilamadi. Internet baglantinizi kontrol edin."));
            }

            @Override
            public void onResponse(Call call, Response response) {
                String body;
                int code = response.code();
                try {
                    body = response.body() != null ? response.body().string() : "";
                } catch (IOException e) {
                    mainHandler.post(() -> callback.onError("Sunucu yaniti okunamadi."));
                    return;
                } finally {
                    response.close();
                }

                if (code == 401 || code == 403) {
                    mainHandler.post(() -> callback.onError("Oturum gecersiz. Tekrar giris yapin."));
                    return;
                }

                try {
                    WarehouseProductInfo product = parse(code, body, barcode);
                    mainHandler.post(() -> callback.onSuccess(product));
                } catch (ApiException e) {
                    Log.w(TAG, "GetProductByBarcode " + code + ": " + e.getMessage());
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    private static WarehouseProductInfo parse(int httpCode, String body, String barcode)
            throws ApiException {
        if (TextUtils.isEmpty(body)) {
            throw new ApiException(httpCode >= 200 && httpCode < 300
                    ? "Sunucudan bos yanit alindi."
                    : "Sunucu hatasi (" + httpCode + ")");
        }
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (JSONException e) {
            throw new ApiException("Sunucu yaniti anlasilamadi.");
        }

        boolean success = json.optBoolean("success", httpCode >= 200 && httpCode < 300);
        String message = json.optString("message", "").trim();
        if (!success || httpCode < 200 || httpCode >= 300) {
            throw new ApiException(!TextUtils.isEmpty(message)
                    ? message
                    : "Urun bilgisi alinamadi (" + httpCode + ").");
        }

        JSONObject data = json.optJSONObject("data");
        if (data == null) {
            throw new ApiException("Sunucu yanitinda urun bilgisi yok.");
        }

        JSONArray items = data.optJSONArray("items");
        JSONObject first = items != null && items.length() > 0 ? items.optJSONObject(0) : null;
        boolean found = data.optBoolean("found", first != null) && first != null;
        if (!found) {
            return new WarehouseProductInfo(barcode, false, "", "", "", "", 0d);
        }

        double total = data.has("totalAvailableInventoryQty1") && !data.isNull("totalAvailableInventoryQty1")
                ? data.optDouble("totalAvailableInventoryQty1", 0d)
                : sumItemQty(items);
        if (Double.isNaN(total)) {
            total = 0d;
        }
        return new WarehouseProductInfo(
                barcode,
                true,
                first.optString("productName", ""),
                first.optString("itemCode", ""),
                first.optString("colorCode", ""),
                first.optString("itemMeasure", ""),
                total);
    }

    private static double sumItemQty(JSONArray items) {
        double sum = 0d;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                sum += item.optDouble("availableInventoryQty1", 0d);
            }
        }
        return sum;
    }

    private static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
