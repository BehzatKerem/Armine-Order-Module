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
 * Toptan siparis (fis) lokal deposu. DRAFT / PENDING_SEND / SENT fişleri tutar.
 * SENT fişler silinemez; EPC tekilligi SENT dahil tum fislerde aranir.
 * Bkz. docs/TOPTAN_SIPARIS.md.
 */
public final class WholesaleOrderStore {

    private static final String PREFS_NAME = "wholesale_order_prefs";
    private static final String KEY_ORDERS_JSON = "orders_json";

    private WholesaleOrderStore() {
    }

    /**
     * Giris yapmis kullanicinin siparisleri. Baska kullanicilarin siparisleri listelenmez
     * ve acilamaz. Sahibi olmayan eski kayitlar (bu ozellikten once kaydedilenler) ilk
     * erisen kullaniciya atanir.
     */
    public static List<WholesaleOrder> getAll(Context context) {
        String owner = AuthPreferences.getOwnerKey(context);
        List<WholesaleOrder> all = loadAll(context);
        boolean claimed = false;
        List<WholesaleOrder> mine = new ArrayList<>();
        for (WholesaleOrder order : all) {
            if (TextUtils.isEmpty(order.ownerUserId) && !TextUtils.isEmpty(owner)) {
                order.ownerUserId = owner;
                claimed = true;
            }
            if (owner.equals(order.ownerUserId)) {
                mine.add(order);
            }
        }
        if (claimed) {
            saveAll(context, all);
        }
        return mine;
    }

    /** Siparis giris yapmis kullaniciya mi ait? */
    public static boolean isOwnedByCurrentUser(Context context, WholesaleOrder order) {
        return order != null
                && AuthPreferences.getOwnerKey(context).equals(order.ownerUserId);
    }

    /** Tum kullanicilarin siparisleri. Sadece EPC cakisma kontrolu ve kayit icin. */
    private static List<WholesaleOrder> loadAll(Context context) {
        String json = prefs(context).getString(KEY_ORDERS_JSON, "");
        List<WholesaleOrder> orders = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return orders;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    orders.add(WholesaleOrder.fromStorageJson(item));
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        Collections.sort(orders, new Comparator<WholesaleOrder>() {
            @Override
            public int compare(WholesaleOrder a, WholesaleOrder b) {
                return Long.compare(b.createdAtMillis, a.createdAtMillis);
            }
        });
        return orders;
    }

    public static WholesaleOrder findById(Context context, String localId) {
        if (TextUtils.isEmpty(localId)) {
            return null;
        }
        for (WholesaleOrder order : getAll(context)) {
            if (localId.equals(order.localId)) {
                return order;
            }
        }
        return null;
    }

    public static boolean hasPendingOrders(Context context) {
        for (WholesaleOrder order : getAll(context)) {
            if (!order.isTransferred()) {
                return true;
            }
        }
        return false;
    }

    /** Ayni localId'ye sahip siparis varsa yerine yazar, yoksa ekler. */
    public static void upsert(Context context, WholesaleOrder order) {
        List<WholesaleOrder> orders = loadAll(context);
        boolean replaced = false;
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).localId.equals(order.localId)) {
                // Ekranlar kaydi yeniden olusturup kaydedebilir; mevcut sahip korunur.
                if (TextUtils.isEmpty(order.ownerUserId)) {
                    order.ownerUserId = orders.get(i).ownerUserId;
                }
                if (TextUtils.isEmpty(order.ownerUserId)) {
                    order.ownerUserId = AuthPreferences.getOwnerKey(context);
                }
                orders.set(i, order);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (TextUtils.isEmpty(order.ownerUserId)) {
                order.ownerUserId = AuthPreferences.getOwnerKey(context);
            }
            orders.add(order);
        }
        saveAll(context, orders);
    }

    /**
     * Aktarilmis (SENT) fis silinmez.
     * @return true silindiyse
     */
    public static boolean delete(Context context, String localId) {
        return delete(context, localId, false);
    }

    /**
     * @param allowSent true ise aktarilmis fisin cihazdaki kaydi da silinir
     *                  (sadece gelistirici modu). Nebim'deki kayda dokunulmaz.
     */
    public static boolean delete(Context context, String localId, boolean allowSent) {
        if (TextUtils.isEmpty(localId)) {
            return false;
        }
        List<WholesaleOrder> orders = loadAll(context);
        for (int i = 0; i < orders.size(); i++) {
            WholesaleOrder order = orders.get(i);
            if (localId.equals(order.localId)) {
                if ((order.isTransferred() && !allowSent) || !isOwnedByCurrentUser(context, order)) {
                    return false;
                }
                orders.remove(i);
                saveAll(context, orders);
                return true;
            }
        }
        return false;
    }

    /**
     * Verilen EPC baska bir lokal sipariste var mi? (taslak, pending ve SENT dahil —
     * aktarilmis etiket baska siparise okutulamaz).
     * {@code excludeLocalId} haric tutulur (acik olan siparis).
     */
    public static WholesaleOrder findOtherOrderContainingEpc(
            Context context, String epc, String excludeLocalId) {
        if (TextUtils.isEmpty(epc)) {
            return null;
        }
        // Tum kullanicilar: ayni etiket iki farkli kullanicinin siparisine giremez.
        for (WholesaleOrder order : loadAll(context)) {
            if (!TextUtils.isEmpty(excludeLocalId) && excludeLocalId.equals(order.localId)) {
                continue;
            }
            for (WholesaleOrderLine line : order.lines) {
                if (epc.equals(line.barcode) || line.epcs.contains(epc)) {
                    return order;
                }
            }
        }
        return null;
    }

    private static void saveAll(Context context, List<WholesaleOrder> orders) {
        JSONArray array = new JSONArray();
        try {
            for (WholesaleOrder order : orders) {
                array.put(order.toStorageJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs(context).edit().putString(KEY_ORDERS_JSON, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
