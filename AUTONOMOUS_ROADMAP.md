# 🚀 Otonom Not Uygulaması Yol Haritası (Autonomous Roadmap)

Bu belge, **NoteApp (Not Defterim)** Android uygulamasının otonom geliştirme sürecini, mevcut mimari durumunu, tamamlanan yetenekleri ve adım adım hayata geçirilecek yeni vizyon özelliklerini takip eder.

---

## 📊 Mevcut Mimari Durumu (Baseline)

* **Dil & Platform:** Kotlin 2.x, Android SDK 35 (minSdk 26), Jetpack Compose Material 3
* **Veritabanı:** Room v4 (`notes`, `categories`), Coroutines Flow, TypeConverters
* **Bağımlılık Enjeksiyonu:** Dagger Hilt
* **Mevcut Özellikler:**
  * ✅ Zengin Markdown & Kontrol Listesi (Checklist) editörü
  * ✅ Ses kaydı ve dalga formu oynatıcı (Audio Helper)
  * ✅ Çizim & El Yazısı Tuvali (Drawing Screen)
  * ✅ Görsel Düzenleme, Kırpma, Döndürme & Filtreler (Image Edit Screen)
  * ✅ ML Kit ile Görselden Metin Okuma (OCR)
  * ✅ Biyometrik Parmak İzi & Master PIN Şifreleme (AES-GCM Crypto)
  * ✅ Çift Yönlü Yerel Ağ Masaüstü Senkronizasyonu (Desktop Sync)
  * ✅ Dinamik Masaüstü Widget'ları (1x1 Hızlı Not & Sabit Hızlı Araç Çubuğu / Quick Action Dock)
  * ✅ Tasarım Stüdyosu & 6 Renk Paleti (Glacial Ice / Cam Mavisi dahil)
  * ✅ Otomatik Güncelleme Motoru (GitHub Releases entegrasyonu)

---

## 🎯 Vizyon Görev Listesi (Backlog & Execution Queue)

### Aşama 1: Not Şablonları Sistemi (Note Templates Engine)
- [x] **Model & Veritabanı:** `NoteTemplate` modeli tasarımı (Toplantı, Günlük, Alışveriş, Proje Planı, Seyahat, Fikir) + Özel Şablon Deposu (`TemplateManager`).
- [x] **Şablon Yöneticisi:** 6 adet zengin dahili hazır şablon + kullanıcının mevcut notunu şablon olarak emoji ile kaydedebilmesi (`SaveTemplateDialog`).
- [x] **Kullanıcı Arayüzü (UI):** Ana ekranda ve not detayda şık "Şablon Seç" BottomSheet (`TemplatePickerBottomSheet`) bileşeni, anında önizleme ve kategori filtreleme.
- [x] **Entegrasyon:** Şablon seçildiğinde içeriğin ve başlığın dinamik `{DATE}`, `{TIME}`, `{DAY}`, `{TITLE}` etiketleriyle anında doldurulması.

### Aşama 2: Not Geçmişi & Versiyon Kontrolü (Note Version History / Rollback)
- [x] **Veritabanı Genişletmesi:** Room Migration (v4 -> v5) ile `note_history` tablosu (`noteId`, `title`, `content`, `timestamp`, `wordCount`). Cascade silme korumalı.
- [x] **Otomatik Versiyonlama:** Not kaydedilirken anlamlı değişikliklerde geçmiş anlık görüntüsü (snapshot) oluşturma ve eski kopyaları budama (prune - max 30 versiyon).
- [x] **Geçmiş İnceleme Arayüzü:** Not detay ekranında "Versiyon Geçmişi" BottomSheet (`NoteHistoryBottomSheet`), göreceli zaman, kelime farkları, tam içerik önizleme.
- [x] **Geri Yükleme (Rollback):** Tek dokunuşla seçilen eski versiyona onay korumalı güvenli geri dönebilme.

