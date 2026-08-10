package com.example.armineordermodule;

import android.text.TextUtils;

import java.io.Serializable;

public class CustomerItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String currAccCode;
    public final String currAccDescription;
    public final String districtDescription;
    public final String cityDescription;
    public final String identityNum;
    public final String taxNumber;

    public CustomerItem(String currAccCode, String currAccDescription,
                        String districtDescription, String cityDescription,
                        String identityNum, String taxNumber) {
        this.currAccCode = currAccCode != null ? currAccCode : "";
        this.currAccDescription = currAccDescription != null ? currAccDescription : "";
        this.districtDescription = districtDescription != null ? districtDescription : "";
        this.cityDescription = cityDescription != null ? cityDescription : "";
        this.identityNum = identityNum != null ? identityNum : "";
        this.taxNumber = taxNumber != null ? taxNumber : "";
    }

    public String displayName() {
        if (!TextUtils.isEmpty(currAccDescription)) {
            return currAccDescription;
        }
        if (!TextUtils.isEmpty(currAccCode)) {
            return currAccCode;
        }
        return "-";
    }

    public String locationLine() {
        if (!TextUtils.isEmpty(districtDescription) && !TextUtils.isEmpty(cityDescription)) {
            return districtDescription + " / " + cityDescription;
        }
        if (!TextUtils.isEmpty(cityDescription)) {
            return cityDescription;
        }
        if (!TextUtils.isEmpty(districtDescription)) {
            return districtDescription;
        }
        return "";
    }
}
