# Cihaz Test Kontrol Listesi

Sürüm: debug, 2026-11-24. Test cihazı: Chainway C5P (Android 11).
Her satırı cihazda deneyip **Sonuç** sütununa ✅ / ❌ ve gerekirse not yazın.

## A. Bu turdaki düzeltmeler

| # | Konu | Nerede | Nasıl test edilir | Beklenen | Sonuç |
|---|---|---|---|---|---|
| 1 | Geri butonu listeye döner | Depo Transferi | Yeni transfer oluştur → depoları seç → okutma ekranında birkaç etiket okut → Kaydet → geri butonuna bas (ekrandaki ok ve cihazın geri tuşu ayrı ayrı) | Depo seçim ekranı **açılmaz**, Transfer Listesi açılır ve kaydedilen transfer listede görünür | |
| 1b | Kaydedilmemiş çıkış uyarısı | Depo Transferi | Etiket okut, **Kaydet'e basmadan** geri bas | Önce "kaydetmeden çık" uyarısı gelir; "çık" denirse Transfer Listesi açılır | |
| 2 | Menü sırası | Ana menü | Ana menüye bak | Sıra: Toptan Sipariş, Depo Transferi / RFID Sayım, Depolarım / Müşteriler, Ürünler / en altta RFID Tarama (Test) | |
| 2b | Menü butonları doğru ekranı açar | Ana menü | Her butona tek tek bas | Her buton kendi ekranını açar (yer değişti, işlev değişmedi) | |
| 3 | RFID durum yazısı tam görünür | Ana menü, sol alt | Alt kısma bak; menüyü yukarı kaydır | "RFID Bağlı • Chainway C5" yazısı **tamamen** görünür, kesilmez. Menü kaydırılabilir, yazı altta sabit kalır | |
| 4 | Çöp kutusu ikonu | Depo Transferi okutma | Birkaç etiket okut, satırların sonuna bak | Her satırın sağında gri çöp kutusu var | |
| 4b | Çöp kutusu modalı açar | Depo Transferi okutma | Herhangi bir satırın çöp kutusuna bas | İptal Barkod modalı açılır; etiket okutup Sil'e basınca silinir (eski akış ile aynı) | |
| 4c | Çöp kutusu ikonu | Toptan Sipariş okutma | Etiket okut, çöp kutusuna bas | Aynı İptal Barkod modalı açılır, silme çalışır | |
| 4d | Modal içinde ikon yok | İptal Barkod modalı | Modalda etiket okut | Modalın listesinde çöp kutusu **görünmez** | |
| 4e | FAB menüsü hâlâ çalışır | Her iki ekran | (+) menüsünden "İptal Barkod"a bas | Aynı modal açılır | |
| 5 | Depolar şifreyle girişte güncellenir | Giriş ekranı | Çıkış yap → tekrar giriş yap → Depolarım'a bak | Depolar yeni giriş yapan kullanıcınınkiler; butona basmaya gerek yok | |
| 5b | Depolar açılışta güncellenir | Uygulama açılışı | Sunucuda kullanıcının deposunu değiştir → uygulamayı kapatıp aç | Depolarım'da güncel depo görünür | |
| 5c | İnternet yokken | Giriş | Wi-Fi kapalıyken giriş yapmayı dene / oturum açıkken uygulamayı aç | Uygulama çökmez; açılışta eski depo listesi kullanılır | |
| 6 | Başka kullanıcının siparişi görünmez | Toptan Sipariş listesi | Kullanıcı A ile sipariş oluştur ve kaydet → çıkış → Kullanıcı B ile gir → Toptan Sipariş listesine bak | A'nın siparişi B'nin listesinde **görünmez** | |
| 6b | Kendi siparişi görünür | Toptan Sipariş listesi | Tekrar A ile gir | A'nın siparişi listede durur | |
| 6c | EPC kontrolü kullanıcılar arası | Toptan Sipariş okutma | B ile yeni sipariş aç, A'nın siparişindeki bir etiketi okut | Etiket eklenmez; uyarı sesi + "Bu etiket başka bir kullanıcının siparişinde kayıtlı." (müşteri adı **görünmez**) | |
| 6d | Kendi diğer siparişi | Toptan Sipariş okutma | A ile ikinci sipariş aç, A'nın ilk siparişindeki etiketi okut | Uyarıda ilk siparişin müşteri adı görünür (eski davranış) | |
| 6e | Transferler de ayrılır | Depo Transferi listesi | 6 ve 6b'yi transfer ile tekrarla | A'nın transferi B'de görünmez | |
| 6f | Eski kayıtlar | Her iki liste | Bu sürümden önce oluşturulmuş kayıtlar varsa: listeyi ilk açan kullanıcıya bak | Eski kayıtlar listeyi **ilk açan** kullanıcıya atanır, diğerleri görmez | |
| 7 | Stok Kontrolü kartı | Toptan Sipariş kurulum (müşteri seçtikten sonra) | "Ürün Okutmaya Geç"in üstüne bak | Kart var; açıkken yeşil kenar/nokta/yazı, kapalıyken kırmızı | |
| 7b | Kartın tamamı tıklanır | Kurulum | Anahtar yerine kartın yazısına dokun | Anahtar değişir, renk değişir | |
| 7c | Seçim hatırlanır | Kurulum | Kapalı yap → çık → yeni siparişe başla | Kart kapalı (kırmızı) gelir | |
| 7d | Stok kontrolü açık | Özet ekranı | Açık ile sipariş oluştur → Özete geç | Satırlarda "Stok: X" / yetersiz stok uyarıları görünür (eski davranış) | |
| 7e | Stok kontrolü kapalı | Özet ekranı | Kapalı ile sipariş oluştur → Özete geç | Stok satırları ve üstteki stok uyarısı **görünmez**; ürün adı/renk görünür | |
| 7f | Taslak ayarı korur | Toptan Sipariş listesi | Kapalı ile oluşturulan taslağı listeden aç → Özete geç | Stok yine gizli | |

