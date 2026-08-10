package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.List;

public class WholesaleOrderLine {

    public final String barcode;
    public int qty;
    /** Bu satira ait okutulmus EPC'ler (API Lines[].Epcs olarak gonderilir). */
    public final List<String> epcs = new ArrayList<>();

    public WholesaleOrderLine(String barcode, int qty) {
        this.barcode = barcode != null ? barcode : "";
        this.qty = qty;
    }
}
