package com.example.armineordermodule.rfid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.armineordermodule.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Okuma sesleri. Okuyucudan bagimsizdir: BLD ve Chainway'de ayni ses calar ve okuyucu
 * bagli olmasa da calisir.
 *
 * Bip `res/raw/rfid_beep.wav` ile [SoundPool] uzerinden calar. Ses yuklenmemisse ya da
 * `play()` basarisiz olursa (0 donerse) [ToneGenerator] ile kisa bir yedek bip calinir;
 * boylece bir cihazda SoundPool sessiz kalsa bile okuma sessiz gecmez.
 */
internal object RfidFeedback {

    private const val TAG = "RfidFeedback"
    private const val WARNING_TONE_MS = 250
    private const val FALLBACK_BEEP_MS = 80
    private const val STRONG_WARNING_TONE_MS = 400
    private const val STRONG_WARNING_GAP_MS = 120L
    private const val STRONG_WARNING_REPEAT = 3
    /** Bekle, titre, bekle, titre... (ms). Uc tonla ayni ritimde. */
    private val STRONG_WARNING_VIBRATION = longArrayOf(0, 400, 120, 400, 120, 400)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rfid-sound").apply { isDaemon = true }
    }
    private val warningInFlight = AtomicBoolean(false)
    private val fallbackBeepInFlight = AtomicBoolean(false)
    private val fallbackLogged = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var soundPool: SoundPool? = null

    @Volatile
    private var beepSoundId = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        if (soundPool == null) {
            loadSoundPool()
        }
    }

    /**
     * SoundPool'u bastan kurar. BLD'de RFID modulune guc verilip baglanildiktan sonra
     * cagrilir: eski (calisan) BLD surumunde ses tam bu noktada yukleniyordu.
     */
    fun reload() {
        loadSoundPool()
    }

    @Synchronized
    private fun loadSoundPool() {
        val context = appContext ?: return
        val old = soundPool
        soundPool = null
        beepSoundId = 0
        old?.let { runCatching { it.release() } }

        runCatching {
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            pool.setOnLoadCompleteListener { loadedPool, sampleId, status ->
                if (loadedPool !== soundPool) {
                    return@setOnLoadCompleteListener
                }
                if (status == 0) {
                    beepSoundId = sampleId
                    Log.i(TAG, "Bip sesi hazir (soundId=$sampleId)")
                } else {
                    Log.w(TAG, "Bip sesi yuklenemedi status=$status")
                }
            }
            soundPool = pool
            pool.load(context, R.raw.rfid_beep, 1)
        }.onFailure { Log.w(TAG, "SoundPool kurulamadi", it) }
    }

    /** Yeni etiket kabulunde tek kisa bip. */
    fun playBeep() {
        val pool = soundPool
        val id = beepSoundId
        val streamId = if (pool != null && id != 0) {
            runCatching { pool.play(id, 1f, 1f, 1, 0, 1f) }
                .onFailure { Log.w(TAG, "Bip calinamadi", it) }
                .getOrDefault(0)
        } else {
            0
        }
        if (streamId == 0) {
            if (fallbackLogged.compareAndSet(false, true)) {
                Log.w(TAG, "SoundPool bip calamadi (pool=${pool != null}, soundId=$id); yedek ton kullaniliyor")
            }
            playFallbackBeep()
        }
    }

    private fun playFallbackBeep() {
        if (!fallbackBeepInFlight.compareAndSet(false, true)) {
            return
        }
        executor.execute {
            try {
                playTone(AudioManager.STREAM_NOTIFICATION, ToneGenerator.TONE_PROP_BEEP, FALLBACK_BEEP_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "Yedek bip calinamadi", t)
            } finally {
                fallbackBeepInFlight.set(false)
            }
        }
    }

    /** Baska kayitta bulunan EPC icin kisa yuksek uyari. Ust uste kuyruklanmaz. */
    fun playWarning() {
        if (!warningInFlight.compareAndSet(false, true)) {
            return
        }
        executor.execute {
            try {
                playTone(AudioManager.STREAM_ALARM, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, WARNING_TONE_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "Uyari sesi calinamadi", t)
            } finally {
                warningInFlight.set(false)
            }
        }
    }

    /**
     * Stokta olmayan urun listeden cikarildiginda: alarm kanalinda uc uzun ton ve titresim.
     * [playWarning]'den belirgin sekilde daha uzun ve serttir; depocu ekrana bakmasa da fark eder.
     * Ust uste kuyruklanmaz.
     */
    fun playStrongWarning() {
        if (!warningInFlight.compareAndSet(false, true)) {
            return
        }
        vibrate(STRONG_WARNING_VIBRATION)
        executor.execute {
            try {
                repeat(STRONG_WARNING_REPEAT) { index ->
                    playTone(AudioManager.STREAM_ALARM, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, STRONG_WARNING_TONE_MS)
                    if (index < STRONG_WARNING_REPEAT - 1) {
                        Thread.sleep(STRONG_WARNING_GAP_MS)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Guclu uyari sesi calinamadi", t)
            } finally {
                warningInFlight.set(false)
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        val context = appContext ?: return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }.onFailure { Log.w(TAG, "Titresim calistirilamadi", it) }
    }

    /** Executor thread'inde cagrilir; ton bitene kadar bekler. */
    private fun playTone(stream: Int, toneType: Int, durationMs: Int) {
        val tone = ToneGenerator(stream, 100)
        try {
            tone.startTone(toneType, durationMs)
            Thread.sleep(durationMs + 10L)
            tone.stopTone()
        } finally {
            runCatching { tone.release() }
        }
    }
}
