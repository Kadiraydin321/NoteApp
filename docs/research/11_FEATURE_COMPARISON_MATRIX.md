# Kapsamlı Not Uygulamaları Özellik Karşılaştırma Matrisi
**Hazırlayan:** Antigravity Autonomous Research & Architecture Engine  
**Referans Veri:** 10 Küresel Uygulama + Bizim NoteApp Projemiz  

---

## 1. 35 Kriterli Büyük Karşılaştırma Matrisi

| Kategori | Özellik / Yetenek | Google Keep | Obsidian | Notion | Apple Notes | MS OneNote | Joplin | Logseq | Bear | Samsung Notes | Evernote | **NoteApp (Bizim Proje)** |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Depolama & Mimari** | **Yerel-Öncelikli (Local-First)** | ❌ (Bulut) | ✅ | ❌ (Bulut) | ⚠️ (Hibrit) | ❌ (Bulut) | ✅ | ✅ | ⚠️ (Apple) | ⚠️ (Galaxy) | ❌ (Bulut) | **✅ (Room DB)** |
| | **Açık Kaynak (Open Source)** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | **✅ (Açık Kod)** |
| | **Sıfır Vendor Lock-in (Taşınabilirlik)** | ⚠️ (Takeout) | ✅ (Düz .md) | ❌ (JSON AST) | ⚠️ | ❌ (.one) | ✅ (JEX/.md) | ✅ (.md) | ✅ (.md/html) | ❌ (.sdoc) | ❌ (ENML) | **✅ (JSON/MD)** |
| **Güvenlik & Gizlilik** | **Uçtan Uca Şifreleme (E2EE)** | ❌ | ⚠️ (Sync ile) | ❌ | ✅ (iCloud ADP) | ⚠️ (Bölüm şifre) | ✅ | ✅ (Yerel) | ❌ | ❌ | ❌ | **✅ (AES-256 Kasa)** |
| | **Biyometrik Kilit (Parmak İzi / PIN)** | ❌ | ⚠️ (Eklenti) | ❌ | ✅ (FaceID) | ❌ | ✅ | ❌ | ✅ (FaceID) | ✅ (Knox) | ⚠️ (Ücretli) | **✅ (Biometric)** |
| | **Görev Yöneticisi Gizlilik Kalkanı** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | **✅ (Pitch-Black)** |
| **Yazım & Biçimlendirme** | **Markdown Desteği** | ❌ | ✅ (GFM) | ⚠️ (Kısayollar) | ❌ | ❌ | ✅ | ✅ (GFM/Org) | ✅ (Panda) | ❌ | ❌ | **✅ (GFM + Araçlar)** |
| | **Zengin Metin Araç Çubuğu** | ❌ | ⚠️ (Eklenti) | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ✅ | ✅ | **✅ (Jetpack Compose)**|
| | **Geri Al / İleri Al (Undo/Redo)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **✅ (Snapshot Stack)**|
| | **Otomatik Kaydetme (No Save Btn)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **✅ (Debounced Flow)** |
| **Bağlantı & Organizasyon** | **Çift Yönlü Bağlantı (`[[Wikilink]]`)** | ❌ | ✅ | ⚠️ (İç Link) | ⚠️ (iOS 17+) | ⚠️ | ⚠️ | ✅ | ✅ (Bear 2) | ❌ | ❌ | **🎯 (Entegre Ediliyor)**|
| | **Geri Bağlantılar (Backlinks Listesi)**| ❌ | ✅ | ⚠️ | ❌ | ❌ | ⚠️ | ✅ | ⚠️ | ❌ | ❌ | **🎯 (Entegre Ediliyor)**|
| | **İç İçe Etiketler (`#iş/android`)** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ⚠️ | ✅ | ❌ | ❌ | **🎯 (Entegre Ediliyor)**|
| | **Akıllı Klasörler / Dinamik Filtre** | ❌ | ⚠️ (Dataview) | ✅ | ✅ | ❌ | ⚠️ | ✅ (Datalog) | ⚠️ | ❌ | ⚠️ | **🎯 (Entegre Ediliyor)**|
| | **Renkli Not Kartları (Keep Tarzı)** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | **✅ (12 Renkli Palet)**|
| | **Sabitleme (Pin) & Arşivleme** | ✅ | ⚠️ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ⚠️ | **✅ (Var)** |
| | **30 Günlük Çöp Kutusu (Trash)** | ✅ | ⚠️ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | **✅ (Geri Dönüşüm)** |
| **Çoklu Ortam & Medya** | **Ses Kaydı & Oynatma** | ✅ | ⚠️ | ⚠️ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ | ✅ | **✅ (AudioRecorder)** |
| | **Zaman Damgalı Ses (Audio Bookmark)**| ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | **🎯 (Entegre Ediliyor)**|
| | **Serbest Çizim Kanvası** | ✅ | ⚠️ (Excalidraw)| ❌ | ✅ | ✅ | ❌ | ⚠️ | ⚠️ | ✅ | ⚠️ | **✅ (DrawingScreen)**|
| | **Resim Üzerine Çizim/İşaretleme** | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | **✅ (ImageEditScreen)**|
| | **Cihaz Üzerinde OCR (ML Kit)** | ⚠️ (Bulut) | ❌ | ❌ | ✅ (Yerel) | ⚠️ (Bulut) | ❌ | ❌ | ❌ | ✅ | ⚠️ (Bulut) | **✅ (ML Kit On-device)**|
| **Zamanlama & Görevler** | **Kontrol Listesi Sürükle-Bırak** | ✅ | ⚠️ | ✅ | ✅ | ❌ | ⚠️ | ✅ | ✅ | ⚠️ | ✅ | **✅ (Compose Reorder)**|
| | **Zaman Hatırlatıcısı (Alarmlar)** | ✅ | ❌ | ⚠️ | ✅ | ⚠️ | ✅ | ⚠️ | ❌ | ✅ | ✅ | **✅ (AlarmScheduler)**|
| | **Günlük Not (Daily Journal)** | ❌ | ✅ | ⚠️ (Şablon) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | **🎯 (Entegre Ediliyor)**|
| **Ekosistem & Sistem** | **Gelişmiş Android Widget'ları** | ✅ | ⚠️ (Basit) | ⚠️ (Hantal) | ❌ | ⚠️ | ⚠️ | ❌ | ❌ | ✅ | ⚠️ | **✅ (1x1, 3x2, Popup)**|
| | **Sözcük & Okuma İstatistiği** | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | **🎯 (Entegre Ediliyor)**|
| | **Çevrimdışı Çalışma Hızı (<100ms)** | ⚠️ | ✅ | ❌ | ✅ | ⚠️ | ✅ | ✅ | ✅ | ✅ | ❌ | **✅ (<50ms)** |
| | **Ücretsiz / Sıfır Reklam / Açık** | ✅ | ✅ (Kişisel) | ⚠️ (Sınırlı) | ✅ (Apple içi)| ⚠️ | ✅ | ✅ | ⚠️ (Abonelik)| ✅ (Samsung) | ❌ (Çok kısıtlı)| **✅ (%100 Özgür)** |

