package com.example.armineordermodule.rfid

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.armineordermodule.R
import com.example.armineordermodule.RfidPreferences
import com.google.android.material.button.MaterialButton

/**
 * RFID el terminali entegrasyonu icin bagimsiz test ekrani (docs/RFID_SDK_ENTEGRASYON.md
 * Bolum 10.3 kapsaminda). Room / siparis akisiyla henuz bagli degil; okunan EPC'leri
 * listeler. Nihai siparis ekrani hazir oldugunda bu ekrandaki mantik `BarcodeScannerProvider`
 * arkasina tasinacak (PRD SS6.4).
 *
 * Baglanti/tarama islemleri [RfidConnectionManager] uzerinden, uygulama genelinde
 * PAYLASILAN tek bir [RfidManager] ornegiyle yapilir (Sunucu Ayarlari'ndaki "RFID"
 * anahtari da ayni ornegi kullanir) — boylece iki ayri baglantinin ayni seri porta
 * cakismasi engellenir.
 */
class RfidScanActivity : AppCompatActivity() {

    private lateinit var rfidManager: RfidManager
    private lateinit var adapter: RfidTagListAdapter

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvTagStats: TextView
    private lateinit var tvRfidError: TextView
    private lateinit var tvEmptyHint: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnToggleScan: MaterialButton

