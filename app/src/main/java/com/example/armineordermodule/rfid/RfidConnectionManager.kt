package com.example.armineordermodule.rfid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uygulamanin RFID'ye tek giris noktasi. Ekranlar hangi el terminalinde calistigini bilmez;
 * cihaza ozel her sey [RfidReader] surucelerinde kalir:
 *
 * ```
 * Ekranlar ──► RfidConnectionManager ──► RfidReader
 *              (tek ornek, tek thread)     ├─ BldRfidReader      (lcrrgxmodule-release.aar)
 *                                          └─ ChainwayRfidReader (Keyboard Emulator broadcast)
 * ```
 *
 * - Surucu [RfidDeviceDetector.resolve] ile secilir (ayardaki elle secim, yoksa otomatik tespit).
 * - Tum surucu cagrilari tek bir arka plan thread'inde sirayla calisir. Ayni seri porta iki
 *   baglanti acilmasini boyle engelliyoruz (BLD cihazinda dogrulandi).
 * - Callback'ler ana thread'de cagrilir. Etiket callback'i ([RfidTagListener]) haric:
 *   o, okuyucunun arka plan thread'inden gelir.
 *
 * Yasam dongusu:
 * - [init]: [com.example.armineordermodule.ArmineApplication] icinde, her seyden once.
 * - Acilista ve ekranlar on plana geldiginde [autoReconnectIfNeededAsync].
 * - Sunucu Ayarlari'nda kaydedince [connectWithRetryAsync] / [disconnectAsync]; cihaz tipi
 *   degisirse [reloadReaderAsync].
 * - Cikis yapinca [disconnectAsync].
 */
object RfidConnectionManager {

