package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public final class AuthPreferences {

    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at_millis";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AUTH_SOURCE = "auth_source";

    private AuthPreferences() {
    }

    public static void saveSession(Context context, LoginResult result) {
        prefs(context).edit()
                .putString(KEY_TOKEN, result.token)
                .putLong(KEY_EXPIRES_AT, result.expiresAtMillis)
                .putString(KEY_USER_ID, result.userId)
                .putString(KEY_USER_NAME, result.userName)
                .putString(KEY_EMAIL, result.email)
                .putString(KEY_AUTH_SOURCE, result.authSource)
                .apply();
    }

    public static boolean hasValidSession(Context context) {
        String token = getToken(context);
        long expiresAtMillis = prefs(context).getLong(KEY_EXPIRES_AT, 0L);
        return !TextUtils.isEmpty(token) && expiresAtMillis > System.currentTimeMillis();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, "");
    }

    public static String getUserName(Context context) {
        return prefs(context).getString(KEY_USER_NAME, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static void clearSession(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
