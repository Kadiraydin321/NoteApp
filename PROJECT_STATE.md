# 📌 Proje Durumu & Hafıza Kartı (PROJECT_STATE)

**Son Güncelleme:** 2026-11-04 17:00
**Aktif Sürüm / Durum:** v1.4.1 / Kararlı & Emülatör Doğrulamalı
**Teknoloji Yığını:** Rust Wry / Tauri (`desktop-native`), Node.js / Electron 33 (`desktop-electron`), Kotlin Jetpack Compose Room SQLite (`app` Android), Compose Multiplatform (`desktop`), CameraX & ML Kit Barcode, Vanilla JS, CSS3 Glassmorphism

---

## 1. 🎯 Projenin Amacı ve Temel Mimarisi
- **Amaç:** Çok platformlu (Masaüstü Windows/Linux, Android Mobil) çalışan, yerel öncelikli (local-first), uçtan uca AES-256-GCM şifreli kasa (`.notevault`), çift yönlü wikilinks, Markdown desteği ve LAN üzerinden Wi-Fi senkronizasyon sunan kurumsal düzeyde not defteri.
- **Giriş Noktaları (Entrypoints):**
  - **Masaüstü Başlatıcı:** `Baslat_NotDefterim_Desktop.bat`
  - **Electron Masaüstü:** `desktop-electron/main.js` (Arayüz: `desktop-electron/src/index.html`)
  - **Rust Native Masaüstü:** `desktop-native/src/main.rs` (Arayüz: `desktop-native/ui/index.html`)
  - **Compose Desktop:** `desktop/src/jvmMain/kotlin/...`
  - **Android Mobil:** `app/src/main/java/...`
  - **Wi-Fi Sync Test Suite:** `tools/sync_tester/run_tests.js` ve `Test_WiFi_Sync.bat`
- **Kritik Modül Haritası:**
  - `desktop-electron/src/`: SQLite veritabanı (`db.js`), PBKDF2/AES-GCM kasa (`crypto.js`), ZIP/Container yedekleme (`backup.js`), HTTP 8765 senkronizasyon sunucusu (`sync-server.js`).
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
- [x] **3 Nokta Menüsü Akordiyon Mimarisi:** Karmaşık 30+ maddelik menü 4 ana akordiyon başlığı altında ("Görünüm & Okuma", "Güvenlik & Kasa", "Paylaş & Dışa Aktar", "Araçlar & Şablonlar") `AnimatedVisibility`, akıcı geçişler ve taşma önleyici dikey kaydırma (`verticalScroll`) ile yeniden yapılandırıldı.
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
  - Windows Yerel: `%APPDATA%\NotDefterim\notes_desktop.db` ve `dev.db`
  - Electron Bellek / Test: `test_tmp_*/notes.db`
- **Wi-Fi Senkronizasyon Portu:** `8765` (HTTP REST API)
- **Aktif Senkronizasyon Rotaları:**
  - `GET /api/status` veya `/status`: Sunucu kimliği, not sayısı ve PIN zorunluluk durumu.
  - `GET /api/sync/download`: ZIP container formatında not ve medya indirme (`X-Sync-PIN` veya `?pin=`).
  - `POST /api/sync/upload`: Mobil cihazdan gelen ZIP veya JSON yedek paketini alma (`X-Sync-PIN`).

---

## 5. ⚠️ Bilinen Durumlar ve Gelecek Yol Haritası (Backlog / TODO)
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
- [ ] Rust Native derlemesi için Windows MSVC C++ Build Tools linker ortamının tanımlanması (uygulama şu anda Electron ve Compose üzerinden tam kararlı çalışmaktadır).
- [ ] Wi-Fi Senkronizasyon Test Paketinin `cihaz-takip` ve `OmniBackupTransfer` projelerinde de CI/CD sürecine bağlanması.
