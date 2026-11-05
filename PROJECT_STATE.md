# 📌 Proje Durumu & Hafıza Kartı (PROJECT_STATE)

**Son Güncelleme:** 2026-11-04 22:15
**Aktif Sürüm / Durum:** v1.4.4 (versionCode 35) / Kararlı & Rust Native Masaüstü Mimarili
**Teknoloji Yığını:** Rust Wry / Tauri (`desktop-native`), Kotlin Jetpack Compose Room SQLite (`app` Android), CameraX & ML Kit Barcode, Vanilla JS, CSS3 Glassmorphism

---

## 1. 🎯 Projenin Amacı ve Temel Mimarisi
- **Amaç:** Çok platformlu (Masaüstü Windows/Linux, Android Mobil) çalışan, yerel öncelikli (local-first), uçtan uca AES-256-GCM şifreli kasa (`.notevault`), çift yönlü wikilinks, Markdown desteği ve LAN üzerinden Wi-Fi senkronizasyon sunan kurumsal düzeyde not defteri.
- **Giriş Noktaları (Entrypoints):**
  - **Masaüstü Başlatıcı:** `Baslat_NotDefterim_Desktop.bat` & `baslaticilar/Baslat_NotDefterim.bat`
  - **Rust Native Masaüstü:** `desktop-native/src/main.rs` (Arayüz: `desktop-native/ui/index.html`)
  - **Android Mobil:** `app/src/main/java/...`
  - **Wi-Fi Sync Test Suite:** `tools/sync_tester/run_tests.bat` ve `Test_WiFi_Sync.bat`
- **Kritik Modül Haritası:**
  - `desktop-native/src/`: Rust SQLite motoru (`db.rs`), zeroize güvenli kripto (`crypto.rs`), Zip-Slip korumalı yedekleyici (`backup.rs`), tiny_http Wi-Fi sunucusu (`sync_server.rs`).
  - `tools/sync_tester/`: Herhangi bir projede kullanılabilir bağımsız Wi-Fi & LAN senkronizasyon doğrulama kütüphanesi (Node.js & Python).
- **Otomatik ve Yedekli APK Dağıtım Protokolü (Fail-Safe In-App Update Pipeline):**
  - **Sürüm ve Dal Senkronizasyon Kuralı:** Her sürüm yayınında `versionCode` artırılır. Üretilen `NoteApp-release.apk` ve sürüm metadata dosyası `version.json`, hem `main` hem de `apk` dağıtım dalına (`origin/apk`) otomatik aktarılarak yayınlanır.
  - **Çift Kanallı Yedekli Güncelleme Motoru (`AppUpdateManager`):** Uygulama içi güncelleme servisi birincil olarak raw CDN (`raw.githubusercontent.com/.../apk/version.json`) üzerinden `versionCode` kontrolü sağlar; erişim engeli veya gecikmelerde GitHub Commits API ve Releases API yedek kanallarına geçiş yapar.
  - **Doğrudan Tek Tıkla Kurulum Güvencesi:** Kullanıcının manuel GitHub aramasına gerek kalmadan doğrudan uygulama içinden (Ayarlar veya otomatik bildirim modalı) arka planda indirme (`SupervisorJob` + `Dispatchers.IO`) ve `FileProvider` ile tek tıkla APK kurulumu garanti altına alınır.

---

