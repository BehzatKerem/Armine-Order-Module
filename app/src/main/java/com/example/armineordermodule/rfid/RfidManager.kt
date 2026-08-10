package com.example.armineordermodule.rfid

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.util.Log
import com.example.armineordermodule.R
import com.lckj.lcrrgxmodule.factory.ILcUhfProduct
import com.lckj.lcrrgxmodule.factory.LcModule
import com.rfid.PowerUtil
import com.rfid.trans.ReadTag
import com.rfid.trans.TagCallback

/**
 * UHF RFID el terminali SDK'sını (`lcrrgxmodule-release.aar`) sarmalar.
 *
 * Bu sınıf dışına hiçbir SDK tipi (`ILcUhfProduct`, `ReadTag`, `TagCallback`) sızmaz;
 * çağıranlar sadece [connect], [startInventory], [stopInventory], [disconnect] ve
 * [RfidTag] ile ilgilenir. Bkz. docs/RFID_SDK_ENTEGRASYON.md Bölüm 4-5.
 *
 * Ses notu: SDK'da `beginSound(boolean)` tek basina ses CALMAZ; sadece dahili
 * `isSound` bayragini set eder. Asil ses `setsoundid(soundId, SoundPool)` ile
 * SoundPool ataninca, arka plan thread'i `playSound()` cagirarak calar.
 * Bu yuzden baglanti sonrasi SoundPool burada kurulur; tek bip icin [playBeep].
 */
class RfidManager(private val context: Context) {

    private var product: ILcUhfProduct? = null
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0

    val isConnected: Boolean
        get() = product != null

    /**
     * Cihaza bağlanır. Aday çip tipleri sırasıyla denenir ve **her aday için hem ürün
     * nesnesi oluşturma hem de gerçek `Connect()` çağrısı** yapılır (sadece nesne
     * oluşturma başarılı diye o adayda durulmaz):
     * 1. Otomatik çip tespiti ([LcModule.createProduct])
     * 2. GX ailesi ([CHIP_TYPE_GX])
     * 3. RR ailesi ([CHIP_TYPE_RR])
     *
     * Gerçek N60/PDA cihazında gözlemlendi: `createProduct(0x20)` (GX) her zaman
     * nesne olarak başarıyla dönüyor ve seri port da açılıyor ("Connect Successfully"
     * native logu basılıyor), ANCAK `Connect()`'in dönüş kodu çip kimliği doğrulanamadığında
     * yine de hata (`-1`) olabiliyor — bu durumda bir sonraki aday (RR) denenmeli. Referans
     * demo uygulaması da tam olarak bu şekilde davranıyor (GX dene → başarısız → RR'a düş).
     *
     * Her adayda önce 115200 baud denenir; SDK'nın "yanlış baud rate" hata kodu
     * ([ERROR_WRONG_BAUD_RATE]) dönerse aynı adayda 57600 ile tekrar denenir.
     */
    fun connect(): ConnectResult {
        product?.let { existing ->
            if (soundPool == null || soundId == 0) {
                setupSound(existing)
            }
            return ConnectResult.Success
        }

        try {
            PowerUtil.power(POWER_ON)
        } catch (t: Throwable) {
            // Emülatörde veya bu GPIO'ya sahip olmayan cihazda sessizce başarısız olabilir.
            Log.w(TAG, "PowerUtil.power($POWER_ON) başarısız (emülatör olabilir)", t)
        }

        val port = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) PORT_API28 else PORT_DEFAULT
        val module = LcModule(context)

        var lastFailureReason = "Çip tipi tespit edilemedi (createProduct null döndü)"

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
                setupSound(uhf)
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