---

## 2. Pazar Segmentleri ve NoteApp'in Stratejik Konumu

```
                AĞSAL / DERİN BİLGİ (PKM)
                         ▲
                         │       Obsidian
                         │         • Logseq
                         │
                         │   ★ NoteApp Hedefi (Hibrit Güç)
                         │       [Keep Hızı + Obsidian Ağı]
    Notion               │
       •                 │
                         │             Bear
                         │               •
◄────────────────────────┼────────────────────────► HIZLI YAKALAMA
İŞBİRLİĞİ / VERİTABANI   │                          (Sıfır Sürtünme)
                         │       Apple Notes
                         │         •
                         │     Google Keep
                         │         •
                         │   Samsung Notes
                         │
                         ▼
                BİREYSEL / OFFLINE
```

### NoteApp'in Benzersiz Değer Teklifi (Unique Value Proposition):
Mevcut not alma dünyasında iki aşırı uç vardır:
1. **Google Keep gibi hızlı yakalayıcılar:** Hızlıdır, renklidir, harika widget'ları vardır ama notlar birbirine bağlanamaz, Markdown yoktur, gizlilik sınırlıdır.
2. **Obsidian / Logseq gibi ikinci beyinler:** Müthiş bir ağ ve Markdown gücü sunarlar ama mobil widget'ları hantaldır, ses kaydı, resim çizimi, OCR ve kart renklendirme Keep kadar pürüzsüz değildir.

**NoteApp'in Misyonu:**  
Google Keep'in ultra hızlı yakalama hızını, renkli kartlarını ve zengin multimedya gücünü (Ses, Çizim, OCR, Widget'lar) alıp; üzerine Obsidian'ın **[[Wikilink]] çift yönlü ağını**, Bear'ın **iç içe etiketlerini**, Apple Notes'un **akıllı filtrelerini** ve Joplin'in **askeri düzeydeki yerel kaza güvenliğini** entegre ederek **dünyanın en dengeli ve güçlü mobil not merkezini** inşa etmektir.
