package com.example.armineordermodule;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sayim icindeki tek bir lokasyon (urun okutma birimi). */
public class InventoryLocation {

    public final String localId;
    public String name;
    public final List<InventoryLine> lines = new ArrayList<>();

    public InventoryLocation(String localId) {
        this.localId = !TextUtils.isEmpty(localId) ? localId : UUID.randomUUID().toString();
    }

    public static InventoryLocation createNew(String name) {
        InventoryLocation location = new InventoryLocation(UUID.randomUUID().toString());
        location.name = name != null ? name : "";
        return location;
    }

    public int totalLineCount() {
        return lines.size();
    }

    public int totalQty() {
        int total = 0;
        for (InventoryLine line : lines) {
            total += line.qty;
        }
        return total;
    }

    JSONObject toStorageJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("localId", localId);
        json.put("name", name != null ? name : "");

        JSONArray linesJson = new JSONArray();
        for (InventoryLine line : lines) {
            JSONObject lineJson = new JSONObject();
            lineJson.put("barcode", line.barcode);
            lineJson.put("qty", line.qty);
            JSONArray epcsJson = new JSONArray();
            for (String epc : line.epcs) {
                if (epc != null && !epc.isEmpty()) {
                    epcsJson.put(epc);
                }
            }
            lineJson.put("epcs", epcsJson);
            linesJson.put(lineJson);
        }
        json.put("lines", linesJson);
        return json;
    }

    static InventoryLocation fromStorageJson(JSONObject json) {
        InventoryLocation location = new InventoryLocation(json.optString("localId", ""));
        location.name = json.optString("name", "");

        JSONArray linesJson = json.optJSONArray("lines");
        if (linesJson != null) {
            for (int i = 0; i < linesJson.length(); i++) {
                JSONObject lineJson = linesJson.optJSONObject(i);
                if (lineJson == null) {
                    continue;
                }
                String barcode = lineJson.optString("barcode", "");
                int qty = lineJson.optInt("qty", 0);
                if (TextUtils.isEmpty(barcode) || qty <= 0) {
                    continue;
                }
                InventoryLine line = new InventoryLine(barcode, qty);
                JSONArray epcsJson = lineJson.optJSONArray("epcs");
                if (epcsJson != null) {
                    for (int e = 0; e < epcsJson.length(); e++) {
                        String epc = epcsJson.optString(e, "");
                        if (!TextUtils.isEmpty(epc) && !line.epcs.contains(epc)) {
                            line.epcs.add(epc);
                        }
                    }
                }
                location.lines.add(line);
            }
        }
        return location;
    }
}
