package com.example.armineordermodule.rfid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import com.example.armineordermodule.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Okuma sesleri. Okuyucudan bagimsizdir: BLD ve Chainway'de ayni ses calar ve okuyucu
 * bagli olmasa da calisir.
 */
internal object RfidFeedback {

    private const val TAG = "RfidFeedback"
    private const val WARNING_TONE_MS = 250

    private val executor = Executors.newSingleThreadExecutor()
    private val warningInFlight = AtomicBoolean(false)

    @Volatile
    private var soundPool: SoundPool? = null

    @Volatile
    private var beepSoundId = 0

    fun init(context: Context) {
        if (soundPool != null) {
            return
        }
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
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    beepSoundId = sampleId
                } else {
                    Log.w(TAG, "Bip sesi yuklenemedi status=$status")
                }
            }
            pool.load(context.applicationContext, R.raw.rfid_beep, 1)
            soundPool = pool
        }.onFailure { Log.w(TAG, "SoundPool kurulamadi", it) }
    }

    /** Yeni etiket kabulunde tek kisa bip. */
    fun playBeep() {
        val pool = soundPool ?: return
        val id = beepSoundId
        if (id == 0) {
            return
        }
        runCatching { pool.play(id, 1f, 1f, 1, 0, 1f) }
            .onFailure { Log.w(TAG, "Bip calinamadi", it) }
    }

    /** Baska kayitta bulunan EPC icin kisa yuksek uyari. Ust uste kuyruklanmaz. */
    fun playWarning() {
        if (!warningInFlight.compareAndSet(false, true)) {
            return
        }
        executor.execute {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                try {
                    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, WARNING_TONE_MS)
                    Thread.sleep(WARNING_TONE_MS + 10L)
                    tone.stopTone()
                } finally {
                    runCatching { tone.release() }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Uyari sesi calinamadi", t)
            } finally {
                warningInFlight.set(false)
            }
        }
    }
}