    /**
     * SDK ses motorunu baslatir. `beginSound` / `playSound` bundan once cagrilirsa
     * SoundPool null oldugu icin sessiz kalir (AAR icinde hazir ses yok).
     * `SoundPool.load` asenkron oldugu icin `setsoundid` load tamamlaninca cagirilir.
     */
    private fun setupSound(uhf: ILcUhfProduct) {
        releaseSoundPool()
        runCatching {
            val pool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            soundPool = pool
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) {
                    Log.w(TAG, "RFID beep load basarisiz status=$status")
                    return@setOnLoadCompleteListener
                }
                soundId = sampleId
                runCatching {
                    product?.setsoundid(sampleId, pool)
                    Log.i(TAG, "RFID SoundPool hazir (soundId=$sampleId)")
                }.onFailure { Log.w(TAG, "setsoundid istisna", it) }
            }
            pool.load(context, R.raw.rfid_beep, 1)
        }.onFailure {
            Log.w(TAG, "RFID SoundPool kurulamadi", it)
            releaseSoundPool()
        }
    }

    private fun releaseSoundPool() {
        runCatching { soundPool?.release() }
        soundPool = null
        soundId = 0
    }

    /** Yeni urun / etiket kabulunde tek bip. SDK `playSound()` + SoundPool kullanir. */
    fun playBeep() {
        val uhf = product
        val pool = soundPool
        val id = soundId
        if (uhf == null) {
            return
        }
        // SoundPool henuz load olmadiysa SDK playSound sessiz doner; dogrudan pool.play dene.
        if (pool != null && id != 0) {
            runCatching {
                pool.play(id, 1f, 1f, 1, 0, 1f)
            }.onFailure {
                runCatching { uhf.playSound() }
            }
        } else {
            runCatching { uhf.playSound() }
                .onFailure { Log.w(TAG, "playSound() istisna", it) }
        }
    }

    /**
     * Baska siparisteki EPC okundugunda kisa yuksek sesli uyari (tek ton).
     * Coklu etiket okumasinda kuyruk / takilma olmamasi icin uyuyan dongu yok.
     */
    fun playDuplicateWarning() {
        runCatching {
            val tone = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_ALARM,
                100,
            )
            try {
                tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                Thread.sleep(260L)
                tone.stopTone()
            } finally {
                runCatching { tone.release() }
            }
        }.onFailure { Log.w(TAG, "playDuplicateWarning istisna", it) }
    }

    /**
     * Envanter okumayı başlatır. [onTagRead] her etiket okunuşunda SDK'nın kendi thread'inden
     * çağrılır ve `true` dönerse SDK ses bayrağını açar (`beginSound(true)` → sürekli
     * `playSound` döngüsü). Tek bip için tercihen [playBeep] kullanın.
     */
    fun startInventory(
        onTagRead: (RfidTag) -> Boolean,
        onCrcError: () -> Unit = {},
    ): Boolean {
        val uhf = product ?: return false
        if (soundPool == null || soundId == 0) {
            setupSound(uhf)
        }

        uhf.SetCallBack(object : TagCallback {
            override fun tagCallback(tag: ReadTag?) {
                if (tag == null) {
                    uhf.beginSound(false)
                    return
                }
                val enableContinuousSound = onTagRead(RfidTag(tag.epcId, tag.rssi, tag.antId))
                uhf.beginSound(enableContinuousSound)
            }

            override fun tagCallbackFailed(reason: Int): Int = 0

            override fun CRCErrorCallBack(reason: Int): Int {
                onCrcError()
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

    fun stopInventory() {
        runCatching {
            product?.beginSound(false)
            product?.StopRead()
        }.onFailure { Log.w(TAG, "StopRead() istisna", it) }
    }

    /**
     * Bağlantıyı kapatır. `onCleared()`/`onDestroy()` çağrılacağı garanti değildir
     * (ör. `am force-stop`); bu durumda modül "sürekli okuma" modunda takılı kalabilir.
     * Bkz. docs/RFID_SDK_ENTEGRASYON.md Bölüm 4.1.
     */
    fun disconnect() {
        runCatching {
            product?.beginSound(false)
            product?.StopRead()
            product?.DisConnect()
        }.onFailure { Log.w(TAG, "disconnect() istisna", it) }

        releaseSoundPool()

        runCatching { PowerUtil.power(POWER_OFF) }
            .onFailure { Log.w(TAG, "PowerUtil.power($POWER_OFF) istisna", it) }

        product = null
    }

    companion object {
        private const val TAG = "RfidManager"

        private const val PORT_DEFAULT = "/dev/ttyS3"
        private const val PORT_API28 = "/dev/ttyS2"

        private const val BAUD_PRIMARY = 115200
        private const val BAUD_FALLBACK = 57600

        private const val CHIP_TYPE_GX = 0x20
        private const val CHIP_TYPE_RR = 0x10

        /** (chipType, log etiketi) - null chipType = otomatik tespit (parametresiz createProduct()). */
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

/** SDK'nın `ReadTag` tipinden bağımsız, dışarıya açılan okunmuş etiket verisi. */
data class RfidTag(
    val epcId: String,
    val rssi: Int,
    val antId: Int,
)

sealed class ConnectResult {
    object Success : ConnectResult()
    data class Failure(val reason: String) : ConnectResult()
}
