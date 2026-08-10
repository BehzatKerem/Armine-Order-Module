# RFID UHF SDK Entegrasyon Rehberi

Bu doküman, bu projede (`Rfid test`) UHF RFID el terminaliyle konuşmak için kullanılan
`lcrrgxmodule-release.aar` SDK'sının nasıl çalıştığını, hangi versiyon/uyumluluk
noktalarına dikkat edilmesi gerektiğini ve tetik tuşu entegrasyonunun nasıl kurulduğunu
anlatır. Amaç: bu mantığı başka bir Android projesine taşırken (ya da sıfırdan kurarken)
referans alınabilecek tek bir kaynak olması.

Kaynak cihaz/SDK: Çin üretimi UHF RFID el terminali (test edilen model: **BLD N60**),
üreticinin verdiği `RRGX_RFID_Demo` örnek uygulaması ve `RFID Development Guide.pdf`
baz alınarak incelenmiştir.

---

## 1. Genel Mimari

```
┌─────────────────────┐      ┌──────────────────────┐      ┌───────────────────────┐
│   Compose UI         │◄────►│ InventoryViewModel    │◄────►│ RfidManager            │
│ (SessionSetupScreen,  │      │ (uiState: StateFlow)  │      │ (SDK sarmalayıcı)      │
│  ScanningScreen)      │      │                       │      │                       │
└─────────────────────┘      └──────────────────────┘      └───────────┬───────────┘
                                                                          │
        ┌─────────────────────────────────────────────────────────────┘
        ▼
┌────────────────────────────┐
│ com.lckj.lcrrgxmodule (AAR) │   ← üretici SDK'sı (kapalı kaynak, .aar)
│  - LcModule / ILcUhfProduct  │
│  - android.bld.RFIDManager   │   ← cihaza özel sistem servisi (sadece gerçek cihazda var)
└──────────────┬─────────────┘
               ▼
      /dev/ttyS3 (seri port) ──► UHF okuma modülü (chip: GX ya da RR ailesi)
```

Katmanlar:

- **`RfidManager`** (`app/src/main/java/com/example/rfidtest/rfid/RfidManager.kt`) — SDK'nın
  tüm düşük seviye detaylarını (güç verme, seri port, baud rate, çip tipi, callback) izole eder.
  Geri kalan kodun tek bildiği şey `connect()`, `startInventory()`, `stopInventory()`, `disconnect()`.
- **`InventoryViewModel`** (`app/src/main/java/com/example/rfidtest/ui/InventoryViewModel.kt`) —
  UI durumunu (`InventoryUiState`) yönetir, `RfidManager`'ı çağırır, tekilleştirme/istatistik mantığını içerir.
- **`MainActivity`** — fiziksel tetik tuşunun `KeyEvent`'lerini yakalayıp ViewModel'e yönlendirir.
- **UI (Compose)** — sadece `uiState`'i okur, hiçbir SDK sınıfını doğrudan görmez.

Bu ayrım sayesinde SDK'ya özgü hiçbir tip (`ILcUhfProduct`, `ReadTag`, `TagCallback` vb.)
`RfidManager` dışına sızmaz — başka bir projeye taşırken sadece `rfid/` paketini ve
`ReadTag`'i tüketen küçük arayüzü taşımak yeterli olur.

---

## 2. Gerekli Dosyalar / Bağımlılıklar

### 2.1 AAR dosyaları

`app/libs/` klasöründe iki `.aar` var, **sadece biri gerekli**:

| Dosya | Gerekli mi? | Ne işe yarar |
|---|---|---|
| `lcrrgxmodule-release.aar` | ✅ **Evet** | Asıl RFID SDK'sı. `com.lckj.lcrrgxmodule.*`, `com.rfid.*`, `android.bld.RFIDManager`, native `libserial_port.so` hepsi bunun içinde. |
| `library1.0.7-release.aar` | ❌ **Hayır** | Üreticinin demosunda kullanılan `me.weyye.hipermission:library:1.0.7` (çalışma zamanı izin isteme UI'ı) — RFID ile alakası yok, projede referans edilmiyor. Başka projeye taşırken kopyalamaya gerek yok. |

`build.gradle.kts` içindeki tek satır bağımlılık:

```kotlin
dependencies {
    implementation(files("libs/lcrrgxmodule-release.aar"))
    // ...
}
```

> Üreticinin kendi demosu `implementation fileTree(include: ['*.aar'], dir: 'libs')` kullanıyor
> (klasördeki tüm .aar'ları otomatik ekliyor). Biz bilinçli olarak tek dosyayı işaret ediyoruz;
> yeni bir proje kurarken de sadece `lcrrgxmodule-release.aar`'ı kopyalayıp doğrudan referans
> etmeniz yeterli.

### 2.2 AAR'ın içeriği (referans için)

`lcrrgxmodule-release.aar` içinde:

- `classes.jar` → asıl API: `com.lckj.lcrrgxmodule.factory.*`, `com.rfid.*`, `android.bld.RFIDManager`
- `libs/reader.jar` → alt seviye "GX" protokol implementasyonu (`com.gg.reader.api.*`) ve USB-seri
  sürücüleri (FTDI/CP21xx/CH34x/Prolific/CDC-ACM) — bu USB sürücüler bizim kullanım senaryomuzda
  (dahili seri port) devreye girmiyor, SDK'nın farklı donanım varyantlarını (USB RFID okuyucular)
  desteklemek için bundle edilmiş.
- `jni/<ABI>/libSerialPort.so`, `libserial_port.so` → native seri port erişimi. **arm64-v8a,
  armeabi, armeabi-v7a, mips, mips64, x86, x86_64** için ayrı ayrı var. `abiFilters` ile bunları
  daraltırsanız hedef cihazın gerçek ABI'sini (çoğu MTK tabanlı el terminali `armeabi-v7a` veya
  `arm64-v8a`) mutlaka dahil edin, yoksa `UnsatisfiedLinkError` alırsınız.

---

## 3. Versiyon Uyumluluğu / Gradle Ayarları

Bu projede kullanılan (doğrulanmış) versiyonlar — `gradle/libs.versions.toml`:

| Bileşen | Versiyon |
|---|---|
| Android Gradle Plugin (AGP) | 9.3.0 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| `compileSdk` | 36 |
| `minSdk` | 24 |
| `targetSdk` | 36 |
| Java uyumluluğu | 11 |

AAR'ın kendi gömülü manifestindeki (`aar/AndroidManifest.xml`) beyanı:

```xml
<uses-sdk android:minSdkVersion="19" android:targetSdkVersion="32" />
```

**Dikkat edilecek noktalar:**

1. **`minSdk` alt sınırı SDK'dan değil, bizim projeden gelir.** AAR `minSdk=19` istiyor
   olsa da, gerçek donanım zaten Android 9/11 gibi vendor ROM'larla geliyor; projede
   `minSdk=24` seçmek güvenli (Compose zaten pratikte 21+ ister, biz 24 kullandık).
2. **`targetSdk` uyuşmazlığı sorun çıkarmaz.** AAR'ın manifestindeki `targetSdkVersion=32`
   sadece kütüphanenin kendi manifest merge davranışı için bilgi amaçlıdır; uygulamanın
   asıl `targetSdk`'sini (36) geçersiz kılmaz. Manifest merge sırasında AGP bunu otomatik
   halleder, ekstra bir `tools:replace` gerekmedi.
3. **AGP/Kotlin versiyonu bağımsızdır** — AAR sadece derlenmiş `.class` + native `.so`
   içerdiği için (Kotlin/Java kaynak kodu değil), yeni projenin Kotlin/AGP versiyonuyla
   uyumluluk sorunu yaşamazsınız. Tek kısıtı `minCompileSdk=1` (aar-metadata.properties) —
   pratikte hiçbir engel çıkarmaz.
4. **ABI**: `abiFilters` tanımlamadıysanız (bu projede tanımlanmadı) Gradle AAR'daki tüm
   ABI'lerin native kütüphanelerini paketler; APK boyutu artar ama uyumluluk sorunu olmaz.
   Boyut önemliyse ve hedef cihazlar biliniyorsa (`arm64-v8a` çoğunlukla yeterli):
   ```kotlin
   defaultConfig {
       ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
   }
   ```
5. **Java 8/11/17 uyumluluğu**: AAR eski `sourceCompatibility 1.8` ile derlenmiş demo projeyle
   birlikte geliyor olsa da, bizim projede `JavaVersion.VERSION_11` ile hiçbir çatışma
   yaşanmadı (AAR zaten derlenmiş bytecode, hedef projenin `compileOptions`'ından etkilenmez).
6. **`compileSdk` sürüm formatı**: AGP 9.x'te `compileSdk { version = release(36) { minorApiLevel = 1 } }`
   şeklindeki yeni sözdizimi kullanıldı; eski `compileSdk = 36` de çalışır, sadece AGP
   versiyonuna göre söz dizimi değişebilir.

### 3.1 İmzalama (signing) — kritik pratik not

`release` build type varsayılan olarak imzasızdır; imzasız APK fiziksel cihaza
**"Uygulama yüklenmedi" (App not installed)** hatasıyla reddedilir. Test/geliştirme
aşamasında debug anahtarıyla imzalamak yeterli:

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

Play Store'a veya kurumsal dağıtıma çıkmadan önce gerçek bir keystore ile değiştirilmeli.

### 3.2 Manifest / izinler

Bu projede RFID okuma için **ekstra bir izin gerekmedi** (seri port erişimi uygulama
imzası/UID seviyesinde vendor ROM tarafından önceden yetkilendirilmiş durumda,
runtime permission istemiyor). Üreticinin demosu şu izinleri istiyor — ama bunlar
RFID okumanın kendisi için değil, demonun **Excel'e (.xls) dışa aktarma** özelliği için:

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Eğer yeni projede dosyaya dışa aktarma / uzun süreli arka plan tarama gibi özellikler
eklenecekse bu izinleri (ve Android 13+ için `READ_MEDIA_*` karşılıklarını) ekleyin;
salt RFID okuma+yazma için gerekmiyor.

---

## 4. Bağlanma (Connect) Akışı

Kod: `RfidManager.connect()` (`app/src/main/java/com/example/rfidtest/rfid/RfidManager.kt:59-114`)

Adımlar:

1. **`PowerUtil.power("1")`** — UHF modülüne güç verir (cihazda `/proc/gpiocontrol/set_uhf`
   dosyasına yazan vendor-özel bir mekanizma; emülatörde veya bu GPIO'ya sahip olmayan
   cihazda sessizce başarısız olabilir, bu yüzden `try/catch` ile sarmalanmalı).
2. **Seri port yolu seçimi**: Android 9 (API 28, `Build.VERSION_CODES.P`) için `/dev/ttyS2`,
   diğer tüm sürümler için `/dev/ttyS3`. Bu, üreticinin demosundaki (`Connect232.java`)
   davranışın birebir aynısı — cihaz OS versiyonuna göre farklı seri port düğümü kullanıyor.
3. **Çip tipi tespiti — 3 aşamalı fallback**:
   - `LcModule(context).createProduct()` → otomatik tespit (iç mekanizma `android.bld.RFIDManager.getRFIDType()`
     sistem servisinden okuyor). Bazı cihazlarda bu değer "ayarlanmamış" gelip `null` dönebiliyor.
   - Başarısızsa `createProduct(0x20)` → **GX ailesi çip** (test edilen N60 cihazında çalışan buydu).
   - O da başarısızsa `createProduct(0x10)` → **RR ailesi çip**.
   - `createProduct()` **emülatörde her zaman `null` döner** (vendor sistem servisi yok) —
     bu durumu `Throwable` yakalayarak ve null kontrolüyle nazikçe `Failure` sonucuna çevirmek şart,
     yoksa `NullPointerException` ile çöker.
4. **`uhf.Connect(port, baud)`** — önce `115200` baud denenir; SDK özel hata kodu **`48`**
   ("yanlış baud rate") dönerse `57600` ile tekrar denenir. `Connect()` başarıda `0` döner.
5. Başarılı olan `ILcUhfProduct` örneği saklanır (`product` alanı); sonraki tüm çağrılar
   (`startInventory`, `stopInventory`, `disconnect`) bu örnek üzerinden yapılır.

```kotlin
// Basitleştirilmiş özet
PowerUtil.power("1")
val port = if (Build.VERSION.SDK_INT == P) "/dev/ttyS2" else "/dev/ttyS3"
val uhf = LcModule(context).createProduct()          // ya da createProduct(0x20) / (0x10)
var result = uhf.Connect(port, 115200)
if (result == 48) result = uhf.Connect(port, 57600)  // 48 = yanlış baud rate
// result == 0 → bağlantı başarılı
```

**Neden fallback zinciri gerekli?** Aynı SDK farklı donanım nesillerinde/ROM'larda
tutarsız davranabiliyor: bazı cihazlarda otomatik tespit güvenilir, bazılarında
(test ettiğimiz N60 dahil) sistem servisi "initial state / unset" değeri döndürüyor ve
otomatik tespiti manuel olarak geçmek gerekiyor. Üreticinin kendi demosu da (`Connect232.java`)
aynı 3 seçeneği kullanıcıya elle seçtiriyor; biz bunu otomatik sırayla deniyoruz.

### 4.1 Bağlantıyı kapatma

```kotlin
fun disconnect() {
    product?.StopRead()
    product?.DisConnect()
    PowerUtil.power("0")
    product = null
}
```

`ViewModel.onCleared()` içinde çağrılır. **Önemli:** Android `onCleared()`/`onDestroy()`
her zaman garanti değildir — `am force-stop` gibi süreç zorla sonlandırıldığında bu
çağrılmaz ve donanım "sürekli okuma" modunda takılı kalabilir (bir sonraki bağlantıda
modülün hâlâ eski oturumdan veri okuduğu gözlemlendi). Üretimde bu riske karşı uygulama
başlangıcında `StopRead()`'i bir kez "temizlik" amaçlı çağırmak faydalı olabilir.

---

## 5. Envanter Okuma (Inventory) Akışı

Kod: `RfidManager.startInventory()` / `stopInventory()`

```kotlin
fun startInventory(
    onTagRead: (ReadTag) -> Boolean,   // true dönerse "tekrar okuma" (ses feedback için)
    onCrcError: () -> Unit,
): Boolean {
    val uhf = product ?: return false
    uhf.SetCallBack(object : TagCallback {
        override fun tagCallback(tag: ReadTag?) {
            if (tag == null) { uhf.beginSound(false); return }
            val isRepeat = onTagRead(tag)
            uhf.beginSound(isRepeat)
        }
        override fun tagCallbackFailed(reason: Int): Int = 0
        override fun CRCErrorCallBack(reason: Int): Int { onCrcError(); return 0 }
        override fun FinishCallBack() = Unit
    })
    return uhf.StartRead() == 0
}

fun stopInventory() { product?.StopRead() }
```

- `TagCallback.tagCallback()` her etiket okunuşunda **ayrı bir thread'den** çağrılır (SDK
  içi okuma döngüsü); bu yüzden `ViewModel` tarafında state güncellemesi `MutableStateFlow.update {}`
  ile yapılıyor (thread-safe, atomik).
- `ReadTag` alanları: `epcId: String`, `rssi: Int`, `antId: Int`.
- Tekilleştirme mantığı `InventoryViewModel.handleTagRead()` içinde: EPC anahtarına göre
  `LinkedHashMap<String, ScannedTag>` tutulur; yeni EPC ise sayaç 1 ile eklenir, mevcutsa
  `readCount++`, `rssi` güncellenir, `antenna` bitwise OR ile birleştirilir (çoklu antenden
  aynı etiket okunursa hangi antenlerden geldiği bilgisi kaybolmaz).
- `beginSound(isRepeat)` → cihazın kendi buzzer/ses geri bildirimini tetikler (farklı ses,
  ilk okuma / tekrar okuma için).
- `getInventoryTagMapList()` / `getInventoryTagResultList()` — SDK'nın kendi iç tuttuğu
  liste (demo bunu kullanıyor); biz callback tabanlı yaklaşımı tercih ettik çünkü UI'a
  gerçek zamanlı akış (StateFlow) ile besleme yapmak daha doğal.

---

## 6. Fiziksel Tetik Tuşu Entegrasyonu

Bu, üretici dokümantasyonunda **resmi olarak belgelenmemiş**, tamamen ampirik olarak
tespit edilen bir konu. Özet yöntem ve sonuç:

### 6.1 Tespit yöntemi

1. `adb shell dumpsys input` ile cihazın giriş aygıtlarını (`input devices`) listeleyip
   hangi `KeyLayoutFile`'ı kullandıklarını bulduk. Tetik tuşu `gpio_keys` aygıtına bağlıydı.
2. `adb shell cat /system/usr/keylayout/<dosya>.kl` ile o layout dosyasını okuduk; tetik
   tuşu ham Linux scancode'ları `KEY_LSCAN` (103) / `KEY_RSCAN` (108) olarak tanımlıydı —
   bunlar **standart Android `KeyEvent` isimleri değil**, vendor'un ROM'a özel eklediği
   tuş isimleri.
3. Bu özel isimler standart olmadığı için Android'in gerçekte `onKeyDown(keyCode, ...)`'a
   ilettiği sayısal `keyCode` tahmin edilemiyordu. Çözüm: uygulamaya her tuş basımını
   loglayan geçici bir `Log.d(...)` eklendi, cihazda tetiğe basılıp `adb logcat` ile
   gerçek değer okundu.
4. **Sonuç: N60 cihazında tetik tuşu `keyCode = 619` üretiyor.** Bu, üreticinin
   `RRGX_RFID_Demo`'sundaki yorum satırında geçen "S70 el terminali tetik tuşu: 619"
   değeriyle aynı çıktı — yani farklı model isimlerine rağmen bazı cihazlar aynı sayısal
   kodu paylaşabiliyor.

> **Başka bir cihaza taşırken**: `MainActivity.onKeyDown` içindeki `Log.d("RFID_TRIGGER_KEY", ...)`
> satırını aktif bırakın, `adb logcat -s RFID_TRIGGER_KEY` ile izleyin, tetiğe basın, çıkan
> `keyCode` değerini `TRIGGER_KEY_CODES` setine ekleyin. Bilinen değerler (üretici demosundan):
> **305** (genel), **619** (S70 / N60), **621** (N41U).

### 6.2 Uygulama tarafı: `MainActivity`

```kotlin
private val TRIGGER_KEY_CODES = setOf(305, 619, 621)

override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode in TRIGGER_KEY_CODES) {
        if (event?.repeatCount == 0) {          // basılı tutmada gelen tekrar olaylarını yok say
            if (viewModel.uiState.value.pressAndHoldMode) viewModel.onTriggerDown()
            else viewModel.onTriggerPressed()
        }
        return true                              // olayı tüket, sistemde başka bir şeyi tetiklemesin
    }
    return super.onKeyDown(keyCode, event)
}

override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode in TRIGGER_KEY_CODES) {
        if (viewModel.uiState.value.pressAndHoldMode) viewModel.onTriggerUp()
        return true
    }
    return super.onKeyUp(keyCode, event)
}
```

**Neden `Activity.onKeyDown/onKeyUp` (Compose değil)?** Fiziksel donanım tuşları önce
`Activity`'nin `dispatchKeyEvent`/`onKeyDown` zincirine düşer; Compose'un kendi
`Modifier.onKeyEvent` mekanizması odaklanmış (focused) bir composable gerektirir ve
donanım tetik tuşları için güvenilir değildir. Bu yüzden `ViewModel`'i `by viewModels()`
ile `Activity` seviyesinde tutup, hem Compose (`viewModel()` composable'ı aynı store'dan
aynı örneği bulur) hem de `onKeyDown/onKeyUp` bu tek örneği paylaşır.

**`event.repeatCount == 0` neden şart?** Bir tuş fiziksel olarak basılı tutulduğunda
Android işletim sistemi `onKeyDown`'ı defalarca (repeat) çağırır (`repeatCount` her
seferinde artar). Bu kontrol olmadan toggle modunda tuşu 1 saniye basılı tutmak bile
okumayı onlarca kez aç/kapa yapardı.

