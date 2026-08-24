package com.example.armineordermodule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Toptan siparis modulunun lokal siparis (fis) modeli. Bkz. docs/TOPTAN_SIPARIS.md.
 *
 * API'ye gonderilecek alanlarin disinda (customerDescription, warehouseDescription,
 * status, createdAtMillis, localId) yalnizca ekranda gosterim / lokal yonetim icin
 * tutulan alanlar da bulunur; bunlar {@link #toApiJson()} ciktisina dahil edilmez.
 * Satir EPC listesi lokal tekillik icin tutulur; API {@link #toApiJson()} yalnizca
 * benzersiz EPC listesini gonderir (ItemCode / miktar gosterimlik).
 */
public class WholesaleOrder {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_SEND = "PENDING_SEND";

    private static final int MODEL_TYPE = 5;

    public final String localId;
    public String customerCode;
    public String customerDescription;
    public String companyCode;
    public String officeCode;
    public String warehouseCode;
    public String warehouseDescription;
    public String orderDate;
    public String description;
    public String status;
    public long createdAtMillis;
    public final List<WholesaleOrderLine> lines = new ArrayList<>();

    public WholesaleOrder(String localId) {
        this.localId = !android.text.TextUtils.isEmpty(localId) ? localId : UUID.randomUUID().toString();
    }

    public static WholesaleOrder createNew() {
        WholesaleOrder order = new WholesaleOrder(UUID.randomUUID().toString());
        order.status = STATUS_DRAFT;
        order.createdAtMillis = System.currentTimeMillis();
        return order;
    }

    public int totalLineCount() {
        return lines.size();
    }

    public int totalQty() {
        int total = 0;
        for (WholesaleOrderLine line : lines) {
            total += line.qty;
        }
        return total;
    }

    /** Siparisteki tum benzersiz EPC'ler (eklenme sirasi korunur). */
    public List<String> collectAllEpcs() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (WholesaleOrderLine line : lines) {
            boolean addedFromEpcs = false;
            if (line.epcs != null) {
                for (String epc : line.epcs) {
                    if (epc != null && !epc.isEmpty()) {
                        set.add(epc);
                        addedFromEpcs = true;
                    }
                }
            }
            // Yeni model: barcode alani ham EPC. Eski modelde barcode 13 haneli urun
            // kodudur; epcs doluysa onu tekrar gondermemek gerekir.
            if (!addedFromEpcs && line.barcode != null && !line.barcode.isEmpty()) {
                set.add(line.barcode);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * API'ye gonderilecek istek govdesi.
     * Gonderimde yalnizca benzersiz EPC listesi vardir; barkod/miktar/ItemCode yok.
     */
    public JSONObject toApiJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("ModelType", MODEL_TYPE);
        json.put("CustomerCode", customerCode);
        json.put("CompanyCode", companyCode);
        json.put("OfficeCode", officeCode);
        json.put("WareHouseCode", warehouseCode);
        json.put("OrderDate", orderDate);
        json.put("IsCompleted", true);
        json.put("IsReturn", false);
        json.put("Description", description);

        JSONArray epcsJson = new JSONArray();
        for (String epc : collectAllEpcs()) {
            epcsJson.put(epc);
        }
        json.put("Epcs", epcsJson);
        return json;
    }

    /** Lokal saklama (SharedPreferences) icin tum alanlari kapsayan JSON. */
    JSONObject toStorageJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("localId", localId);
        json.put("customerCode", customerCode);
        json.put("customerDescription", customerDescription);
        json.put("companyCode", companyCode);
        json.put("officeCode", officeCode);
        json.put("warehouseCode", warehouseCode);
        json.put("warehouseDescription", warehouseDescription);
        json.put("orderDate", orderDate);
        json.put("description", description);
        json.put("status", status);
        json.put("createdAtMillis", createdAtMillis);

        JSONArray linesJson = new JSONArray();
        for (WholesaleOrderLine line : lines) {
            JSONObject lineJson = new JSONObject();
            lineJson.put("barcode", line.barcode);
            lineJson.put("qty", line.qty);
            JSONArray epcsJson = new JSONArray();
            for (String epc : line.epcs) {
                if (epc != null && !epc.isEmpty()) {
                    epcsJson.put(epc);
                }
            }
            lineJson.put("epcs", epcsJson);
            linesJson.put(lineJson);
        }
        json.put("lines", linesJson);
        return json;
    }

    static WholesaleOrder fromStorageJson(JSONObject json) {
        WholesaleOrder order = new WholesaleOrder(json.optString("localId", ""));
        order.customerCode = json.optString("customerCode", "");
        order.customerDescription = json.optString("customerDescription", "");
        order.companyCode = json.optString("companyCode", "");
        order.officeCode = json.optString("officeCode", "");
        order.warehouseCode = json.optString("warehouseCode", "");
        order.warehouseDescription = json.optString("warehouseDescription", "");
        order.orderDate = json.optString("orderDate", "");
        order.description = json.optString("description", "");
        order.status = json.optString("status", STATUS_DRAFT);
        order.createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis());

        JSONArray linesJson = json.optJSONArray("lines");
        if (linesJson != null) {
            for (int i = 0; i < linesJson.length(); i++) {
                JSONObject lineJson = linesJson.optJSONObject(i);
                if (lineJson == null) {
                    continue;
                }
                String barcode = lineJson.optString("barcode", "");
                int qty = lineJson.optInt("qty", 0);
                if (android.text.TextUtils.isEmpty(barcode) || qty <= 0) {
                    continue;
                }
                WholesaleOrderLine line = new WholesaleOrderLine(barcode, qty);
                JSONArray epcsJson = lineJson.optJSONArray("epcs");
                if (epcsJson != null) {
                    for (int e = 0; e < epcsJson.length(); e++) {
                        String epc = epcsJson.optString(e, "");
                        if (!android.text.TextUtils.isEmpty(epc) && !line.epcs.contains(epc)) {
                            line.epcs.add(epc);
                        }
                    }
                }
                order.lines.add(line);
            }
        }
        return order;
    }
}
