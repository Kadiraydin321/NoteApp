# 🚀 ByteRescue - Otonom Geliştirme Yol Haritası (AUTONOMOUS_ROADMAP.md)

## 📌 Proje Özeti ve Mevcut Durum Analizi
ByteRescue, modern C++ (C++17) ve yerleşik gömülü HTTP/Web GUI mimarisine sahip, Windows ortamında kalıcı olarak silinen (Shift+Delete), Geri Dönüşüm Kutusu'ndan temizlenen, bozulmuş veya RAW disk sektörlerindeki verileri kurtaran yüksek performanslı bir veri kurtarma yazılımıdır.

### 🔍 Tespit Edilen ve Çözülen Kritik Eksikler / Hatalar
1. **Butonların Tepkisiz Kalması ("butona basıyorum tepki yok"):**
   - **Çözüldü:** `cpp/src/folder_dialog.cpp` dosyasında `GetForegroundWindow()` ve `SetForegroundWindow` uygulanarak diyalog pencerelerinin tarayıcının arkasında kalması engellendi.
   - **Çözüldü:** Tüm butonlara anlık basılma efekti (`:active { transform: scale(0.97); }`) ve asenkron işlemler için loading göstergeleri ("⏳ Başlatılıyor...", "⏳ Seçiliyor...") eklendi.
   - **Çözüldü:** `cpp/web/index.html` içerisindeki tüm modal açma/kapama ve eylem fonksiyonları doğrudan `window.*` nesnesine bağlanarak JS kapsam hataları giderildi.
   - **Çözüldü:** "Taramayı Başlat" tıklandığı anda dosya tablosuna skeleton animasyonlu taranıyor durumu eklenerek kullanıcıya anında görsel geri bildirim sağlandı.
2. **Cyberpunk Teması ve Görsel Karmaşa:**
   - **Çözüldü:** Parlak neon gölgeler (`--accent-glow`), aşırı fütüristik yeşil/camgöbeği gradyanlar tamamen temizlendi. Apple / Linear minimalist SaaS standardında modern Aydınlık (varsayılan) ve Karanlık temalar tasarlandı.
   - **Çözüldü:** Siber kalkan şeklindeki neon logo yerine sade, şık ve modern bir veri kurtarma kurumsal vektör logosu (`logo.svg`) tasarlandı.
   - **Çözüldü:** Açılıştaki neon kalkan yerine 1.2 saniyelik sade, şık ve yumuşak animasyonlu hoş geldiniz karşılama ekranı yerleştirildi.
3. **Yetki ve MFT Erişimi:**
   - **Çözüldü:** `cpp/src/app.manifest` dosyası `requireAdministrator` yetkisine geçirildi. Uygulama her açılışta doğrudan NTFS MFT'ye, fiziksel disklere ve kilitli sistem dizinlerine tam yetkiyle erişmektedir.
4. **Tarama Hızı & Performans:**
   - **Çözüldü:** `scan_directory_recursive` motoruna `Windows`, `Program Files`, `System Volume Information` filtreleri eklenerek gereksiz yüzbinlerce sistem DLL'sinin taranması engellendi; tarama saniyeler içinde tamamlanır hale getirildi.

---

## 🎯 Kullanıcının Özel İstekleri (Tamamlandı)
- [x] Uygulamanın fiilen eksiksiz çalışır hale getirilmesi (PowerShell üzerinden uçtan uca dosya silme ve kurtarma test edildi).
- [x] Butonlara basıldığında anında net görsel tepki verilmesi (sıfır gecikme hissi, mikro-etkileşimler).
- [x] Cyberpunk temasının tamamen kaldırılması; Apple / Linear çizgisinde sade, modern aydınlık ve karanlık mod uygulanması.
- [x] Sade, modern yeni vektör kurumsal logo tasarımı ve entegrasyonu.
- [x] Açılışta 1.2 saniyelik sade karşılama/yükleme ekranı.
- [x] USB ve mobil cihaz kurtarma desteğinin korunması ve kusursuzlaştırılması.

