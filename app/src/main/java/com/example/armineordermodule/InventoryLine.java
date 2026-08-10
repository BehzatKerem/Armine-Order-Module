package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.List;

/** Sayim lokasyonundaki urun satiri (barkod + miktar + EPC listesi). */
public class InventoryLine {

    public final String barcode;
    public int qty;
    public final List<String> epcs = new ArrayList<>();

    public InventoryLine(String barcode, int qty) {
        this.barcode = barcode != null ? barcode : "";
        this.qty = qty;
    }
}
