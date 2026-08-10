package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Sayim taslaklarinin lokal deposu (SharedPreferences). */
public final class InventoryStore {

    private static final String PREFS_NAME = "inventory_count_prefs";
    private static final String KEY_COUNTS_JSON = "counts_json";

    private InventoryStore() {
    }

    public static List<InventoryCount> getAll(Context context) {
        String json = prefs(context).getString(KEY_COUNTS_JSON, "");
        List<InventoryCount> counts = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return counts;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    counts.add(InventoryCount.fromStorageJson(item));
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        Collections.sort(counts, new Comparator<InventoryCount>() {
            @Override
            public int compare(InventoryCount a, InventoryCount b) {
                return Long.compare(b.createdAtMillis, a.createdAtMillis);
            }
        });
        return counts;
    }

    public static InventoryCount findById(Context context, String localId) {
        if (TextUtils.isEmpty(localId)) {
            return null;
        }
        for (InventoryCount count : getAll(context)) {
            if (localId.equals(count.localId)) {
                return count;
            }
        }
        return null;
    }

    public static void upsert(Context context, InventoryCount count) {
        List<InventoryCount> counts = getAll(context);
        boolean replaced = false;
        for (int i = 0; i < counts.size(); i++) {
            if (counts.get(i).localId.equals(count.localId)) {
                counts.set(i, count);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            counts.add(count);
        }
        saveAll(context, counts);
    }

    public static void delete(Context context, String localId) {
        List<InventoryCount> counts = getAll(context);
        for (int i = 0; i < counts.size(); i++) {
            if (counts.get(i).localId.equals(localId)) {
                counts.remove(i);
                break;
            }
        }
        saveAll(context, counts);
    }

    /**
     * EPC baska sayimda veya ayni sayimin baska lokasyonunda var mi?
     * {@code excludeCountId} / {@code excludeLocationId} haric tutulur.
     *
     * @return cakisan sayim; yoksa null
     */
    public static InventoryCount findOtherContainingEpc(
            Context context, String epc, String excludeCountId, String excludeLocationId) {
        if (TextUtils.isEmpty(epc)) {
            return null;
        }
        for (InventoryCount count : getAll(context)) {
            for (InventoryLocation location : count.locations) {
                boolean sameCount = !TextUtils.isEmpty(excludeCountId)
                        && excludeCountId.equals(count.localId);
                boolean sameLocation = !TextUtils.isEmpty(excludeLocationId)
                        && excludeLocationId.equals(location.localId);
                if (sameCount && sameLocation) {
                    continue;
                }
                for (InventoryLine line : location.lines) {
                    if (line.epcs.contains(epc)) {
                        return count;
                    }
                }
            }
        }
        return null;
    }

    /** Cakisan lokasyon adini bulmak icin (uyari mesaji). */
    public static String findLocationNameContainingEpc(
            Context context, String epc, String excludeCountId, String excludeLocationId) {
        if (TextUtils.isEmpty(epc)) {
            return "";
        }
        for (InventoryCount count : getAll(context)) {
            for (InventoryLocation location : count.locations) {
                boolean sameCount = !TextUtils.isEmpty(excludeCountId)
                        && excludeCountId.equals(count.localId);
                boolean sameLocation = !TextUtils.isEmpty(excludeLocationId)
                        && excludeLocationId.equals(location.localId);
                if (sameCount && sameLocation) {
                    continue;
                }
                for (InventoryLine line : location.lines) {
                    if (line.epcs.contains(epc)) {
                        String locName = !TextUtils.isEmpty(location.name) ? location.name : "-";
                        String countName = !TextUtils.isEmpty(count.name) ? count.name : "-";
                        if (sameCount) {
                            return locName;
                        }
                        return countName + " / " + locName;
                    }
                }
            }
        }
        return "";
    }

    private static void saveAll(Context context, List<InventoryCount> counts) {
        JSONArray array = new JSONArray();
        try {
            for (InventoryCount count : counts) {
                array.put(count.toStorageJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs(context).edit().putString(KEY_COUNTS_JSON, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
