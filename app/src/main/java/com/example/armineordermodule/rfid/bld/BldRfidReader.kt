package com.example.armineordermodule.rfid.bld

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.armineordermodule.rfid.ConnectResult
import com.example.armineordermodule.rfid.RfidReader
import com.example.armineordermodule.rfid.RfidReaderType
import com.example.armineordermodule.rfid.RfidTag
import com.example.armineordermodule.rfid.RfidTagListener
import com.example.armineordermodule.rfid.RfidTriggerListener
import com.example.armineordermodule.rfid.TriggerSource
import com.lckj.lcrrgxmodule.factory.ILcUhfProduct
import com.lckj.lcrrgxmodule.factory.LcModule
import com.rfid.PowerUtil
import com.rfid.trans.ReadTag
import com.rfid.trans.TagCallback

/**
 * BLD N60 (ve ayni SDK'yi kullanan) el terminalleri icin surucu: `lcrrgxmodule-release.aar`.
 *
 * Bu sinif disina hicbir SDK tipi (`ILcUhfProduct`, `ReadTag`, `TagCallback`) sizmaz.
 * SDK siniflari sadece bu sinif olusturulup [connect] cagrilinca yuklenir; Chainway gibi
 * baska bir cihazda bu surucu hic olusturulmaz. Bkz. docs/RFID_SDK_ENTEGRASYON.md.
 *
 * Ses bu surucude degil, [com.example.armineordermodule.rfid.RfidFeedback] icinde: SDK'nin
 * `beginSound`/`setsoundid` mekanizmasi kullanilmaz, boylece iki cihazda da ayni ses calar.
 */
internal class BldRfidReader(private val context: Context) : RfidReader {

    private var product: ILcUhfProduct? = null

    override val type: RfidReaderType = RfidReaderType.BLD
    override val triggerSource: TriggerSource = TriggerSource.KEY_EVENT
    override val isHoldToReadOnly: Boolean = false

    override val isConnected: Boolean
        get() = product != null

    /**
     * Cihaza baglanir. Aday cip tipleri sirasiyla denenir ve **her aday icin hem urun
     * nesnesi olusturma hem de gercek `Connect()` cagrisi** yapilir (sadece nesne
     * olusturma basarili diye o adayda durulmaz):
     * 1. Otomatik cip tespiti ([LcModule.createProduct])
     * 2. GX ailesi ([CHIP_TYPE_GX])
     * 3. RR ailesi ([CHIP_TYPE_RR])
     *
     * Gercek N60/PDA cihazinda gozlemlendi: `createProduct(0x20)` (GX) her zaman
     * nesne olarak basariyla donuyor ve seri port da aciliyor, ANCAK `Connect()`'in donus
     * kodu cip kimligi dogrulanamadiginda yine de hata (`-1`) olabiliyor; bu durumda bir
     * sonraki aday (RR) denenmeli.
     *
     * Her adayda once 115200 baud denenir; SDK'nin "yanlis baud rate" hata kodu
     * ([ERROR_WRONG_BAUD_RATE]) donerse ayni adayda 57600 ile tekrar denenir.
     */
    override fun connect(): ConnectResult {
        if (product != null) {
            return ConnectResult.Success
        }

        try {
            PowerUtil.power(POWER_ON)
        } catch (t: Throwable) {
            // Emulatorde veya bu GPIO'ya sahip olmayan cihazda sessizce basarisiz olabilir.
            Log.w(TAG, "PowerUtil.power($POWER_ON) basarisiz (emulator olabilir)", t)
        }

        val port = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) PORT_API28 else PORT_DEFAULT
        val module = try {
            LcModule(context)
        } catch (t: Throwable) {
            return ConnectResult.Failure("BLD RFID SDK baslatilamadi: ${t.message}")
        }

        var lastFailureReason = "Cip tipi tespit edilemedi (createProduct null dondu)"

