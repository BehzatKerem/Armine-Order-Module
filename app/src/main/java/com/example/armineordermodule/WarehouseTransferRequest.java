package com.example.armineordermodule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * POST /api/FrogMobileHandheldWarehouse/CreateWarehouseTransfer govdesi.
 * Alan adlari swagger camelCase.
 */
public final class WarehouseTransferRequest {

    public static final int MODEL_TYPE = 109;
    public static final String APPLICATION_CODE = "Inner";
    public static final int INNER_PROCESS_TYPE = 4;

    private WarehouseTransferRequest() {
    }

    public static JSONObject toApiJson(String transferGuid,
                                        String companyCode,
                                        String description,
                                        String officeCode,
                                        String warehouseCode,
                                        String toOfficeCode,
                                        String toWarehouseCode,
                                        String database,
                                        List<String> epcs) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("modelType", MODEL_TYPE);
        json.put("transferGuid", transferGuid != null ? transferGuid : "");
        json.put("applicationCode", APPLICATION_CODE);
        json.put("companyCode", companyCode != null ? companyCode : "");
        json.put("description", description != null ? description : "");
        json.put("innerProcessType", INNER_PROCESS_TYPE);
        json.put("isCompleted", true);

        JSONArray epcsJson = new JSONArray();
        if (epcs != null) {
            for (String epc : epcs) {
                if (epc != null && !epc.isEmpty()) {
                    epcsJson.put(epc);
                }
            }
        }
        json.put("epcs", epcsJson);

        json.put("officeCode", officeCode != null ? officeCode : "");
        json.put("storeCode", "");
        json.put("toOfficeCode", toOfficeCode != null ? toOfficeCode : "");
        json.put("toStoreCode", "");
        json.put("toWarehouseCode", toWarehouseCode != null ? toWarehouseCode : "");
        json.put("warehouseCode", warehouseCode != null ? warehouseCode : "");
        json.put("database", database != null ? database : "");
        return json;
    }
}
