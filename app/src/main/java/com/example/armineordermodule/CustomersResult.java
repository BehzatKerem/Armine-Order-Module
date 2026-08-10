package com.example.armineordermodule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomersResult {

    public final String databaseName;
    public final int totalCount;
    public final List<CustomerItem> customers;

    public CustomersResult(String databaseName, int totalCount, List<CustomerItem> customers) {
        this.databaseName = databaseName != null ? databaseName : "";
        this.totalCount = totalCount;
        this.customers = customers != null
                ? Collections.unmodifiableList(new ArrayList<>(customers))
                : Collections.emptyList();
    }
}