    // SDK'nin kendi okuma thread'inden (handleTagRead) VE UI thread'inden (clearTags) ayni anda
    // erisiliyor; yogun okumada (binlerce okuma/sn) bu kilit olmadan ConcurrentModificationException
    // ile cokuyordu (gercek cihazda gozlemlendi).
    private val tagsLock = Any()
    private val scannedTags = LinkedHashMap<String, ScannedTagUi>()
    private var isScanning = false
    private var totalReadCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rfid_scan)

        rfidManager = RfidConnectionManager.getManager(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvTagStats = findViewById(R.id.tvTagStats)
        tvRfidError = findViewById(R.id.tvRfidError)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        btnConnect = findViewById(R.id.btnConnect)
        btnToggleScan = findViewById(R.id.btnToggleScan)
        val btnClear = findViewById<TextView>(R.id.btnClear)
        val rvTags = findViewById<RecyclerView>(R.id.rvTags)

        adapter = RfidTagListAdapter()
        rvTags.layoutManager = LinearLayoutManager(this)
        rvTags.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnConnect.setOnClickListener { onConnectClicked() }
        btnToggleScan.setOnClickListener { toggleScanning() }
        btnClear.setOnClickListener { clearTags() }

        updateConnectionUi()
        updateScanUi()
        updateStatsUi()
    }

    override fun onDestroy() {
        // Baglanti artik uygulama genelinde paylasilan bir kaynak (bkz. RfidConnectionManager):
        // Sunucu Ayarlari'ndan acilmis olabilir ve sadece cikis yapinca kapanmali. Bu yuzden
        // burada disconnect() cagrilMAZ — sadece bu ekrandan ayrilirken aktif taramayi durdururuz
        // (SDK'nin okuma thread'i artik yok olan bu Activity'ye callback atmaya devam etmesin).
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync()
        }
        super.onDestroy()
    }

    private fun onConnectClicked() {
        if (rfidManager.isConnected) {
            stopScanningIfNeeded()
            setConnectButtonBusy(getString(R.string.rfid_disconnecting))
            RfidConnectionManager.disconnectAsync {
                btnConnect.isEnabled = true
                updateConnectionUi()
                updateScanUi()
            }
            return
        }

        clearError()
        setConnectButtonBusy(getString(R.string.rfid_connecting))

        RfidConnectionManager.connectAsync(this) { result ->
            when (result) {
                is ConnectResult.Success ->
                    Toast.makeText(this, R.string.rfid_connected, Toast.LENGTH_SHORT).show()
                is ConnectResult.Failure ->
                    showError(getString(R.string.rfid_connect_failed, result.reason))
            }
            btnConnect.isEnabled = true
            updateConnectionUi()
            updateScanUi()
        }
    }

    private fun setConnectButtonBusy(text: String) {
        btnConnect.isEnabled = false
        btnConnect.text = text
    }

    private fun toggleScanning() {
        if (!rfidManager.isConnected) return
        if (isScanning) stopScanningIfNeeded() else startScanning()
    }

    private fun startScanning() {
        btnToggleScan.isEnabled = false
        RfidConnectionManager.startInventoryAsync(
            context = this,
            onTagRead = { tag -> handleTagRead(tag) },
            onCrcError = { Log.w(TAG, "CRC hatasi bildirildi") },
        ) { started ->
            if (started) {
                isScanning = true
            } else {
                showError(getString(R.string.rfid_start_failed))
            }
            updateScanUi()
        }
    }

    private fun stopScanningIfNeeded() {
        if (!isScanning) return
        btnToggleScan.isEnabled = false
        RfidConnectionManager.stopInventoryAsync {
            isScanning = false
            updateScanUi()
        }
    }

    /**
     * SDK'nin kendi okuma thread'inden cagrilir; bu yuzden UI guncellemesi [runOnUiThread]
     * ile yapiliyor. Donus degeri "bu EPC daha once okundu mu" (SDK'nin tekrar-okuma sesi icin).
     */
    private fun handleTagRead(tag: RfidTag): Boolean {
        val isRepeat: Boolean
        val snapshot: List<ScannedTagUi>
        synchronized(tagsLock) {
            val existing = scannedTags[tag.epcId]
            isRepeat = existing != null
            scannedTags[tag.epcId] = if (existing != null) {
                existing.copy(
                    rssi = tag.rssi,
                    antennaMask = existing.antennaMask or tag.antId,
                    readCount = existing.readCount + 1,
                )
            } else {
                ScannedTagUi(tag.epcId, tag.rssi, tag.antId, readCount = 1)
            }
            totalReadCount++
            // Snapshot'i kilit ICINDE aliyoruz; aksi halde UI thread'e postalanan bu liste,
            // SDK'nin okuma thread'i tarafindan ayni anda mutasyona ugrarsa
            // ConcurrentModificationException firlatir (gercek cihazda gozlemlendi).
            snapshot = scannedTags.values.toList()
        }

        runOnUiThread {
            adapter.submitList(snapshot)
            updateStatsUi()
        }
        return isRepeat
    }

    private fun clearTags() {
        synchronized(tagsLock) {
            scannedTags.clear()
            totalReadCount = 0
        }
        adapter.submitList(emptyList())
        updateStatsUi()
    }

    private fun updateConnectionUi() {
        val connected = rfidManager.isConnected
        tvConnectionStatus.setText(
            if (connected) R.string.rfid_status_connected else R.string.rfid_status_disconnected
        )
        btnConnect.setText(if (connected) R.string.rfid_disconnect else R.string.rfid_connect)
    }

    private fun updateScanUi() {
        btnToggleScan.isEnabled = rfidManager.isConnected
        btnToggleScan.setText(if (isScanning) R.string.rfid_stop_scan else R.string.rfid_start_scan)
    }

    private fun updateStatsUi() {
        tvTagStats.text = getString(R.string.rfid_stats, scannedTags.size, totalReadCount)
        tvEmptyHint.visibility = if (scannedTags.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        tvRfidError.text = message
        tvRfidError.visibility = View.VISIBLE
    }

    private fun clearError() {
        tvRfidError.visibility = View.GONE
        tvRfidError.text = ""
    }

    // --- Fiziksel tetik tusu ---
    // Bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 6. Bu spesifik cihazda (BLD N60/PDA) gercek
    // keyCode HENUZ dogrulanmadi -- cihazdaki tetik tusunun keylayout'u dokumandaki referans
    // birimden farkli cikti (scancode 51, "KEY_20"). Test asamasinda TUM tus basimlari
    // loglanir; cihazda tetige basip "adb logcat -s RFID_TRIGGER_KEY" ile gercek degeri
    // tespit edin, sonra TRIGGER_KEY_CODES setine ekleyin.
    //
    // Iki tetik modu (Sunucu Ayarlari > "Tetik Bas-Cek" switch'i, bkz. docs Bolum 6.3):
    // - Toggle (varsayilan, switch kapali): her basis okumayi ac/kapa yapar, onKeyUp'in
    //   bir etkisi yok.
    // - Bas-Cek / momentary (switch acik): onKeyDown ile okuma baslar, onKeyUp ile durur;
    //   tetik birakilana kadar okumaya devam eder.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TRIGGER_LOG_TAG, "keyCode=$keyCode repeatCount=${event?.repeatCount}")
        if (keyCode in TRIGGER_KEY_CODES) {
            if (event?.repeatCount == 0 && rfidManager.isConnected) {
                if (RfidPreferences.isPressAndHoldMode(this)) {
                    if (!isScanning) startScanning()
                } else {
                    toggleScanning()
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in TRIGGER_KEY_CODES) {
            if (isScanning && RfidPreferences.isPressAndHoldMode(this)) {
                stopScanningIfNeeded()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG = "RfidScanActivity"
        private const val TRIGGER_LOG_TAG = "RFID_TRIGGER_KEY"
        private val TRIGGER_KEY_CODES = setOf(305, 619, 621)
    }
}
