# 🚀 Cross-Platform LAN / Wi-Fi Senkronizasyon Test Suite & Doğrulama Çerçevesi

Bu araç, **Not Defterim** ve gelecekte geliştirilecek tüm yerel ağ (LAN / Wi-Fi) senkronizasyonuna sahip projeler için tasarlanmış, **tamamen bağımsız (zero-dependency)**, modüler, çift dilli (Node.js + Python 3) bir test ve protokol doğrulama çerçevesidir.

Harici hiçbir `npm` paketi veya `pip` kütüphanesi gerektirmez; standart sistem kütüphaneleriyle doğrudan çalışır.

---

## 🌟 Öne Çıkan Yetenekler

| Modül | Görev | Doğrulanan Kriterler |
|---|---|---|
| **Discovery & Ping Probe** | Ağ Keşfi ve Durum Yoklaması | TCP Ping gecikmesi, HTTP `/api/status` sorgusu, uygulama adı, not sayısı ve versiyon ayrıştırma. |
| **PIN Security Validator** | Kimlik ve Erişim Güvenliği | Eksik PIN reddi (401), hatalı PIN reddi (401), doğru PIN kabulü (200), `X-Sync-PIN`, `X-Pairing-PIN`, `?pin=...` parametre desteği, SQL/XSS enjeksiyon direnci, seri hatalı istek (brute-force) dayanıklılığı. |
| **Data Integrity Engine** | Çift Yönlü Veri Bütünlüğü | ZIP paketi indirme, ZIP içerik analizi (`backup_info.json`, `categories.json`, `notes.json`), deterministik test verisi sentezleme, yükleme (upload), SHA-256 hash doğrulaması, UTF-8 Türkçe karakter ve Markdown tam round-trip doğrulaması. |
| **Edge Cases & Stress Runner** | Sınır Durumlar & Yük Testi | 10 MB aşırı yük paketi (`HTTP 413 Payload Too Large`), bozuk ZIP ikili verisi (`HTTP 400 Bad Request`), 5+ paralel istemci ile eşzamanlılık (concurrency) ve kilitlenmesiz yanıt süresi ölçümü. |
| **Reporting & Export** | Raporlama ve Analiz | Konsolda renkli ANSI çıktıları, özet performans kartları, detaylı `SYNC_TEST_REPORT.md` ve makine tarafından okunabilir `sync_test_results.json` üretimi. |
| **Built-in Mock Servers** | Sıfır Kurulumlu Test (Self-Test) | Hem Node.js hem Python için entegre referans Wi-Fi sunucuları. Uygulama açık olmasa bile CI/CD veya yerel geliştirme için test koşturabilme. |

---

## 📁 Proje Dizin Yapısı

```
tools_sync_tester/
├── README.md                           # Detaylı kullanım kılavuzu
├── SYNC_SPECIFICATION.md               # Wi-Fi Senkronizasyon Protokol Spesifikasyonu (v2.0)
├── package.json                        # Zero-dependency npm betikleri
├── run_tests.js                        # Node.js Test Motoru & CLI Runner
├── run_tests.py                        # Python 3 Test Motoru & CLI Runner
├── mock_server.js                      # Dahili Node.js Referans Wi-Fi Sync Sunucusu
├── mock_server.py                      # Dahili Python 3 Referans Wi-Fi Sync Sunucusu
├── Test_WiFi_Sync.bat                  # Windows 1-Click İnteraktif Başlatıcı
├── lib/                                # Node.js Modülleri
│   ├── discovery_probe.js              # TCP Ping ve durum yoklama
│   ├── security_validator.js           # PIN doğrulama ve enjeksiyon testleri
│   ├── integrity_engine.js             # İki yönlü senkronizasyon ve SHA-256 motoru
│   ├── stress_runner.js                # Aşırı yük ve eşzamanlılık testleri
│   ├── zip_util.js                     # Saf (zero-dep) ZIP paketleyici & açıcı
│   └── reporter.js                     # Renkli konsol ve Markdown/JSON raporlama
└── lib_py/                             # Python 3 Modülleri
    ├── discovery_probe.py
    ├── security_validator.py
    ├── integrity_engine.py
    ├── stress_runner.py
    └── reporter.py
```

---

## ⚡ Hızlı Başlangıç