        for ((chipType, label) in CHIP_CANDIDATES) {
            val uhf = createProduct(module, chipType, label) ?: continue

            val primaryResult = try {
                uhf.Connect(port, BAUD_PRIMARY)
            } catch (t: Throwable) {
                lastFailureReason = "Connect() istisna ($label): ${t.message}"
                continue
            }

            val finalResult = if (primaryResult == ERROR_WRONG_BAUD_RATE) {
                try {
                    uhf.Connect(port, BAUD_FALLBACK)
                } catch (t: Throwable) {
                    lastFailureReason = "Connect() fallback baud istisna ($label): ${t.message}"
                    continue
                }
            } else {
                primaryResult
            }

            if (finalResult == RESULT_OK) {
                product = uhf
                return ConnectResult.Success
            }

            lastFailureReason = "Connect() hata kodu: $finalResult ($label, port=$port)"
            runCatching { uhf.DisConnect() }
        }

        return ConnectResult.Failure(lastFailureReason)
    }

    private fun createProduct(module: LcModule, chipType: Int?, label: String): ILcUhfProduct? {
        return runCatching {
            if (chipType == null) module.createProduct() else module.createProduct(chipType)
        }.onFailure { Log.w(TAG, "createProduct() istisna ($label)", it) }
            .getOrNull()
    }

    override fun startInventory(listener: RfidTagListener): Boolean {
        val uhf = product ?: return false

        uhf.SetCallBack(object : TagCallback {
            override fun tagCallback(tag: ReadTag?) {
                val epc = tag?.epcId?.trim()
                if (epc.isNullOrEmpty()) {
                    return
                }
                listener.onTagRead(RfidTag(epc, tag.rssi, tag.antId))
            }

            override fun tagCallbackFailed(reason: Int): Int = 0

            override fun CRCErrorCallBack(reason: Int): Int {
                Log.w(TAG, "CRC hatasi: $reason")
                return 0
            }

            override fun FinishCallBack() = Unit
        })

        return runCatching { uhf.StartRead() == RESULT_OK }
            .getOrElse {
                Log.w(TAG, "StartRead() istisna", it)
                false
            }
    }

    override fun stopInventory() {
        runCatching { product?.StopRead() }
            .onFailure { Log.w(TAG, "StopRead() istisna", it) }
    }

    /**
     * Baglantiyi kapatir. `onDestroy()` cagrilacagi garanti degildir (ör. `am force-stop`);
     * bu durumda modul "surekli okuma" modunda takili kalabilir.
     * Bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 4.1.
     */
    override fun disconnect() {
        runCatching {
            product?.StopRead()
            product?.DisConnect()
        }.onFailure { Log.w(TAG, "disconnect() istisna", it) }

        runCatching { PowerUtil.power(POWER_OFF) }
            .onFailure { Log.w(TAG, "PowerUtil.power($POWER_OFF) istisna", it) }

        product = null
    }

    override fun isTriggerKey(keyCode: Int): Boolean = keyCode in TRIGGER_KEY_CODES

    /** Tetik `KeyEvent` olarak geldigi icin bu surucude kullanilmaz. */
    override fun setTriggerListener(listener: RfidTriggerListener?) = Unit

    companion object {
        private const val TAG = "BldRfidReader"

        /**
         * Bilinen tetik tus kodlari: 305 (genel), 619 (S70 / N60), 621 (N41U).
         * Bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 6.1.
         */
        private val TRIGGER_KEY_CODES = setOf(305, 619, 621)

        private const val PORT_DEFAULT = "/dev/ttyS3"
        private const val PORT_API28 = "/dev/ttyS2"

        private const val BAUD_PRIMARY = 115200
        private const val BAUD_FALLBACK = 57600

        private const val CHIP_TYPE_GX = 0x20
        private const val CHIP_TYPE_RR = 0x10

        /** (chipType, log etiketi). null chipType = otomatik tespit (parametresiz createProduct()). */
        private val CHIP_CANDIDATES: List<Pair<Int?, String>> = listOf(
            null to "auto",
            CHIP_TYPE_GX to "GX(0x20)",
            CHIP_TYPE_RR to "RR(0x10)",
        )

        private const val RESULT_OK = 0
        private const val ERROR_WRONG_BAUD_RATE = 48

        private const val POWER_ON = "1"
        private const val POWER_OFF = "0"
    }
}
