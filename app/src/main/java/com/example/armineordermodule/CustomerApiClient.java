package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class CustomerApiClient {

    private static final String SEARCH_PATH = "/api/FrogMobileHandheldCustomer/Search";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_PAGE_SIZE = 200;

    private CustomerApiClient() {
    }

    public interface SearchCallback {
        void onSuccess(CustomersResult result);

        void onError(String message);
    }

    public static void search(String serverUrl, String token, String search,
                              int skip, int pageSize, SearchCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(token)) {
            mainHandler.post(() -> callback.onError("Oturum bulunamadi. Tekrar giris yapin."));
            return;
        }

        int safePageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        int safeSkip = Math.max(skip, 0);

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("search", search != null ? search : "");
            requestJson.put("skip", safeSkip);
            requestJson.put("pageSize", safePageSize);
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

    private static String buildUrl(String serverUrl) {
        String base = ServerPreferences.normalizeServerForAndroidClient(serverUrl);
        if (TextUtils.isEmpty(base)) {
            throw new IllegalArgumentException("Empty server url");
        }
        return base + SEARCH_PATH;
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
                    parsed.message = "Musteriler alinamadi.";
                }
                return parsed;
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                data = json;
            }

            String databaseName = data.optString("databaseName", "");
            int totalCount = data.optInt("totalCount", 0);
            List<CustomerItem> customers = parseCustomerList(data.optJSONArray("customers"));
            if (totalCount <= 0) {
                totalCount = customers.size();
            }

            parsed.result = new CustomersResult(databaseName, totalCount, customers);
        } catch (JSONException e) {
            parsed.success = false;
            parsed.message = "Sunucu yaniti anlasilamadi.";
        }

        if (!parsed.success && TextUtils.isEmpty(parsed.message)) {
            parsed.message = "Musteriler alinamadi.";
        }

        return parsed;
    }

    private static List<CustomerItem> parseCustomerList(JSONArray array) {
        List<CustomerItem> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            CustomerItem item = parseCustomer(array.optJSONObject(i));
            if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    private static CustomerItem parseCustomer(JSONObject json) {
        if (json == null) {
            return null;
        }

        String currAccCode = json.optString("currAccCode", "");
        String currAccDescription = json.optString("currAccDescription", "");
        String districtDescription = json.optString("districtDescription", "");
        String cityDescription = json.optString("cityDescription", "");
        String identityNum = json.optString("identityNum", "");
        String taxNumber = json.optString("taxNumber", "");

        if (TextUtils.isEmpty(currAccCode) && TextUtils.isEmpty(currAccDescription)) {
            return null;
        }

        return new CustomerItem(currAccCode, currAccDescription, districtDescription,
                cityDescription, identityNum, taxNumber);
    }

    private static class ParsedResponse {
        boolean success;
        String message = "";
        CustomersResult result;
    }
}