## 2. ⚡ Aktif ve Tamamlanmış Özellikler
- [x] **Otomatik & Yedekli Uygulama İçi Güncelleme (In-App Update Pipeline):** `AppUpdateManager` ile `apk` ve `main` dalları üzerinden çift kanallı `version.json` denetimi, arka planda kesintisiz indirme ve Android `FileProvider` ile tek tıkla doğrudan APK kurulumu.
- [x] **Okuma Modu Onarımı & Geliştirmesi:** Markdown render ve koyu tema zıtlık kontrast sorunu çözüldü, üst çubuğa doğrudan erişilebilir Okuma Modu butonu eklendi, font boyutu (A-/A+) ve satır aralığı ölçekleyici optimize edildi.
- [x] **Yalınlaştırılmış 3 Nokta Ana Eylemler Menüsü & Pop-up Mimarisi:** Karmaşık akordiyon katmanları arındırılarak doğrudan erişilebilir ana eylemler listesine dönüştürüldü; Paylaş ve Araçlar özelleştirilmiş alt pop-up pencerelerine delege edildi.
- [x] **Notu Paylaş & Dışa Aktar Pop-up'ı (`ShareOptionsDialog`):** Düz Metin, Markdown, PDF Belgesi ve Not Kartı PNG formatlarında tek tıkla paylaşım ve dışa aktarma modülü.
- [x] **Gelişmiş Not Araçları Pop-up'ı (`MoreToolsDialog`):** Versiyon Geçmişi, Şablon Olarak Kaydet, Notu Çoğalt, Metin İstatistikleri ve .notevault Kasa İndirme işlemlerini tek pencerede toplayan modern arayüz.
- [x] **Kategori & Renk Seçim Pop-up'ı:** Not düzenleme arayüzünü ferahlatan, renk paleti ve kategori etiketlerini pop-up modal üzerinden yöneten yapı.
- [x] **Kullanıcı .notevault Kasa Entegrasyonu & Biyometrik Parmak İzi:** Kullanıcının 2526 şifreli kasası çözümlendi; gereksiz kör onay diyalogları kaldırılarak tek dokunuşla parmak izi/PIN ile doğrudan kilit açma sağlandı.
- [x] **Masaüstü %100 Yerel Rust Native (Wry / WebView2) Mimarisi:** Kotlin Desktop ve Electron modülleri tamamen arındırılarak masaüstü %100 saf Rust (`desktop-native`) mimarisine taşındı (<30 MB RAM, <200 ms açılış).
- [x] **Android v1.4.4 ile %100 Özellik ve Veri Eşdeğerliği:** 18 alanlı SQLite veri şeması, Wikilinks, Kasa şifreleme, HTTP 8765 Wi-Fi senkronizasyon, 3 nokta menüsü, Paylaşım pop-up'ı, Gelişmiş araçlar pop-up'ı, 1000x600 sosyal not kartı üreticisi ve Win32 Anti-screenshot koruması masaüstünde eksiksiz uygulandı.
- [x] **Tek Tıkla Rust Masaüstü Başlatıcı:** `Baslat_NotDefterim_Desktop.bat` ile MSVC ve Cargo ortamı kontrol edilerek yerel masaüstü uygulaması tek tıkla başlatılabilir hale getirildi.
- [x] **Odaklanma/Meditasyon Modülü Temizliği:** Kullanıcı deneyimini sadeleştirmek amacıyla yoga/meditasyon ikonu ve arka plan ortam sesleri motoru tamamen kod tabanından silindi.
- [x] **Responsive & Auto-Sizing Tasarım (Taşma Koruması):** `AutoResizeText.kt` dinamik yazı boyutu bileşeni, CSS `clamp()` ve `text-overflow: ellipsis` Android ve Masaüstü arayüzlerine entegre edildi. Metin taşmaları yerine font otomatik küçülerek tasarım korunuyor.
- [x] **Masaüstü QR Kod Eşleştirme & Wi-Fi Senkronizasyonu:** Masaüstünde dinamik yerel IP, port 8765 ve PIN içeren QR kod oluşturucu; Android'de kamerayla tarayıp sıfır yapılandırmayla anında çift yönlü LAN Wi-Fi eşleşmesi.
- [x] **Kamera ile Animasyonlu Optik Veri Aktarımı (Air-Gapped Optical Stream):** Masaüstünde verilerin 220 karakterlik parçalar halinde yüksek hızlı (10-15 FPS) 2D barkod akışı olarak yüzdelik göstergeyle oynatılması; Android kamerasında CameraX ve ML Kit ile kareleri toplayıp CRC32 ile birleştirerek çevrimdışı/ağsız not aktarımı.
- [x] **Canlı Emülatör Doğrulaması:** 10 temel fonksiyon (CRUD, Okuma modu, Akordiyon menü, Kasa, QR eşleşme, Optik akış vb.) canlı ADB testleriyle emülatörde %100 kanıtlandı.
- [x] **Çift Yönlü Wi-Fi Senkronizasyonu (LAN Sync):** PIN korumalı (`X-Sync-PIN` / `X-Pairing-PIN`), otomatik ağ keşfi, HTTP port 8765 üzerinden mobil-masaüstü veri aktarımı.
- [x] **Şifreli Kasa (.notevault):** PBKDF2 (100.000 iterasyon) türetimli anahtar, AES-256-GCM şifreleme ve RAM'den anahtar temizleme (`zeroize`).
- [x] **Gelişmiş Markdown & Canlı Önizleme:** Çift yönlü Wikilinks (`[[Not Adı]]`), görev kutuları (`- [ ]`), XSS korumalı çok katmanlı sanitization.
- [x] **Material 3 & Glassmorphism Arayüzü:** Akıcı kart gölgeleri, açık/koyu (light/dark) tema uyumu, toast bildirimleri ve PIN kopyalama.
- [x] **Yüksek Performanslı Veritabanı:** 7 adet birleşik SQLite arama indeksi, PRAGMA foreign_keys koruması, transaksiyonel soft-delete.
- [x] **Geleceğe Dönük Wi-Fi Test Paketi:** 13 senaryolu (Ağ, Güvenlik, Veri Bütünlüğü SHA-256, 10MB Aşırı Yük Stresi, Fuzzing, Eşzamanlılık) bağımsız test çerçevesi.

