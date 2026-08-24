package com.example.armineordermodule;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFID sayim aktarimini 1000 EPC'lik parcaya boler.
 * Basarili parca lokalde isaretlenir; kopan gonderim kaldigi yerden devam eder.
 */
public final class RfidHandheldCountingSendChunker {

    public static final int CHUNK_SIZE = 1000;

    private RfidHandheldCountingSendChunker() {
    }

    public static final class Chunk {
        public final JSONObject json;
        public final Map<String, List<String>> epcsByLocationGuid;
        public final int epcCount;
        public final int chunkIndex;
        public final int chunkCount;
        public final boolean lastChunk;

        Chunk(JSONObject json, Map<String, List<String>> epcsByLocationGuid,
              int epcCount, int chunkIndex, int chunkCount, boolean lastChunk) {
            this.json = json;
            this.epcsByLocationGuid = epcsByLocationGuid;
            this.epcCount = epcCount;
            this.chunkIndex = chunkIndex;
            this.chunkCount = chunkCount;
            this.lastChunk = lastChunk;
        }
    }

    public static Chunk takeNext(RfidHandheldCounting counting) throws JSONException {
        if (counting == null) {
            return null;
        }
        counting.beginSendSessionIfNeeded();
        Map<String, List<String>> byLocation = new LinkedHashMap<>();
        int remainingBudget = CHUNK_SIZE;

        for (RfidHandheldCountingLocation location : counting.locations) {
            if (remainingBudget <= 0) {
                break;
            }
            String guid = location.localGuid != null ? location.localGuid : "";
            if (TextUtils.isEmpty(guid)) {
                continue;
            }
            for (String epc : location.unsentEpcs()) {
                if (remainingBudget <= 0) {
                    break;
                }
                List<String> locEpcs = byLocation.get(guid);
                if (locEpcs == null) {
                    locEpcs = new ArrayList<>();
                    byLocation.put(guid, locEpcs);
                }
                locEpcs.add(epc);
                remainingBudget--;
            }
        }

        int epcCount = CHUNK_SIZE - remainingBudget;
        if (epcCount <= 0) {
            return null;
        }

        int chunkIndex = Math.max(1, counting.sendNextChunkIndex);
        int chunkCount = Math.max(chunkIndex, counting.sendChunkCount);
        boolean lastChunk = counting.unsentEpcCount() == epcCount;

        JSONObject json = new JSONObject();
        json.put("CountingCode", counting.countingCode);
        json.put("Id", counting.id);
        json.put("TransferGuid", counting.sendTransferGuid);
        json.put("ChunkIndex", chunkIndex);
        json.put("ChunkCount", chunkCount);
        json.put("IsLastChunk", lastChunk);
        json.put("EpcCount", epcCount);
        json.put("BatchGuid", counting.batchGuid);
        json.put("BatchName", counting.batchName);
        json.put("OfficeCode", counting.officeCode);
        json.put("WareHouseCode", counting.warehouseCode);
        json.put("DatabaseName", counting.databaseName);

        JSONArray locationsJson = new JSONArray();
        for (RfidHandheldCountingLocation location : counting.locations) {
            List<String> locEpcs = byLocation.get(location.localGuid);
            if (locEpcs == null || locEpcs.isEmpty()) {
                continue;
            }
            JSONObject locJson = new JSONObject();
            locJson.put("LocalGuid", location.localGuid);
            locJson.put("LocationId", location.locationId);
            locJson.put("LocationName", location.locationName);
            JSONArray epcsJson = new JSONArray();
            for (String epc : locEpcs) {
                epcsJson.put(epc);
            }
            locJson.put("Epcs", epcsJson);
            locationsJson.put(locJson);
        }
        json.put("Locations", locationsJson);

        return new Chunk(json, byLocation, epcCount, chunkIndex, chunkCount, lastChunk);
    }
}
