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

/** Depo transferi taslaklarini cihazda tutar. */
public final class WarehouseTransferStore {

    private static final String PREFS_NAME = "warehouse_transfer_prefs";
    private static final String KEY_TRANSFERS_JSON = "transfers_json";

    private WarehouseTransferStore() {
    }

    public static List<WarehouseTransfer> getAll(Context context) {
        String json = prefs(context).getString(KEY_TRANSFERS_JSON, "");
        List<WarehouseTransfer> transfers = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return transfers;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    transfers.add(WarehouseTransfer.fromStorageJson(item));
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        Collections.sort(transfers, new Comparator<WarehouseTransfer>() {
            @Override
            public int compare(WarehouseTransfer a, WarehouseTransfer b) {
                return Long.compare(b.createdAtMillis, a.createdAtMillis);
            }
        });
        return transfers;
    }

    public static WarehouseTransfer findByGuid(Context context, String transferGuid) {
        if (TextUtils.isEmpty(transferGuid)) {
            return null;
        }
        for (WarehouseTransfer transfer : getAll(context)) {
            if (transferGuid.equals(transfer.transferGuid)) {
                return transfer;
            }
        }
        return null;
    }

    /** Ayni TransferGuid varsa yerine yazar, yoksa ekler. */
    public static void upsert(Context context, WarehouseTransfer transfer) {
        if (transfer == null || TextUtils.isEmpty(transfer.transferGuid)) {
            return;
        }
        List<WarehouseTransfer> transfers = getAll(context);
        boolean replaced = false;
        for (int i = 0; i < transfers.size(); i++) {
            if (transfer.transferGuid.equals(transfers.get(i).transferGuid)) {
                transfers.set(i, transfer);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            transfers.add(transfer);
        }
        saveAll(context, transfers);
    }

    public static boolean delete(Context context, String transferGuid) {
        if (TextUtils.isEmpty(transferGuid)) {
            return false;
        }
        List<WarehouseTransfer> transfers = getAll(context);
        for (int i = 0; i < transfers.size(); i++) {
            if (transferGuid.equals(transfers.get(i).transferGuid)) {
                if (transfers.get(i).isNebimSend) {
                    return false;
                }
                transfers.remove(i);
                saveAll(context, transfers);
                return true;
            }
        }
        return false;
    }

    private static void saveAll(Context context, List<WarehouseTransfer> transfers) {
        JSONArray array = new JSONArray();
        try {
            for (WarehouseTransfer transfer : transfers) {
                array.put(transfer.toStorageJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs(context).edit().putString(KEY_TRANSFERS_JSON, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
