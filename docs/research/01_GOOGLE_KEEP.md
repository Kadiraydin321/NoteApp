# Derinlemesine Özellik Analizi: Google Keep
**Uygulama Adı:** Google Keep  
**Geliştirici:** Google LLC  
**İlk Çıkış:** Mart 2013  
**Kategori:** Hızlı Fikir Yakalama, Renkli Yapışkan Notlar, Minimalist Hatırlatıcılar  

---

## 1. Temel Felsefe ve Kullanıcı Deneyimi (UX)

Google Keep'in kalbinde yatan felsefe **"Sıfır Sürtünme ile Yakala (Capture at Zero Friction)"** yaklaşımıdır. Geleneksel not defterlerindeki klasörleme, biçimlendirme ve hiyerarşi kurma zahmetini tamamen ortadan kaldırarak buzdolabı kapağına yapıştırılan renkli post-it kağıtları deneyimini dijital ortama taşımıştır.

### Ayırt Edici UX Desenleri:
- **Masonry (Mozaik) Izgara Düzeni:** Notlar sabit satırlar yerine içeriğin uzunluğuna göre dinamik olarak boyutlanan 1 veya 2 sütunlu kartlar halinde dizilir.
- **Renk Tabanlı Görsel Ayrım:** Klasörler yerine 12 adet pastel renk ve özel tematik arka plan desenleri kullanılır. Beyin, rengi metinden 60.000 kat daha hızlı algıladığı için kullanıcılar kategorileri renklerle hatırlar.
- **Genişletilebilir FAB (Floating Action Button):** Ekranın sağ alt köşesinde ya da alt barında tek dokunuşla metin, liste, çizim, ses ve görsel modlarına doğrudan geçiş imkanı.
- **Otomatik Kaydetme (No Save Button):** Kullanıcı notu yazdığı anda her harf asenkron olarak saklanır. "Kaydet" butonu bulunmaz; geri tuşuna basmak yeterlidir.

---

## 2. Mimari ve Veri Modeli

Google Keep, arka planda Google Drive/Cloud altyapısı ve yerel SQLite/Room önbelleğiyle çalışan hibrit bir yapıya sahiptir.

```
+-------------------------------------------------------------+
|                      GOOGLE KEEP VERİ MODELİ                |
+-------------------------------------------------------------+
| Note Entity:                                                |
|  - id: UUID / String                                        |
|  - title: String                                            |
|  - text_content: String                                     |
|  - is_checklist: Boolean                                    |
|  - checklist_items: List<{text, is_checked, order}>         |
|  - color: Int (Enum: Pastel Palette)                        |
|  - background_illustration: String? (Groceries, Music, vb.) |
|  - is_pinned: Boolean                                       |
|  - is_archived: Boolean                                     |
|  - is_trashed: Boolean + trashed_timestamp (30 gün silinme) |
|  - labels: List<LabelId>                                    |
|  - reminder: { type: TIME | LOCATION, timestamp, lat, lng }|
|  - attachments: List<{ type: IMAGE|AUDIO|DRAWING, url }>    |
|  - collaborators: List<UserEmail>                           |
+-------------------------------------------------------------+
```

---

## 3. Detaylı Özellik İncelemesi

### A. Kontrol Listeleri (Checklist Engine)
- Tek dokunuşla normal metni onay kutularına dönüştürme.
- Tamamlanan maddelerin otomatik olarak alta kayması, üzerinin çizilmesi ve saydamlaşması.
- Tamamlanan öğeleri topluca açma ("Tümünü işaretini kaldır") veya temizleme ("İşaretlenenleri sil").
- Maddeleri parmakla sürükleyip bırakarak (Drag & Drop) yeniden sıralama.
- Girintileme (Indentation) ile alt madde oluşturma desteği.

### B. Multimodal Giriş (Ses, Çizim, OCR)
1. **Ses Kaydı ve Eşzamanlı Metne Dönüştürme:**
   - Mikrofon butonuna basıldığında ses kaydedilirken Google Konuşma Tanıma API'si sesi eşzamanlı olarak metne döker. Not hem ses dosyasını (`.mp3`/`.m4a`) hem de transkripti aynı kart içinde barındırır.
