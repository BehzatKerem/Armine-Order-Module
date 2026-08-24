package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * POST /api/FrogMobileHandheldWholesaleOrder/WholeSaleSummaryCheckInfo
 * Barkod listesi ile urun / stok enrich. JWT (FrogMobile) gerekir.
 */
public final class WholesaleOrderProductApiClient {

    private static final String TAG = "WholesaleProductApi";
    private static final String ENRICH_PATH =
            "/api/FrogMobileHandheldWholesaleOrder/WholeSaleSummaryCheckInfo";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private WholesaleOrderProductApiClient() {
    }

    public interface Callback {
        void onSuccess(List<WholesaleOrderProductInfo> products);

        void onError(String message);
    }

    public static void fetchProducts(
            String serverUrl,
            String token,
            Collection<String> barcodes,
            Map<String, Integer> qtyByBarcode,
            String officeCode,
            String warehouseCode,
            String userName,
            String databaseName,
            Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (barcodes == null || barcodes.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }
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
            requestJson.put("OfficeCode", officeCode != null ? officeCode : "");
            requestJson.put("WareHouseCode", warehouseCode != null ? warehouseCode : "");
            requestJson.put("UserName", userName != null ? userName : "");
            requestJson.put("Database", databaseName);
            JSONArray barcodeArray = new JSONArray();
            for (String barcode : barcodes) {
                if (!TextUtils.isEmpty(barcode)) {
                    barcodeArray.put(barcode);
                }
            }
            requestJson.put("Barcodes", barcodeArray);
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
            Log.d(TAG, "WholeSaleSummaryCheckInfo: " + url);
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Sunucu adresi gecersiz."));
            return;
        }

        ApiHttpClient.get().newCall(request).enqueue(new okhttp3.Callback() {
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

                ParsedResponse parsed = parseResponse(response.code(), bodyString, qtyByBarcode);
                mainHandler.post(() -> {
                    if (parsed.success && parsed.products != null) {
                        callback.onSuccess(parsed.products);
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
        return base + ENRICH_PATH;
    }

    private static ParsedResponse parseResponse(
            int httpCode, String body, Map<String, Integer> qtyByBarcode) {
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
            parsed.success = json.optBoolean("success",
                    json.optBoolean("Success", httpCode >= 200 && httpCode < 300));
            parsed.message = firstNonEmpty(
                    json.optString("message", ""),
                    json.optString("Message", ""));

            if (!parsed.success) {
                if (TextUtils.isEmpty(parsed.message)) {
                    parsed.message = "Urun bilgisi alinamadi.";
                }
                return parsed;
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                data = json.optJSONObject("Data");
            }
            if (data == null) {
                data = json;
            }

            JSONArray items = data.optJSONArray("items");
            if (items == null) {
                items = data.optJSONArray("Items");
            }

            Map<String, JSONObject> byBarcode = new LinkedHashMap<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String barcode = firstNonEmpty(
                            item.optString("Barcode", ""),
                            item.optString("barcode", ""));
                    if (!TextUtils.isEmpty(barcode) && !byBarcode.containsKey(barcode)) {
                        byBarcode.put(barcode, item);
                    }
                }
            }

            List<WholesaleOrderProductInfo> products = new ArrayList<>();
            if (qtyByBarcode != null) {
                for (Map.Entry<String, Integer> entry : qtyByBarcode.entrySet()) {
                    String barcode = entry.getKey();
                    int qty = Math.max(1, entry.getValue());
                    JSONObject item = byBarcode.get(barcode);
                    products.add(fromApiItem(barcode, qty, item));
                }
            }
            parsed.products = products;
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Urun bilgisi alinamadi.";
        }
        return parsed;
    }

    private static WholesaleOrderProductInfo fromApiItem(String barcode, int qty, JSONObject item) {
        if (item == null) {
            return new WholesaleOrderProductInfo(barcode, "", "", "", "", null, qty, false);
        }
        String productDescription = firstNonEmpty(
                item.optString("ProductDescription", ""),
                item.optString("productDescription", ""));
        String itemCode = firstNonEmpty(
                item.optString("ItemCode", ""),
                item.optString("itemCode", ""));
        String colorCode = firstNonEmpty(
                item.optString("ColorCode", ""),
                item.optString("colorCode", ""));
        String colorDescription = firstNonEmpty(
                item.optString("ColorDescription", ""),
                item.optString("colorDescription", ""));
        Integer stock = parseStock(item);
        boolean found = !TextUtils.isEmpty(productDescription)
                || !TextUtils.isEmpty(itemCode)
                || stock != null;
        return new WholesaleOrderProductInfo(
                barcode, productDescription, itemCode, colorCode, colorDescription, stock, qty, found);
    }

    private static Integer parseStock(JSONObject item) {
        if (item == null) {
            return null;
        }
        if (item.has("Stock") && !item.isNull("Stock")) {
            return (int) Math.round(item.optDouble("Stock", 0d));
        }
        if (item.has("stock") && !item.isNull("stock")) {
            return (int) Math.round(item.optDouble("stock", 0d));
        }
        return null;
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

    /** EPC listesinden barkod -> adet haritasi. */
    public static Map<String, Integer> groupEpcsByBarcode(Collection<String> epcs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (epcs == null) {
            return counts;
        }
        for (String epc : epcs) {
            String barcode = EpcBarcodeGrouping.extractBarcodeForEnrich(epc);
            if (barcode == null) {
                continue;
            }
            Integer current = counts.get(barcode);
            counts.put(barcode, current == null ? 1 : current + 1);
        }
        return counts;
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        List<WholesaleOrderProductInfo> products;
    }
}
