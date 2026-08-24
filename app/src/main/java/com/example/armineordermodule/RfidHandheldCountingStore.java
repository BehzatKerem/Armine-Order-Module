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

/**
 * RFID el terminali sayim detaylarinin lokal deposu.
 * Ayni countingCode / id ile tekrar cekilirse uzerine yazar.
 */
public final class RfidHandheldCountingStore {

    private static final String PREFS_NAME = "rfid_handheld_counting_prefs";
    private static final String KEY_COUNTS_JSON = "counts_json";

    private RfidHandheldCountingStore() {
    }

    public static List<RfidHandheldCounting> getAll(Context context) {
        String json = prefs(context).getString(KEY_COUNTS_JSON, "");
        List<RfidHandheldCounting> list = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    list.add(RfidHandheldCounting.fromStorageJson(item));
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        Collections.sort(list, new Comparator<RfidHandheldCounting>() {
            @Override
            public int compare(RfidHandheldCounting a, RfidHandheldCounting b) {
                return Long.compare(b.fetchedAtMillis, a.fetchedAtMillis);
            }
        });
        return list;
    }

    public static RfidHandheldCounting findByCode(Context context, String countingCode) {
        if (TextUtils.isEmpty(countingCode)) {
            return null;
        }
        for (RfidHandheldCounting counting : getAll(context)) {
            if (countingCode.equals(counting.countingCode)
                    || countingCode.equals(String.valueOf(counting.id))) {
                return counting;
            }
        }
        return null;
    }

    public static void upsert(Context context, RfidHandheldCounting counting) {
        if (counting == null) {
            return;
        }
        List<RfidHandheldCounting> list = getAll(context);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            RfidHandheldCounting existing = list.get(i);
            boolean sameCode = !TextUtils.isEmpty(counting.countingCode)
                    && counting.countingCode.equals(existing.countingCode);
            boolean sameId = counting.id > 0 && counting.id == existing.id;
            if (sameCode || sameId) {
                list.set(i, counting);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(counting);
        }
        saveAll(context, list);
    }

    public static void deleteByCode(Context context, String countingCode) {
        if (TextUtils.isEmpty(countingCode)) {
            return;
        }
        List<RfidHandheldCounting> list = getAll(context);
        for (int i = 0; i < list.size(); i++) {
            RfidHandheldCounting existing = list.get(i);
            if (countingCode.equals(existing.countingCode)
                    || countingCode.equals(String.valueOf(existing.id))) {
                list.remove(i);
                break;
            }
        }
        saveAll(context, list);
    }

    /** EPC baska lokasyonda okutulmus mu? (acik lokasyon haric) */
    public static String findOtherLocationNameContainingEpc(
            Context context, String countingCode, String excludeLocationGuid, String epc) {
        if (TextUtils.isEmpty(epc)) {
            return "";
        }
        RfidHandheldCounting counting = findByCode(context, countingCode);
        if (counting == null) {
            return "";
        }
        for (RfidHandheldCountingLocation location : counting.locations) {
            if (!TextUtils.isEmpty(excludeLocationGuid)
                    && excludeLocationGuid.equals(location.localGuid)) {
                continue;
            }
            if (location.epcs.contains(epc)) {
                return !TextUtils.isEmpty(location.locationName)
                        ? location.locationName : location.localGuid;
            }
        }
        return "";
    }

    private static void saveAll(Context context, List<RfidHandheldCounting> list) {
        JSONArray array = new JSONArray();
        try {
            for (RfidHandheldCounting counting : list) {
                array.put(counting.toStorageJson());
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
