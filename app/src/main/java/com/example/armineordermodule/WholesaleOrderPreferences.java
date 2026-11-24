package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;

/** Toptan siparis kurulum ekraninin hatirlanan secimleri. */
public final class WholesaleOrderPreferences {

    private static final String PREFS_NAME = "wholesale_order_setup_prefs";
    private static final String KEY_STOCK_CONTROL = "stock_control_enabled";

    private WholesaleOrderPreferences() {
    }

    /** Son secilen stok kontrolu; ilk kullanimda acik. */
    public static boolean isStockControlEnabled(Context context) {
        return prefs(context).getBoolean(KEY_STOCK_CONTROL, true);
    }

    public static void setStockControlEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_STOCK_CONTROL, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