### Yöntem 1: Windows Tek Tıkla Başlatıcı (Önerilen)
`Test_WiFi_Sync.bat` dosyasına çift tıklayarak interaktif menüyü açabilirsiniz:
- `[1]` Yerel Not Defterim masaüstü sunucusunu test eder.
- `[2]` Özel bir IP (örneğin telefonun Wi-Fi IP'si) ve PIN girerek test eder.
- `[3]` Yerel ağdaki (LAN) senkronizasyon sunucularını otomatik tarar (Auto-Discovery).
- `[4]` Dahili Mock sunucu ile sıfır konfigürasyonlu Node.js testi koşturur.
- `[5]` Dahili Mock sunucu ile sıfır konfigürasyonlu Python testi koşturur.

---

### Yöntem 2: Node.js CLI ile Çalıştırma

```bash
# 1. Dahili Mock Sunucu ile Kendi Kendini Test Etme (Self-Test)
node run_tests.js --mock

# 2. Çalışan Yerel Not Defterim Sunucusunu Test Etme (Port: 8765, PIN: 1234)
node run_tests.js --host 127.0.0.1 --port 8765 --pin 1234

# 3. Yerel Ağdaki Başka Bir Cihazı Test Etme
node run_tests.js --host 192.168.1.35 --port 8765 --pin 5821

# 4. Ağdaki Aktif Senkronizasyon Sunucularını Otomatik Keşfetme
node run_tests.js --discover

# 5. Özel Eşzamanlılık Sayısı ile Stres Testi
node run_tests.js --mock --concurrency 10
```

---

### Yöntem 3: Python 3 CLI ile Çalıştırma

```bash
# 1. Dahili Mock Sunucu ile Test Etme
python run_tests.py --mock

# 2. Canlı Sunucuyu Test Etme
python run_tests.py --host 127.0.0.1 --port 8765 --pin 1234
```

---

## 🛠️ Bağımsız Referans Mock Sunucusu Başlatma

Geliştirme yaparken veya mobil uygulamadan masaüstü olmadan test etmek istediğinizde bağımsız sunucu başlatabilirsiniz:

```bash
# Node.js Mock Sunucu (Port: 8765, PIN: 1234)
node mock_server.js --port 8765 --pin 1234

# Python Mock Sunucu
python mock_server.py 8765 1234
```

Tarayıcınızdan `http://127.0.0.1:8765` adresine girerek web eşleşme arayüzünü de görüntüleyebilirsiniz.

---

## 📊 Örnek Test Çıktısı

```
========================================================================
   🚀  CROSS-PLATFORM LAN / WI-FI SYNC TEST & VERIFICATION SUITE   
   Enterprise-Grade Wi-Fi Synchronization Protocol Validator v2.0
========================================================================

▶ Aşama 1: Keşif ve Bağlantı Yoklaması (Discovery & Ping Probe)
 ✅ [DISC-01] Hedef Sunucu Erişilebilirliği ve /api/status Durum Sorgusu  6.83ms
    └─ Sunucu kimliği: "Not Defterim Mock Sync Server (Node.js)", Not Sayısı: 1, PIN Zorunlu: true

▶ Aşama 2: Güvenlik ve Eşleşme PIN Doğrulama Motoru
 ✅ [SEC-01]  PIN Başlığı Olmadan İndirme Reddi (HTTP 401)          0.91ms
 ✅ [SEC-02]  Geçersiz PIN ile İndirme Reddi (X-Sync-PIN: 9999)     0.48ms
 ✅ [SEC-03]  Doğru PIN ile Başarılı Yetkilendirme (X-Sync-PIN)    13.64ms
 ✅ [SEC-04]  URL Parametresi ile Yetkilendirme (?pin=...) Doğrulaması  0.88ms
 ✅ [SEC-05]  Alternatif Başlık Desteği (X-Pairing-PIN)             0.75ms
 ✅ [SEC-06]  Sınır Değer ve SQL/XSS Enjeksiyon Koruması               0ms
 ✅ [SEC-07]  Seri Hatalı PIN / Brute-Force Dayanıklılık Testi         1ms

▶ Aşama 3: Çift Yönlü Veri Aktarımı ve Bütünlük Motoru (SHA-256)
 ✅ [DATA-01] Yedek Arşivi İndirme ve ZIP Doğrulaması               0.76ms
 ✅ [DATA-02] İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi 1.85ms

▶ Aşama 4: Sınır Durumlar ve Stres/Dayanıklılık Testleri
 ✅ [STR-01]  Aşırı Yük Güvenlik Testi (10 MB Payload Too Large)     8.9ms
 ✅ [STR-02]  Bozuk ZIP / Geçersiz İkili Veri Paketi Testi           3.7ms
 ✅ [STR-03]  Eşzamanlı Çoklu İstemci Testi (5 Paralel İstek)          2ms

════════════════════════════════════════════════════════════════════════
               📊 TEST SONUÇLARI VE PERFORMANS ÖZETİ                    
════════════════════════════════════════════════════════════════════════
 Hedef Sunucu       : 127.0.0.1:8769
 Toplam Test Sayısı : 13
 Başarılı           : 13
 Başarısız          : 0
 Başarı Oranı       : %100
 Toplam Süre        : 64 ms
════════════════════════════════════════════════════════════════════════
```

---

## 📄 Test Raporları
Her test çalıştırmasında iki tür rapor otomatik olarak oluşturulur:
1. `SYNC_TEST_REPORT.md`: Markdown formatında tablolar, güvenlik analizi ve iyileştirme tavsiyeleri içeren detaylı rapor.
2. `sync_test_results.json`: CI/CD ve otomatik test araçları tarafından tüketilebilen makine dostu JSON verisi.

---

## 🔗 Gelecek Projelerde Kullanım
Bu test çerçevesi yalnızca Not Defterim için değil, [SYNC_SPECIFICATION.md](SYNC_SPECIFICATION.md) protokolünü uygulayan herhangi bir masaüstü, mobil veya IoT cihazının Wi-Fi senkronizasyonunu test etmek için istenilen dizine kopyalanıp doğrudan kullanılabilir.
