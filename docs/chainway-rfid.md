# Chainway C5 RFID Entegrasyonu (Keyboard Emulator Broadcast)

Bu doküman, **Chainway C5** (model `C5P`, Android 11, build `C5P.A11.GL.20250113.V3.35`)
UHF RFID el terminalinden okumaların uygulamaya nasıl alınacağını anlatır.

Okumalar Chainway SDK ile değil, cihazda hazır gelen **Keyboard Emulator**
uygulamasının (`com.rscja.scanner`, v11.4.60) gönderdiği **Android broadcast**'leri
dinlenerek alınır. Uygulamaya ek kütüphane gerekmez.

> Başka bir cihaz (BLD N60) ve onun `.aar` SDK'sı için bkz. [RFID_SDK_ENTEGRASYON.md](RFID_SDK_ENTEGRASYON.md).
> İki yöntem birbirinden bağımsızdır.

Aşağıdaki bilgiler 2026-11-23'te gerçek cihazda test edilerek doğrulanmıştır.

---

## 1. Akış

```
[Tetik, keycode 293] → Keyboard Emulator → UHF modülü etiketi okur
                ↓
   sendBroadcast( action = "com.rscja.scanner.action.scanner.RFID",
                  extras = { epc, data, rssi, pc, ... } )
                ↓
        Android broadcast sistemi
                ↓
   registerReceiver ile bu action'ı dinleyen uygulama
                ↓
        onReceive(intent) → intent.getStringExtra("epc")
```

Uygulama okuyucuyu başlatmaz ve durdurmaz. Tetiği Keyboard Emulator yönetir. Uygulama
sadece gelen sonuçları dinler.

---

## 2. Cihaz Ayarı (ön koşul)

Keyboard Emulator → **AppSettings** → **Process mode = BroadcastReceiver**

> Varsayılan "Scan content on cursor" modunda veri broadcast olarak gelmez,
> odaktaki metin kutusuna klavyeden yazılmış gibi yazılır.

Diğer ayarlar (test edilen durum):

| Ayar | Değer |
|---|---|
| Function → Enable Scanner | Açık |
| Function → UHF | Açık, tetik keycode `293` |
| AppSettings → Data Format → RFID | Hex |
| Okuma modu | Sürekli (tetik basılı tutuldukça okur) |

---

## 3. Broadcast Sözleşmesi

| | Action | Veri anahtarı |
|---|---|---|
| **RFID (UHF)** | `com.rscja.scanner.action.scanner.RFID` | `epc` (ayrıca `data`) |
| Barkod | `com.scanner.broadcast` | `data` |

RFID broadcast'inin extra'ları (hepsi `String`):

| Key | Örnek | Not |
|---|---|---|
| `epc` | `868472656594800000000400` | **Kullanılacak alan** |
| `data` | `868472656594800000000400` | Ayarlarda seçilen çıktı. Şu an EPC ile aynı. |
| `rssi` | `-50,40` | Ondalık ayırıcı **virgül** (cihaz dili Türkçe). Parse etmeden önce `,` → `.` |
| `pc` | `3000` | Protocol Control: 96 bit EPC |
| `SCAN_STATE` | `success` | |
| `EXTRA_SCAN_STATE` | `ok` | |
| `epc_tid`, `epc_user`, `tid_user`, `epc_tid_user` | `EPC,TID,USER` | Virgülle birleşik. TID/USER şu an boş, çünkü sadece EPC okunuyor. |
| `user`, `dataBytes`, `messageHex`, `responseHex`, `shortenedTidHex` | `null` | Kullanılmıyor |
| `CODE_TYPE` | boş | |

---

## 4. EPC Yapısı

Etiketler standart SGTIN-96 değildir. Ürün barkodu doğrudan EPC'ye yazılmıştır
(24 haneli, tamamı rakam):

```
8684726565948 00000000400
└── EAN-13 ─┘ └ seri no ┘   13 + 11 hane
```

- `epc.take(13)` → ürün barkodu (EAN-13, `868` Türkiye öneki)
- `epc.drop(13)` → tekil seri numarası
- **Geçerli etiket:** 24 hane, tamamı rakam, `868` ile başlar, EAN-13 kontrol hanesi doğru.
  Testte okunan 4 farklı ürünün kontrol hanesi doğrulandı.
- **`E280…` ile başlayan EPC'ler** kodlanmamış (fabrika çıkışı) etiketlerdir.
  "Tanımsız etiket" olarak ayrılmalıdır.

---

## 5. Uygulama Kuralları

- **Receiver kodda kaydedilir, manifest'te değil.** `onResume`'da `registerReceiver`,
  `onPause`'da `unregisterReceiver`. Android 8+ örtük broadcast'leri manifest
  receiver'larına iletmez.
- `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_EXPORTED)` kullanılır.
  Broadcast başka uygulamadan geliyor ve Android 14+ bu bayrağı zorunlu tutuyor.
- **Tekilleştirme şarttır.** Sürekli modda aynı etiket birkaç kez gelir.
  Testte 19 saniyede 78 broadcast geldi, bunların 31'i tekil EPC idi. `epc` değeri bir `Set` içinde tutulur.
- Arayüz her broadcast'te değil, 200–500 ms'de bir toplu güncellenir.
- **Chainway SDK ile birlikte kullanılmaz.** Keyboard Emulator UHF modülünü tutar,
  SDK modülü açamaz (init başarısız olur).

---

## 6. Asgari Receiver (Kotlin)

```kotlin
private const val ACTION_RFID = "com.rscja.scanner.action.scanner.RFID"

private val okunanlar = LinkedHashSet<String>()

private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RFID) return
        val epc = intent.getStringExtra("epc")?.trim().orEmpty()
        if (epc.isEmpty() || !okunanlar.add(epc)) return          // boş veya tekrar

        val rssi = intent.getStringExtra("rssi")?.replace(',', '.')?.toDoubleOrNull()
        val gecerli = epc.length == 24 && epc.all(Char::isDigit) && epc.startsWith("868")
        val barkod = if (gecerli) epc.take(13) else null
        val seriNo = if (gecerli) epc.drop(13) else null
        // barkod == null → tanımsız etiket
    }
}

override fun onResume() {
    super.onResume()
    ContextCompat.registerReceiver(
        this, receiver, IntentFilter(ACTION_RFID), ContextCompat.RECEIVER_EXPORTED
    )
}

override fun onPause() {
    super.onPause()
    unregisterReceiver(receiver)
}
```

---

## 7. Test ve Hata Ayıklama

- adb şurada: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (PATH'te değil).
- Git Bash'te adb kullanırken `export MSYS_NO_PATHCONV=1` gerekir. Yoksa `/sdcard/...` yolları bozulur.
- Broadcast'in gönderildiğini görmek için:
  `adb shell dumpsys activity broadcasts history | grep -A6 "scanner.RFID"`
  Extras burada `STRIPPED` görünür. İçeriği görmek için receiver gerekir.
- İçeriği görmenin en hızlı yolu, `onReceive` içinde `intent.extras` üzerindeki tüm anahtarları
  loglamak ve `adb logcat -s <TAG>` ile izlemektir.
- Keyboard Emulator'ün dinlediği kontrol action'ları (okumayı başlat/durdur, çıktı modu vb.)
  `adb shell dumpsys package com.rscja.scanner` çıktısındaki
  `MainBroadcastReceiver` ve `CustomBroadcastReceiver_xb` filtrelerinde listelenir
  (ör. `com.rscja.scanner.action.START_BARCODE_RFID`, `ACTION_KE_OUTPUTMODE`). Bunlar henüz test edilmedi.