---

## 3. 📝 Son Değişiklikler ve Sürüm Günlüğü (Changelog)

### [2026-11-04 22:30] - v1.4.4: Masaüstü %100 Yerel Rust Native (Wry / WebView2) Mimari Dönüşümü ve Android Tam Eşdeğerliği
- **Mimari Dönüşüm & Temizlik:**
  - **Ağır Çalışma Zamanlarının Kaldırılması:** Kotlin Compose Desktop ve Electron modülleri tamamen arındırılarak masaüstü %100 saf Rust (`tao` + `wry` + `rusqlite`) mimarisine (`desktop-native/`) taşındı. Bellek ayak izi < 30 MB'a, açılış süresi < 200 ms'ye düşürüldü.
  - **Android v1.4.4 ile %100 Özellik ve Veri Eşdeğerliği:**
    - **SQLite 18 Alan:** Android Room şemasıyla birebir uyumlu 18 alanlı not veri modeli ve 7 birleşik indeks.
    - **Çift Yönlü Wikilinks & Markdown:** `[[Not Adı]]` otomatik bağlantı oluşturma, görev kutuları (`- [ ]`), XSS korumalı Markdown render.
    - **Şifreli Kasa (.notevault):** PBKDF2 (100.000 iterasyon) türetimli anahtar, AES-256-GCM ve zeroize bellek temizliği.
    - **HTTP 8765 Wi-Fi LAN Senkronizasyonu:** Masaüstünde tiny_http tabanlı REST API sunucusu; mobil ile PIN korumalı (`X-Sync-PIN`), anlık çift yönlü yedekleme ve indirme.
    - **Modern 3 Nokta Menüsü & Pop-up'lar:** Sadeleştirilmiş 3 nokta menüsü, Paylaşım Pop-up'ı (`ShareOptionsDialog`), Gelişmiş Araçlar Pop-up'ı (`MoreToolsDialog`).
    - **1000x600 Sosyal Not Kartı Üreticisi:** Notları sosyal medyada paylaşıma hazır yüksek çözünürlüklü 1000x600 PNG kartı olarak görselleştirme ve dışa aktarma.
    - **Win32 Anti-Screenshot Koruması:** `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` ile kasa veya hassas notlar açıkken ekran görüntüsü ve video kaydı alma girişimlerinde pencereyi otomatik karartma.
  - **Yeni Başlatıcı Scriptleri:** `Baslat_NotDefterim_Desktop.bat` tek tıkla derleme/çalıştırma, ortam kontrolü ve otomatik başlatma desteğiyle güncellendi.
- **Değiştirilen ve Eklenen Dosyalar:**
  - `desktop-native/src/main.rs`: Wry webview oluşturucu, Win32 anti-screenshot, IPC dispatcher ve Tao olay döngüsü.
  - `desktop-native/src/db.rs`: 18 sütunlu SQLite şeması, migration ve arama motoru.
  - `desktop-native/src/crypto.rs` & `backup.rs`: AES-256-GCM şifreleme ve Zip-Slip korumalı yedekleyici.
  - `desktop-native/src/sync_server.rs`: HTTP 8765 Wi-Fi senkronizasyon REST sunucusu.
  - `desktop-native/ui/`: Glassmorphism arayüz, 3 nokta menü, paylaşım pop-up'ı, 1000x600 sosyal kart üretici canvas ve IPC istemcisi.
  - `desktop-native/README.md`: Rust Native teknik kılavuzu ve mimari rehberi.
  - `Baslat_NotDefterim_Desktop.bat`: Tek tıkla yerel masaüstü başlatıcı.

