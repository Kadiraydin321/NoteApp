# Not Defterim — Masaüstü (JavaScript / Electron Sürümü)

Bu proje, **Not Defterim** Android uygulamasının tüm özelliklerini masaüstü platformuna (Windows, Linux, macOS) eksiksiz taşıyan, modern JavaScript ve Electron ile geliştirilmiş sürümüdür.

---

## 🌟 Öne Çıkan Özellikler ve Parite (Android & Masaüstü)

1. **Zengin Markdown Editörü & Canlı Önizleme:**
   - H1 (`#`), H2 (`##`), H3 (`###`), Kalın (`**`), İtalik (`*`), Üstü Çizili (`~~`), Kod (`code`), Alıntı (`>`), Yatay Çizgi (`---`).
   - **Etkileşimli Yapılacaklar Listesi (`- [ ]`, `- [x]`):** Canlı önizleme panelinde checkbox'a tıklandığında metin otomatik güncellenir ve veritabanına kaydedilir.
   - **Çift Yönlü Not Bağlantıları (`[[Not Başlığı]]` - Wikilinks):** Tıklandığında ilgili nota anında geçiş yapar, yoksa aynı başlıkla yeni not üretir.
   - **Geri Bağlantılar (Backlinks Paneli):** Düzenlenen nota başka hangi notların bağlantı verdiğini otomatik analiz eder ve tek tıkla geçiş imkanı sağlar.
   - **Dinamik Etiketler (`#etiket`):** Notların içindeki `#tag` etiketlerini otomatik tarar, sol menüde listeler ve tıklanarak notları filtreler.
   - **Geri Al / İleri Al (Undo / Redo):** `Ctrl+Z` ve `Ctrl+Y` klavye kısayolları ve arayüz butonları.
   - **3 Farklı Görünüm:** Sadece Düzenle, Bölünmüş (Yan Yana Canlı Önizleme), Sadece Önizleme.

2. **Gizli Kasa (Secret Vault - AES-256-GCM + PBKDF2):**
   - Donanım düzeyinde güvenli şifreleme.
   - Android ve Kotlin Compose sürümleriyle **1:1 bayt düzeyinde uyumlu** şifreleme motoru.
   - Kasa kilitlendiğinde içerik şifrelenir, ekran görüntüsü veya dosya taramalarında korunur.

3. **Gelişmiş Çizim Stüdyosu (Drawing Studio):**
   - Kalem (Pen), Fosforlu Kalem (Highlighter - yarı saydam), Çizgi, Ok, Dikdörtgen, Çember ve Silgi.
   - Defter Kağıdı Desenleri: Düz, Çizgili Defter, Kareli Kağıt, Kara Tahta.
   - Renk paleti, çizgi kalınlığı, geri/ileri al.
   - Çizimi PNG olarak ekler klasörüne kaydeder ve doğrudan nota gömer.

4. **Wi-Fi Senkronizasyon Sunucusu (Port 8765):**
   - Dahili yerel HTTP senkronizasyon sunucusu.
   - 4 haneli dinamik eşleşme PIN kodu doğrulaması.
   - Android uygulaması doğrudan masaüstü IP adresine bağlanıp notları yükleyebilir veya indirebilir.
   - Tarayıcı üzerinden erişilebilir web arayüzü.

5. **Akıllı Hatırlatıcılar ve Masaüstü Bildirimleri:**
   - Hızlı şablonlar (Bugün 19:00, Yarın 09:00, Yarın 19:00) ve özel tarih/saat seçici.
   - Süresi gelen hatırlatıcılar için yerel işletim sistemi bildirimleri (OS Native Notifications).

6. **Tam Yedekleme & Geri Yükleme (.zip ve .notevault):**
   - Android yedekleriyle `%100 uyumlu` standart `.zip` arşivi (`notes.json`, `categories.json`, `backup_info.json`, `attachments/`).
   - Kasa şifresi ile şifrelenmiş `.notevault` korumalı arşiv desteği.

7. **Çoklu Seçim ve Toplu İşlemler:**
   - Birden fazla notu seçip toplu olarak çöpe atma veya kalıcı olarak silme.

8. **Görsel Özelleştirme & Güvenlik:**
   - 9 farklı Google Keep tarzı pastel renk teması.
   - Not bazlı özel arka plan görseli ekleme.
   - Anti-Screenshot (Ekran Görüntüsü Engelleme) koruması.
   - Koyu Tema / Açık Tema desteği.

---

## 🚀 Çalıştırma

### Windows:
Kök dizindeki `Baslat_NotDefterim_Electron.bat` dosyasını çift tıklayarak çalıştırabilirsiniz.
Veya komut satırından:
```cmd
cd desktop-electron
npm start
```

### Linux / WSL:
```bash
./run_electron.sh
```

### Testleri Çalıştırma:
```bash
cd desktop-electron
npm test
```
