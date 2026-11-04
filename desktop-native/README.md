# 🦀 Not Defterim - Yerel Masaüstü (desktop-native)

> **Mimari Dönüşüm:** Kotlin Compose Desktop ve Node.js / Electron modülleri tamamen arındırılarak masaüstü istemcisi **%100 yerel Rust (`tao` + `wry` + `rusqlite`)** mimarisine taşınmıştır. Sistem, Windows'un yerel WebView2 bileşenini kullanarak ultra düşük bellek ayak izi (< 30 MB RAM) ve anlık açılış (< 200 ms) sağlar.

---

## 1. 🚀 Hızlı Başlatma (Launcher)

Uygulama kök dizinindeki akıllı başlatıcı ile tek tıkla derlenir ve başlatılır:

```cmd
Baslat_NotDefterim_Desktop.bat
```

Bu başlatıcı betik:
- MSVC C++ Build Tools ve Rust/Cargo araç zincirini otomatik tespit eder ve ortam değişkenlerini ayarlar.
- Port `8765` durumunu kontrol eder ve çakışma durumunda otomatik temizlik yapar.
- Geliştirme veya doğrudan Release modunda uygulamayı ayağa kaldırır.

---

## 2. 📱 Android v1.4.4 ile %100 Özellik ve Veri Eşdeğerliği

Masaüstü istemcisi, Android mobil uygulaması (v1.4.4 / versionCode 35) ile birebir aynı işlevsel derinliğe ve veri yapısına sahiptir:

1. **18 Alanlı SQLite Veri Şeması (`db.rs`):**
   - Android Room veritabanı ile tam uyumlu 18 alan: `id`, `title`, `content`, `category`, `color`, `createdAt`, `updatedAt`, `isPinned`, `isFavorite`, `isArchived`, `isDeleted`, `reminderTime`, `tags`, `isLocked`, `folderId`, `drawingPaths`, `vaultCategory`, `version`.
   - 7 adet birleşik SQLite arama indeksi ve soft-delete koruması.
2. **Çift Yönlü Wikilinks & Markdown Motoru:**
   - `[[Not Adı]]` sözdizimi ile notlar arası akıllı çift yönlü bağlantı oluşturma.
   - İnteraktif görev kutuları (`- [ ]`, `- [x]`), kod blokları ve XSS korumalı HTML filtreleme.
3. **Şifreli Kasa (.notevault / `crypto.rs`):**
   - PBKDF2 (100.000 iterasyon) ile türetilen AES-256-GCM şifreleme.
   - Güvenlik sonrası bellekten anahtar temizleme (`zeroize`) ve Zip-Slip / Path Traversal korumalı arşivleyici (`backup.rs`).
4. **HTTP 8765 Wi-Fi LAN Senkronizasyonu (`sync_server.rs`):**
   - Yerel ağ üzerinden telefon ve masaüstü arasında port `8765` üzerinden çift yönlü JSON / ZIP veri aktarımı.
   - `X-Sync-PIN` ve dinamik QR kod tabanlı sıfır yapılandırmalı anında eşleşme.
5. **Yalınlaştırılmış 3 Nokta Menüsü:**
   - Karmaşık akordiyon menüden arındırılmış, doğrudan erişilebilir modern eylemler listesi.
6. **Notu Paylaş & Dışa Aktar Pop-up'ı (`ShareOptionsDialog`):**
   - Düz Metin (.txt), Markdown (.md), PDF Belgesi ve görsel Not Kartı (.png) olarak tek tıkla dışa aktarma.
7. **Gelişmiş Not Araçları Pop-up'ı (`MoreToolsDialog`):**
   - Versiyon Geçmişi, Şablon Olarak Kaydet, Notu Çoğalt, Metin İstatistikleri (kelime, karakter, okuma süresi) ve `.notevault` Kasa İndirme.
8. **1000x600 Sosyal Not Kartı Üreticisi:**
   - Notun başlık, içerik, kategori ve tarihini yüksek çözünürlüklü (1000x600 piksel) görsel bir sosyal medya kartına dönüştüren yerel Canvas motoru.
9. **Win32 Anti-Screenshot / Ekran Karartma Koruması:**
   - Win32 API `SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)` entegrasyonu; kasa ve gizli notlar açıkken ekran görüntüsü ve video kaydı alma araçlarında pencere otomatik olarak siyah/boş görünür.

---

## 3. 🏗️ Sistem ve Katman Mimarisi

```
desktop-native/
├── Cargo.toml               # Rust bağımlılıkları (wry, tao, rusqlite, aes-gcm, pbkdf2, tiny_http)
├── ARCHITECTURE.md          # Ayrıntılı teknik şartname ve IPC kontratları
├── src/
│   ├── main.rs              # Tao Event Loop, Wry WebView oluşturma ve IPC Dispatcher
│   ├── db.rs                # 18 sütunlu SQLite motoru ve sorgular
│   ├── crypto.rs            # AES-256-GCM ve PBKDF2 kasa güvenlik motoru
│   ├── backup.rs            # Zip-Slip korumalı ZIP dışa aktarma / içe aktarma
│   ├── sync_server.rs       # HTTP 8765 tiny_http senkronizasyon REST sunucusu
│   └── models.rs            # Android v1.4.4 uyumlu veri yapıları
└── ui/
    ├── index.html           # Glassmorphism HTML5 iskeleti ve modallar
    ├── app.js               # IPC istemcisi, Wikilinks, 1000x600 kart üretici ve Markdown editörü
    └── style.css            # Dark/Light tema, Material 3 kartlar ve akıcı animasyonlar
```

---

## 4. 🔌 IPC (Inter-Process Communication) Kontratı

Arayüz (JavaScript) ile Rust çekirdeği arasında `window.ipc.postMessage` protokolü çalışır:

| Eylem (Action) | Parametreler | Açıklama |
|---|---|---|
| `getNotes` | `{ filter }` | Aktif notları veya çöp kutusunu listeler |
| `saveNote` | `{ note }` | 18 alanlı not kaydeder veya günceller |
| `deleteNote` | `{ id, permanent }` | Soft-delete veya kalıcı silme |
| `unlockVault` | `{ password }` | .notevault kasasını deşifre eder |
| `setSecurityWindow` | `{ enabled }` | Win32 WDA_EXCLUDEFROMCAPTURE açar/kapatır |
| `getSyncStatus` | - | Wi-Fi LAN sunucu port ve PIN durumunu döner |

---

## 5. 🛡️ Güvenlik ve Uyumluluk Notları

- **Veritabanı Yolu:** Windows ortamında `%APPDATA%\NotDefterim\notes_desktop.db` konumunda saklanır.
- **Güvenli Bellek:** Şifreler ve türetilen anahtarlar işlem bittiğinde `zeroize::Zeroize` makrosu ile RAM'den sıfırlanır.
- **Platform Uyumu:** Windows 10/11 x64 yerel derlemesi doğrudan çalışır; harici çalışma zamanı (Node.js, JRE) gerektirmez.