### 6.3 İki tetik modu

`InventoryUiState.pressAndHoldMode: Boolean` alanı ile kullanıcı ekranda (sağ üstteki
"Tetik Bas-Çek" switch'i) iki mod arasında seçim yapar:

| Mod | Switch | Davranış |
|---|---|---|
| **Toggle (varsayılan)** | Kapalı | Her tetik basışı okumayı **aç/kapa** yapar (`onTriggerPressed()` → `toggleReading()`). Bırakma anının (`onKeyUp`) bir etkisi yok. |
| **Bas-Çek (momentary)** | Açık | `onKeyDown` → okuma **başlar** (`onTriggerDown()`), `onKeyUp` → okuma **durur** (`onTriggerUp()`). Tetik bırakılana kadar okumaya devam eder. |

Her iki mod da aynı alt fonksiyona (`InventoryViewModel.resumeOrPause()`) yönlenir;
fark sadece "ne zaman çağrıldığı". Bu sayede duraklatma/devam ettirme mantığı
(aşağıya bakınız) her iki modda da tutarlı çalışır.

### 6.4 Duraklat / Devam Et (pause/resume) semantiği

Önemli tasarım kararı: **"Taramayı Başlat" ekran butonu artık SDK okumasını doğrudan
başlatmaz.** Sadece oturumu (lokasyon, sayaçlar) hazırlar ve "duraklatılmış" durumda
bekler; gerçek `StartRead()` çağrısı yalnızca tetik (veya ekrandaki eşdeğer buton) ile
tetiklenir. Bu, gerçek el terminali kullanım desenine uyar: operatör lokasyonu seçer,
ardından tarama boyunca sadece tetiğe basar/bırakır.

- **Duraklatma** (`isReading=false`) oturumu **bitirmez**: `tags`, `elapsedSeconds`,
  `readsPerSecond` olduğu gibi kalır (dondurulur).
- **Devam ettirme**, kaldığı yerden sürer: `elapsedSeconds` sıfırlanmaz, `tags` Map'i
  temizlenmez. Hız (`readsPerSecond`) hesaplayan zamanlayıcı, yeniden başlarken
  `previousTotalReads`'i **0 yerine mevcut birikmiş okuma sayısından** başlatır —
  aksi halde ilk saniyede yanlış bir "hız patlaması" (ör. 300+/sn) görünür.
- Oturumu **tamamen bitirmek** (`ScanPhase.STOPPED`, özet ekranı) ayrı bir eylem
  (`endSession()` / "Oturumu Bitir" butonu) — tetik veya toggle bununla karışmaz.

---

## 7. Bilinen Sorunlar ve Çözümleri (özet tablo)

| Belirti | Kök neden | Çözüm |
|---|---|---|
| Emülatörde uygulama çöküyor | `createProduct()` emülatörde `null` döner; `null` kontrolü yoksa `Connect()` çağrısı NPE atar | `try/catch(Throwable)` + null kontrolü, `ConnectResult.Failure` döndür |
| Fiziksel cihazda "Uygulama yüklenemedi" | `release` build imzasız | `signingConfig = signingConfigs.getByName("debug")` (ya da gerçek keystore) |
| Kurulum yine reddediliyor | Cihazda vendor MDM'in "Application Installation Whitelist" özelliği açık | Cihazın MDM ayarları uygulamasından (bu cihazda `com.bld.settings.mdm`) beyaz listeyi kapatmak gerekti |
| Gerçek cihazda "UHF modülü bulunamadı" | Otomatik çip tipi tespiti (`createProduct()`) bu ROM'da güvenilir sonuç vermiyor | `createProduct(0x20)` / `createProduct(0x10)` ile manuel fallback |
| Tetik tuşu hiçbir şey yapmıyor | Tuş kodu standart değil, tahmin edilemez | `Log.d` ile gerçek `keyCode`'u logcat'ten oku, listeye ekle |
| Tetik basılı tutulunca okuma çırpınıyor | `onKeyDown` repeat olayları | `event.repeatCount == 0` kontrolü |
| `adb shell input tap` yanlış yere basıyor | Ekran görüntüsü önizlemesi cihazın gerçek çözünürlüğünden (bu cihazda 720×1440) küçük ölçekte gösteriliyor | Koordinatları `wm size` ile alınan gerçek çözünürlüğe göre ölçekleyin |

---

## 8. Başka Bir Projeye Taşıma Checklist'i

1. `app/libs/lcrrgxmodule-release.aar` dosyasını yeni projeye kopyalayın (diğer AAR gerekmez).
2. `build.gradle.kts` → `implementation(files("libs/lcrrgxmodule-release.aar"))` ekleyin.
3. `minSdk ≥ 21` olacak şekilde ayarlayın (biz 24 kullandık); `compileSdk`/`targetSdk`
   projenizin güncel değerleri olabilir, AAR ile çakışma yaşanmaz.
4. `rfid/RfidManager.kt` dosyasını olduğu gibi taşıyın (bağımsız, sadece SDK'ya bağımlı).
5. Tetik tuşu için `MainActivity`'nizde `onKeyDown`/`onKeyUp` override'larını ekleyin;
   `TRIGGER_KEY_CODES` setini **yeni cihazda mutlaka yeniden doğrulayın** (Bölüm 6.1'deki
   yöntemle) — farklı model/ROM farklı kod üretebilir.
6. `release` build type'ı imzalayın (gerçek keystore veya en azından debug key), aksi
   halde fiziksel cihaza kurulum başarısız olur.
7. Cihazda vendor MDM/whitelist kısıtlaması varsa (özellikle kurumsal/toplu satın alınan
   el terminallerinde yaygın) devre dışı bırakılması gerekebilir.
8. İlk bağlantıyı test ederken `connect()`'i Bölüm 4'teki 3 aşamalı fallback ile
   çağırdığınızdan emin olun; sadece `createProduct()` (otomatik) ile denemeyin.
9. Room/veritabanı, dosyaya aktarma gibi ek özellikler eklerseniz Bölüm 3.2'deki izinleri
   (storage, vs.) manifeste ekleyin.

---

## 9. Referans: Tam API Yüzeyi (`ILcUhfProduct`)

`javap` ile AAR'ın `classes.jar`'ından çıkarılan tam arayüz (ileride ihtiyaç duyulabilecek
ama şu an kullanılmayan metodlar da dahil — okuma/yazma, kilitleme, RF gücü, bölge ayarı vb.):

```java
public interface com.lckj.lcrrgxmodule.factory.ILcUhfProduct {
    void beginSound(boolean);
    void setsoundid(int, android.media.SoundPool);
    void playSound();
    int Connect();
    int Connect(String port, int baudRate);
    int DisConnect();
    void SetInventoryParameter(com.rfid.trans.ReaderParameter);
    com.rfid.trans.ReaderParameter GetInventoryParameter();
    int GetUHFInformation(byte[], byte[], byte[], byte[], byte[], byte[], byte[]);
    int SetRfPower(int power);
    int SetRegion(int region, int startFreq, int endFreq);
    String ReadDataByEPC(String epc, byte memBank, int wordPtr, byte wordCnt, byte[] password);
    String ReadDataByTID(String tid, byte memBank, int wordPtr, byte wordCnt, byte[] password);
    int WriteDataByEPC(String epc, byte memBank, int wordPtr, byte[] data, String password);
    int WriteDataByTID(String tid, byte memBank, byte wordPtr, byte[] data, String password);
    int WriteEPCByTID(String tid, String newEpc, byte[] password);
    int Lock(String epc, byte lockBank, byte lockType, String password);
    int Kill(String epc, String password);
    void SetCallBack(com.rfid.trans.TagCallback);
    int StartRead();
    void StopRead();
    java.util.List<com.rfid.InventoryTagMap> getInventoryTagMapList();
    java.util.List<com.rfid.InventoryTagResult> getInventoryTagResultList();
    int RfOutput(byte);
    int SetPowerMode(int);
    int MeasureTemperature(byte[]);
    int MeasureReturnLoss(byte[]);
    int SetAntenna(byte);
    int SetBeepNotification(int);
    int SetWorkMode(byte);
    int SetBaudRate(byte);
    int GetModuleVersion();
}
```

Yardımcı veri sınıfları:

```java
public class com.rfid.trans.ReadTag {
    public String epcId;
    public int rssi;
    public int antId;
}

public class com.rfid.trans.ReaderParameter {
    public byte ComAddr;
    public int TidPtr, TidLen, Session, QValue, ScanTime, Antenna, Interval, AsciiPtr;
}

public interface com.rfid.trans.TagCallback {
    void tagCallback(com.rfid.trans.ReadTag);
    int tagCallbackFailed(int reason);
    int CRCErrorCallBack(int reason);
    void FinishCallBack();
}

public class com.rfid.PowerUtil {
    public static void power(String onOff); // "1" = aç, "0" = kapat
}

public class com.lckj.lcrrgxmodule.factory.LcModule {
    public ILcUhfProduct createProduct();      // otomatik çip tipi tespiti
    public ILcUhfProduct createProduct(int chipType); // 0x10 = RR ailesi, 0x20 = GX ailesi
}
```

Bu projede kullanılmayan ama SDK'da mevcut olan yetenekler (ileride ihtiyaç olursa):
etiket **yazma/kilitleme/kill** (`WriteDataByEPC/TID`, `Lock`, `Kill`), **RF gücü ve
bölge/frekans** ayarı (`SetRfPower`, `SetRegion`), **anten seçimi** (`SetAntenna`,
çoklu antenli sabit okuyucular için).

---

## 10. Hedef Proje: Armine Order Module — Yapılacaklar

Hedef dizin: `C:\Users\Behzat\Documents\GitHub\Armine Order Module`

Uyumluluk kontrolü (2026-08-03): Gradle/SDK kısıtları karşılanıyor (`minSdk=24`,
`compileSdk=37`, `targetSdk=36`, Java 11, AGP 9.1.1, `abiFilters` yok). RFID henüz
entegre edilmemiş; aşağıdaki maddeler tamamlanmalı.

### 10.1 Altyapı

- [ ] `app/libs/` klasörünü oluştur; `lcrrgxmodule-release.aar` dosyasını kopyala
      (kaynak: `Rfid test/app/libs/lcrrgxmodule-release.aar`)
- [ ] `app/build.gradle.kts` → `implementation(files("libs/lcrrgxmodule-release.aar"))` ekle
- [ ] Kotlin plugin ekle (proje şu an Java/XML; `RfidManager` Kotlin ise) **veya**
      `RfidManager`'ı Java'ya çevir
- [ ] `release` build type için imzalama ayarla
      (`signingConfig = signingConfigs.getByName("debug")` veya gerçek keystore)

### 10.2 RFID katmanı

- [ ] `RfidManager` taşı / yaz (`rfid/` paketi; SDK tipleri dışarı sızmasın)
- [ ] Connect akışında 3 aşamalı çip fallback kullan:
      `createProduct()` → `createProduct(0x20)` → `createProduct(0x10)` + baud 115200/57600
- [ ] Emülatör / vendor servisi yokluğunda `try/catch(Throwable)` + null kontrolü
- [ ] PRD §6.4 ile hizala: `BarcodeScannerProvider` (veya benzeri) arkasında
      UHF RFID implementasyonu; okunan EPC → Room barkod indeksi ile ürün eşleştirme

### 10.3 Tetik tuşu

- [ ] Tarama/sipariş ekranı `Activity`'sinde `onKeyDown` / `onKeyUp` override et
      (Compose `onKeyEvent` kullanma — güvenilir değil)
- [ ] `TRIGGER_KEY_CODES = setOf(305, 619, 621)` ile başla; hedef cihazda
      `Log.d("RFID_TRIGGER_KEY", ...)` + `adb logcat` ile gerçek `keyCode`'u doğrula
- [ ] `event.repeatCount == 0` kontrolü ekle (basılı tutmada çırpınma olmasın)
- [ ] Toggle ve/veya bas-çek (momentary) modunu ürün akışına göre seç

### 10.4 Doğrulama (cihaz)

- [ ] Fiziksel el terminaline imzalı APK kur (MDM whitelist açıksa kapat)
- [ ] Connect başarısını doğrula (güç, seri port, çip tipi)
- [ ] Inventory okuma + ses geri bildirimi
- [ ] Tetik tuşu ile start/stop
- [ ] Force-stop sonrası yeniden açılışta takılı okuma kalırsa başlangıçta `StopRead()` temizliği
