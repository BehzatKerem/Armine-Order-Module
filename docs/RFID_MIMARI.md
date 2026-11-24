# RFID Mimarisi (Çoklu Cihaz)

Uygulama birden fazla RFID el terminalinde aynı APK ile çalışır. Ekranlar hangi cihazda
olduklarını bilmez. Cihaza özel her şey `rfid/` paketindeki sürücülerdedir.

| Cihaz | Sürücü | Yöntem | Ayrıntı |
|---|---|---|---|
| BLD N60 | `rfid/bld/BldRfidReader` | `lcrrgxmodule-release.aar` (seri port) | [RFID_SDK_ENTEGRASYON.md](RFID_SDK_ENTEGRASYON.md) |
| Chainway C5 | `rfid/chainway/ChainwayRfidReader` | Keyboard Emulator broadcast'i | [chainway-rfid.md](chainway-rfid.md) |

## Katmanlar

```
Tarama ekranları (Wholesale, Inventory, WarehouseTransfer, RfidHandheldCounting, RfidScan)
   │  startInventoryAsync / stopInventoryAsync / isConnected
   │  onKeyDown/onKeyUp ──► RfidTriggerController (tetik davranışı, ekran yaşam döngüsü)
   ▼
RfidConnectionManager      tek örnek, tek arka plan thread'i ("rfid-io"), ses (RfidFeedback)
   │
   ▼
RfidReader (arayüz) ◄── RfidDeviceDetector.resolve(): ayardaki seçim, yoksa otomatik tespit
   ├─ BldRfidReader        tetik = KeyEvent (305/619/621), uygulama okumayı başlatır/durdurur
   └─ ChainwayRfidReader   tetik = Keyboard Emulator, sürücü broadcast akışından tetik olayı üretir
```

| Dosya | Görev |
|---|---|
| `RfidReader.kt` | Sürücü sözleşmesi ve ortak tipler (`RfidTag`, `ConnectResult`, dinleyiciler) |
| `RfidDeviceDetector.kt` | Hangi sürücünün kullanılacağına karar verir ve sürücüyü oluşturur |
| `RfidConnectionManager.kt` | Ekranların tek giriş noktası. Bütün sürücü çağrılarını sırayla çalıştırır. |
| `RfidTriggerController.kt` | Toggle / Bas-Çek / kilitli tarama kuralları. Ekran arka plana geçince okumayı durdurur. |
| `RfidFeedback.kt` | Bip ve uyarı sesi. Cihazdan bağımsız. |

## Cihaz seçimi

Sunucu Ayarları → **RFID Cihazı**: *Otomatik algıla* (varsayılan) / *BLD N60* / *Chainway C5*.

Otomatik tespit şu sırayla karar verir:
1. `com.rscja.scanner` paketi kuruluysa, ya da `Build.MANUFACTURER`/`BRAND` içinde
   `chainway`/`rscja` geçiyorsa **Chainway** seçilir.
2. Hiçbiri yoksa **BLD** seçilir.

Paket kontrolü Android 11+ cihazlarda manifestteki `<queries>` girdisini gerektirir.

Seçim değişince eski sürücü kapatılır (`reloadReaderAsync`) ve yeni tiple bağlanılır.

## Chainway'de tetik nasıl çalışıyor?

Chainway'de tetiği Keyboard Emulator yönetir. Uygulama okumayı başlatamaz ve tetiğin
`KeyEvent`'ini almaz. Ekranlar ise "tetik basıldı/bırakıldı" modeline göre yazılmış
(iptal modu, diyaloglar, özet ekranına geçiş vb.). Bu modeli bozmamak için sürücü
tetik olaylarını broadcast akışından üretir:

- Ekran ön plandayken receiver kayıtlıdır. İlk etiket gelince `onTriggerPressed` çağrılır,
  ekran `startInventoryAsync` yapar. Bu arada gelen etiketler tamponlanır, kaybolmaz.
- 600 ms broadcast gelmezse `onTriggerReleased` çağrılır.
- Bu cihazda Toggle modu yoktur: tetik basılı tutuldukça okur. Ayarlardaki "Tetik Bas-Çek"
  anahtarı Chainway seçiliyken pasiftir.
- Receiver ekran `onPause` olunca bırakılır (yalnızca aktif okuma kalmadıysa).

## Yeni bir cihaz eklemek

1. `rfid/<marka>/<Marka>RfidReader.kt` içinde `RfidReader`'ı uygula. Cihaz SDK tipleri
   bu sınıfın dışına çıkmasın.
2. `RfidReaderType`'a yeni değer ekle (`prefValue` kalıcıdır, sonradan değiştirme).
3. `RfidDeviceDetector.detect` ve `createReader` içine ekle. Saf `detect(...)` için test yaz.
4. Sunucu Ayarları'ndaki radyo grubuna seçenek ve metin ekle.
5. Tetik `KeyEvent` olarak geliyorsa `isTriggerKey` ile kodları bildir. Kodu bulmak için
   `adb logcat -s RFID_TRIGGER_KEY` (`RfidTriggerController` tanınmayan tuşları loglar).

## Testler

`app/src/test/.../rfid/`: broadcast ayrıştırma (`ChainwayBroadcastParserTest`) ve
cihaz tespiti (`RfidDeviceDetectorTest`). `./gradlew :app:testDebugUnitTest`