2. **El Çizimi (Canvas Drawing):**
   - Kalem, fosforlu kalem, silgi, cetvel ve seçim araçları.
   - Farklı arka plan ızgaraları (kareli, çizgili, noktalı).
3. **Resimden Metin Çıkarma (OCR):**
   - Nota bir fotoğraf (belge, fiş, tabela) eklendiğinde "Resim metnini al" (Grab image text) özelliğiyle fotoğraftaki yazılar doğrudan not içeriğine aktarılır.

### C. Hatırlatıcılar (Zaman ve Konum Tabanlı)
- **Zaman Hatırlatıcıları:** Sabah (08:00), Öğleden sonra (13:00), Akşam (18:00) veya özel tarih/saat seçimi. Tekrarlanan alarmlar (günlük, haftalık).
- **Konum Hatırlatıcıları (Geofencing):** Kullanıcı belirli bir koordinata yaklaştığında (örneğin "Market" veya belirli bir adres) bildirim tetiklenir.

### D. Organizasyon ve Temizlik
- **Sabitleme (Pinning):** Önemli notlar listenin en tepesindeki "Sabitlenenler" alanında kilitlenir.
- **Arşivleme:** Ana ekranda kalabalık yapmayan ancak silinmek istenmeyen notlar tek bir sağa kaydırma hareketiyle arşive gönderilir.
- **Etiketler (Labels):** Klasör hiyerarşisi yoktur; notlara birden fazla etiket atanabilir (`#market`, `#iş`, `#tatil`).
- **30 Günlük Çöp Kutusu:** Silinen notlar 30 gün boyunca çöp kutusunda bekletilir, ardından kalıcı olarak temizlenir.

### E. Android Widget Ekosistemi
- **1x1 Hızlı Not Kısayolu:** Doğrudan yeni not, ses, liste veya çizim açan kompakt widget.
- **3x2 / 4x4 Kaydırılabilir Liste Widget'ı:** Ana ekrandan notları okuma, kontrol listesini tamamlama ve arama yapma imkanı.
- **Yüzen Hızlı İşlem Çubuğu:** Ana ekran üzerinden anında not oluşturma.

---

## 4. Güçlü ve Zayıf Yönler Analizi

### Güçlü Yönler:
- Benzersiz açılış ve işlem hızı (cold start < 300ms).
- Sıfır kafa karışıklığı sunan renkli minimalist arayüz.
- Çoklu cihaz senkronizasyonunun kusursuzluğu.
- Google Ekosistemi (Google Asistan, Takvim, Docs) entegrasyonu.

### Zayıf Yönler:
- Markdown desteğinin olmaması veya çok kısıtlı kalması.
- Notlar arası çift yönlü bağlantı kurulamaması.
- Kod blokları, sözdizimi vurgulama veya LaTeX formüllerinin bulunmaması.
- Uçtan uca şifreleme (E2EE) sunmaması (Google sunucularında düz metin olarak indekslenir).
- Hiyerarşik klasör veya iç içe etiket yapısının olmaması.

---

## 5. NoteApp İçin Çıkarılan Dersler ve Uygulama Mimarisi

NoteApp projemiz Google Keep'in en güçlü yönlerini temel almıştır:
1. **Uygulananlar:** Renkli kart ızgarası, Masonry mizanpaj, genişletilebilir Keep tarzı FAB, ses kaydı, resim işaretleme ve OCR, çöp kutusu, biyometrik kilit, zengin Android widget'ları.
2. **Aşılan Noktalar:** Google Keep'in sunmadığı **Biyometrik / Şifreli Kasa Koruması**, **Yerel-Öncelikli Gizlilik (Veriler cihaz dışına çıkmaz)**, **Markdown Desteği** ve **Zengin Metin Araç Çubuğu** NoteApp'e başarıyla kazandırılmıştır.
