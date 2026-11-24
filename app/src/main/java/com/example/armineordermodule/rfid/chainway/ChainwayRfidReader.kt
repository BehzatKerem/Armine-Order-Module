package com.example.armineordermodule.rfid.chainway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.armineordermodule.rfid.ConnectResult
import com.example.armineordermodule.rfid.RfidDeviceDetector
import com.example.armineordermodule.rfid.RfidReader
import com.example.armineordermodule.rfid.RfidReaderType
import com.example.armineordermodule.rfid.RfidTag
import com.example.armineordermodule.rfid.RfidTagListener
import com.example.armineordermodule.rfid.RfidTriggerListener
import com.example.armineordermodule.rfid.TriggerSource

/**
 * Chainway C5 surucusu. Cihazdaki Keyboard Emulator'un (`com.rscja.scanner`) gonderdigi
 * broadcast'leri dinler. Chainway SDK'si kullanilmaz. Bkz. docs/chainway-rfid.md.
 *
 * Okuyucuyu ve tetigi Keyboard Emulator yonetir; uygulama okumayi baslatamaz ve
 * durduramaz. Ekranlarin geri kalani tetik bas/birak modeline gore yazildigi icin bu
 * surucu tetik olaylarini broadcast akisindan uretir ([TriggerSource.READER]):
 * - Ekran beklerken ilk etiket gelirse [RfidTriggerListener.onTriggerPressed] cagrilir.
 *   O ana kadar gelen etiketler kaybolmasin diye tamponlanir; [startInventory] gelince
 *   dinleyiciye aktarilir.
 * - [BURST_END_TIMEOUT_MS] boyunca yeni broadcast gelmezse
 *   [RfidTriggerListener.onTriggerReleased] cagrilir.
 *
 * Receiver sadece dinleyen biri varken (on plandaki ekran ya da aktif okuma) kayitlidir.
 * Kayit kodda yapilir, manifest'te degil: Android 8+ ortuk broadcast'leri manifest
 * receiver'larina iletmez.
 */
internal class ChainwayRfidReader(context: Context) : RfidReader {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerHandler: Handler by lazy {
        val thread = HandlerThread("rfid-chainway").apply { start() }
        Handler(thread.looper)
    }

    private val lock = Any()

    @Volatile
    private var connected = false
    private var tagListener: RfidTagListener? = null
    private var triggerListener: RfidTriggerListener? = null
    private var receiverRegistered = false
    private var triggerActive = false
    private val pendingTags = ArrayList<RfidTag>()

