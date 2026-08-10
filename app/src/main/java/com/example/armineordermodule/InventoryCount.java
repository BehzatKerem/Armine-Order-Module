package com.example.armineordermodule;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sayim (inventory count) lokal modeli. Siparis akisina benzer sekilde DRAFT /
 * PENDING_SEND durumlari ile SharedPreferences'ta tutulur.
 */
public class InventoryCount {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_SEND = "PENDING_SEND";

    public final String localId;
    public String name;
    public String companyCode;
    public String officeCode;
    public String warehouseCode;
    public String warehouseDescription;
    public String countDate;
    public String status;
    public long createdAtMillis;
    public final List<InventoryLocation> locations = new ArrayList<>();

    public InventoryCount(String localId) {
        this.localId = !TextUtils.isEmpty(localId) ? localId : UUID.randomUUID().toString();
    }

    public static InventoryCount createNew() {
        InventoryCount count = new InventoryCount(UUID.randomUUID().toString());
        count.status = STATUS_DRAFT;
        count.createdAtMillis = System.currentTimeMillis();
        return count;
    }

    public InventoryLocation findLocationById(String locationId) {
        if (TextUtils.isEmpty(locationId)) {
            return null;
        }
        for (InventoryLocation location : locations) {
            if (locationId.equals(location.localId)) {
                return location;
            }
        }
        return null;
    }

    public int totalLocationCount() {
        return locations.size();
    }

    public int totalLineCount() {
        int total = 0;
        for (InventoryLocation location : locations) {
            total += location.totalLineCount();
        }
        return total;
    }

    public int totalQty() {
        int total = 0;
        for (InventoryLocation location : locations) {
            total += location.totalQty();
        }
        return total;
    }

    /** API hazir olana kadar log / aktarim icin govde. */
    public JSONObject toApiJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Name", name != null ? name : "");
        json.put("CompanyCode", companyCode != null ? companyCode : "");
        json.put("OfficeCode", officeCode != null ? officeCode : "");
        json.put("WareHouseCode", warehouseCode != null ? warehouseCode : "");
        json.put("CountDate", countDate != null ? countDate : "");
        json.put("IsCompleted", true);

        JSONArray locationsJson = new JSONArray();
        for (InventoryLocation location : locations) {
            JSONObject locJson = new JSONObject();
            locJson.put("Name", location.name != null ? location.name : "");
            JSONArray linesJson = new JSONArray();
            for (InventoryLine line : location.lines) {
                JSONObject lineJson = new JSONObject();
                lineJson.put("Barcode", line.barcode);
                lineJson.put("Qty1", line.qty);
                JSONArray epcsJson = new JSONArray();
                for (String epc : line.epcs) {
                    if (epc != null && !epc.isEmpty()) {
                        epcsJson.put(epc);
                    }
                }
                lineJson.put("Epcs", epcsJson);
                linesJson.put(lineJson);
            }
            locJson.put("Lines", linesJson);
            locationsJson.put(locJson);
        }
        json.put("Locations", locationsJson);
        return json;
    }

    JSONObject toStorageJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("localId", localId);
        json.put("name", name != null ? name : "");
        json.put("companyCode", companyCode != null ? companyCode : "");
        json.put("officeCode", officeCode != null ? officeCode : "");
        json.put("warehouseCode", warehouseCode != null ? warehouseCode : "");
        json.put("warehouseDescription", warehouseDescription != null ? warehouseDescription : "");
        json.put("countDate", countDate != null ? countDate : "");
        json.put("status", status != null ? status : STATUS_DRAFT);
        json.put("createdAtMillis", createdAtMillis);

        JSONArray locationsJson = new JSONArray();
        for (InventoryLocation location : locations) {
            locationsJson.put(location.toStorageJson());
        }
        json.put("locations", locationsJson);
        return json;
    }

    static InventoryCount fromStorageJson(JSONObject json) {
        InventoryCount count = new InventoryCount(json.optString("localId", ""));
        count.name = json.optString("name", "");
        count.companyCode = json.optString("companyCode", "");
        count.officeCode = json.optString("officeCode", "");
        count.warehouseCode = json.optString("warehouseCode", "");
        count.warehouseDescription = json.optString("warehouseDescription", "");
        count.countDate = json.optString("countDate", "");
        count.status = json.optString("status", STATUS_DRAFT);
        count.createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis());

        JSONArray locationsJson = json.optJSONArray("locations");
        if (locationsJson != null) {
            for (int i = 0; i < locationsJson.length(); i++) {
                JSONObject locJson = locationsJson.optJSONObject(i);
                if (locJson != null) {
                    count.locations.add(InventoryLocation.fromStorageJson(locJson));
                }
            }
        }
        return count;
    }
}
