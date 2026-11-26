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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Depo transferi taslaklarini cihazda tutar. */
public final class WarehouseTransferStore {

    private static final String PREFS_NAME = "warehouse_transfer_prefs";
    private static final String KEY_TRANSFERS_JSON = "transfers_json";

    private WarehouseTransferStore() {
    }

    /**
     * Giris yapmis kullanicinin transferleri. Sahibi olmayan eski kayitlar ilk erisen
     * kullaniciya atanir.
     */
    public static List<WarehouseTransfer> getAll(Context context) {
        String owner = AuthPreferences.getOwnerKey(context);
        List<WarehouseTransfer> all = loadAll(context);
        boolean claimed = false;
        List<WarehouseTransfer> mine = new ArrayList<>();
        for (WarehouseTransfer transfer : all) {
            if (TextUtils.isEmpty(transfer.ownerUserId) && !TextUtils.isEmpty(owner)) {
                transfer.ownerUserId = owner;
                claimed = true;
            }
            if (owner.equals(transfer.ownerUserId)) {
                mine.add(transfer);
            }
        }
        if (claimed) {
            saveAll(context, all);
        }
        return mine;
    }

    /** Tum kullanicilarin transferleri; sadece kayit icin. */
    private static List<WarehouseTransfer> loadAll(Context context) {
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
        List<WarehouseTransfer> transfers = loadAll(context);
        boolean replaced = false;
        for (int i = 0; i < transfers.size(); i++) {
            if (transfer.transferGuid.equals(transfers.get(i).transferGuid)) {
                // Okutma ekrani kaydi her seferinde yeniden olusturur; mevcut sahip korunur.
                if (TextUtils.isEmpty(transfer.ownerUserId)) {
                    transfer.ownerUserId = transfers.get(i).ownerUserId;
                }
                if (TextUtils.isEmpty(transfer.ownerUserId)) {
                    transfer.ownerUserId = AuthPreferences.getOwnerKey(context);
                }
                transfers.set(i, transfer);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (TextUtils.isEmpty(transfer.ownerUserId)) {
                transfer.ownerUserId = AuthPreferences.getOwnerKey(context);
            }
            transfers.add(transfer);
        }
        saveAll(context, transfers);
    }

    public static boolean delete(Context context, String transferGuid) {
        return delete(context, transferGuid, false);
    }

    /**
     * @param allowSent true ise Nebim'e gonderilmis transferin cihazdaki kaydi da silinir
     *                  (sadece gelistirici modu). Nebim'deki kayda dokunulmaz.
     */
    public static boolean delete(Context context, String transferGuid, boolean allowSent) {
        if (TextUtils.isEmpty(transferGuid)) {
            return false;
        }
        String owner = AuthPreferences.getOwnerKey(context);
        List<WarehouseTransfer> transfers = loadAll(context);
        for (int i = 0; i < transfers.size(); i++) {
            if (transferGuid.equals(transfers.get(i).transferGuid)) {
                if ((transfers.get(i).isNebimSend && !allowSent) || !owner.equals(transfers.get(i).ownerUserId)) {
                    return false;
                }
                transfers.remove(i);
                saveAll(context, transfers);
                return true;
            }
        }
        return false;
    }

    /** Transfer giris yapmis kullaniciya mi ait? */
    public static boolean isOwnedByCurrentUser(Context context, WarehouseTransfer transfer) {
        return transfer != null
                && AuthPreferences.getOwnerKey(context).equals(transfer.ownerUserId);
    }

    /**
     * Verilen EPC baska bir lokal transferde var mi? (taslak ve gonderilmis dahil —
     * aktarilmis etiket baska transfere okutulamaz). Tum kullanicilarin transferlerine bakar.
     * {@code excludeGuid} haric tutulur (acik olan transfer).
     */
    public static WarehouseTransfer findOtherTransferContainingEpc(
            Context context, String epc, String excludeGuid) {
        if (TextUtils.isEmpty(epc)) {
            return null;
        }
        for (WarehouseTransfer transfer : loadAll(context)) {
            if (!TextUtils.isEmpty(excludeGuid) && excludeGuid.equals(transfer.transferGuid)) {
                continue;
            }
            if (transfer.epcs.contains(epc)) {
                return transfer;
            }
        }
        return null;
    }

    /**
     * {@link #findOtherTransferContainingEpc}'in toplu hali: diger tum transferlerin EPC'leri
     * -> iceren transfer. Okutma ekrani acilirken bir kez kurulur; her okumada kaydi bastan
     * parse etmek yerine sabit surede bakilir. Ayni EPC birden fazla transferdeyse
     * findOtherTransferContainingEpc ile ayni sirada ilk bulunan transfer doner.
     */
    public static Map<String, WarehouseTransfer> buildOtherTransfersEpcIndex(
            Context context, String excludeGuid) {
        Map<String, WarehouseTransfer> index = new HashMap<>();
        for (WarehouseTransfer transfer : loadAll(context)) {
            if (!TextUtils.isEmpty(excludeGuid) && excludeGuid.equals(transfer.transferGuid)) {
                continue;
            }
            for (String epc : transfer.epcs) {
                if (!TextUtils.isEmpty(epc) && !index.containsKey(epc)) {
                    index.put(epc, transfer);
                }
            }
        }
        return index;
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
