package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WarehousesResult {

    public final String databaseName;
    public final String defaultOfficeCode;
    public final String defaultWarehouseCode;
    public final String defaultWarehouseDescription;
    public final WarehouseItem defaultWarehouse;
    public final List<WarehouseItem> warehouses;

    public WarehousesResult(String databaseName, String defaultOfficeCode, String defaultWarehouseCode,
                             String defaultWarehouseDescription, WarehouseItem defaultWarehouse,
                             List<WarehouseItem> warehouses) {
        this.databaseName = databaseName != null ? databaseName : "";
        this.defaultOfficeCode = defaultOfficeCode != null ? defaultOfficeCode : "";
        this.defaultWarehouseCode = defaultWarehouseCode != null ? defaultWarehouseCode : "";
        this.defaultWarehouseDescription = defaultWarehouseDescription != null
                ? defaultWarehouseDescription : "";
        this.defaultWarehouse = defaultWarehouse;
        this.warehouses = warehouses != null
                ? Collections.unmodifiableList(new ArrayList<>(warehouses))
                : Collections.emptyList();
    }
}
