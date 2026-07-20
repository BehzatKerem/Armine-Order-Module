package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;

public final class ServerPreferences {

    private static final String PREFS_NAME = "server_prefs";
    private static final String KEY_SERVER_URL = "server_url";

    private ServerPreferences() {
    }

    public static void saveServerUrl(Context context, String serverUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, serverUrl.trim()).apply();
    }

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }
}
