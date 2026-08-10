package com.example.armineordermodule

import android.app.Application
import android.widget.Toast
import com.example.armineordermodule.rfid.ConnectResult
import com.example.armineordermodule.rfid.RfidConnectionManager

/**
 * Uygulama process'i olusturuldugunda (herhangi bir Activity acilmadan once) calisir.
 * Sunucu Ayarlari'nda "RFID" anahtari acikken kaydedilmisse, uygulama her acildiginda
 * RFID okuyucuya otomatik baglanmayi dener (arka planda, UI thread'i bloklamadan).
 *
 * ONEMLI: Application.onCreate() Android tarafindan process basina TEK SEFER cagrilir.
 * Yani process olduren bir kapanis olmadan uygulamaya geri donuldugunde (ekran arka plana
 * alinip tekrar acilmasi) veya cikis yapip tekrar giris yapildiginda BU FONKSIYON TEKRAR
 * CALISMAZ. O senaryolar icin asil guvence [MainActivity.onResume]'daki
 * [RfidConnectionManager.autoReconnectIfNeededAsync] cagrisidir; buradaki cagri sadece
 * process ilk baslarken (soguk baslangic) bir baslangic denemesi saglar.
 */
class ArmineApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (RfidPreferences.isRfidEnabled(this)) {
            RfidConnectionManager.autoReconnectIfNeededAsync(this) { result ->
                if (result is ConnectResult.Failure) {
                    Toast.makeText(
                        this,
                        getString(R.string.server_rfid_connect_failed, result.reason),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
