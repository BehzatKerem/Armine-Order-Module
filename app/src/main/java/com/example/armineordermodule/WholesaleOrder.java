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
 * {@code localId}: yalnizca cihaz ici taslak yonetimi (API'ye gitmez).
 * {@code orderGuid}: siparis olusturulunca uretilir; API'ye {@code OrderGuid} olarak gider
 * (sunucu tarafinda tekillik / tekrar gonderim).
 * customerDescription, warehouseDescription, status, createdAtMillis gosterim / lokal
 * yonetim alanlaridir; {@link #toApiJson()} icinde yoktur.
 */
public class WholesaleOrder {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_SEND = "PENDING_SEND";
    /** Nebim'e basariyla aktarildi (Aktar butonu gizlenir). */
    public static final String STATUS_SENT = "SENT";

    private static final int MODEL_TYPE = 5;

    public final String localId;
    /** API'ye gonderilen siparis GUID'i (localId'den bagimsiz). */
    public String orderGuid;
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
    /** Sunucudan donen headerId (basarili gonderim). */
    public int headerId;
    /** Nebim aktarimi tamamlandi mi. */
    public boolean isNebimSend;
    public final List<WholesaleOrderLine> lines = new ArrayList<>();

    public WholesaleOrder(String localId) {
        this.localId = !android.text.TextUtils.isEmpty(localId) ? localId : UUID.randomUUID().toString();
    }

    public static WholesaleOrder createNew() {
        WholesaleOrder order = new WholesaleOrder(UUID.randomUUID().toString());
        order.orderGuid = UUID.randomUUID().toString();
        order.status = STATUS_DRAFT;
        order.createdAtMillis = System.currentTimeMillis();
        return order;
    }

    /** Eski taslaklarda OrderGuid yoksa uretir (gonderim oncesi guvenlik). */
    public void ensureOrderGuid() {
        if (android.text.TextUtils.isEmpty(orderGuid)) {
            orderGuid = UUID.randomUUID().toString();
        }
    }

    /** Nebim'e basariyla aktarilmis (tekrar Aktar yok). */
    public boolean isTransferred() {
        return STATUS_SENT.equals(status) || isNebimSend;
    }

    /** Basarili WholesaleCreate yanitina gore lokal durumu gunceller. */
    public void markTransferred(int headerId, String apiOrderGuid, boolean nebimSend) {
        this.isNebimSend = nebimSend;
        if (headerId > 0) {
            this.headerId = headerId;
        }
        if (!android.text.TextUtils.isEmpty(apiOrderGuid)) {
            this.orderGuid = apiOrderGuid;
        }
        if (nebimSend) {
            this.status = STATUS_SENT;
        }
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
     * API WholesaleCreate govdesi (Swagger camelCase + database).
     * @param database AuthPreferences.getDefaultDatabase()
     */
    public JSONObject toApiJson(String database) throws JSONException {
        ensureOrderGuid();
        JSONObject json = new JSONObject();
        json.put("modelType", MODEL_TYPE);
        json.put("orderGuid", orderGuid);
        json.put("customerCode", customerCode != null ? customerCode : "");
        json.put("companyCode", companyCode != null ? companyCode : "");
        json.put("officeCode", officeCode != null ? officeCode : "");
        // Swagger'da bu alan PascalCase: WareHouseCode
        json.put("WareHouseCode", warehouseCode != null ? warehouseCode : "");
        json.put("orderDate", orderDate != null ? orderDate : "");
        json.put("isCompleted", true);
        json.put("isReturn", false);
        json.put("description", description != null ? description : "");
        json.put("database", database != null ? database : "");

        JSONArray epcsJson = new JSONArray();
        for (String epc : collectAllEpcs()) {
            epcsJson.put(epc);
        }
        json.put("epcs", epcsJson);
        return json;
    }

    /** Lokal saklama (SharedPreferences) icin tum alanlari kapsayan JSON. */
    JSONObject toStorageJson() throws JSONException {
        ensureOrderGuid();
        JSONObject json = new JSONObject();
        json.put("localId", localId);
        json.put("orderGuid", orderGuid);
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
        json.put("headerId", headerId);
        json.put("isNebimSend", isNebimSend);

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
        order.orderGuid = json.optString("orderGuid", "");
        order.ensureOrderGuid();
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
        order.headerId = json.optInt("headerId", 0);
        order.isNebimSend = json.optBoolean("isNebimSend", false);
        if (order.isNebimSend && !STATUS_SENT.equals(order.status)) {
            order.status = STATUS_SENT;
        }

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
