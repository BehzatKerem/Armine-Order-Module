package com.example.armineordermodule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Cihazda saklanan depo transferi. transferGuid API'ye gider ve tekrar
 * gonderimde ayni kalir.
 */
public class WarehouseTransfer {

    public String transferGuid = "";
    public String officeCode = "";
    public String warehouseCode = "";
    public String sourceDescription = "";
    public String toOfficeCode = "";
    public String toWarehouseCode = "";
    public String targetDescription = "";
    public long createdAtMillis;
    public int headerId;
    /** Nebim aktarimi tamamlandi. alreadySent yaniti da bunu true yapar. */
    public boolean isNebimSend;
    public final List<String> epcs = new ArrayList<>();

    public int productCount() {
        return epcs.size();
    }

    JSONObject toStorageJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("transferGuid", transferGuid != null ? transferGuid : "");
        json.put("officeCode", officeCode != null ? officeCode : "");
        json.put("warehouseCode", warehouseCode != null ? warehouseCode : "");
        json.put("sourceDescription", sourceDescription != null ? sourceDescription : "");
        json.put("toOfficeCode", toOfficeCode != null ? toOfficeCode : "");
        json.put("toWarehouseCode", toWarehouseCode != null ? toWarehouseCode : "");
        json.put("targetDescription", targetDescription != null ? targetDescription : "");
        json.put("createdAtMillis", createdAtMillis);
        json.put("headerId", headerId);
        json.put("isNebimSend", isNebimSend);

        JSONArray epcsJson = new JSONArray();
        for (String epc : epcs) {
            if (epc != null && !epc.isEmpty()) {
                epcsJson.put(epc);
            }
        }
        json.put("epcs", epcsJson);
        return json;
    }

    static WarehouseTransfer fromStorageJson(JSONObject json) {
        WarehouseTransfer transfer = new WarehouseTransfer();
        if (json == null) {
            return transfer;
        }
        transfer.transferGuid = json.optString("transferGuid", "");
        transfer.officeCode = json.optString("officeCode", "");
        transfer.warehouseCode = json.optString("warehouseCode", "");
        transfer.sourceDescription = json.optString("sourceDescription", "");
        transfer.toOfficeCode = json.optString("toOfficeCode", "");
        transfer.toWarehouseCode = json.optString("toWarehouseCode", "");
        transfer.targetDescription = json.optString("targetDescription", "");
        transfer.createdAtMillis = json.optLong("createdAtMillis", 0L);
        transfer.headerId = json.optInt("headerId", 0);
        transfer.isNebimSend = json.optBoolean("isNebimSend", false);

        JSONArray epcsJson = json.optJSONArray("epcs");
        if (epcsJson != null) {
            for (int i = 0; i < epcsJson.length(); i++) {
                String epc = epcsJson.optString(i, "");
                if (!epc.isEmpty()) {
                    transfer.epcs.add(epc);
                }
            }
        }
        return transfer;
    }
}
