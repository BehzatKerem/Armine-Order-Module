package com.example.armineordermodule;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GET /api/FrogMobileRFIDHandheldCounting/{id} donus modeli + lokal okutma verisi.
 */
public class RfidHandheldCounting {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_SEND = "PENDING_SEND";
    public static final String STATUS_SENT = "SENT";

    public String countingCode = "";
    public int id;
    public String batchGuid = "";
    public String batchName = "";
    public String officeCode = "";
    public String warehouseCode = "";
    public String databaseName = "";
    public String countCreatedAt = "";
    public String userId = "";
    public String userEmail = "";
    public int locationCount;
    public int itemCount;
    public int totalQuantity;
    public String createdDate = "";
    public boolean isNebimSend;
    public String status = STATUS_DRAFT;
    public long fetchedAtMillis;
    /** Ayni aktarim oturumundaki parcalari sunucuda birlestirmek icin. */
    public String sendTransferGuid = "";
    public int sendChunkCount;
    /** 1 tabanli; 0 = oturum baslamadi. */
    public int sendNextChunkIndex;
    public final List<RfidHandheldCountingLocation> locations = new ArrayList<>();

    public RfidHandheldCounting() {
    }

    /** API olmadan, sadece kod ile yeni bos lokal sayim olusturur. */
    public static RfidHandheldCounting createLocal(String countingCode) {
        RfidHandheldCounting c = new RfidHandheldCounting();
        c.countingCode = countingCode != null ? countingCode.trim() : "";
        c.batchName = countingCode != null ? countingCode.trim() : "";
        c.status = STATUS_DRAFT;
        c.fetchedAtMillis = System.currentTimeMillis();
        return c;
    }

    public RfidHandheldCountingLocation findLocationByGuid(String localGuid) {
        if (TextUtils.isEmpty(localGuid)) {
            return null;
        }
        for (RfidHandheldCountingLocation location : locations) {
            if (localGuid.equals(location.localGuid)) {
                return location;
            }
        }
        return null;
    }

    public int totalScannedEpcCount() {
        int total = 0;
        for (RfidHandheldCountingLocation location : locations) {
            total += location.scannedEpcCount();
        }
        return total;
    }

    public int unsentEpcCount() {
        int total = 0;
        for (RfidHandheldCountingLocation location : locations) {
            total += location.unsentEpcCount();
        }
        return total;
    }

    public int sentEpcCount() {
        int total = 0;
        for (RfidHandheldCountingLocation location : locations) {
            total += location.sentEpcs.size();
        }
        return total;
    }

    public void beginSendSessionIfNeeded() {
        int unsent = unsentEpcCount();
        if (unsent <= 0) {
            return;
        }
        if (TextUtils.isEmpty(sendTransferGuid)) {
            sendTransferGuid = UUID.randomUUID().toString();
            sendNextChunkIndex = 1;
        }
        if (sendNextChunkIndex <= 0) {
            sendNextChunkIndex = 1;
        }
        int alreadySentChunks = Math.max(0, sendNextChunkIndex - 1);
        int remainingChunks = (unsent + RfidHandheldCountingSendChunker.CHUNK_SIZE - 1)
                / RfidHandheldCountingSendChunker.CHUNK_SIZE;
        sendChunkCount = alreadySentChunks + remainingChunks;
    }

    public void markChunkSent(Map<String, List<String>> epcsByLocationGuid) {
        if (epcsByLocationGuid != null) {
            for (Map.Entry<String, List<String>> entry : epcsByLocationGuid.entrySet()) {
                RfidHandheldCountingLocation location = findLocationByGuid(entry.getKey());
                if (location != null) {
                    location.markEpcsSent(entry.getValue());
                }
            }
        }
        sendNextChunkIndex++;
    }

    public void clearSendSession() {
        sendTransferGuid = "";
        sendChunkCount = 0;
        sendNextChunkIndex = 0;
    }

    public void refreshStatusAfterScanChange() {
        if (STATUS_SENT.equals(status) && unsentEpcCount() > 0) {
            status = STATUS_DRAFT;
            clearSendSession();
        }
    }

    public static RfidHandheldCounting fromApiData(JSONObject data, String countingCode)
            throws JSONException {
        if (data == null) {
            throw new JSONException("data null");
        }
        RfidHandheldCounting counting = new RfidHandheldCounting();
        counting.countingCode = countingCode != null ? countingCode : "";
        counting.id = data.optInt("id", 0);
        counting.batchGuid = data.optString("batchGuid", "");
        counting.batchName = data.optString("batchName", "");
        counting.officeCode = data.optString("officeCode", "");
        counting.warehouseCode = data.optString("warehouseCode", "");
        counting.databaseName = data.optString("databaseName", "");
        counting.countCreatedAt = data.optString("countCreatedAt", "");
        counting.userId = data.optString("userId", "");
        counting.userEmail = data.optString("userEmail", "");
        counting.locationCount = data.optInt("locationCount", 0);
        counting.itemCount = data.optInt("itemCount", 0);
        counting.totalQuantity = data.optInt("totalQuantity", 0);
        counting.createdDate = data.optString("createdDate", "");
        counting.isNebimSend = data.optBoolean("isNebimSend", false);
        counting.status = data.optString("status", STATUS_DRAFT);
        counting.fetchedAtMillis = System.currentTimeMillis();

        JSONArray locationsJson = data.optJSONArray("locations");
        if (locationsJson != null) {
            for (int i = 0; i < locationsJson.length(); i++) {
                JSONObject locJson = locationsJson.optJSONObject(i);
                if (locJson != null) {
                    counting.locations.add(RfidHandheldCountingLocation.fromJson(locJson));
                }
            }
        }

        if (counting.locationCount <= 0) {
            counting.locationCount = counting.locations.size();
        }
        return counting;
    }