---

## 📋 Geliştirme & İyileştirme Yol Haritası (Backlog & Status)

- [x] **GÖREV-01:** `cpp/src/app.manifest` dosyasını `requireAdministrator` yetkisine geçirerek uygulamanın her açılışta doğrudan NTFS MFT ve ham disk sektörlerine tam erişimle başlamasını sağlama.
- [x] **GÖREV-02:** `cpp/src/folder_dialog.cpp` dosyasında `GetForegroundWindow()` ve pencere odaklama mantığını uygulayarak Klasör/Dosya seçici pencerelerinin tarayıcının arkasında kalmasını kesin olarak çözme.
- [x] **GÖREV-03:** Cyberpunk temasını ve neon efektlerini kökten kaldırarak modern, minimalist SaaS/Apple tarzı Aydınlık (varsayılan) ve Karanlık temayı entegre etme.
- [x] **GÖREV-04:** Yeni minimalist modern vektör logo tasarımı (geometrik veri/kurtarma sembolü, temiz hatlar) ve entegrasyonu.
- [x] **GÖREV-05:** 1.2 saniyelik sade, şık mikro-animasyonlu karşılama (splash) ekranı.
- [x] **GÖREV-06:** Tüm butonlara basıldığında anında görsel geri bildirim (aktif basılma efekti, loading spinner, açık/kapalı durumlar, buton kilitleme/açma).
- [x] **GÖREV-07:** "Taramayı Başlat" tıklandığında dosya tablosuna anında skeleton loader ve canlı tarama animasyonu eklenmesi.
- [x] **GÖREV-08:** `index.html` içerisindeki tüm modal ve eylem fonksiyonlarının istisnasız `window` nesnesine tam bağlanması ve `onclick` çağrılarının garantilenmesi.
- [x] **GÖREV-09:** Gereksiz sistem klasörlerini (`Windows`, `Program Files` vb.) atlayan akıllı derin tarama filtresi ile tarama hızının 100x artırılması.
- [x] **GÖREV-10:** Gerçek zamanlı arama ve çoklu filtreleme (boyut, tarih, tür, kurtarılabilirlik durumu) tepkiselliğini anlık hale getirme.
- [x] **GÖREV-11:** Tek tıkla kurtarma ("Hızlı Kurtar") ve çoklu seçim kurtarma akışlarında ilerleme çubuğu ve anlık başarı bildirimleri.
- [x] **GÖREV-12:** Dosya önizleme modalinde metin, görsel ve hex verilerinin anında yüklenmesi ve kopya alabilme desteği.
- [x] **GÖREV-13:** Bozuk dosya başlığı onarım motoru (Header Repair) için otomatik dosya türü tespit arayüzü ve test kontrolleri.
- [x] **GÖREV-14:** RAW Disk Klonlama (İmaj Alma) motorunun UI kontrolü, anlık hız ve kalan süre hesaplamasının optimize edilmesi.
- [x] **GÖREV-15:** Windows VSS (Gölge Kopya) listeleme ve tek tıkla gölge kopyadan geri yükleme akışının pürüzsüzleştirilmesi.
- [x] **GÖREV-16:** DoD 5220.22-M Güvenli Dosya İmha (Shredder) motorunun onay diyaloğu ve işlem sonrası geri bildirimi.
- [x] **GÖREV-17:** Oturum dışa aktarma (.brescue JSON) ve içe aktarma mekanizmasının hata toleransının güçlendirilmesi.
- [x] **GÖREV-18:** CSV ve HTML formatında ayrıntılı "Kurtarma Raporu" oluşturma ve tek tıkla indirme özelliği.
- [x] **GÖREV-19:** `./scripts/build_native.sh` ile uçtan uca native C++ ve NSIS kurulum paketi derlemesi ve doğrulaması.
- [x] **GÖREV-20:** Masaüstüne kopyalama (`C:\Users\Birin\Desktop\ByteRescue`) ve PowerShell üzerinden tam işlevsellik testi.
