package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gelistirici modu. Profil ekraninda kullanici adina 5 sn basili tutunca acilir/kapanir.
 * Cihaz geneli bir ayardir, oturumdan bagimsizdir; cikis yapinca kapanir.
 *
 * Acikken Nebim'e gonderilmis transfer ve siparislerin cihazdaki kaydi silinebilir.
 * Bu sadece lokal veriyi siler; Nebim'deki kayda dokunmaz.
 */
public final class DeveloperModePreferences {

    private static final String PREFS_NAME = "developer_mode_prefs";
    private static final String KEY_ENABLED = "enabled";

    private DeveloperModePreferences() {
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
