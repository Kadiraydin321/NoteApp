# 📡 LAN / Wi-Fi Senkronizasyon Protokol Spesifikasyonu (v2.0)

Bu doküman, masaüstü (Electron, JVM, Rust), mobil (Android, iOS) ve web uygulamaları arasında **yerel ağ (LAN/Wi-Fi)** üzerinden güvenli, iki yönlü ve yüksek performanslı veri senkronizasyonu için standart protokol şartnamesini tanımlar.

---

## 1. Ağ & Servis Mimarisi

- **Varsayılan Port:** `8765` (Yapılandırılabilir)
- **Protokol:** HTTP/1.1 (Opsiyonel mDNS / Bonjour servis ilanı: `_notesync._tcp`)
- **Veri Taşıma Biçimi:** JSON (`application/json`) ve Standart ZIP Arşivi (`application/zip`)
- **Karakter Kodlaması:** UTF-8
- **Eşleşme Koruması:** 4 Haneli Dinamik Güvenlik PIN Kodu (Numeric PIN)

---

## 2. API Uç Noktaları (Endpoints)

### 2.1. Servis Durumu ve Keşif (Discovery)
- **Yol:** `GET /api/status` (Alternatif: `/status`)
- **Yetkilendirme:** PIN gerektirmez (Açık durum sorgusu)
- **Başarılı Yanıt (HTTP 200 OK):**
  ```json
  {
    "status": "ok",
    "appName": "Not Defterim Masaüstü",
    "version": "2.1.0",
    "notesCount": 124,
    "categoriesCount": 6,
    "requiresPin": true
  }
  ```

### 2.2. Veri Paketi İndirme (Export / Download)
- **Yol:** `GET /api/sync/download`
- **Yetkilendirme:** Zorunlu PIN
  - Header: `X-Sync-PIN: <pin>` veya `X-Pairing-PIN: <pin>`
  - URL Query Parametresi (Tarayıcıdan indirme için): `?pin=<pin>`
- **Hatalı/Eksik PIN:** `HTTP 401 Unauthorized`
- **Başarılı Yanıt (HTTP 200 OK):**
  - `Content-Type: application/zip`
  - `Content-Disposition: attachment; filename="NoteApp_Sync.zip"`
  - **Gövde:** Standart ZIP arşivi

### 2.3. Veri Paketi Yükleme (Import / Restore)
- **Yol:** `POST /api/sync/upload`
- **Yetkilendirme:** Zorunlu PIN
  - Header: `X-Sync-PIN: <pin>`
  - Opsiyonel Header: `X-Vault-Password: <parola>` (Şifreli kasa desteği)
  - Opsiyonel Header: `X-Clear-Existing: true|false` (Mevcut veriyi silme / birleştirme)
- **Maksimum Paket Boyutu:** 50 MB (Büyük yüklerde `HTTP 413 Payload Too Large` döner)
- **Başarılı Yanıt (HTTP 200 OK):**
  ```json
  {
    "success": true,
    "notesCount": 45,
    "categoriesCount": 4,
    "attachmentsCount": 2
  }
  ```
- **Hata Durumları:**
  - Hatalı PIN: `HTTP 401 Unauthorized`
  - Bozuk / Geçersiz ZIP: `HTTP 400 Bad Request` (`{ "success": false, "error": "Bozuk ZIP" }`)
  - Şifre Gereksinimi: `HTTP 200` (`{ "success": false, "needsPassword": true }`)

---

## 3. ZIP Paket İçeriği ve Şeması

Bir senkronizasyon paketi (.zip) aşağıdaki dosya hiyerarşisine sahip olmalıdır:

```
NoteApp_Sync.zip
├── backup_info.json       # Meta veri ve sürüm bilgisi
├── categories.json        # Kategori listesi
├── notes.json             # Notlar listesi (Markdown, etiketler, meta veriler)
└── attachments/           # Görseller ve ek dosyalar (Opsiyonel)
    ├── img_001.png
    └── voice_note.m4a
```

### `backup_info.json`
```json
{
  "version": 2,
  "appName": "Not Defterim",
  "timestamp": 1730721600000,
  "exportDate": "04.11.2026 15:00:00",
  "notesCount": 10,
  "categoriesCount": 2
}
```

---

## 4. Güvenlik ve Dayanıklılık Standartları

1. **CORS Desteği:** `Access-Control-Allow-Origin: *` ve `OPTIONS` preflight desteği bulunmalıdır.
2. **Kaba Kuvvet (Brute-Force) Koruması:** Seri hatalı PIN denemelerinde sunucu çökmeksizin talepleri 401 ile sonlandırmalıdır.
3. **Bellek Koruma (Stream / Chunking):** 10 MB+ büyük dosyalarda sunucu hafıza sızıntısı yapmadan bağlantıyı yönetebilmelidir.
4. **Veri Bütünlüğü (SHA-256):** İstemci ve sunucu, aktarılan veri paketlerinin SHA-256 hash kontrollerini doğrulayabilmelidir.
