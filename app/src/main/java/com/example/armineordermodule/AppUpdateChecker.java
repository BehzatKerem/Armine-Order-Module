package com.example.armineordermodule;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Process basina bir kez GitHub'daki son surumu sorar.
 * Application.onCreate yalnizca soguk baslangicta calistigi icin
 * uygulama arka plandan donunce tekrar sorulmaz.
 */
final class AppUpdateChecker {

    interface Listener {
        void onUpdateAvailable(String versionName);
    }

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/BehzatKerem/Armine-Order-Module/releases/latest";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static boolean started;
    private static String availableVersion;
    private static String apkUrl;

    private AppUpdateChecker() {
    }

    static void start() {
        if (started) {
            return;
        }
        started = true;

        Request request = new Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ArmineOrderModule")
                .build();

        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                // Ag yoksa veya GitHub cevap vermezse guncelleme gostermeyiz.
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        return;
                    }
                    publish(parseOffer(body.string(), BuildConfig.VERSION_NAME));
                } catch (Exception ignored) {
                    // Bozuk cevapta buton cikmaz.
                }
            }
        });
    }

    static void listen(Listener listener) {
        LISTENERS.add(listener);
        String version = availableVersion;
        if (version != null) {
            listener.onUpdateAvailable(version);
        }
    }

    static void unlisten(Listener listener) {
        LISTENERS.remove(listener);
    }

    static String apkUrl() {
        return apkUrl;
    }

    private static void publish(Offer offer) {
        if (offer == null) {
            return;
        }
        MAIN.post(() -> {
            availableVersion = offer.versionName;
            apkUrl = offer.apkUrl;
            for (Listener listener : LISTENERS) {
                listener.onUpdateAvailable(offer.versionName);
            }
        });
    }

    private static Offer parseOffer(String json, String localVersion) throws Exception {
        JSONObject release = new JSONObject(json);
        String tag = release.optString("tag_name", "");
        String remoteVersion = tag.startsWith("v") || tag.startsWith("V")
                ? tag.substring(1)
                : tag;
        if (!isNewer(remoteVersion, localVersion)) {
            return null;
        }

        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            String url = asset.optString("browser_download_url", "");
            if (name.endsWith(".apk") && !url.isEmpty()) {
                return new Offer(remoteVersion, url);
            }
        }
        return null;
    }

    static boolean isNewer(String remoteVersion, String localVersion) {
        String[] remote = remoteVersion.split("\\.");
        String[] local = localVersion.split("\\.");
        int count = Math.max(remote.length, local.length);
        for (int i = 0; i < count; i++) {
            int remotePart = i < remote.length ? leadingNumber(remote[i]) : 0;
            int localPart = i < local.length ? leadingNumber(local[i]) : 0;
            if (remotePart != localPart) {
                return remotePart > localPart;
            }
        }
        return false;
    }

    private static int leadingNumber(String part) {
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static final class Offer {
        final String versionName;
        final String apkUrl;

        Offer(String versionName, String apkUrl) {
            this.versionName = versionName;
            this.apkUrl = apkUrl;
        }
    }
}