    private val burstEndRunnable = Runnable { onBurstEnded() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ChainwayBroadcastParser.ACTION_RFID) {
                return
            }
            val tag = ChainwayBroadcastParser.parse(
                epc = intent.getStringExtra(ChainwayBroadcastParser.EXTRA_EPC),
                data = intent.getStringExtra(ChainwayBroadcastParser.EXTRA_DATA),
                rssi = intent.getStringExtra(ChainwayBroadcastParser.EXTRA_RSSI),
            ) ?: return
            onTagBroadcast(tag)
        }
    }

    override val type: RfidReaderType = RfidReaderType.CHAINWAY
    override val triggerSource: TriggerSource = TriggerSource.READER

    /** Keyboard Emulator tetik basili tutuldukca okur; Toggle modu yoktur. */
    override val isHoldToReadOnly: Boolean = true

    override val isConnected: Boolean
        get() = connected

    /**
     * Gercek bir donanim baglantisi yok. Keyboard Emulator kuruluysa "bagli" sayilir.
     * Process mode = BroadcastReceiver ayari uygulamadan dogrulanamaz
     * (bkz. docs/chainway-rfid.md Bolum 2).
     */
    override fun connect(): ConnectResult {
        if (connected) {
            return ConnectResult.Success
        }
        if (!RfidDeviceDetector.isChainwayKeyboardEmulatorInstalled(appContext)) {
            return ConnectResult.Failure(
                "Chainway Keyboard Emulator (${RfidDeviceDetector.CHAINWAY_SCANNER_PACKAGE}) bulunamadi"
            )
        }
        synchronized(lock) {
            connected = true
            updateRegistrationLocked()
        }
        return ConnectResult.Success
    }

    override fun disconnect() {
        synchronized(lock) {
            connected = false
            tagListener = null
            pendingTags.clear()
            triggerActive = false
            updateRegistrationLocked()
        }
        workerHandler.removeCallbacks(burstEndRunnable)
    }

    override fun startInventory(listener: RfidTagListener): Boolean {
        val buffered: List<RfidTag>
        synchronized(lock) {
            if (!connected) {
                return false
            }
            tagListener = listener
            buffered = pendingTags.toList()
            pendingTags.clear()
            updateRegistrationLocked()
        }
        if (buffered.isNotEmpty()) {
            // Diger etiketlerle ayni thread'de ve sirada teslim edilsin.
            workerHandler.post { buffered.forEach(listener::onTagRead) }
        }
        return true
    }

    override fun stopInventory() {
        synchronized(lock) {
            tagListener = null
            pendingTags.clear()
            updateRegistrationLocked()
        }
    }

    override fun isTriggerKey(keyCode: Int): Boolean = false

    override fun setTriggerListener(listener: RfidTriggerListener?) {
        synchronized(lock) {
            triggerListener = listener
            if (listener == null) {
                triggerActive = false
                if (tagListener == null) {
                    pendingTags.clear()
                }
            }
            updateRegistrationLocked()
        }
    }

    private fun onTagBroadcast(tag: RfidTag) {
        val deliverTo: RfidTagListener?
        val pressTarget: RfidTriggerListener?
        synchronized(lock) {
            if (!connected) {
                return
            }
            deliverTo = tagListener
            if (deliverTo == null && pendingTags.size < MAX_PENDING_TAGS) {
                pendingTags.add(tag)
            }
            pressTarget = if (!triggerActive && triggerListener != null) {
                triggerActive = true
                triggerListener
            } else {
                null
            }
        }

        workerHandler.removeCallbacks(burstEndRunnable)
        workerHandler.postDelayed(burstEndRunnable, BURST_END_TIMEOUT_MS)

        pressTarget?.let { mainHandler.post { it.onTriggerPressed() } }
        deliverTo?.onTagRead(tag)
    }

    private fun onBurstEnded() {
        val releaseTarget: RfidTriggerListener?
        synchronized(lock) {
            if (!triggerActive) {
                return
            }
            triggerActive = false
            if (tagListener == null) {
                pendingTags.clear()
            }
            releaseTarget = triggerListener
        }
        releaseTarget?.let { mainHandler.post { it.onTriggerReleased() } }
    }

    /** [lock] tutulurken cagrilmali. */
    private fun updateRegistrationLocked() {
        val shouldListen = connected && (tagListener != null || triggerListener != null)
        if (shouldListen == receiverRegistered) {
            return
        }
        if (shouldListen) {
            try {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(ChainwayBroadcastParser.ACTION_RFID),
                    null,
                    workerHandler,
                    // Broadcast baska uygulamadan geliyor; Android 14+ bu bayragi zorunlu tutar.
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
            } catch (t: Throwable) {
                Log.w(TAG, "RFID receiver kaydedilemedi", t)
            }
        } else {
            runCatching { appContext.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "RFID receiver kaydi silinemedi", it) }
            receiverRegistered = false
        }
    }

    companion object {
        private const val TAG = "ChainwayRfidReader"

        /**
         * Bu sure boyunca broadcast gelmezse tetik birakilmis sayilir. Testte surekli modda
         * broadcast'ler ~250 ms aralikla geldi (19 sn'de 78 broadcast); 600 ms, tetik basili
         * dururken yanlislikla "birakildi" uretmeyecek kadar genis.
         */
        private const val BURST_END_TIMEOUT_MS = 600L

        /** Okuma baslamadan once tamponlanacak en fazla etiket. */
        private const val MAX_PENDING_TAGS = 512
    }
}
