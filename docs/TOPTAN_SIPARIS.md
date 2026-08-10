# Toptan Sipariş Modülü

**Durum:** Taslak — akış ve JSON formatı belirlendi, ekranlar geliştiriliyor.

---

## 1. Sipariş Gönderim JSON Formatı

Bu format `Kaydet ve Gönder` aksiyonunda API'ye gönderilecek gövdedir
(endpoint henüz belirlenmedi).

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
  "Lines": [
    {
      "Barcode": "8680123456789",
      "Qty1": 2,
      "Epcs": [
        "8680123456789ABCD0001",
        "8680123456789ABCD0002"
      ]
    }
  ]
}
```

### Alan açıklamaları

| Alan | Tip | Açıklama |
|------|-----|----------|
| `ModelType` | int | Sabit değer: `5` |
| `CustomerCode` | string | Seçilen müşterinin cari kodu (`CustomerItem.currAccCode`) |
| `CompanyCode` | string | Girişte alınan şirket kodu (`AuthPreferences.getCompanyCode()`) |
| `OfficeCode` | string | Seçilen deponun ofis kodu (`WarehouseItem.officeCode`) |
| `WareHouseCode` | string | Seçilen depo kodu (`WarehouseItem.warehouseCode`) |
| `OrderDate` | string (`yyyy-MM-dd`) | Bugünün tarihi |
| `IsCompleted` | bool | Varsayılan `true` |
| `IsReturn` | bool | Satış olduğu için her zaman `false` |
| `Description` | string | Serbest metin; öneri: `"{kullanıcı adı} - {müşteri unvanı}"` |
| `Lines[].Barcode` | string | Okutulan / girilen barkod (EPC’nin ilk 13 hanesi) |
| `Lines[].Qty1` | number | Miktar |
| `Lines[].Epcs` | string[] | Bu satıra ait okutulmuş EPC listesi |

**EPC kuralları:** Aynı EPC bir siparişte okutulduysa diğer taslak / aktarılmamış fişlerde tekrar kabul edilmez; cihaz yüksek sesli uyarı verir. Satırdan / fişten silinince serbest kalır.

---

## 2. Ekran Akışı

1. **Toptan Sipariş** menüsüne girilir → aktarılmamış / taslak fiş listesi.
2. **+** ile yeni sipariş: **Müşteri arama** ekranı (`CustomerApiClient.search`).
3. Müşteri seçilir → **Depo seçimi** ekranı (`WholesaleOrderSetupActivity`):
   - `WarehousePreferences` içindeki senkronize depolar listelenir (yeniden API yok).
   - Varsayılan depo ön-seçili gelir.
   - Altında **açıklama** alanı (varsayılan: `{kullanıcı} - {müşteri}`).
4. **Ürün okutmaya geç** → okutma ekranı (`WholesaleOrderActivity`):
   - Depo / açıklama gösterilmez (ekran ürün listesine ayrılır).
   - RFID tetik veya manuel satır ekleme.
5. Sağ altta kalem FAB → **Kaydet** / **Kaydet ve Gönder**.
   - **Kaydet**: lokal `DRAFT`, *"Aktarılmamış Fişiniz Bulunmaktadır"*.
   - **Kaydet ve Gönder**: JSON üretilir, lokal `PENDING_SEND` (API endpoint bekleniyor).

Mevcut taslak fişe dokunulunca doğrudan okutma ekranı açılır (depo/açıklama zaten kayıtlıdır).

---

## 3. Açık Noktalar

- Barkod girişi donanımsal olarak nasıl yapılacak (klavye-wedge / manuel / RFID)?
- Aynı barkod tekrar okutulursa: mevcut satırın miktarı mı artırılacak, yeni satır mı eklenecek?
- Taslak siparişler tekil mi (tek aktif taslak) yoksa liste halinde mi (birden fazla bekleyen fiş) tutulacak?
- `Kaydet ve Gönder` endpoint'i netleşene kadar bu buton ne yapmalı (sadece JSON oluşturup yerelde
  "gönderim bekliyor" işaretlemeli mi)?

Bu sorulara verilen yanıtlar bu dokümana ve ilgili koda yansıtılacaktır.
