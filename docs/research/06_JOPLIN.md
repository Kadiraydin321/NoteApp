# Derinlemesine Özellik Analizi: Joplin
**Uygulama Adı:** Joplin  
**Geliştirici:** Laurent Cozic & Açık Kaynak Topluluğu  
**İlk Çıkış:** 2017  
**Kategori:** Açık Kaynak, Uçtan Uca Şifreli (E2EE), Bulut Bağımsız Kişisel Not Sistemi  

---

## 1. Temel Felsefe: "Özgürlük, Mahremiyet ve Bulut Bağımsızlığı"

Joplin, Evernote'un ücretli kısıtlamalarına ve kapalı kaynak gizlilik endişelerine tepki olarak doğmuştur.
Temel Felsefesi: **"Verilerinizi hiçbir şirketin sunucusuna rehin bırakmayın; istediğiniz yere şifreli kaydedin."**

Joplin; ticari bir şirketin insafına kalmadan, notlarını kendi Nextcloud, WebDAV, Dropbox veya yerel sürücüsünde saklamak isteyen gizlilik savunucularının bir numaralı tercihidir.

---

## 2. Mimari ve Senkronizasyon Modeli

Joplin, istemcide yerel bir SQLite veritabanı çalıştırır. Senkronizasyon sırasında her not, etiket ve ek dosya bağımsız birer şifreli JSON/blob dosyasına dönüştürülüp hedef depolama alanına aktarılır.

```
+-------------------------------------------------------------+
|                      JOPLIN MİMARİSİ                        |
+-------------------------------------------------------------+
| [Yerel İstemci (Android / Desktop)]                         |
|   └── Yerel SQLite DB (notes, tags, folders, resources)     |
|   └── E2EE Şifreleme Motoru (AES-256)                       |
|          │                                                  |
|          ▼ Senkronizasyon Katmanı (Sync Engine)             |
|   +───────────────────────────────────────────────────────+ |
|   | Hedefler: Nextcloud | WebDAV | Dropbox | S3 | Joplin Cloud|
|   +───────────────────────────────────────────────────────+ |
+-------------------------------------------------------------+
```

---

## 3. Detaylı Özellik İncelemesi

### A. Askeri Düzeyde Uçtan Uca Şifreleme (E2EE)
- Joplin'de senkronizasyon hedefine giden her veri (metinler, fotoğraflar, ses kayıtları, etiket isimleri) cihazdan çıkmadan önce AES-256 ile şifrelenir.
- Sunucu yöneticisi veya bulut sağlayıcısı verileri yalnızca rastgele anlamsız baytlar olarak görür.
- Anahtar türetme fonksiyonu (PBKDF2) ile brute-force saldırılarına karşı korunur.

### B. Çift Düzenleyici: Markdown ve WYSIWYG
- Gelişmiş kullanıcılar için sol tarafta ham Markdown editörü ve sağ tarafta canlı HTML önizlemesi.
- Düz kullanıcılar için tek tıkla zengin metin (WYSIWYG) arayüzüne geçiş.
- MathJax/KaTeX formül desteği, Mermaid diyagram desteği ve kontrol listeleri.

### C. Coğrafi Konum Etiketleme (Geo-location Tagging)
- Yeni bir not oluşturulduğunda cihazın GPS koordinatları (enlem, boylam, rakım) otomatik olarak notun metadatasında saklanır.
- Kullanıcı daha sonra notlarını bir dünya haritası üzerinde nerede oluşturduğunu görerek filtreleyebilir.

### D. Taşınabilirlik ve Dışa Aktarma Motoru
Joplin veriyi hapsetmez:
- **JEX (Joplin Export):** Tüm notları, klasörleri, etiketleri ve ekleri tek bir sıkıştırılmış arşivde saklar.
- **Ham Markdown Klasörü:** Tüm notları `.md` dosyalarına, tüm resimleri `resources/` klasörüne dönüştürerek Obsidian veya başka araçlara kayıpsız aktarım sağlar.
- **PDF ve HTML Dışa Aktarma:** Tekil veya toplu döküman üretimi.

---

## 4. Güçlü ve Zayıf Yönler Analizi

### Güçlü Yönler:
- %100 Açık Kaynak ve ticari kısıtlamalardan muaf.
- Kendi sunucunu barındırma (Self-hosting) imkanı.
- Üstün E2EE güvenliği.
- Terminal CLI arayüzü (Linux sunucularda komut satırından not alma olanağı).

### Zayıf Yönler:
- Mobil kullanıcı arayüzü modern standartlara göre biraz kaba ve hantaldır.
- Gerçek zamanlı çok kullanıcılı ortak çalışma (Google Docs/Notion tarzı) desteği yoktur.
- Dosya çakışmalarında otomatik birleştirme bazen çakışma notu (conflict note) kopyaları üretir.

---

## 5. NoteApp İçin Çıkarılan Dersler ve Uygulama Mimarisi

NoteApp için Joplin'den benimsenecek temel taşlar:
1. **Veri Egemenliği ve Dışa Aktarma:** NoteApp içindeki tüm notların tek dokunuşla standart Markdown (`.md`) zip arşivi veya JSON Vault olarak dışa aktarılabilmesi.
2. **Kriptografik Güvenlik:** NoteApp'in mevcut `NoteCryptoManager` (AES-256-GCM) bileşeninin sağlamlığının Joplin prensipleriyle pekiştirilmesi.
3. **Çevrimdışı-Öncelikli SQLite Mimarisi:** NoteApp'in Room DB mimarisi Joplin ile birebir örtüşmektedir.
