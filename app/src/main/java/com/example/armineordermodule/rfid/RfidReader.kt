package com.example.armineordermodule.rfid

/**
 * Tek bir RFID el terminali ailesi icin donanim surucusu sozlesmesi.
 *
 * Uygulamada birbirinden cok farkli calisan iki okuyucu var:
 * - [RfidReaderType.BLD]: `lcrrgxmodule-release.aar` SDK'si. Uygulama seri porta baglanir,
 *   okumayi kendisi baslatir/durdurur, tetik tusunu `KeyEvent` olarak kendisi yakalar.
 * - [RfidReaderType.CHAINWAY]: Cihazdaki Keyboard Emulator uygulamasi okuyucuyu ve tetigi
 *   yonetir, sonuclari broadcast olarak yollar. Uygulama sadece dinler.
 *
 * Ekranlar bu arayuzu dogrudan gormez; [RfidConnectionManager] uzerinden konusur. Butun
 * metotlar [RfidConnectionManager]'in tek arka plan thread'inden cagrilir (sira garantili),
 * bu yuzden uygulamalarin kendi aralarinda ayrica kilitlemesi gerekmez. Istisna:
 * [setTriggerListener] ve [isTriggerKey] ana thread'den de cagrilabilir.
 */
interface RfidReader {

    val type: RfidReaderType

    val isConnected: Boolean

    /**
     * Fiziksel tetigin nasil yonetildigi. [TriggerSource.KEY_EVENT] ise tetik `KeyEvent` olarak
     * Activity'ye duser ([isTriggerKey]); [TriggerSource.READER] ise tetik olaylarini surucu
     * kendisi uretir ([setTriggerListener]).
     */
    val triggerSource: TriggerSource

    /**
     * `true` ise tetik sadece "basili tuttukca okur" seklinde calisir; uygulamadaki
     * Toggle / Bas-Cek tercihi bu okuyucuda uygulanamaz.
     */
    val isHoldToReadOnly: Boolean

    /** Bloklayan cagri. Zaten bagliysa hemen [ConnectResult.Success] doner. */
    fun connect(): ConnectResult

    fun disconnect()

    /**
     * Envanter okumayi baslatir. [listener] her okunan etiket icin **arka plan thread'inden**
     * cagrilir; ayni EPC birden fazla kez gelebilir (tekillestirme cagirandadir).
     */
    fun startInventory(listener: RfidTagListener): Boolean

    fun stopInventory()

    /** Bu `keyCode` bu okuyucunun fiziksel tetigi mi? [TriggerSource.READER] ise hep `false`. */
    fun isTriggerKey(keyCode: Int): Boolean

    /**
     * [TriggerSource.READER] okuyucularda tetik basildi/birakildi olaylari bu dinleyiciye
     * **ana thread'de** iletilir. `null` verilince dinleme biter. [TriggerSource.KEY_EVENT]
     * okuyucularda etkisizdir.
     */
    fun setTriggerListener(listener: RfidTriggerListener?)
}

enum class RfidReaderType(val prefValue: String) {
    /** BLD N60 ve benzerleri: `lcrrgxmodule-release.aar` (seri port, uygulama kontrollu). */
    BLD("bld"),

    /** Chainway C5: Keyboard Emulator (`com.rscja.scanner`) broadcast'leri. */
    CHAINWAY("chainway");

    companion object {
        @JvmStatic
        fun fromPrefValue(value: String?): RfidReaderType? =
            entries.firstOrNull { it.prefValue == value }
    }
}

enum class TriggerSource { KEY_EVENT, READER }

/** SDK tiplerinden bagimsiz okunmus etiket. [rssi] bilinmiyorsa `null`. */
data class RfidTag(
    val epcId: String,
    val rssi: Int?,
    val antId: Int,
)

fun interface RfidTagListener {
    /** Arka plan thread'inden cagrilir; UI guncellemesi icin `runOnUiThread` kullanin. */
    fun onTagRead(tag: RfidTag)
}

interface RfidTriggerListener {
    fun onTriggerPressed()
    fun onTriggerReleased()
}

/** Java tarafindan `Unit.INSTANCE` dondurmeden kullanilabilen sonuc callback'i. */
fun interface RfidCallback<T> {
    fun onResult(result: T)
}

sealed class ConnectResult {
    object Success : ConnectResult()
    data class Failure(val reason: String) : ConnectResult()

    val isSuccess: Boolean
        get() = this is Success
}
