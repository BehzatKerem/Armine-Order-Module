package com.example.armineordermodule.rfid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uygulama genelinde **tek** bir [RfidManager] örneğini yaşatır ve tüm SDK erişimini
 * tek bir arka plan thread'inde seri hale getirir.
 *
 * Neden gerekli: Aynı anda birden fazla `RfidManager`'ın aynı seri porta
 * (`/dev/ttyS3`) bağlanmaya çalışması çakışmaya yol açıyor (gerçek cihazda doğrulandı —
 * bkz. docs/RFID_SDK_ENTEGRASYON.md). Bu yüzden hem Sunucu Ayarları ekranındaki
 * "RFID" anahtarı hem de RFID Tarama (Test) ekranı bu tekil örneği paylaşır.
 *
 * Bağlantı yaşam döngüsü:
 * - Uygulama process'i başlarken RFID anahtarı açıksa ([ArmineApplication]) otomatik bağlanır.
 * - Sunucu Ayarları'nda RFID anahtarı açılıp "Kaydet"e basılınca [connectAsync] çağrılır.
 * - Kullanıcı çıkış yapınca ([ProfileActivity]) [disconnectAsync] çağrılır.
 * - RFID Tarama (Test) ekranı da kendi Bağlan/Bağlantıyı Kes butonlarıyla aynı
 *   paylaşılan örneği kullanır (yani ekranlardan biri bağlanırsa diğeri de "Bağlı" görür).
 */
object RfidConnectionManager {

    private const val TAG = "RfidConnectionManager"
    private const val CONNECT_RETRY_ATTEMPTS = 3
    private const val CONNECT_RETRY_DELAY_MS = 1500L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var manager: RfidManager? = null

    /** [autoReconnectIfNeededAsync] cagrilarinin ust uste binmesini (ayni anda birden fazla
     * yeniden deneme zincirinin kuyruklanmasini) engellemek icin kullanilir. */
    private val autoReconnecting = AtomicBoolean(false)

    @JvmStatic
    @Synchronized
    fun getManager(context: Context): RfidManager {
        return manager ?: RfidManager(context.applicationContext).also { manager = it }
    }

    @JvmStatic
    val isConnected: Boolean
        get() = manager?.isConnected == true

    @JvmStatic
    @JvmOverloads
    fun connectAsync(context: Context, onResult: ((ConnectResult) -> Unit)? = null) {
        val rfidManager = getManager(context)
        executor.execute {
            val result = rfidManager.connect()
            if (result is ConnectResult.Failure) {
                Log.w(TAG, "RFID baglanti hatasi: ${result.reason}")
            }
            onResult?.let { callback -> mainHandler.post { callback(result) } }
        }
    }

    /**
     * [connectAsync] ile ayni, ancak başarısız olursa otomatik olarak birkaç kez daha dener.
     *
     * Neden gerekli: Modül bazen ilk denemede hazır olmuyor (örn. cihaz henüz açılıyor,
     * ya da önceki oturum `am force-stop` ile düzgün kapanmadığı için modül "sürekli okuma"
     * modunda takılı kalmış olabilir — bkz. docs/RFID_SDK_ENTEGRASYON.md Bölüm 4.1). Bu
     * yüzden her başarısız denemeden sonra [RfidManager.disconnect] ile bir "temizlik"
     * (güç kapat) yapılıp kısa bir bekleme ile tekrar denenir; bu, takılı kalmış modülü
     * sıfırlamaya yardımcı olur.
     *
     * Sadece [ArmineApplication] (uygulama açılışı) ve Sunucu Ayarları'ndaki "Kaydet"
     * akışında kullanılır. RFID Tarama (Test) ekranındaki manuel "Bağlan" butonu, kullanıcı
     * anında tekrar deneyebildiği için tek seferlik [connectAsync] kullanır.
     */
    @JvmStatic
    @JvmOverloads
    fun connectWithRetryAsync(context: Context, onResult: ((ConnectResult) -> Unit)? = null) {
        connectWithRetryAsync(context, CONNECT_RETRY_ATTEMPTS, onResult)
    }

