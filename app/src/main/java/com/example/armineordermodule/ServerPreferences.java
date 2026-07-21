package com.example.armineordermodule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerPreferences {

    private static final String PREFS_NAME = "server_prefs";
    private static final String KEY_SERVER_URL = "server_url";

    private static final Pattern HAS_SCHEME =
            Pattern.compile("(?i)^https?://.+");
    private static final Pattern SERVER_URL_PATTERN =
            Pattern.compile("(?i)^https?://([^/:\\s]+)(:\\d+)?(/.*)?$");
    private static final Pattern LOCALHOST_HOST =
            Pattern.compile("(?i)^(https?://)(localhost|127\\.0\\.0\\.1)(?=[:/]|$)");

    private ServerPreferences() {
    }

    public static void saveServerUrl(Context context, String serverUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Kayitta sadece bosluk temizligi + protokol; localhost yazim hatasi duzeltilmez.
        prefs.edit().putString(KEY_SERVER_URL, normalizeForStorage(serverUrl)).apply();
    }

    public static String getServerUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeForStorage(prefs.getString(KEY_SERVER_URL, ""));
    }

    /**
     * API istekleri icin kullan: trim, protokol, sondaki /, emulatore localhost -> 10.0.2.2.
     */
    public static String normalizeServerForAndroidClient(String serverUrl) {
        String cleaned = normalizeForStorage(serverUrl);
        if (TextUtils.isEmpty(cleaned)) {
            return "";
        }

        if (isEmulator()) {
            Matcher matcher = LOCALHOST_HOST.matcher(cleaned);
            if (matcher.find()) {
                cleaned = matcher.replaceFirst(
                        Matcher.quoteReplacement(matcher.group(1) + "10.0.2.2"));
            }
        }

        return cleaned;
    }

    public static boolean isValidServerUrl(String serverUrl) {
        String cleaned = normalizeForStorage(serverUrl);
        if (TextUtils.isEmpty(cleaned)) {
            return false;
        }
        Matcher matcher = SERVER_URL_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            return false;
        }
        String host = matcher.group(1);
        return !TextUtils.isEmpty(host);
    }

    /** Bosluklari temizler, eksik http:// ekler, sondaki / kaldirir. */
    public static String normalizeForStorage(String serverUrl) {
        if (serverUrl == null) {
            return "";
        }

        String cleaned = serverUrl.trim().replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            return "";
        }

        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        if (!HAS_SCHEME.matcher(cleaned).matches()) {
            cleaned = "http://" + cleaned;
        }

        return cleaned;
    }

    /** @deprecated {@link #normalizeForStorage(String)} kullan. */
    public static String normalizeServerUrl(String serverUrl) {
        return normalizeForStorage(serverUrl);
    }

    public static boolean isEmulator() {
        String fingerprint = Build.FINGERPRINT != null
                ? Build.FINGERPRINT.toLowerCase(Locale.US) : "";
        String model = Build.MODEL != null
                ? Build.MODEL.toLowerCase(Locale.US) : "";
        String product = Build.PRODUCT != null
                ? Build.PRODUCT.toLowerCase(Locale.US) : "";
        String manufacturer = Build.MANUFACTURER != null
                ? Build.MANUFACTURER.toLowerCase(Locale.US) : "";
        String brand = Build.BRAND != null
                ? Build.BRAND.toLowerCase(Locale.US) : "";
        String device = Build.DEVICE != null
                ? Build.DEVICE.toLowerCase(Locale.US) : "";
        String hardware = Build.HARDWARE != null
                ? Build.HARDWARE.toLowerCase(Locale.US) : "";

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || manufacturer.contains("genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk".equals(product)
                || product.contains("sdk")
                || product.contains("emulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu");
    }
}
