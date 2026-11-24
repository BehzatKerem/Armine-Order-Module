package com.example.armineordermodule.rfid.chainway

import com.example.armineordermodule.rfid.RfidTag
import kotlin.math.roundToInt

/**
 * Chainway Keyboard Emulator RFID broadcast'inin extra'larini [RfidTag]'e cevirir.
 * Android bagimsiz tutuldu; JVM birim testleriyle dogrulanir.
 * Bkz. docs/chainway-rfid.md Bolum 3.
 */
internal object ChainwayBroadcastParser {

    const val ACTION_RFID = "com.rscja.scanner.action.scanner.RFID"
    const val EXTRA_EPC = "epc"
    const val EXTRA_DATA = "data"
    const val EXTRA_RSSI = "rssi"

    /** Chainway tek anten kullanir; BLD'deki anten maskesiyle uyumlu olsun diye 1. */
    private const val SINGLE_ANTENNA = 1

    /**
     * @param epc  `epc` extra'si (asil alan)
     * @param data `data` extra'si. `epc` bos gelirse yedek olarak kullanilir.
     * @param rssi `rssi` extra'si; cihaz dili Turkce iken ondalik ayirici virgul (`-50,40`).
     */
    fun parse(epc: String?, data: String?, rssi: String?): RfidTag? {
        val id = normalizeEpc(epc) ?: normalizeEpc(data) ?: return null
        return RfidTag(epcId = id, rssi = parseRssi(rssi), antId = SINGLE_ANTENNA)
    }

    /** Bosluklari atar, harfleri buyuk harfe cevirir (BLD SDK'si da hex'i buyuk harf verir). */
    private fun normalizeEpc(value: String?): String? {
        val trimmed = value?.trim()?.uppercase().orEmpty()
        return trimmed.ifEmpty { null }
    }

    fun parseRssi(value: String?): Int? =
        value?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { !it.isNaN() && !it.isInfinite() }
            ?.roundToInt()
}
