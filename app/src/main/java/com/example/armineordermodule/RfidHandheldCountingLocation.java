package com.example.armineordermodule;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** RFID el terminali sayimindaki lokasyon. API + lokal eklenenler. */
public class RfidHandheldCountingLocation {

    /** Lokal tanimlayici (yeni lokasyonlarda UUID; API lokasyonlarinda kararli guid). */
    public String localGuid = "";
    public int id;
    public int locationId;
    public String locationName = "";
    public int itemCount;
    public int totalQuantity;
    /** API'den gelen barkod/miktar satirlari. */
    public final List<RfidHandheldCountingItem> items = new ArrayList<>();
    /** El terminalinde okutulan ham EPC listesi (parse / miktar yok). */
    public final List<String> epcs = new ArrayList<>();
    /** Bu lokasyonda API'ye basariyla gonderilmis EPC'ler. */
    public final Set<String> sentEpcs = new LinkedHashSet<>();

    public RfidHandheldCountingLocation() {
    }

    /** Kullanicinin ekledigi yeni lokasyon. */
    public static RfidHandheldCountingLocation createLocal(String locationName) {
        RfidHandheldCountingLocation location = new RfidHandheldCountingLocation();
        location.localGuid = UUID.randomUUID().toString();
        location.locationName = locationName != null ? locationName.trim() : "";
        location.locationId = 0;
        location.id = 0;
        return location;
    }

    public boolean isLocalOnly() {
        return locationId <= 0 && id <= 0;
    }

    public int scannedEpcCount() {
        return epcs.size();
    }

    public List<String> unsentEpcs() {
        List<String> unsent = new ArrayList<>();
        for (String epc : epcs) {
            if (!TextUtils.isEmpty(epc) && !sentEpcs.contains(epc)) {
                unsent.add(epc);
            }
        }
        return unsent;
    }

    public int unsentEpcCount() {
        return unsentEpcs().size();
    }

    public void markEpcsSent(Collection<String> toMark) {
        if (toMark == null) {
            return;
        }
        for (String epc : toMark) {
            if (!TextUtils.isEmpty(epc)) {
                sentEpcs.add(epc);
            }
        }
    }

    public void removeEpc(String epc) {
        if (TextUtils.isEmpty(epc)) {
            return;
        }
        epcs.remove(epc);
        sentEpcs.remove(epc);
    }

    static RfidHandheldCountingLocation fromJson(JSONObject locJson) throws JSONException {
        RfidHandheldCountingLocation location = new RfidHandheldCountingLocation();
        location.localGuid = locJson.optString("localGuid", "");
        location.id = locJson.optInt("id", 0);
        location.locationId = locJson.optInt("locationId", 0);
        location.locationName = locJson.optString("locationName", "");
        location.itemCount = locJson.optInt("itemCount", 0);
        location.totalQuantity = locJson.optInt("totalQuantity", 0);

        if (TextUtils.isEmpty(location.localGuid)) {
            if (location.locationId > 0) {
                location.localGuid = "api-loc-" + location.locationId;
            } else {
                location.localGuid = UUID.randomUUID().toString();
            }
        }

        JSONArray epcsJson = locJson.optJSONArray("epcs");
        if (epcsJson != null) {
            for (int e = 0; e < epcsJson.length(); e++) {
                String epc = epcsJson.optString(e, "");
                if (!TextUtils.isEmpty(epc) && !location.epcs.contains(epc)) {
                    location.epcs.add(epc);
                }
            }
        }

        JSONArray sentEpcsJson = locJson.optJSONArray("sentEpcs");
        if (sentEpcsJson != null) {
            for (int e = 0; e < sentEpcsJson.length(); e++) {
                String epc = sentEpcsJson.optString(e, "");
                if (!TextUtils.isEmpty(epc)) {
                    location.sentEpcs.add(epc);
                }
            }
        }

        JSONArray itemsJson = locJson.optJSONArray("items");
        if (itemsJson != null) {
            for (int j = 0; j < itemsJson.length(); j++) {
                JSONObject itemJson = itemsJson.optJSONObject(j);
                if (itemJson == null) {
                    continue;
                }
                String barcode = itemJson.optString("barcode", "");
                int qty = itemJson.optInt("quantity", 0);
                if (TextUtils.isEmpty(barcode)) {
                    continue;
                }
                location.items.add(new RfidHandheldCountingItem(
                        itemJson.optInt("id", 0), barcode, qty));
            }
        }
        return location;
    }

    JSONObject toJson() throws JSONException {
        JSONObject locJson = new JSONObject();
        locJson.put("localGuid", localGuid != null ? localGuid : "");
        locJson.put("id", id);
        locJson.put("locationId", locationId);
        locJson.put("locationName", locationName != null ? locationName : "");
        locJson.put("itemCount", itemCount);
        locJson.put("totalQuantity", totalQuantity);

        JSONArray epcsJson = new JSONArray();
        for (String epc : epcs) {
            if (epc != null && !epc.isEmpty()) {
                epcsJson.put(epc);
            }
        }
        locJson.put("epcs", epcsJson);

        JSONArray sentEpcsJson = new JSONArray();
        for (String epc : sentEpcs) {
            if (epc != null && !epc.isEmpty()) {
                sentEpcsJson.put(epc);
            }
        }
        locJson.put("sentEpcs", sentEpcsJson);

        JSONArray itemsJson = new JSONArray();
        for (RfidHandheldCountingItem item : items) {
            JSONObject itemJson = new JSONObject();
            itemJson.put("id", item.id);
            itemJson.put("barcode", item.barcode);
            itemJson.put("quantity", item.quantity);
            itemsJson.put(itemJson);
        }
        locJson.put("items", itemsJson);
        return locJson;
    }
}