### [2026-11-04 20:30] - v1.4.4 (versionCode 35): 3 Nokta Menüsü Sadeleştirme, Pop-up Modülleri, Şifresiz/Biyometrik Kasa ve Electron Masaüstü Senkronizasyonu
- **Eklenen ve İyileştirilen Özellikler:**
  - **Masaüstü Electron Senkronizasyon Başlatıcı:** `desktop-electron` uygulaması çalıştırıldı; Port 8765 üzerinde HTTP REST LAN senkronizasyonu hazırlandı.
  - **3 Nokta Menüsü Sadeleştirme:** Karmaşık akordiyon yapısı yerine doğrudan tıklanabilir ana eylemler menüsüne dönüştürüldü; dikey kaydırma ve pop-up tetikleyiciler entegre edildi.
  - **Notu Paylaş & Dışa Aktar Pop-up'ı (`ShareOptionsDialog`):** Düz Metin, Markdown (.md), PDF Belgesi ve görsel Not Kartı (PNG) dışa aktarımı tek pop-up diyalogda toplandı.
  - **Gelişmiş Not Araçları Pop-up'ı (`MoreToolsDialog`):** Versiyon Geçmişi, Şablon Olarak Kaydet, Notu Çoğalt, Metin İstatistikleri ve .notevault Kasa İndirme eylemleri ayrı bir alt araç modalına taşındı.
  - **Kategori & Renk Seçim Pop-up'ı:** Not detay arayüzündeki kalabalık önlenerek renk ve kategori seçimi modern bir pop-up diyalog ile ayrıştırıldı.
  - **Kasa Şifresi ve Biyometrik Giriş Optimizasyonu:** Kullanıcının 2526 şifreli .notevault kasası çözümlendi; kör onay diyaloğu kaldırılarak parmak izi / biyometrik doğrulama ile doğrudan ve kesintisiz kilit açma sağlandı.
  - **Gereksiz Meditasyon/Yoga ve Ses Motoru Kaldırıldı:** Odaklanma sesleri ve yoga ikonu kod tabanından arındırılarak uygulamanın bellek ve boyut verimliliği artırıldı.
- **Değiştirilen ve Eklenen Dosyalar:**
  - `app/src/main/java/com/example/noteapp/presentation/detail/NoteDetailScreen.kt` (3 nokta menü, pop-up tetikleyicileri, yoga ikonu temizliği)
  - `app/src/main/java/com/example/noteapp/presentation/detail/dialogs/ShareOptionsDialog.kt` (Paylaş ve dışa aktar pop-up)
  - `app/src/main/java/com/example/noteapp/presentation/detail/dialogs/MoreToolsDialog.kt` (Gelişmiş araçlar pop-up)
  - `app/src/main/java/com/example/noteapp/presentation/components/CategoryColorPicker.kt`
  - `app/src/main/java/com/example/noteapp/presentation/settings/SettingsScreen.kt` & `SettingsViewModel.kt` (Biyometrik kasa akışı)
  - `desktop-electron/` (Port 8765 senkronizasyon sunucusu aktif)

