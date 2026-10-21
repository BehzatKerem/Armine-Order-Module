package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class WarehouseApiClient {

    private static final String GET_WAREHOUSES_PATH = "/api/FrogMobileHandheldWarehouse/GetWarehouses";
    private static final String SEARCH_WAREHOUSE_PATH = "/api/FrogMobileHandheldWarehouse/SearchWarehouse";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private WarehouseApiClient() {
    }

    public interface WarehousesCallback {
        void onSuccess(WarehousesResult result);

        void onError(String message);
    }

    public interface WarehouseSearchCallback {
        void onSuccess(List<WarehouseItem> warehouses);

        void onError(String message);
    }

    public static void getWarehouses(String serverUrl, String token, String databaseName,
                                     WarehousesCallback callback) {
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
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Istek olusturulamadi."));
            return;
        }

        Request request;
        try {
            String url = buildUrl(serverUrl);
            RequestBody body = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
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

    /**
     * Bloklu olmayan depolari aciklamaya gore arar.
     * Donen kayitlar: warehouseCode, warehouseDescription, officeCode.
     * Cagiran, yeni karakter geldiginde onceki {@link Call#cancel()} ile iptal edebilir.
     */
    public static Call searchWarehouses(String serverUrl, String token, String databaseName,
                                        String search, WarehouseSearchCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return null;
        }
        if (TextUtils.isEmpty(databaseName)) {
            mainHandler.post(() -> callback.onError("Varsayilan veritabani bilgisi eksik."));
            return null;
        }

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("databaseName", databaseName);
            requestJson.put("search", search != null ? search : "");
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Istek olusturulamadi."));
            return null;
        }

        Request request;
        try {
            String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
            if (TextUtils.isEmpty(base)) {
                throw new IllegalArgumentException("Empty server url");
            }
            RequestBody body = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            request = new Request.Builder()
                    .url(base + SEARCH_WAREHOUSE_PATH)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return null;
        }

        Call call = ApiHttpClient.get().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException e) {
                if (failedCall.isCanceled()) {
                    return;
                }
                mainHandler.post(() -> callback.onError(
                        "Sunucuya baglanilamadi. Internet baglantinizi ve sunucu adresini kontrol edin."));
            }

            @Override
            public void onResponse(Call responseCall, Response response) {
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

                SearchParsed parsed = parseSearchResponse(response.code(), bodyString);
                mainHandler.post(() -> {
                    if (parsed.success && parsed.warehouses != null) {
                        callback.onSuccess(parsed.warehouses);
                    } else {
                        callback.onError(parsed.message);
                    }
                });
            }
        });
        return call;
    }

    private static String buildUrl(String serverUrl) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + GET_WAREHOUSES_PATH;
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
            parsed.success = json.optBoolean("success", httpCode >= 200 && httpCode < 300);
            parsed.message = json.optString("message", "");

            if (!parsed.success) {
                if (TextUtils.isEmpty(parsed.message)) {
                    parsed.message = "Depolar alinamadi.";
                }
                return parsed;
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                // Bazi yanitlar data sarmalayicisi olmadan donebilir.
                data = json;
            }

            String databaseName = data.optString("databaseName", "");
            String defaultOfficeCode = data.optString("defaultOfficeCode", "");
            String defaultWarehouseCode = data.optString("defaultWarehouseCode", "");
            String defaultWarehouseDescription = data.optString("defaultWarehouseDescription", "");

            WarehouseItem defaultWarehouse = parseWarehouse(data.optJSONObject("defaultWarehouse"));
            List<WarehouseItem> warehouses = parseWarehouseList(data.optJSONArray("warehouses"));

            if (warehouses.isEmpty() && defaultWarehouse != null) {
                warehouses.add(defaultWarehouse);
            } else if (defaultWarehouse != null) {
                warehouses = ensureDefaultFlag(warehouses, defaultWarehouse);
            }

            Collections.sort(warehouses, WarehouseItem.displayComparator());
            parsed.result = new WarehousesResult(databaseName, defaultOfficeCode, defaultWarehouseCode,
                    defaultWarehouseDescription, defaultWarehouse, warehouses);
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Depolar alinamadi.";
        }

        return parsed;
    }

    private static List<WarehouseItem> parseWarehouseList(JSONArray array) {
        List<WarehouseItem> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            WarehouseItem warehouse = parseWarehouse(item);
            if (warehouse != null) {
                list.add(warehouse);
            }
        }
        return list;
    }

    /**
     * Bir depo kaydini parse eder. Her kayit TEK bir officeCode tasir
     * (depo -> ofis iliskisi tekildir); ayni officeCode farkli kayitlarda
     * tekrarlanarak ofis -> cok depo iliskisi dogal olarak korunur.
     */
    private static WarehouseItem parseWarehouse(JSONObject json) {
        if (json == null) {
            return null;
        }

        String id = json.has("id") ? json.optString("id", "") : "";
        String userId = json.optString("userId", "");
        String databaseName = json.optString("databaseName", "");
        String officeCode = json.optString("officeCode", "");
        String warehouseCode = json.optString("warehouseCode", "");
        String warehouseDescription = json.optString("warehouseDescription", "");
        String storeCode = json.optString("storeCode", "");
        boolean isDefault = json.optBoolean("isDefault", false);
        String userName = json.optString("userName", "");

        if (TextUtils.isEmpty(officeCode) && TextUtils.isEmpty(warehouseCode)
                && TextUtils.isEmpty(storeCode)) {
            return null;
        }

        return new WarehouseItem(id, userId, databaseName, officeCode, warehouseCode,
                warehouseDescription, storeCode, isDefault, userName);
    }

    private static List<WarehouseItem> ensureDefaultFlag(List<WarehouseItem> warehouses,
                                                          WarehouseItem defaultWarehouse) {
        List<WarehouseItem> updated = new ArrayList<>(warehouses.size());
        for (WarehouseItem item : warehouses) {
            boolean matchesDefault = !TextUtils.isEmpty(defaultWarehouse.officeCode)
                    && !TextUtils.isEmpty(defaultWarehouse.warehouseCode)
                    && defaultWarehouse.officeCode.equals(item.officeCode)
                    && defaultWarehouse.warehouseCode.equals(item.warehouseCode);
            if (matchesDefault && !item.isDefault) {
                updated.add(new WarehouseItem(item.id, item.userId, item.databaseName,
                        item.officeCode, item.warehouseCode, item.warehouseDescription,
                        item.storeCode, true, item.userName));
            } else {
                updated.add(item);
            }
        }
        return updated;
    }

    private static SearchParsed parseSearchResponse(int httpCode, String body) {
        SearchParsed parsed = new SearchParsed();

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
                    parsed.message = "Depolar aranamadi.";
                }
                return parsed;
            }

            parsed.warehouses = parseWarehouseList(extractSearchArray(json));
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Depolar aranamadi.";
        }
        if (parsed.success && parsed.warehouses == null) {
            parsed.warehouses = new ArrayList<>();
        }
        return parsed;
    }

    private static JSONArray extractSearchArray(JSONObject json) {
        Object data = json.opt("data");
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        if (data instanceof JSONObject) {
            JSONObject dataObject = (JSONObject) data;
            JSONArray warehouses = dataObject.optJSONArray("warehouses");
            if (warehouses != null) {
                return warehouses;
            }
            JSONArray items = dataObject.optJSONArray("items");
            if (items != null) {
                return items;
            }
        }
        JSONArray root = json.optJSONArray("warehouses");
        return root != null ? root : new JSONArray();
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        WarehousesResult result;
    }

    private static class SearchParsed {
        boolean success;
        String message = "";
        List<WarehouseItem> warehouses;
    }
}
