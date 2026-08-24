package com.example.armineordermodule;

/** RFID el terminali sayimindaki urun satiri (API items[]). */
public class RfidHandheldCountingItem {

    public int id;
    public String barcode = "";
    public int quantity;

    public RfidHandheldCountingItem() {
    }

    public RfidHandheldCountingItem(int id, String barcode, int quantity) {
        this.id = id;
        this.barcode = barcode != null ? barcode : "";
        this.quantity = quantity;
    }
}
