# Armine Order Module — Ürün Gereksinim Dokümanı (PRD)

**Versiyon:** 0.1 (Taslak)  
**Tarih:** 16 Temmuz 2026  
**Platform:** Android (öncelik: RFID el terminalleri)  
**Durum:** Geliştirme öncesi — API ve kullanıcı tabloları bekleniyor

---

## 1. Özet

Armine Order Module, saha personelinin **internet olmadan** müşteri cari, depo, ürün barkodu ve ürün bilgilerine erişerek sipariş oluşturmasını sağlayan bir Android el terminali uygulamasıdır. İnternet bağlantısı geldiğinde kullanıcı master verileri günceller ve oluşturduğu siparişleri sunucuya gönderir.

**Depo Transferi** modülü ise yalnızca internet varken aktif olur ve transfer işlemleri anlık API üzerinden yapılır.

---

## 2. Hedefler

| Hedef | Açıklama |
|-------|----------|
| Offline sipariş | Depo / mağaza içinde bağlantı olmadan sipariş girişi |
| Hızlı barkod okuma | RFID / barkod okuyucu ile milisaniye seviyesinde ürün eşleştirme |
| Güvenilir senkronizasyon | Siparişler kaybolmadan kuyruğa alınır, internet gelince gönderilir |
| Düşük donanım yükü | Eski / düşük RAM'li el terminallerinde akıcı çalışma |
| Rol bazlı erişim | Kullanıcı tablosu ve yetkiler API'den yönetilir |

---

## 3. Kapsam Dışı (v1)

