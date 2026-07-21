package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class WarehousePreferences {

    private static final String PREFS_NAME = "warehouse_prefs";
    private static final String KEY_DATABASE_NAME = "database_name";
    private static final String KEY_DEFAULT_OFFICE_CODE = "default_office_code";
    private static final String KEY_DEFAULT_WAREHOUSE_CODE = "default_warehouse_code";
    private static final String KEY_DEFAULT_WAREHOUSE_DESCRIPTION = "default_warehouse_description";
    private static final String KEY_WAREHOUSES_JSON = "warehouses_json";
    private static final String KEY_DEFAULT_WAREHOUSE_JSON = "default_warehouse_json";

    private WarehousePreferences() {
    }

    public static void save(Context context, WarehousesResult result) {
        SharedPreferences.Editor editor = prefs(context).edit();
        try {
            editor.putString(KEY_DATABASE_NAME, result.databaseName);
            editor.putString(KEY_DEFAULT_OFFICE_CODE, result.defaultOfficeCode);
            editor.putString(KEY_DEFAULT_WAREHOUSE_CODE, result.defaultWarehouseCode);
            editor.putString(KEY_DEFAULT_WAREHOUSE_DESCRIPTION, result.defaultWarehouseDescription);
            editor.putString(KEY_WAREHOUSES_JSON, toJsonArray(result.warehouses).toString());
            if (result.defaultWarehouse != null) {
                editor.putString(KEY_DEFAULT_WAREHOUSE_JSON, toJsonObject(result.defaultWarehouse).toString());
            } else {
                editor.remove(KEY_DEFAULT_WAREHOUSE_JSON);
            }
            editor.apply();
        } catch (JSONException e) {
            // Kayit basarisiz olursa sessizce gecme; bos yazma.
        }
    }

    public static WarehousesResult get(Context context) {
        SharedPreferences prefs = prefs(context);
        String databaseName = prefs.getString(KEY_DATABASE_NAME, "");
        String defaultOfficeCode = prefs.getString(KEY_DEFAULT_OFFICE_CODE, "");
        String defaultWarehouseCode = prefs.getString(KEY_DEFAULT_WAREHOUSE_CODE, "");
        String defaultWarehouseDescription = prefs.getString(KEY_DEFAULT_WAREHOUSE_DESCRIPTION, "");
        String warehousesJson = prefs.getString(KEY_WAREHOUSES_JSON, "");
        String defaultJson = prefs.getString(KEY_DEFAULT_WAREHOUSE_JSON, "");

        List<WarehouseItem> warehouses = new ArrayList<>();
        WarehouseItem defaultWarehouse = null;

        try {
            if (!TextUtils.isEmpty(warehousesJson)) {
                JSONArray array = new JSONArray(warehousesJson);
                for (int i = 0; i < array.length(); i++) {
                    warehouses.add(fromJsonObject(array.getJSONObject(i)));
                }
            }
            if (!TextUtils.isEmpty(defaultJson)) {
                defaultWarehouse = fromJsonObject(new JSONObject(defaultJson));
            }
        } catch (JSONException ignored) {
            warehouses.clear();
            defaultWarehouse = null;
        }

        return new WarehousesResult(databaseName, defaultOfficeCode, defaultWarehouseCode,
                defaultWarehouseDescription, defaultWarehouse, warehouses);
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static JSONArray toJsonArray(List<WarehouseItem> items) throws JSONException {
        JSONArray array = new JSONArray();
        for (WarehouseItem item : items) {
            array.put(toJsonObject(item));
        }
        return array;
    }

    private static JSONObject toJsonObject(WarehouseItem item) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", item.id);
        json.put("userId", item.userId);
        json.put("databaseName", item.databaseName);
        json.put("officeCode", item.officeCode);
        json.put("warehouseCode", item.warehouseCode);
        json.put("warehouseDescription", item.warehouseDescription);
        json.put("storeCode", item.storeCode);
        json.put("isDefault", item.isDefault);
        json.put("userName", item.userName);
        return json;
    }

    private static WarehouseItem fromJsonObject(JSONObject json) {
        return new WarehouseItem(
                json.optString("id", ""),
                json.optString("userId", ""),
                json.optString("databaseName", ""),
                json.optString("officeCode", ""),
                json.optString("warehouseCode", ""),
                json.optString("warehouseDescription", ""),
                json.optString("storeCode", ""),
                json.optBoolean("isDefault", false),
                json.optString("userName", "")
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
