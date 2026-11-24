package com.example.armineordermodule.rfid

import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.armineordermodule.RfidPreferences

/**
 * Tarama ekranlarinin fiziksel tetik davranisini tek yerde toplar. Okuyucu tipinden
 * bagimsizdir:
 * - BLD: tetik `KeyEvent` olarak gelir. Activity, [onKeyDown] / [onKeyUp]'a yonlendirir.
 * - Chainway: tetik olaylarini surucu uretir; bu sinif ekran on plandayken onlari dinler.
 *
 * Davranis:
 * - **Kilitli tarama** ([Host.isScanLatched], ör. Iptal Barkod diyalogu): basinca okuma
 *   baslar, birakinca durmaz.
 * - **Bas-Cek**: basinca baslar, birakinca durur. Okuyucu [RfidReader.isHoldToReadOnly]
 *   ise ayar ne olursa olsun bu mod kullanilir.
 * - **Toggle**: her basis okumayi acar/kapatir.
 *
 * Ekran arka plana gecince aktif okuma durdurulur.
 *
 * Kullanim (Activity):
 * ```java
 * trigger = new RfidTriggerController(this, host);
 * public boolean onKeyDown(int c, KeyEvent e) { return trigger.onKeyDown(c, e) || super.onKeyDown(c, e); }
 * public boolean onKeyUp(int c, KeyEvent e)   { return trigger.onKeyUp(c, e)   || super.onKeyUp(c, e); }
 * ```
 */
class RfidTriggerController(
    private val activity: ComponentActivity,
    private val host: Host,
) : DefaultLifecycleObserver, RfidTriggerListener {

    interface Host {
        fun isScanning(): Boolean
        fun startScanning()
        fun stopScanning()

        /** `true` iken tetik sadece okumayi baslatir; birakmak durdurmaz. */
        fun isScanLatched(): Boolean
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    /** @return olay tetige aitse `true` (tuketildi). */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!RfidConnectionManager.isTriggerKey(keyCode)) {
            // Yeni bir cihazin tetik kodunu bulmak icin: adb logcat -s RFID_TRIGGER_KEY
            Log.d(KEY_LOG_TAG, "keyCode=$keyCode repeatCount=${event?.repeatCount}")
            return false
        }
        if (event == null || event.repeatCount == 0) {
            onTriggerPressed()
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!RfidConnectionManager.isTriggerKey(keyCode)) {
            return false
        }
        onTriggerReleased()
        return true
    }

    override fun onTriggerPressed() {
        if (!isResumed() || !RfidConnectionManager.isConnected) {
            return
        }
        if (host.isScanLatched() || isHoldToRead()) {
            if (!host.isScanning()) {
                host.startScanning()
            }
        } else if (host.isScanning()) {
            host.stopScanning()
        } else {
            host.startScanning()
        }
    }

    override fun onTriggerReleased() {
        if (host.isScanning() && !host.isScanLatched() && isHoldToRead()) {
            host.stopScanning()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        RfidConnectionManager.setTriggerListener(this)
    }

    override fun onPause(owner: LifecycleOwner) {
        RfidConnectionManager.setTriggerListener(null)
        if (host.isScanning()) {
            host.stopScanning()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
    }

    private fun isHoldToRead(): Boolean =
        RfidConnectionManager.isHoldToReadOnly || RfidPreferences.isPressAndHoldMode(activity)

    private fun isResumed(): Boolean =
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private companion object {
        const val KEY_LOG_TAG = "RFID_TRIGGER_KEY"
    }
}
