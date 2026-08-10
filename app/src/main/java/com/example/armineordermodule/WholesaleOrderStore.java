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
 * Toptan siparis (fis) taslaklarinin lokal deposu. API endpoint'i hazir olana kadar
 * hem "Kaydet" (DRAFT) hem "Kaydet ve Gonder" (PENDING_SEND) ile olusan siparisler
 * burada tutulur. Bkz. docs/TOPTAN_SIPARIS.md.
 */
public final class WholesaleOrderStore {

    private static final String PREFS_NAME = "wholesale_order_prefs";
    private static final String KEY_ORDERS_JSON = "orders_json";

    private WholesaleOrderStore() {
    }

    public static List<WholesaleOrder> getAll(Context context) {
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
        return !getAll(context).isEmpty();
    }

    /** Ayni localId'ye sahip siparis varsa yerine yazar, yoksa ekler. */
    public static void upsert(Context context, WholesaleOrder order) {
        List<WholesaleOrder> orders = getAll(context);
        boolean replaced = false;
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).localId.equals(order.localId)) {
                orders.set(i, order);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            orders.add(order);
        }
        saveAll(context, orders);
    }

    public static void delete(Context context, String localId) {
        List<WholesaleOrder> orders = getAll(context);
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).localId.equals(localId)) {
                orders.remove(i);
                break;
            }
        }
        saveAll(context, orders);
    }

    /**
     * Verilen EPC baska bir lokal sipariste (taslak / gonderim bekleyen) var mi?
     * {@code excludeLocalId} haric tutulur (acik olan siparis).
     */
    public static WholesaleOrder findOtherOrderContainingEpc(
            Context context, String epc, String excludeLocalId) {
        if (TextUtils.isEmpty(epc)) {
            return null;
        }
        for (WholesaleOrder order : getAll(context)) {
            if (!TextUtils.isEmpty(excludeLocalId) && excludeLocalId.equals(order.localId)) {
                continue;
            }
            for (WholesaleOrderLine line : order.lines) {
                if (line.epcs.contains(epc)) {
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
