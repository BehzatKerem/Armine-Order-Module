package com.example.armineordermodule;

import android.text.TextUtils;

/**
 * GetProductByBarcode yaniti: bir barkodun urun bilgisi ve secilen depodaki stok.
 * Depo transferi ekraninda gruplu satirlarda gosterilir; transfer gonderimine dahil edilmez.
 */
public class WarehouseProductInfo {

    public final String barcode;
    /** false: barkod Nebim'de tanimli degil (hata degil, "urun bulunamadi"). */
    public final boolean found;
    public final String productName;
    public final String itemCode;
    public final String colorCode;
    /** Beden (Nebim ItemDim1Code). */
    public final String itemMeasure;
    /** Secilen depodaki toplam kullanilabilir stok (totalAvailableInventoryQty1). */
    public final double availableQty;

    public WarehouseProductInfo(String barcode, boolean found, String productName, String itemCode,
                                String colorCode, String itemMeasure, double availableQty) {
        this.barcode = barcode != null ? barcode : "";
        this.found = found;
        this.productName = productName != null ? productName : "";
        this.itemCode = itemCode != null ? itemCode : "";
        this.colorCode = colorCode != null ? colorCode : "";
        this.itemMeasure = itemMeasure != null ? itemMeasure : "";
        this.availableQty = availableQty;
    }

    /** Urun tanimli ama depoda kullanilabilir stogu yok: listeden cikarilir. */
    public boolean isOutOfStock() {
        return found && availableQty <= 0d;
    }

    /** Transfere girmemesi gereken urun: Nebim'de tanimsiz ya da depoda stogu yok. */
    public boolean shouldRemoveFromTransfer() {
        return !found || isOutOfStock();
    }

    public String displayName() {
        return !TextUtils.isEmpty(productName) ? productName : barcode;
    }

    /** 5.0 -> "5", 2.5 -> "2.5". */
    public String formattedAvailableQty() {
        if (availableQty == Math.rint(availableQty)) {
            return String.valueOf((long) availableQty);
        }
        return String.valueOf(availableQty);
    }
}
