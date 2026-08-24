# Toptan Sipariş Modülü

**Durum:** Okutma + özet UX uygulandı. Özet enrich endpoint bağlandı. Sipariş gönderim endpoint’i bekleniyor.

---

## 1. Sipariş Gönderim JSON Formatı

`Kaydet ve Gönder` aksiyonunda API’ye gönderilecek gövde (endpoint henüz yok; lokal `PENDING_SEND`).

```json
{
  "ModelType": 5,
  "CustomerCode": "120.01",
  "CompanyCode": "1",
  "OfficeCode": "M01",
  "WareHouseCode": "M01",
  "OrderDate": "2016-06-22",
  "IsCompleted": true,
  "IsReturn": false,
  "Description": "Açiklama",
  "Epcs": [
    "8680123456789ABCD0001",
    "8680123456789ABCD0002"
  ]
}
```

### Alan açıklamaları

| Alan | Tip | Açıklama |
|------|-----|----------|
| `ModelType` | int | Sabit değer: `5` |
| `CustomerCode` | string | Seçilen müşterinin cari kodu |
| `CompanyCode` | string | Girişte alınan şirket kodu |
| `OfficeCode` | string | Seçilen deponun ofis kodu |
| `WareHouseCode` | string | Seçilen depo kodu |
| `OrderDate` | string (`yyyy-MM-dd`) | Bugünün tarihi |
| `IsCompleted` | bool | Varsayılan `true` |
| `IsReturn` | bool | Satış olduğu için her zaman `false` |
| `Description` | string | Serbest metin |
| `Epcs` | string[] | Benzersiz ham EPC listesi (miktar / ItemCode yok) |

**Not:** `ItemCode`, ürün adı, renk yalnızca özet ekranı içindir; gönderime dahil edilmez.

**EPC kuralları:** Aynı EPC bir siparişte okutulduysa diğer taslak / aktarılmamış fişlerde tekrar kabul edilmez. İptal Barkod ile silinince serbest kalır.

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

1. **Toptan Sipariş** → aktarılmamış / taslak fiş listesi.
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

Taslak fişe dokunulunca doğrudan okutma ekranı açılır.

---

## 3. Açık Noktalar

- Sipariş gönderim endpoint’i
- Stok hatalı ürünler için “listeden sil / yine de gönder” UX (sonraya bırakıldı)
