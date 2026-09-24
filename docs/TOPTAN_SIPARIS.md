# Toptan Sipariş Modülü

**Durum:** Okutma + özet UX + enrich + `WholesaleCreate` + SENT ayrımı / kilit bağlandı.

---

## 1. Sipariş Gönderim

`POST /api/FrogMobileHandheldWholesaleOrder/WholesaleCreate`  
FrogMobile JWT (`Authorization: Bearer`) gerekir.

### İstek gövdesi

```json
{
  "modelType": 5,
  "orderGuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerCode": "120.01",
  "companyCode": "1",
  "officeCode": "M01",
  "WareHouseCode": "M01",
  "orderDate": "2016-06-22",
  "isCompleted": true,
  "isReturn": false,
  "description": "Açiklama",
  "database": "Armine",
  "epcs": [
    "8680123456789ABCD0001",
    "8680123456789ABCD0002"
  ]
}
```

### Alan açıklamaları

| Alan | Tip | Açıklama |
|------|-----|----------|
| `modelType` | int | Sabit değer: `5` |
| `orderGuid` | string (UUID) | Cihazda sipariş oluşturulunca üretilir; API’ye iletilir (idempotent retry). `localId` ile aynı değil |
| `customerCode` | string | Seçilen müşterinin cari kodu |
| `companyCode` | string | Girişte alınan şirket kodu |
| `officeCode` | string | Seçilen deponun ofis kodu |
| `WareHouseCode` | string | Seçilen depo kodu (Swagger’da PascalCase) |
| `orderDate` | string (`yyyy-MM-dd`) | Bugünün tarihi |
| `isCompleted` | bool | Varsayılan `true` |
| `isReturn` | bool | Satış olduğu için her zaman `false` |
| `description` | string | Serbest metin |
| `database` | string | `AuthPreferences.getDefaultDatabase()` |
| `epcs` | string[] | Benzersiz ham EPC listesi (miktar / ItemCode yok) |

**OrderGuid (cihaz-lokal, v1):** GUID o telefonda fiş açılınca üretilir. Retry / idempotent akış aynı cihaz + aynı `OrderGuid` ile çalışır. Cihazlar arası taslak senkronu yok. İleride gerekirse sunucudan bekleyen sipariş çekme eklenebilir.

**Not:** `ItemCode`, ürün adı, renk yalnızca özet ekranı içindir; gönderime dahil edilmez.

**EPC kuralları:** Her EPC benzersizdir. Aynı EPC bir siparişte (taslak, pending **veya SENT**) varsa başka siparişe okutulamaz. İptal Barkod yalnızca henüz aktarılmamış fişte silince serbest kalır; SENT fişte EPC kalıcı kilitlidir.

### Başarı (HTTP 200)

`success: true`, `data.isNebimSend: true`, `data.orderGuid` dolu.

| Senaryo | Ne olur |
|---------|---------|
| Yeni OrderGuid + Nebim OK | SQL insert → Nebim POST → `isNebimSend=true` |
| Aynı OrderGuid, önceki deneme failed, tekrar + Nebim OK | Yeni SQL yok; EPC farkı varsa lines yenilenir → Nebim tekrar → `isNebimSend=true` |
| Aynı OrderGuid zaten `isNebimSend=true` | Idempotent: Nebim’e gitmez, mevcut kaydı 200 döner |

Mesaj: `Toptan siparis kaydedildi ve Nebim'e gonderildi`

Uygulama: `{description}` + `Sipariş başarıyla NebimV3 gönderildi`.  
Lokal fiş **silinmez**; `STATUS_SENT` + `isNebimSend=true`. Fiş **Aktarılmamış** listesinden çıkar, **NebimV3 aktarılmış siparişler** bölümüne geçer. Aç / Aktar / Sil yok; okutma ve özet ekranına girilemez.

### Hata (HTTP 400)

`success: false`, `message` = hata metni. SQL kaydı kalır, `isNebimSend` false; aynı `OrderGuid` ile tekrar denenebilir.

| Senaryo | message |
|---------|---------|
| Validasyon (boş alan, boş Guid, EPC vs.) | İlgili validasyon mesajı |
| Nebim ModelType=0 | Nebim ExceptionMessage |

HTTP 500: beklenmeyen sunucu hatası.

Uygulama: `message` kırmızı bantta; fiş `PENDING_SEND` kalır (Aktar görünür).

---

## 1b. Ürün / stok enrich (özet ekranı)

`POST /api/FrogMobileHandheldWholesaleOrder/WholeSaleSummaryCheckInfo`  
FrogMobile JWT (`Authorization: Bearer`) gerekir.

İstek:

```json
{
  "OfficeCode": "M01",
  "WareHouseCode": "M01",
  "UserName": "KULLANICI_ADI",
  "Database": "DatabaseName",
  "Barcodes": ["8680123456789", "8680123456790"]
}
```

- `Barcodes`: EPC’nin ilk 13 hanesi, benzersiz liste.
- `UserName`: `AuthPreferences.getUserName()`
- `Database`: `AuthPreferences.getDefaultDatabase()` (sorgu bu veritabanında çalışır)
- Yanıt: `Data.Items[]` — `Barcode`, `ProductDescription` (`cdItemDesc` / TR), `ItemCode`, `ColorCode`, `ColorDescription`, `Stock` (`AvailableInventoryQty1` toplamı)
- Adet yerel EPC sayısından gelir; gönderime dahil edilmez.

Stok yok / yetersiz / bulunamadı → özet ekranında uyarı. Şimdilik listeden silme yok.

---

## 2. Ekran Akışı

1. **Toptan Sipariş** (`WholesaleOrderListActivity`):
   - **Aktarılmamış / taslak** — Aç / Aktar / Sil
   - **NebimV3 aktarılmış siparişler** — salt bilgi; açılmaz, silinmez
2. **+** → müşteri arama → **Depo seçimi** (`WholesaleOrderSetupActivity`).
3. **Ürün okutmaya geç** → `WholesaleOrderActivity`:
   - Ham EPC listesi + İptal Barkod.
   - **Özete Geç** (Kaydet burada yok).
   - Okutulmuş EPC varken geri çıkışta kaydetmeden çıkma uyarısı.
4. **Sipariş Özeti** (`WholesaleOrderSummaryActivity`):
   - Barkod gruplu ürün / renk / stok / adet.
   - Stok uyarısı (silme yok).
   - FAB: **Kaydet** / **Kaydet ve Gönder**.
5. Geri: özetten okutmaya dönüş.

Taslak / pending fişe dokunulunca okutma açılır. SENT fiş intent ile açılmaya çalışılırsa toast + kapanır.

---

## 3. Açık Noktalar

- Stok hatalı ürünler için “listeden sil / yine de gönder” UX (sonraya bırakıldı)
- **TODO (API / Behzat):** Fatura kesilip depodan çıkınca sunucudan aktarılmış sipariş temizleme endpoint’i; uygulama listesini o endpoint ile senkronlamak (liste şişmesi şimdilik kabul)

---

## 4. Manuel QA checklist

- [ ] Yeni sipariş → başarı → üst listeden düşer, alt “NebimV3 aktarılmış”a gelir; Aç/Sil yok
- [ ] Fail → üstte kalır, Aktar görünür; aynı `OrderGuid` ile tekrar → başarı → alta geçer
- [ ] SENT EPC başka siparişte okutulamaz
- [ ] Intent ile SENT `localId` açılmaya çalışılırsa toast + kapanır
- [ ] SENT fiş store üzerinden silinemez