    /** [maxAttempts] parametresini özelleştirmek gerektiğinde (ör. testlerde) kullan. */
    @JvmStatic
    fun connectWithRetryAsync(
        context: Context,
        maxAttempts: Int,
        onResult: ((ConnectResult) -> Unit)?,
    ) {
        val rfidManager = getManager(context)
        executor.execute {
            var result: ConnectResult = ConnectResult.Failure("Baglanti denenmedi")
            for (attempt in 1..maxAttempts) {
                result = rfidManager.connect()
                if (result is ConnectResult.Success) {
                    Log.i(TAG, "RFID baglantisi basarili (deneme $attempt/$maxAttempts)")
                    break
                }

                val reason = (result as ConnectResult.Failure).reason
                Log.w(TAG, "RFID baglanti denemesi basarisiz ($attempt/$maxAttempts): $reason")

                if (attempt < maxAttempts) {
                    // Takilan modulu sifirlamak icin guc kapat + kisa bekleme, sonra tekrar dene.
                    runCatching { rfidManager.disconnect() }
                    runCatching { Thread.sleep(CONNECT_RETRY_DELAY_MS) }
                }
            }
            onResult?.let { callback -> mainHandler.post { callback(result) } }
        }
    }

    /**
     * "Sessiz" / fırsatçı yeniden bağlanma denemesi: [MainActivity] her ön plana geldiğinde
     * (uygulama yeniden açılışı, çıkış yapıp tekrar giriş yapılışı, arka plandan geri dönüş)
     * ve [ArmineApplication] process başlangıcında bunu çağırır.
     *
     * Neden ayrı bir fonksiyon: Bu tetikleyiciler kısa süre içinde üst üste ateşlenebilir
     * (örn. process başlangıcında hem [ArmineApplication.onCreate] hem de ilk
     * `MainActivity.onResume` çalışır, ya da kullanıcı ekranlar arasında hızla geçiş yapar).
     * [connectWithRetryAsync]'i doğrudan her seferinde çağırmak, her biri kendi 3 deneme +
     * güç-döngüsü zincirini kuyruğa alacağından toplam bağlanma süresini gereksiz uzatır.
     * Zaten bağlıysa veya bir deneme zinciri devam ediyorsa bu fonksiyon sessizce çıkar;
     * [ServerSettingsActivity]'deki "Kaydet" gibi kullanıcının doğrudan tetikledigi ve her
     * zaman geri bildirim beklenen akışlar hâlâ [connectWithRetryAsync]'i doğrudan kullanır.
     */
    @JvmStatic
    @JvmOverloads
    fun autoReconnectIfNeededAsync(context: Context, onResult: ((ConnectResult) -> Unit)? = null) {
        if (isConnected) {
            return
        }
        if (!autoReconnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Otomatik yeniden baglanma zaten devam ediyor, yeni deneme atlaniyor")
            return
        }
        connectWithRetryAsync(context, CONNECT_RETRY_ATTEMPTS) { result ->
            autoReconnecting.set(false)
            onResult?.invoke(result)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun disconnectAsync(onDone: (() -> Unit)? = null) {
        val rfidManager = manager
        if (rfidManager == null) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        executor.execute {
            rfidManager.disconnect()
            onDone?.let { callback -> mainHandler.post(callback) }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun startInventoryAsync(
        context: Context,
        onTagRead: (RfidTag) -> Boolean,
        onCrcError: () -> Unit = {},
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        val rfidManager = getManager(context)
        executor.execute {
            val started = rfidManager.startInventory(onTagRead, onCrcError)
            onResult?.let { callback -> mainHandler.post { callback(started) } }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun stopInventoryAsync(onDone: (() -> Unit)? = null) {
        val rfidManager = manager
        if (rfidManager == null) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        executor.execute {
            rfidManager.stopInventory()
            onDone?.let { callback -> mainHandler.post(callback) }
        }
    }

    /** Uyari sesinin executor'da ust uste kuyruklanmasini engeller. */
    private val duplicateWarningInFlight = AtomicBoolean(false)

    /** Yeni urun kabulunde tek bip (SDK SoundPool / playSound). */
    @JvmStatic
    fun playBeep() {
        val rfidManager = manager ?: return
        executor.execute {
            rfidManager.playBeep()
        }
    }

    /** Baska sipariste kayitli EPC icin tek kisa yuksek sesli uyari. */
    @JvmStatic
    fun playDuplicateWarning() {
        if (!duplicateWarningInFlight.compareAndSet(false, true)) {
            return
        }
        val rfidManager = manager
        executor.execute {
            try {
                if (rfidManager != null) {
                    rfidManager.playDuplicateWarning()
                } else {
                    runCatching {
                        val tone = android.media.ToneGenerator(
                            android.media.AudioManager.STREAM_ALARM,
                            100,
                        )
                        try {
                            tone.startTone(
                                android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                                250,
                            )
                            Thread.sleep(260L)
                            tone.stopTone()
                        } finally {
                            runCatching { tone.release() }
                        }
                    }
                }
            } finally {
                duplicateWarningInFlight.set(false)
            }
        }
    }
}