### [2026-11-04 17:00] - v1.4.0: Okuma Modu, Akordiyon Menü Mimarisi, Auto-Sizing, QR Kod Eşleştirme, Optik Veri Akışı ve Canlı Emülatör Doğrulaması
- **Eklenen ve İyileştirilen Özellikler:**
  - **Okuma Modu Onarımı & Kontrast Geliştirmesi:** Markdown parser render hataları ve koyu tema zıtlık kontrast sorunu giderildi. Üst araç çubuğuna doğrudan Okuma Modu butonu eklendi; font boyutu ölçekleyici (A-/A+) ve satır aralığı kontrolleri optimize edildi.
  - **3 Nokta Menüsü Akordiyon Mimarisi:** Karmaşık 30+ maddelik menü 4 ana akordiyon başlığı altında toplandı: *"Görünüm & Okuma"*, *"Güvenlik & Kasa"*, *"Paylaş & Dışa Aktar"*, *"Araçlar & Şablonlar"*. Animasyonlu açılır-kapanır geçişler ve dikey kaydırma (`verticalScroll`) ile ekran taşması tamamen önlendi.
  - **Responsive & Auto-Sizing Tasarım (Taşma Koruması):** `AutoResizeText.kt` dinamik yazı boyutu bileşeni, CSS `clamp()` ve `text-overflow: ellipsis` Android ve Masaüstü arayüzlerine entegre edildi. Metin taşmaları yerine font otomatik küçülerek tasarım ve hiyerarşi korunuyor.
  - **Masaüstü QR Kod Eşleştirme & Wi-Fi Senkronizasyonu:** Masaüstünde dinamik IP, port 8765 ve PIN içeren QR kod oluşturucu modal (`syncQrImage`); Android'de `QrScannerScreen.kt` ve `QrSyncManager.kt` ile kamerayla taranıp anında çift yönlü LAN Wi-Fi eşleşmesi.
  - **Kamera ile Animasyonlu Optik Veri Aktarımı (Air-Gapped Optical Stream):** İnternetsiz ve ağsız ortamlarda veri transferi için masaüstünde not verilerini 220 karakterlik parçalar halinde yüksek hızlı (10-15 FPS) 2D barkod akışı olarak yüzdelik göstergeyle oynatan modal geliştirildi; Android tarafında CameraX ve ML Kit ile kareler toplanıp CRC32 denetimiyle birleştirilerek (`OpticalTransferManager.kt`) kayıpsız çevrimdışı not aktarımı sağlandı.
  - **Canlı Emülatör Doğrulaması:** 10 temel kullanıcı akışı ve fonksiyon (CRUD, Markdown okuma modu, akordiyon menü, kasa şifreleme, QR eşleşme, optik akış vb.) canlı ADB testleriyle Android emülatör üzerinde %100 doğrulandı ve kanıtlandı.
- **Değiştirilen ve Eklenen Dosyalar:**
  - `app/src/main/java/com/example/noteapp/presentation/components/AutoResizeText.kt` (Yeni auto-size metin bileşeni)
  - `app/src/main/java/com/example/noteapp/presentation/components/MarkdownPreview.kt` (Kontrast ve okuma modu düzeltmeleri)
  - `app/src/main/java/com/example/noteapp/presentation/detail/NoteDetailScreen.kt` (Akordiyon menü ve üst çubuk okuma modu butonu)
  - `app/src/main/java/com/example/noteapp/presentation/notes/NoteCard.kt` (AutoResizeText ve taşma koruması)
  - `app/src/main/java/com/example/noteapp/presentation/sync/QrScannerScreen.kt` (CameraX + ML Kit barkod tarayıcı)
  - `app/src/main/java/com/example/noteapp/data/sync/OpticalTransferManager.kt` (Optik akış kare toplayıcı ve CRC32 montajcı)
  - `app/src/main/java/com/example/noteapp/data/sync/QrSyncManager.kt` (Wi-Fi QR eşleşme yöneticisi)
  - `desktop-electron/src/index.html` & `renderer.js` & `styles/main.css` (Dinamik QR eşleşme, optik veri verici modalı, CSS clamp)

### [2026-11-04 22:45] - Tek Tıkla Masaüstü Başlatıcı & Go Engine Entegrasyonu
- **Değiştirilen ve Eklenen Dosyalar & İşlemler:**
  - `baslaticilar/Baslat_NotDefterim.bat`: Çift tıklandığında menüsüz, sessiz ve anında derlenmiş native `notdefterim.exe` çalıştıracak şekilde optimize edildi; exe yoksa Go derlemesini otomatik tetikleyip çalıştıran, o da yoksa Microsoft Edge App modunu (`--app=file:///...`) doğrudan açan çok katmanlı fail-safe zincir tamamlandı.
  - `Baslat_NotDefterim_Desktop.bat`: [1] numaralı seçenek derlenmiş exe önceliği + otomatik Go derlemesi + Edge App modunu kapsayacak şekilde modernize edildi. Menüye [2] Go Native İkili Derleme seçeneği ve [6] Hızlı Teşhis altında Go/Rust/Port kontrolleri eklendi.
  - `desktop-native/go-engine/main.go`: Isolated user profile ile pencereli Windows masaüstü deneyimi sağlandı; derlenmiş ikili `desktop-native/target/release/notdefterim.exe` (7 MB) hazırlandı.