- iOS veya web istemcisi
- Offline depo transferi
- Çoklu şube / çoklu firma (API yapısına göre v2'de değerlendirilir)
- Gelişmiş raporlama ve dashboard
- Yazıcı entegrasyonu (v1 sonrası değerlendirilir)

---

## 4. Kullanıcı Profilleri

> Detaylar API'den gelecek kullanıcı tablosuna göre netleştirilecek.

| Rol (örnek) | Yetkiler |
|-------------|----------|
| Saha satış / sipariş personeli | Sipariş oluşturma, master veri güncelleme, cari seçimi |
| Depo personeli | Depo transferi (yalnızca online) |
| Yönetici | Tüm menüler, kullanıcı yetkileri API tarafında |

---

## 5. Fonksiyonel Gereksinimler

### 5.1 Kimlik Doğrulama ve Kullanıcı

- Kullanıcı bilgileri ve yetkileri API üzerinden alınır.
- Oturum token'ı güvenli şekilde cihazda saklanır.
- İlk girişte veya token süresi dolduğunda internet gerekir.
- Kullanıcı tablosu yapısı API dokümantasyonu geldikten sonra bu bölüm güncellenecek.

### 5.2 Master Veri İndirme (Offline Kullanım İçin)

Aşağıdaki veriler internet varken indirilir ve cihazda lokal tutulur:

| Veri | Açıklama | Offline erişim |
|------|----------|----------------|
| Müşteri carileri | Cari kod, unvan, adres, vb. | Evet |
| Depolar | Depo kodu, adı, ilişkili bilgiler | Evet |
| Ürün barkodları | Barkod ↔ ürün eşlemesi | Evet |
| Ürün bilgileri | Stok kodu, ad, birim, fiyat (API'ye göre) | Evet |

**Güncelleme akışı:**
1. Kullanıcı "Verileri Güncelle" aksiyonunu tetikler (manuel).
2. Uygulama internet bağlantısını kontrol eder.
3. Delta veya tam senkron API'ye göre veri çekilir.
4. Lokal veritabanı güncellenir; son senkron zamanı kaydedilir.

> **Öneri:** Tam indirme ilk kurulumda; sonraki güncellemelerde `lastSyncAt` / `updatedAt` ile delta senkron tercih edilmeli. Büyük ürün kataloglarında bant genişliği ve süre ciddi azalır.

### 5.3 Sipariş Modülü (Offline-First)

**Offline (internet yok):**
- Cari seçimi (lokal listeden)
- Depo seçimi (lokal listeden)
- Barkod / RFID ile ürün ekleme
- Miktar, not, satır düzenleme / silme
- Sipariş taslak olarak kaydedilir
- Durum: `TASLAK` veya `GÖNDERİM BEKLİYOR`

**Online (internet var):**
- Bekleyen siparişler sunucuya gönderilir
- Başarılı gönderimde durum `GÖNDERİLDİ`; sunucu sipariş numarası lokal kayda yazılır
- Hata durumunda kullanıcıya mesaj; sipariş kuyrukta kalır

**Sipariş gönderim kuyruğu:**
- Gönderilemeyen siparişler silinmez
- Yeniden deneme: kullanıcı manuel + opsiyonel arka plan (WorkManager)
- Çakışma / duplicate önleme: her siparişe cihazda benzersiz `localOrderId` (UUID)

### 5.4 Depo Transferi Modülü (Online-Only)

- Menü yalnızca aktif internet bağlantısı varken görünür / erişilebilir.
- Kaynak depo → hedef depo transferi API üzerinden anlık yapılır.
- Offline modda menü devre dışı veya "İnternet gerekli" uyarısı gösterilir.
- Transfer geçmişi API'den sorgulanır; v1'de lokal cache opsiyonel.

### 5.5 Bağlantı Durumu

- Uygulama genelinde bağlantı göstergesi (online / offline)
- Offline modda sipariş akışı kesintisiz devam eder
- Online modda senkronizasyon aksiyonları aktif olur

---

## 6. Teknik Mimari Önerileri

### 6.1 Lokal Depolama — Önerilen Çözüm

#### Birincil öneri: **Room (SQLite) + DataStore**

| Katman | Teknoloji | Kullanım |
|--------|-----------|----------|
| İlişkisel veri | **Room** | Cari, depo, ürün, barkod, sipariş, sipariş satırları, senkron kuyruğu |
| Ayarlar / küçük veri | **DataStore (Preferences)** | Token, son senkron zamanı, kullanıcı tercihleri |
| Arka plan senkron | **WorkManager** | İnternet gelince bekleyen siparişleri gönderme (opsiyonel v1.1) |

**Neden Room?**

1. **Performans:** SQLite indeksleri ile barkod / stok kodu araması mikrosaniye–milisaniye seviyesinde; RFID el terminallerinde kritik.
2. **İlişkisel model:** Cari → Sipariş → Sipariş satırı → Ürün ilişkileri doğal şekilde kurulur.
3. **ACID:** Sipariş kaydı ve satırları tek transaction içinde yazılır; yarım kayıt riski düşük.
4. **Android ekosistemi:** Jetpack'in resmi ORM'i; AGP ve Kotlin/Java ile uyumlu.
5. **Bakım:** Migration desteği, test edilebilirlik (in-memory DB), geniş topluluk.

**Önerilen indeksler (performans):**
```sql
-- Ürün barkodu araması (en sık sorgu)
CREATE INDEX idx_product_barcode ON product_barcodes(barcode);

-- Cari arama
CREATE INDEX idx_customer_code ON customers(code);
CREATE INDEX idx_customer_name ON customers(name);

-- Bekleyen siparişler
CREATE INDEX idx_orders_sync_status ON orders(sync_status);
```

#### Opsiyonel: **SQLCipher** (Room üzerine şifreleme)

- Cihazda cari ve fiyat gibi hassas ticari veri varsa disk şifrelemesi önerilir.
- Performans maliyeti ~%5–15; çoğu el terminali için kabul edilebilir.
- v1'de karar: API / güvenlik politikasına göre.

#### Neden diğerleri değil?

| Alternatif | Değerlendirme |
|------------|---------------|
| Realm | İyi performans ama ekosistem Room kadar yaygın değil; yeni projede Room tercih edilir |
| Ham SQLite | Room'un sağladığı tip güvenliği ve DAO kolaylığı olmadan gereksiz düşük seviye kod |
| JSON / dosya tabanlı | Barkod araması yavaş; indeks yok; büyük kataloglarda uygun değil |
| Firebase Offline | Sunucu bağımlılığı; mevcut API mimarisine uygun değil |

### 6.2 Uygulama Mimarisi

```
┌─────────────────────────────────────────────────┐
│  UI (Activity / Fragment veya Compose — karar)   │
├─────────────────────────────────────────────────┤
│  ViewModel                                       │
├─────────────────────────────────────────────────┤
│  Repository (tek veri kaynağı soyutlaması)       │
│    ├── Local: Room DAO                           │
│    └── Remote: Retrofit API                      │
├─────────────────────────────────────────────────┤
│  Sync Manager                                    │
│    ├── Master data sync (pull)                   │
│    └── Order outbox sync (push)                  │
└─────────────────────────────────────────────────┘
```

**Önerilen desenler:**
- **Repository Pattern:** UI, verinin lokal mi uzak mı geldiğini bilmez
- **Outbox Pattern:** Sipariş önce lokal `GÖNDERİM BEKLİYOR` olarak yazılır, sonra API'ye push edilir
- **Single Source of Truth:** Room Flow / LiveData ile UI her zaman lokal DB'den beslenir

### 6.3 Ağ Katmanı

- **Retrofit + OkHttp** (mevcut API ile uyumlu REST)
- Timeout ve retry politikası el terminali ağları için agresif değil, kullanıcı odaklı olmalı
- Interceptor: auth token, connectivity check

### 6.4 RFID / Barkod Entegrasyonu

- Cihaz üreticisine göre SDK (Zebra, Honeywell, Urovo, vb.) — hedef cihaz listesi netleştirilmeli
- Soyutlama katmanı: `BarcodeScannerProvider` interface; farklı cihazlara kolay geçiş
- Okunan değer → Room'da barkod indeksi ile ürün eşleştirme

### 6.5 Dil ve UI Framework

| Konu | Öneri | Gerekçe |
|------|-------|---------|
| Dil | **Kotlin** (yeni kod) | Android ekosistemi standardı; coroutine desteği |
| UI | **XML + ViewBinding** (v1) veya **Compose** | Mevcut proje Java/XML ile başladı; kademeli Kotlin geçişi mümkün |
| Async | **Coroutines + Flow** | Room ve Retrofit ile doğal entegrasyon |

> **Öneri:** Yeni modüller Kotlin ile yazılsın; `MainActivity` zamanla migrate edilsin.

---

## 7. Veri Modeli (Taslak)

API detayları geldikçe alanlar güncellenecek.

### 7.1 Lokal Tablolar

```
customers          — Müşteri carileri
warehouses         — Depolar
products           — Ürün ana bilgileri
product_barcodes   — Barkod ↔ product_id eşlemesi (1 ürün, N barkod)
orders             — Sipariş başlık
order_lines        — Sipariş satırları
sync_metadata      — Tablo bazlı son senkron bilgisi
```

### 7.2 Sipariş Durumları

| Durum | Açıklama |
|-------|----------|
| `DRAFT` | Kullanıcı henüz tamamlamadı |
| `PENDING_SYNC` | Tamamlandı, gönderim bekliyor |
| `SYNCING` | Gönderim deneniyor |
| `SYNCED` | Sunucuya ulaştı |
| `SYNC_FAILED` | Hata; yeniden denenebilir |

### 7.3 Senkron Meta Verisi

```
sync_metadata:
  - entity_type (customers | warehouses | products | ...)
  - last_sync_at
  - record_count
  - sync_version (API'den)
```

---

## 8. Ekranlar (v1)

| # | Ekran | Açıklama |
|---|-------|----------|
| 1 | Giriş | Kullanıcı adı / şifre veya token |
| 2 | Ana menü | Sipariş, veri güncelleme, depo transferi (koşullu), ayarlar |
| 3 | Veri güncelleme | Master veri indirme ilerleme çubuğu |
| 4 | Sipariş listesi | Taslak / bekleyen / gönderilmiş |
| 5 | Sipariş oluştur | Cari, depo, barkod okuma, satır listesi |
| 6 | Cari seçimi | Arama + liste |
| 7 | Ürün detay / satır düzenleme | Miktar, sil |
| 8 | Depo transferi | Kaynak / hedef depo, ürün, miktar (online) |
| 9 | Ayarlar | Son senkron, çıkış, cihaz bilgisi |

---

## 9. API Beklentileri (Taslak)

Kullanıcı tabloları ve endpoint'ler paylaşıldığında bu bölüm doldurulacak.

### 9.1 Gerekli Endpoint Grupları

| Grup | Metot (örnek) | Açıklama |
|------|---------------|----------|
| Auth | `POST /auth/login` | Token al |
| Users | `GET /users/me` | Yetkiler |
| Customers | `GET /customers?since=` | Cari listesi / delta |
| Warehouses | `GET /warehouses?since=` | Depo listesi |
| Products | `GET /products?since=` | Ürün listesi |
| Barcodes | `GET /barcodes?since=` veya products içinde | Barkod eşlemesi |
| Orders | `POST /orders` | Sipariş gönder |
| Transfers | `POST /warehouse-transfers` | Depo transferi |

### 9.2 Senkron Stratejisi Tercihi

| Senaryo | Öneri |
|---------|-------|
| < 10.000 ürün | Tam indirme + periyodik delta |
| > 10.000 ürün | Sayfalı (pagination) + delta zorunlu |
| Sipariş gönderimi | Idempotent: `localOrderId` header veya body'de |

---

## 10. Offline / Online Davranış Matrisi

| Özellik | Offline | Online |
|---------|---------|--------|
| Giriş (ilk / token yenileme) | Hayır* | Evet |
| Cari / depo / ürün görüntüleme | Evet | Evet |
| Master veri güncelleme | Hayır | Evet |
| Sipariş oluşturma | Evet | Evet |
| Sipariş gönderme | Hayır (kuyrukta bekler) | Evet |
| Depo transferi | Hayır | Evet |

\*Daha önce giriş yapılmış ve token geçerliyse offline sipariş akışı devam edebilir.

---

## 11. Performans ve Cihaz Gereksinimleri

| Metrik | Hedef |
|--------|-------|
| Barkod → ürün eşleştirme | < 100 ms (lokal DB) |
| Sipariş kaydetme | < 200 ms |
| 50.000 ürünlük tam indirme | < 5 dk (ağ koşuluna bağlı) |
| minSdk | 24 (mevcut) |
| RAM | 2 GB+ el terminalleri hedef |

**Optimizasyon önerileri:**
- Ürün aramasında yalnızca gerekli kolonları çek (`SELECT id, code, name, price`)
- Büyük senkronlarda batch insert (Room `@Transaction`)
- Görsel / resim v1'de indirilmez (varsa)

---

## 12. Hata Yönetimi ve Kullanıcı Mesajları

| Durum | Kullanıcı mesajı (örnek) |
|-------|--------------------------|
| İnternet yok + güncelleme | "Verileri güncellemek için internet bağlantısı gerekli." |
| İnternet yok + depo transferi | "Depo transferi için internet bağlantısı gerekli." |
| Barkod bulunamadı | "Barkod sistemde kayıtlı değil: {barcode}" |
| Sipariş gönderim hatası | "Sipariş gönderilemedi. Daha sonra tekrar denenecek." |
| Token süresi doldu | "Oturumunuz sona erdi. Lütfen tekrar giriş yapın." |

---

## 13. Güvenlik

- HTTPS zorunlu
- Token: EncryptedSharedPreferences veya DataStore + şifreleme
- Hassas ticari veri: SQLCipher değerlendirmesi
- Root / debug build ayrımı
- Oturum zaman aşımı API politikasına uygun

---

## 14. Test Stratejisi

| Tür | Kapsam |
|-----|--------|
| Unit | Repository, Sync Manager, DAO sorguları |
| Integration | Room migration, API mock |
| Cihaz | RFID okuyucu, offline/online geçiş, düşük RAM |
| Senaryo | Sipariş oluştur → internet kes → internet aç → gönder |

---

## 15. Kilometre Taşları (Önerilen)

### Faz 1 — Temel altyapı (2–3 hafta)
- [ ] Kotlin + Room + Retrofit kurulumu
- [ ] Veri modeli ve migration
- [ ] Auth ve kullanıcı yetkileri (API gelince)
- [ ] Master veri indirme ekranı

### Faz 2 — Sipariş modülü (2–3 hafta)
- [ ] Sipariş oluşturma UI
- [ ] Barkod entegrasyonu (hedef cihaz SDK)
- [ ] Offline kayıt + outbox kuyruğu
- [ ] Sipariş gönderimi

### Faz 3 — Depo transferi (1–2 hafta)
- [ ] Online-only menü ve transfer akışı
- [ ] API entegrasyonu

### Faz 4 — Sertleştirme (1 hafta)
- [ ] Hata senaryoları, edge case'ler
- [ ] Performans testi (büyük katalog)
- [ ] El terminali saha testi

---

## 16. Açık Sorular (API / İş Birimi Yanıtı Bekleniyor)

1. Kullanıcı tablosu alanları ve rol / yetki matrisi nedir?
2. API base URL, auth tipi (JWT, API key, OAuth)?
3. Master veri için delta senkron destekleniyor mu? (`since`, `page`, `limit`)
4. Bir ürünün birden fazla barkodu var mı? RFID EPC formatı?
5. Siparişte fiyat düzenlenebilir mi? İskonto / KDV alanları?
6. Depo transferinde onay akışı var mı?
7. Hedef RFID el terminali marka / model listesi?
8. SQLCipher / disk şifreleme zorunlu mu?
9. Çoklu dil desteği gerekli mi?

---

## 17. Karar Özeti (Önerilen Stack)

| Bileşen | Seçim |
|---------|-------|
| Lokal DB | **Room (SQLite)** |
| Küçük ayarlar | **DataStore** |
| API | **Retrofit + OkHttp** |
| Arka plan iş | **WorkManager** (v1.1) |
| Mimari | **MVVM + Repository + Outbox** |
| Dil | **Kotlin** (kademeli geçiş) |
| Şifreleme | **SQLCipher** — güvenlik politikasına göre |

---

## 18. Revizyon Geçmişi

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 0.1 | 16.07.2026 | İlk taslak PRD |
