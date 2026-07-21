package com.example.armineordermodule;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bir depo kaydi. Her kaydin TEK bir officeCode'u vardir (bir depo bir ofise baglidir),
 * ama ayni officeCode birden fazla WarehouseItem'da tekrar edebilir
 * (bir ofisin birden fazla deposu olabilir).
 */
public class WarehouseItem {

    public final String id;
    public final String userId;
    public final String databaseName;
    public final String officeCode;
    public final String warehouseCode;
    public final String warehouseDescription;
    public final String storeCode;
    public final boolean isDefault;
    public final String userName;

    public WarehouseItem(String id, String userId, String databaseName, String officeCode,
                          String warehouseCode, String warehouseDescription, String storeCode,
                          boolean isDefault, String userName) {
        this.id = id != null ? id : "";
        this.userId = userId != null ? userId : "";
        this.databaseName = databaseName != null ? databaseName : "";
        this.officeCode = officeCode != null ? officeCode : "";
        this.warehouseCode = warehouseCode != null ? warehouseCode : "";
        this.warehouseDescription = warehouseDescription != null ? warehouseDescription : "";
        this.storeCode = storeCode != null ? storeCode : "";
        this.isDefault = isDefault;
        this.userName = userName != null ? userName : "";
    }

    /** Ofis + depo kodunu birlestirilmis gosterim icin kullan. */
    public String officeWarehouseCode() {
        if (TextUtils.isEmpty(officeCode) && TextUtils.isEmpty(warehouseCode)) {
            return "";
        }
        if (TextUtils.isEmpty(officeCode)) {
            return warehouseCode;
        }
        if (TextUtils.isEmpty(warehouseCode)) {
            return officeCode;
        }
        return officeCode + "-" + warehouseCode;
    }

    /** Varsayilanlar ustte, sonra ofis kodu, sonra depo kodu. */
    public static List<WarehouseItem> sortedForDisplay(List<WarehouseItem> warehouses) {
        List<WarehouseItem> sorted = new ArrayList<>();
        if (warehouses != null) {
            sorted.addAll(warehouses);
        }
        Collections.sort(sorted, displayComparator());
        return sorted;
    }

    public static Comparator<WarehouseItem> displayComparator() {
        return (a, b) -> {
            if (a.isDefault != b.isDefault) {
                return a.isDefault ? -1 : 1;
            }
            int officeCompare = a.officeCode.compareToIgnoreCase(b.officeCode);
            if (officeCompare != 0) {
                return officeCompare;
            }
            return a.warehouseCode.compareToIgnoreCase(b.warehouseCode);
        };
    }
}
