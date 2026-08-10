package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;

/** Sunucu Ayarlari ekranindaki "RFID" anahtarinin acik/kapali durumunu saklar. */
public final class RfidPreferences {

    private static final String PREFS_NAME = "rfid_prefs";
    private static final String KEY_RFID_ENABLED = "rfid_enabled";
    private static final String KEY_PRESS_AND_HOLD_MODE = "press_and_hold_mode";

    private RfidPreferences() {
    }

    public static boolean isRfidEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_RFID_ENABLED, false);
    }

    public static void setRfidEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_RFID_ENABLED, enabled).apply();
    }

    /**
     * Fiziksel tetik tusu modu: {@code true} ise "Bas-Cek" (momentary — tus basili tutulunca
     * okur, birakinca durur), {@code false} (varsayilan) ise "Toggle" (her basis okumayi
     * ac/kapa yapar). Bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 6.3.
     */
    public static boolean isPressAndHoldMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PRESS_AND_HOLD_MODE, false);
    }

    public static void setPressAndHoldMode(Context context, boolean pressAndHold) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PRESS_AND_HOLD_MODE, pressAndHold).apply();
    }
}
