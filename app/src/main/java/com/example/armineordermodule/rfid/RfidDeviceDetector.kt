package com.example.armineordermodule.rfid

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.armineordermodule.RfidPreferences
import com.example.armineordermodule.rfid.bld.BldRfidReader
import com.example.armineordermodule.rfid.chainway.ChainwayRfidReader

/**
 * Hangi RFID surucusunun kullanilacagina karar verir.
 *
 * Oncelik sirasi:
 * 1. Sunucu Ayarlari'nda elle secilmis cihaz tipi ([RfidPreferences.getReaderTypeOverride]).
 * 2. Otomatik tespit ([detect]).
 */
object RfidDeviceDetector {

    /** Chainway Keyboard Emulator paket adi. AndroidManifest'teki `<queries>` ile ayni olmali. */
    const val CHAINWAY_SCANNER_PACKAGE = "com.rscja.scanner"

    @JvmStatic
    fun resolve(context: Context): RfidReaderType =
        RfidReaderType.fromPrefValue(RfidPreferences.getReaderTypeOverride(context))
            ?: detect(context)

    @JvmStatic
    fun detect(context: Context): RfidReaderType = detect(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        chainwayAppInstalled = isChainwayKeyboardEmulatorInstalled(context),
    )

    /**
     * Saf karar fonksiyonu (birim testi icin ayrildi).
     *
     * Chainway isareti varsa Chainway, yoksa BLD secilir. BLD varsayilandir: bu uygulamanin
     * ilk hedef cihazi oydu ve onun SDK'si cihaz kimligini guvenilir bicimde vermiyor
     * (bkz. docs/RFID_SDK_ENTEGRASYON.md Bolum 4).
     */
    internal fun detect(
        manufacturer: String?,
        brand: String?,
        chainwayAppInstalled: Boolean,
    ): RfidReaderType {
        val vendorLooksChainway = listOf(manufacturer, brand).any { value ->
            value != null && CHAINWAY_VENDOR_MARKERS.any { value.contains(it, ignoreCase = true) }
        }
        return if (chainwayAppInstalled || vendorLooksChainway) {
            RfidReaderType.CHAINWAY
        } else {
            RfidReaderType.BLD
        }
    }

    @JvmStatic
    fun isChainwayKeyboardEmulatorInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(CHAINWAY_SCANNER_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(CHAINWAY_SCANNER_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    internal fun createReader(context: Context, type: RfidReaderType): RfidReader = when (type) {
        RfidReaderType.BLD -> BldRfidReader(context.applicationContext)
        RfidReaderType.CHAINWAY -> ChainwayRfidReader(context.applicationContext)
    }

    /** `rscja` Chainway'in Android paketlerinde kullandigi sirket adi. */
    private val CHAINWAY_VENDOR_MARKERS = listOf("chainway", "rscja")
}