    /** API'den tekrar cekildiginde lokal EPC ve kullanici ekledigi lokasyonlari korur. */
    public static void mergeLocalData(RfidHandheldCounting existing, RfidHandheldCounting fresh) {
        if (existing == null || fresh == null) {
            return;
        }
        fresh.status = !TextUtils.isEmpty(existing.status) ? existing.status : STATUS_DRAFT;
        fresh.sendTransferGuid = existing.sendTransferGuid;
        fresh.sendChunkCount = existing.sendChunkCount;
        fresh.sendNextChunkIndex = existing.sendNextChunkIndex;

        List<RfidHandheldCountingLocation> merged = new ArrayList<>();
        for (RfidHandheldCountingLocation apiLoc : fresh.locations) {
            RfidHandheldCountingLocation old = findByLocationId(existing.locations, apiLoc.locationId);
            if (old != null) {
                apiLoc.localGuid = old.localGuid;
                apiLoc.epcs.clear();
                apiLoc.epcs.addAll(old.epcs);
                apiLoc.sentEpcs.clear();
                apiLoc.sentEpcs.addAll(old.sentEpcs);
            }
            merged.add(apiLoc);
        }
        for (RfidHandheldCountingLocation oldLoc : existing.locations) {
            if (oldLoc.isLocalOnly()) {
                merged.add(oldLoc);
            }
        }
        fresh.locations.clear();
        fresh.locations.addAll(merged);
        fresh.locationCount = fresh.locations.size();
    }

    private static RfidHandheldCountingLocation findByLocationId(
            List<RfidHandheldCountingLocation> list, int locationId) {
        if (locationId <= 0) {
            return null;
        }
        for (RfidHandheldCountingLocation loc : list) {
            if (loc.locationId == locationId) {
                return loc;
            }
        }
        return null;
    }

    public JSONObject toStorageJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("countingCode", countingCode);
        json.put("id", id);
        json.put("batchGuid", batchGuid);
        json.put("batchName", batchName);
        json.put("officeCode", officeCode);
        json.put("warehouseCode", warehouseCode);
        json.put("databaseName", databaseName);
        json.put("countCreatedAt", countCreatedAt);
        json.put("userId", userId);
        json.put("userEmail", userEmail);
        json.put("locationCount", locationCount);
        json.put("itemCount", itemCount);
        json.put("totalQuantity", totalQuantity);
        json.put("createdDate", createdDate);
        json.put("isNebimSend", isNebimSend);
        json.put("status", status != null ? status : STATUS_DRAFT);
        json.put("fetchedAtMillis", fetchedAtMillis);
        json.put("sendTransferGuid", sendTransferGuid != null ? sendTransferGuid : "");
        json.put("sendChunkCount", sendChunkCount);
        json.put("sendNextChunkIndex", sendNextChunkIndex);

        JSONArray locationsJson = new JSONArray();
        for (RfidHandheldCountingLocation location : locations) {
            locationsJson.put(location.toJson());
        }
        json.put("locations", locationsJson);
        return json;
    }

    /** Gonderim icin govde (endpoint hazir olunca kullanilacak). */
    public JSONObject toSendJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("CountingCode", countingCode);
        json.put("BatchGuid", batchGuid);
        json.put("BatchName", batchName);
        json.put("OfficeCode", officeCode);
        json.put("WareHouseCode", warehouseCode);
        json.put("DatabaseName", databaseName);
        json.put("Status", status);

        JSONArray locationsJson = new JSONArray();
        for (RfidHandheldCountingLocation location : locations) {
            JSONObject locJson = new JSONObject();
            locJson.put("LocalGuid", location.localGuid);
            locJson.put("LocationId", location.locationId);
            locJson.put("LocationName", location.locationName);
            JSONArray epcsJson = new JSONArray();
            for (String epc : location.epcs) {
                if (epc != null && !epc.isEmpty()) {
                    epcsJson.put(epc);
                }
            }
            locJson.put("Epcs", epcsJson);
            locationsJson.put(locJson);
        }
        json.put("Locations", locationsJson);
        return json;
    }

    static RfidHandheldCounting fromStorageJson(JSONObject json) throws JSONException {
        String code = json.optString("countingCode", "");
        if (TextUtils.isEmpty(code)) {
            code = String.valueOf(json.optInt("id", 0));
        }
        RfidHandheldCounting counting = fromApiData(json, code);
        long storedFetchedAt = json.optLong("fetchedAtMillis", 0L);
        if (storedFetchedAt > 0L) {
            counting.fetchedAtMillis = storedFetchedAt;
        }
        if (json.has("status")) {
            counting.status = json.optString("status", STATUS_DRAFT);
        }
        counting.sendTransferGuid = json.optString("sendTransferGuid", "");
        counting.sendChunkCount = json.optInt("sendChunkCount", 0);
        counting.sendNextChunkIndex = json.optInt("sendNextChunkIndex", 0);
        return counting;
    }
}
