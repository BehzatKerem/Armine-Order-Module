package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/** Sunucu Ayarlari ekranindaki "RFID" anahtarinin acik/kapali durumunu saklar. */
public final class RfidPreferences {

    private static final String PREFS_NAME = "rfid_prefs";
    private static final String KEY_RFID_ENABLED = "rfid_enabled";
    private static final String KEY_PRESS_AND_HOLD_MODE = "press_and_hold_mode";
    private static final String KEY_READER_TYPE = "reader_type";
    /** Bu surumle birlikte acilis baglantisi varsayilan oldu. Bayrak bir kez yazilir;
     *  kullanici anahtari sonradan kapatirsa tekrar acilmaz. */
    private static final String KEY_AUTO_CONNECT_DEFAULT = "auto_connect_default_v2";

    private RfidPreferences() {
    }

    public static boolean isRfidEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_RFID_ENABLED, true);
    }

    /**
     * El terminalinde okuyucu acilista bagli olmali. Daha once anahtar hic
     * kaydedilmemisse veya eski kurulumda kapali kalmissa bir kez acilir.
     */
    public static void enableAutoConnectOnce(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_AUTO_CONNECT_DEFAULT, false)) {
            return;
        }
        prefs.edit()
                .putBoolean(KEY_RFID_ENABLED, true)
                .putBoolean(KEY_AUTO_CONNECT_DEFAULT, true)
                .apply();
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

    /**
     * Elle secilmis RFID cihaz tipi ({@link com.example.armineordermodule.rfid.RfidReaderType#getPrefValue()}).
     * {@code null} ise otomatik tespit kullanilir. Bkz. {@link com.example.armineordermodule.rfid.RfidDeviceDetector}.
     */
    @Nullable
    public static String getReaderTypeOverride(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_READER_TYPE, null);
    }

    /** {@code null} vermek otomatik tespite dondurur. */
    public static void setReaderTypeOverride(Context context, @Nullable String prefValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (prefValue == null) {
            editor.remove(KEY_READER_TYPE);
        } else {
            editor.putString(KEY_READER_TYPE, prefValue);
        }
        editor.apply();
    }
}
