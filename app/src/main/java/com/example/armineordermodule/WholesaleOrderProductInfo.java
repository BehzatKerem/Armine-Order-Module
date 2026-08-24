package com.example.armineordermodule;

import android.text.TextUtils;

/**
 * Ozet ekraninda gosterilen urun / stok satiri (API enrich yaniti + lokal adet).
 * Siparis gonderimine dahil edilmez; yalnizca UI.
 */
public class WholesaleOrderProductInfo {

    public final String barcode;
    public final String productDescription;
    public final String itemCode;
    public final String colorCode;
    public final String colorDescription;
    /** Depodaki stok; null = API bilgisi yok. */
    public final Integer stock;
    /** Bu barkoda dusen EPC adedi. */
    public final int qty;
    public final boolean found;

    public WholesaleOrderProductInfo(
            String barcode,
            String productDescription,
            String itemCode,
            String colorCode,
            String colorDescription,
            Integer stock,
            int qty,
            boolean found) {
        this.barcode = barcode != null ? barcode : "";
        this.productDescription = productDescription != null ? productDescription : "";
        this.itemCode = itemCode != null ? itemCode : "";
        this.colorCode = colorCode != null ? colorCode : "";
        this.colorDescription = colorDescription != null ? colorDescription : "";
        this.stock = stock;
        this.qty = qty;
        this.found = found;
    }

    /** Stok uyarisi gosterilsin mi (bulunamadi veya stok &lt; istenen adet). */
    public boolean hasStockIssue() {
        if (!found) {
            return true;
        }
        if (stock == null) {
            return true;
        }
        return stock < qty;
    }

    public String displayTitle() {
        if (!TextUtils.isEmpty(productDescription)) {
            return productDescription;
        }
        return barcode;
    }
}
