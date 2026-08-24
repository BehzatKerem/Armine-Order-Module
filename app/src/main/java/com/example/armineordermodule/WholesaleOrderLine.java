package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.List;

public class WholesaleOrderLine {

    /**
     * Satir kimligi: artik parse edilmis barkod degil, ham EPC.
     * (Alan adi geriye uyumluluk / API {@code Barcode} icin korunuyor.)
     */
    public final String barcode;
    /** Ham EPC modelinde her satir 1 adet (lokal). */
    public int qty;
    /** Bu satira ait EPC'ler (lokal tekillik; gonderimde collectAllEpcs). */
    public final List<String> epcs = new ArrayList<>();

    public WholesaleOrderLine(String barcode, int qty) {
        this.barcode = barcode != null ? barcode : "";
        this.qty = qty;
    }
}
