package com.example.armineordermodule.rfid

import android.os.Bundle
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
 * RFID el terminali entegrasyonu icin bagimsiz test ekrani. Okunan EPC'leri listeler;
 * siparis akisina bagli degildir. Hangi cihazda calisirsa calissin (BLD / Chainway) ayni
 * [RfidConnectionManager] uzerinden calisir.
 */
class RfidScanActivity : AppCompatActivity() {

    private lateinit var adapter: RfidTagListAdapter
    private lateinit var rfidTrigger: RfidTriggerController

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvTagStats: TextView
    private lateinit var tvRfidError: TextView
    private lateinit var tvEmptyHint: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnToggleScan: MaterialButton

    // Okuyucu thread'inden (handleTagRead) VE UI thread'inden (clearTags) ayni anda
    // erisiliyor; yogun okumada bu kilit olmadan ConcurrentModificationException ile
    // cokuyordu (gercek cihazda gozlemlendi).
    private val tagsLock = Any()
    private val scannedTags = LinkedHashMap<String, ScannedTagUi>()
    private var isScanning = false
    private var totalReadCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rfid_scan)

        rfidTrigger = RfidTriggerController(this, object : RfidTriggerController.Host {
            override fun isScanning() = isScanning
            override fun startScanning() = this@RfidScanActivity.startScanning()
            override fun stopScanning() = stopScanningIfNeeded()
            override fun isScanLatched() = false
        })

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
        // Baglanti uygulama genelinde paylasilir; burada kesilmez. Sadece bu ekranin
        // baslattigi okuma durdurulur.
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync()
        }
        super.onDestroy()
    }

    private fun onConnectClicked() {
        if (RfidConnectionManager.isConnected) {
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

        RfidConnectionManager.connectAsync { result ->
            when (result) {
                is ConnectResult.Success -> {
                    Toast.makeText(this, R.string.rfid_connected, Toast.LENGTH_SHORT).show()
                    RfidPreferences.setRfidEnabled(this, true)
                }
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
        if (!RfidConnectionManager.isConnected) return
        if (isScanning) stopScanningIfNeeded() else startScanning()
    }

    private fun startScanning() {
        btnToggleScan.isEnabled = false
        RfidConnectionManager.startInventoryAsync(::handleTagRead) { started ->
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

    /** Okuyucu thread'inden cagrilir; UI guncellemesi [runOnUiThread] ile yapilir. */
    private fun handleTagRead(tag: RfidTag) {
        val snapshot: List<ScannedTagUi>
        synchronized(tagsLock) {
            val existing = scannedTags[tag.epcId]
            scannedTags[tag.epcId] = if (existing != null) {
                existing.copy(
                    rssi = tag.rssi ?: existing.rssi,
                    antennaMask = existing.antennaMask or tag.antId,
                    readCount = existing.readCount + 1,
                )
            } else {
                ScannedTagUi(tag.epcId, tag.rssi ?: 0, tag.antId, readCount = 1)
            }
            totalReadCount++
            // Snapshot kilit ICINDE alinir; aksi halde UI thread'e giden liste okuyucu
            // thread'i tarafindan ayni anda degistirilebilir.
            snapshot = scannedTags.values.toList()
        }

        runOnUiThread {
            adapter.submitList(snapshot)
            updateStatsUi()
        }
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
        val connected = RfidConnectionManager.isConnected
        tvConnectionStatus.text = if (connected) {
            getString(R.string.rfid_status_connected_with_type, readerTypeLabel())
        } else {
            getString(R.string.rfid_status_disconnected)
        }
        btnConnect.setText(if (connected) R.string.rfid_disconnect else R.string.rfid_connect)
    }

    private fun readerTypeLabel(): String = getString(
        when (RfidConnectionManager.readerType) {
            RfidReaderType.BLD -> R.string.rfid_reader_type_bld
            RfidReaderType.CHAINWAY -> R.string.rfid_reader_type_chainway
        }
    )

    private fun updateScanUi() {
        btnToggleScan.isEnabled = RfidConnectionManager.isConnected
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

    // --- Fiziksel tetik (cihaza gore davranis RfidTriggerController icinde) ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        rfidTrigger.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        rfidTrigger.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
}