## B. Önceki turdan (Chainway entegrasyonu)

| # | Konu | Nerede | Nasıl test edilir | Beklenen | Sonuç |
|---|---|---|---|---|---|
| 8 | Ön koşul | Keyboard Emulator | AppSettings → Process mode | **BroadcastReceiver** seçili | |
| 9 | Cihaz otomatik tanınır | Sunucu Ayarları → RFID Cihazı | Ekrana bak | "Otomatik algıla" seçili, "algılanan: Chainway C5" yazıyor | |
| 10 | Tetik ile okuma | RFID Tarama (Test) | Tetiği basılı tut, etiketlere tut | Etiketler listeye düşer, bip çalar | |
| 10b | Tetiği bırakınca durur | RFID Tarama (Test) | Tetiği bırak | Okuma ~0,6 sn içinde durur | |
| 10c | Basılıyken kesilmez | RFID Tarama (Test) | Tetiği uzun süre basılı tut | Okuma kesilip yeniden başlamaz | |
| 11 | Sipariş/transfer/sayım okuma | Toptan Sipariş, Depo Transferi, RFID Sayım | Her ekranda tetikle etiket okut | Etiketler eklenir, tekrar eden etiket iki kez eklenmez | |
| 12 | Tek bip | Tüm okuma ekranları | Okuma sırasında dinle | Tek bip; iki bip duyuluyorsa Keyboard Emulator'ün kendi sesi kapatılmalı | |
| 13 | Tetik Bas-Çek anahtarı | Sunucu Ayarları | Chainway seçiliyken bak | Anahtar pasif, açıklamada "bu cihazda uygulanmaz" yazıyor | |
| 14 | Kaydet butonu görünür | Sunucu Ayarları | Ekranın altına bak | Kaydet butonu altta sabit, tam görünür | |
| 15 | Uygulama ikonu | Ana ekran / uygulama listesi | İkona bak | Siyah zemin, beyaz "ARMINE" yazısı, kesilmemiş | |
| 16 | Arka plana geçince | Herhangi bir okuma ekranı | Okurken Home tuşuna bas, geri dön | Uygulama çökmez, okuma durmuş olur, tetikle yeniden başlar | |

## Notlar

Test sırasında bulunan sorunları buraya ya da sohbete yazın.

| # | Sorun | Hangi adımda |
|---|---|---|
| | | |