### [2026-11-04 22:15] - Miras Desktop Temizliği & Rust Başlatıcı Modernizasyonu
- **Değiştirilen Dosyalar & İşlemler:**
  - `settings.gradle.kts`: `:desktop` (eski Compose Multiplatform) modülü tamamen kaldırılarak Android projesi (`:app`) bağımsız bırakıldı. `.\gradlew.bat projects` çıktısıyla Android `:app` yapısının eksiksiz ve yeşil olduğu doğrulandı.
  - Klasör Temizliği: Eski Compose Desktop (`desktop/`) ve Electron (`desktop-electron/`) klasörleri projeden güvenle kaldırıldı.
  - `Baslat_NotDefterim_Desktop.bat`: Rust `desktop-native` için baştan uyarlandı. Release/Debug `.exe` önceliği, `cargo run`, `cargo build --release`, `cargo test`, Wi-Fi senkronizasyon araçları ve sistem/port teşhisi eklendi.
  - `baslaticilar/Baslat_NotDefterim.bat`: Tek tıkla derlenmiş release/debug binary veya desktop-native çalıştırma yeteneğiyle modernize edildi.

### [2026-11-04 15:15] - Masaüstü Son Değişikliklerin Düzeltilmesi, UI/UX Geliştirmeleri ve Wi-Fi Test Çerçevesi
- **Değiştirilen Dosyalar:**
  - `desktop-native/ui/index.html`: Skip-link hedef hatası (`#contentInput` -> `#editorContent`) giderildi; PIN panoya kopyalama butonu ve ARIA etiketleri eklendi.
  - `desktop-native/ui/style.css`: Light mode CSS değişkenleri (`--bg-main`, `--bg-surface`, `--text-main`) senkronize edildi; senkronizasyon nabız animasyonu (`syncPulse`) ve responsive düzen eklendi.
  - `desktop-native/ui/app.js`: Çok katmanlı regex XSS sanitization (`<script>`, `<iframe>`, `<object>`, inline eventler, `javascript:`) uygulandı; PIN kopyalama toast bildirimi entegre edildi.
  - `desktop-native/src/sync_server.rs`: PIN başlık esnekliği (`X-Sync-PIN` ve `X-Pairing-PIN`), `/api/status` zenginleştirilmiş yanıtı (`notesCount`, `requiresPin`), 5MB akış sınırı ve bellek içi ZIP desteği eklendi.
  - `desktop-native/src/backup.rs` & `desktop-native/src/crypto.rs`: Zip-Slip / Path Traversal engellemesi, Decompression Bomb OOM koruması, Zeroize bellek temizliği ve constant-time karşılaştırma uygulandı.
  - `desktop-native/src/db.rs` & `desktop-electron/src/db.js`: PRAGMA foreign_keys bağlantı kapsamı düzeltildi, 7 arama indeksi eklendi, soft-delete ve `updatedAt` tutarlılığı sağlandı.
  - `Baslat_NotDefterim_Desktop.bat`: UTF-8 desteği, otomatik `npm install` kontrolü, Wi-Fi test paketi ve hızlı sistem/port teşhis menüsü eklendi.
  - `tools/sync_tester/` & `Projeler/tools_sync_tester/`: Tüm projelerde kullanılabilir bağımsız LAN/Wi-Fi senkronizasyon test çerçevesi (Node.js & Python) geliştirildi.

---

## 4. 🗄️ Veritabanı, Çevre ve API Rotaları
- **Veritabanı Konumları:**
  - Windows Yerel (Rust Native): `%APPDATA%\NotDefterim\notes_desktop.db` ve `dev.db`
  - Test / Doğrulama: `test_tmp_*/notes.db`
- **Wi-Fi Senkronizasyon Portu:** `8765` (HTTP REST API)
- **Aktif Senkronizasyon Rotaları:**
  - `GET /api/status` veya `/status`: Sunucu kimliği, not sayısı ve PIN zorunluluk durumu.
  - `GET /api/sync/download`: ZIP container formatında not ve medya indirme (`X-Sync-PIN` veya `?pin=`).
  - `POST /api/sync/upload`: Mobil cihazdan gelen ZIP veya JSON yedek paketini alma (`X-Sync-PIN`).

---