    private const val TAG = "RfidConnectionManager"
    private const val CONNECT_RETRY_ATTEMPTS = 3
    private const val CONNECT_RETRY_DELAY_MS = 1500L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rfid-io").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoReconnecting = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var reader: RfidReader? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
        RfidFeedback.init(context)
    }

    @Synchronized
    private fun requireReader(): RfidReader {
        reader?.let { return it }
        val context = checkNotNull(appContext) { "RfidConnectionManager.init() cagrilmadi" }
        val type = RfidDeviceDetector.resolve(context)
        Log.i(TAG, "RFID surucusu: $type")
        return RfidDeviceDetector.createReader(context, type).also { reader = it }
    }

    // --- Durum ---

    @JvmStatic
    val isConnected: Boolean
        get() = reader?.isConnected == true

    @JvmStatic
    val readerType: RfidReaderType
        get() = requireReader().type

    /** `true` ise tetik sadece basili tuttukca okur; Toggle tercihi uygulanmaz. */
    @JvmStatic
    val isHoldToReadOnly: Boolean
        get() = requireReader().isHoldToReadOnly

    @JvmStatic
    fun isTriggerKey(keyCode: Int): Boolean = requireReader().isTriggerKey(keyCode)

    /** Bkz. [RfidReader.setTriggerListener]. Ana thread'den cagrilir. */
    @JvmStatic
    fun setTriggerListener(listener: RfidTriggerListener?) {
        requireReader().setTriggerListener(listener)
    }

    // --- Baglanti ---

    @JvmStatic
    @JvmOverloads
    fun connectAsync(onResult: RfidCallback<ConnectResult>? = null) {
        executor.execute {
            val result = connectOnce()
            post(onResult, result)
        }
    }

    /**
     * [connectAsync] ile ayni, ancak basarisiz olursa birkac kez daha dener. Her denemeden
     * sonra [RfidReader.disconnect] ile temizlik yapilir: BLD'de bu, takili kalmis modulu
     * guc kapatip acarak sifirlar (bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 4.1).
     */
    @JvmStatic
    @JvmOverloads
    fun connectWithRetryAsync(onResult: RfidCallback<ConnectResult>? = null) {
        executor.execute {
            val result = connectWithRetry(CONNECT_RETRY_ATTEMPTS)
            post(onResult, result)
        }
    }

    /**
     * Sessiz yeniden baglanma. Zaten bagliysa ya da bir deneme zinciri suruyorsa bir sey
     * yapmaz; boylece pes pese gelen on plan olaylari deneme zincirlerini kuyruga yigmaz.
     * Callback sadece gercekten deneme yapildiysa cagrilir.
     */
    @JvmStatic
    @JvmOverloads
    fun autoReconnectIfNeededAsync(onResult: RfidCallback<ConnectResult>? = null) {
        if (isConnected) {
            return
        }
        if (!autoReconnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Otomatik yeniden baglanma zaten devam ediyor")
            return
        }
        executor.execute {
            val result = try {
                connectWithRetry(CONNECT_RETRY_ATTEMPTS)
            } finally {
                autoReconnecting.set(false)
            }
            post(onResult, result)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun disconnectAsync(onDone: Runnable? = null) {
        val current = reader
        executor.execute {
            current?.let { runCatching { it.disconnect() } }
            onDone?.let { mainHandler.post(it) }
        }
    }

    /**
     * Cihaz tipi ayari degistiginde cagrilir: mevcut surucu kapatilir ve sonraki erisimde
     * yeni tip [RfidDeviceDetector.resolve] ile secilir.
     */
    @JvmStatic
    @JvmOverloads
    fun reloadReaderAsync(onDone: Runnable? = null) {
        executor.execute {
            val old = synchronized(this) { reader.also { reader = null } }
            old?.let {
                runCatching { it.setTriggerListener(null) }
                runCatching { it.disconnect() }
            }
            onDone?.let { mainHandler.post(it) }
        }
    }

    // --- Okuma ---

    @JvmStatic
    @JvmOverloads
    fun startInventoryAsync(listener: RfidTagListener, onResult: RfidCallback<Boolean>? = null) {
        executor.execute {
            val current = requireReader()
            val started = current.isConnected && runCatching { current.startInventory(listener) }
                .onFailure { Log.w(TAG, "startInventory istisna", it) }
                .getOrDefault(false)
            post(onResult, started)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun stopInventoryAsync(onDone: Runnable? = null) {
        val current = reader
        executor.execute {
            current?.let { runCatching { it.stopInventory() } }
            onDone?.let { mainHandler.post(it) }
        }
    }

    // --- Ses ---

    /** Yeni urun / etiket kabulunde tek bip. */
    @JvmStatic
    fun playBeep() = RfidFeedback.playBeep()

    /** Baska kayitta bulunan EPC icin tek kisa yuksek sesli uyari. */
    @JvmStatic
    fun playDuplicateWarning() = RfidFeedback.playWarning()

    /** Stokta olmayan urun listeden cikarildiginda: uc uzun alarm tonu ve titresim. */
    @JvmStatic
    fun playOutOfStockWarning() = RfidFeedback.playStrongWarning()

    // --- Ic yardimcilar (executor thread'inde) ---

    private fun connectOnce(): ConnectResult {
        val result = runCatching { requireReader().connect() }
            .getOrElse { ConnectResult.Failure("Baglanti istisnasi: ${it.message}") }
        if (result is ConnectResult.Failure) {
            Log.w(TAG, "RFID baglanti hatasi: ${result.reason}")
        }
        return result
    }

    private fun connectWithRetry(maxAttempts: Int): ConnectResult {
        var result: ConnectResult = ConnectResult.Failure("Baglanti denenmedi")
        for (attempt in 1..maxAttempts) {
            result = connectOnce()
            if (result is ConnectResult.Success) {
                Log.i(TAG, "RFID baglantisi basarili (deneme $attempt/$maxAttempts)")
                break
            }
            if (attempt < maxAttempts) {
                runCatching { reader?.disconnect() }
                runCatching { Thread.sleep(CONNECT_RETRY_DELAY_MS) }
            }
        }
        return result
    }

    private fun <T> post(callback: RfidCallback<T>?, value: T) {
        callback?.let { mainHandler.post { it.onResult(value) } }
    }
}