### Aşama 3: İstatistikler & Yazma Analitiği Ekranı (Statistics & Insights)
- [x] **Analitik Motoru:** Toplam not, kelime sayısı, karakter sayısı, okuma süresi hesaplayıcı (`StatisticsCalculator`).
- [x] **Günlük Yazma Serisi (Writing Streak):** Kesintisiz gün serisi hesaplama (mevcut seri, en uzun seri, 7 günlük aktivite grafiği).
- [x] **Etiket & Kategori Dağılımı:** En çok kullanılan `#etiketler` ve kategorilerin yüzdelik oranları ve renkli ilerleme çubukları.
- [x] **Medya İstatistikleri:** Kaydedilen sesler, fotoğraflar ve çizimlerin genel envanteri.
- [x] **İstatistik Ekranı UI (`StatisticsScreen`):** Modern streak hero banner, 2x2 metrik kartları, animasyonlu 7 günlük bar grafik. Yan çekmece ve ayarlara tam entegre.

### Aşama 4: Odaklanma Modu & Pomodoro / Ortam Sesi (Zen Focus Mode)
- [x] **Dikkat Dağıtmayan Yazma Alanı:** Not detayda tüm barları ve dikkat dağıtıcıları gizleyen tam ekran Zen modu.
- [x] **Dahili Pomodoro Sayacı:** 25 dk odaklanma / 5 dk mola döngüsü, canlı dairesel sayaç, duraklatma ve sıfırlama.
- [x] **Sıfır Bağımlılıklı Rahatlatıcı Ses Motoru (Audio Synthesizer):** Ağ veya harici mp3 dosyası gerektirmeyen, Android `AudioTrack` ile 100% çevrimdışı sentezlenen Pembe Gürültü (Rain), Beyaz Gürültü, Akarsu ve 10Hz Alfa Dalga binaural ton üreticisi (`FocusAudioSynthesizer`).
- [x] **Canlı HUD:** Yüzen cam hap kapsül (`FocusHudOverlay`), anlık kelime/karakter sayacı ve ses seviyesi kontrolü.

### Aşama 5: Kod Kalitesi, Veritabanı Optimizasyonu & Kapsamlı Unit Testler
- [x] **Room Sorgu Optimizasyonları:** Foreign key cascade kuralları, Room Migration 4->5 SQL doğrulaması.
- [x] **Kapsamlı Unit Test Paketi:**
  * `StatisticsCalculatorTest`: Kelime/karakter sayımı, okuma süresi, streak devamlılığı, etiket frekansı testleri (%100 Başarılı).
  * `TemplateInterpolationTest`: `{DATE}`, `{TIME}`, `{DAY}`, `{TITLE}` dinamik değişken doldurucu testleri (%100 Başarılı).
  * Tüm testler ve derleme `testDebugUnitTest` ve `assembleDebug` ile doğrulandı.

---

## 📌 İlerleme Günlüğü (Progress Log)

* **[Aşama 1-5 Tamamlandı]** Şablonlar, versiyon geçmişi geri yükleme, yazma istatistikleri ve analitik ekranı, Zen odaklanma modu ve sentetik ses motoru, Room v5 migrasyonu ve birim testler başarıyla entegre edildi.
* **[Sıradaki Backlog Genişletmesi - Aşama 6]**
  - **PDF & Gelişmiş Dışa Aktarım (Styled PDF & Markdown Export):** Notları şık PDF dokümanı olarak dışa aktarma (başlık, tarih, etiketler, formatlı içerik).
  - **Ana Ekranda Etiket Filtreleme Çipleri (Interactive Tag Filter Bar):** Not listesi üzerinde yatay kaydırılabilir dinamik etiket filtreleme barı.
  - **Gelişmiş Not Sıralama Seçenekleri (Multi-criteria Sorting):** Son güncellenme, oluşturulma tarihi, alfabetik A-Z / Z-A, renge göre sıralama ve iğnelenmiş (pinned) not önceliği.
  - **Hızlı Sesli Not & Yapılacaklar Kontrol İlerlemesi (Checklist Progress Bar):** Not kartlarında kontrol listesi varsa tamamlanma yüzdesi göstergesi (`%75 Tamamlandı [===--]`).