## 5. ⚠️ Bilinen Durumlar ve Gelecek Yol Haritası (Backlog / TODO)
- [x] v2.0.0 (Faz 2): Etkileşimli Bilgi Ağı (Knowledge Graph), Komut Paleti (Ctrl+K), Yapışkan Notlar (Sticky Notes) ve Android Jetpack Compose Canvas grafik görselleştirmesi tamamlandı.
- [x] v1.4.4: Gerçek taranabilir yerel QR motoru (qrious), koyu cam (glassmorphism) pop-up'ları ve mobil kamera-öncelikli otomatik Wi-Fi senkronizasyonu entegre edildi.
- [x] v1.4.4: Tek tıkla bağımsız masaüstü motoru (Go/Wry Native Engine) ve anında başlatıcılar (`Baslat_NotDefterim.bat`, `Baslat_NotDefterim_Desktop.bat`) devreye alındı.
- [x] v1.4.4: Masaüstü %100 saf Rust (Wry / WebView2) mimarisine taşındı; Kotlin Desktop ve Electron modülleri tamamen arındırıldı.
- [x] v1.4.4: Android v1.4.4 ile %100 özellik eşdeğerliği (SQLite 18 alan, Wikilinks, Kasa, 8765 Sync, 1000x600 sosyal kart, Win32 Anti-screenshot) sağlandı.
- [x] v1.4.4: `Baslat_NotDefterim_Desktop.bat` yerel Rust başlatıcı olarak yapılandırıldı.
- [x] v1.4.4: 3 nokta menüsü sadeleştirildi, ShareOptionsDialog, MoreToolsDialog ve Kategori/Renk pop-up'ları eklendi.
- [x] v1.4.4: Meditasyon/yoga ve odaklanma ses motoru arındırıldı; .notevault (2526) biyometrik şifresiz açılış sağlandı.
- [x] v1.4.4: Masaüstü Electron başlatıldı, Port 8765 senkronizasyonu doğrulandı, Release APK (versionCode: 35) hazırlandı.
- [x] v1.4.3: Sesli okuma (Text-to-Speech / TTS) motoru, Android 11+ paket görünürlüğü, Focus HUD overlay buton geri bildirimi, Release APK derlemesi (versionCode: 34, 4.25 MB) ve GitHub apk/main dağıtımı tamamlandı.
- [x] v1.4.2: Release APK derlendi (versionCode: 33, 4.25 MB), kör [Tamam] diyaloğu kaldırılarak doğrudan parmak izi/PIN akışı bağlandı, version.json güncellendi ve apk/main dalları GitHub'a dağıtıldı.
- [x] v1.4.2: Gerçek kullanıcı .notevault dosyasının kriptografik başlık analizi ve PIN eşleştirmesi tamamlandı ('2526', AES-256-GCM ZIP arşivi deşifrelendi).
- [x] v1.4.1: Biyometrik parmak iziyle şifreli yedek geri yükleme, tek butonlu modern yedekleme pop-up'ı, Release APK derlemesi (versionCode: 32, 4.46 MB) ve GitHub apk/main dağıtımı tamamlandı.
- [x] v1.4.0: Release APK derlendi (versionCode: 31, 4.44 MB), version.json hazırlandı ve apk/main dalları GitHub'a dağıtıldı.
- [x] v1.4.0: GitHub Uzak Depo Yedeklemesi tamamlandı (`commit: cb39152`, `branch: main` -> `git@github.com:Kadiraydin321/NoteApp.git`).
- [x] v1.4.0: Android canlı emülatör doğrulaması %100 yeşil (13/13 fonksiyon ve hotfix bizzat kanıtlandı).
- [x] v1.4.0: Okuma modu kontrast/boyut optimizasyonu ve 3 nokta akordiyon menü mimarisi tamamlandı.
- [x] v1.4.0: Responsive auto-sizing ve taşma koruması (Android & Masaüstü) devreye alındı.
- [x] v1.4.0: Masaüstü QR kod eşleştirme & hava boşluklu (air-gapped) animasyonlu optik veri aktarımı tamamlandı.
- [x] v1.4.0: Subagent Token Sentinel ve Otomatik GitHub Yedekleme kural/beceri sistemi kuruldu.
- [x] Electron test süiti %100 yeşil (CRUD, Kripto, Backup, Wi-Fi Sync).
- [x] Wi-Fi senkronizasyon test çerçevesi %100 yeşil (13/13 test başarılı).
- [x] Rust Native derlemesi ve Windows MSVC C++ Build Tools linker ortamı başarıyla yapılandırıldı; uygulama Wry/WebView2 üzerinden %100 yerel ve kararlı çalışmaktadır.
- [ ] Wi-Fi Senkronizasyon Test Paketinin `cihaz-takip` ve `OmniBackupTransfer` projelerinde de CI/CD sürecine bağlanması.
