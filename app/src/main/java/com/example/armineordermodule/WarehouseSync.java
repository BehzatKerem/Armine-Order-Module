package com.example.armineordermodule;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Kullanicinin varsayilan depolarini sunucudan ceker ve {@link WarehousePreferences}'a yazar.
 *
 * Depolarim ekranindaki "Varsayilan Depolari Guncelle" butonu ve her giris (kullanici adi /
 * sifre ile giris ya da gecerli oturumla uygulama acilisi) ayni yolu kullanir: kullanicinin
 * depolari sunucuda degismis olabilir. Hata olursa cihazdaki liste korunur, boylece
 * cevrimdisi calisma bozulmaz.
 */
public final class WarehouseSync {

    private static final String TAG = "WarehouseSync";

    public interface Callback {
        /** Ana thread'de cagrilir. */
        void onSuccess(WarehousesResult result);

        /** Ana thread'de cagrilir; cihazdaki liste degismemistir. */
        void onError(String message);
    }

    private WarehouseSync() {
    }

    public static void refresh(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();

        String serverUrl = ServerPreferences.getServerUrl(appContext);
        if (TextUtils.isEmpty(serverUrl)) {
            callback.onError(appContext.getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(appContext);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(appContext)) {
            callback.onError(appContext.getString(R.string.warehouses_session_required));
            return;
        }

        String databaseName = AuthPreferences.getDefaultDatabase(appContext);
        if (TextUtils.isEmpty(databaseName)) {
            callback.onError(appContext.getString(R.string.warehouses_database_missing));
            return;
        }

        WarehouseApiClient.getWarehouses(serverUrl, token, databaseName,
                new WarehouseApiClient.WarehousesCallback() {
                    @Override
                    public void onSuccess(WarehousesResult result) {
                        WarehousePreferences.save(appContext, result);
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /**
     * Giris sonrasi sessiz guncelleme. Kullanici beklemez; sadece hata olursa
     * {@code onError} ile haber verilir (null ise sadece loglanir).
     */
    public static void refreshInBackground(Context context, @Nullable Callback onDone) {
        refresh(context, new Callback() {
            @Override
            public void onSuccess(WarehousesResult result) {
                Log.i(TAG, "Varsayilan depolar guncellendi: " + result.warehouses.size());
                if (onDone != null) {
                    onDone.onSuccess(result);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Varsayilan depolar guncellenemedi: " + message);
                if (onDone != null) {
                    onDone.onError(message);
                }
            }
        });
    }
}
