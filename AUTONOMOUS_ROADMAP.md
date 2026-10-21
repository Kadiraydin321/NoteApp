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

### Aşama 6: Gelişmiş Filtreleme, Kart Kontrol İlerlemesi & Stilize PDF Dışa Aktarımı
- [x] **Etkileşimli Filtre & Etiket Şeridi (`NotesScreen`):** Aktif Kategori ve Aktif Etiket iptal butonları (`✕`), Sabitlenenler, Kilitliler, Görseller, Sesler, Hatırlatıcılar ve ilk 15 `#etiket` için hızlı yatay dokunma çipleri.
- [x] **Not Kartı Kontrol İlerlemesi (Checklist Progress Tracker):** Tüm ızgara, liste ve kompakt modlarda `- [ ]` / `- [x]` maddeleri otomatik algılanarak `ChecklistParser` ile hesaplanır; tamamlanma oranı (`✓ 3/5 yapıldı (%60)`) ve şık minyatür `LinearProgressIndicator` kartlarda gösterilir.
- [x] **Stilize Vektör PDF & Markdown Dışa Aktarıcı (`NoteExporter`):** Not başlıkları (`# H1, ## H2, ### H3`), kontrol kutuları (`[✓], [ ]`), madde işaretleri (`•`), alıntı çizgileri (`>`), kod blokları ve sayfa numaralı altbilgi içeren yüksek kaliteli A4 PDF çizimi.
- [x] **Cihaz İndirilenler'e Doğrudan Kaydetme:** Android MediaStore / Q+ uyumlu, tek dokunuşla PDF dokümanlarını `Downloads/NotDefterim` klasörüne kaydetme yeteneği.
- [x] **Renge Göre Sıralama (`NoteSortOrder.COLOR`):** Renkli notları önceliklendirip palet tonlarına göre gruplayan akıllı sıralama mantığı.
- [x] **Birim Testleri (%100 Başarılı):** `ChecklistParserTest` ve `NoteSortTest` ile tüm checklist ve renk sıralama senaryoları doğrulandı.

### Aşama 7: Akıllı Hatırlatıcılar & SQLite Veritabanı Bakımı (Smart Reminders & Storage Optimization)
- [x] **Bildirimden Hızlı Erteleme (Notification Snooze Actions):** Hatırlatıcı çaldığında bildirim üzerinden doğrudan "✓ Tamamlandı", "⏱ 15 Dk Ertele" ve "⏱ 1 Saat Ertele" aksiyon butonları (`ReminderActionReceiver` & `NotificationHelper`).
- [x] **SQLite WAL Checkpoint, VACUUM ve ANALYZE Motoru:** `DatabaseMaintenanceManager` ile disk alanı geri kazanımı, indeks optimizasyonu ve geçici önbellek/çöp temizliği.
- [x] **Ayarlar Ekranında Bakım Paneli (`SettingsScreen`):** Canlı veritabanı boyutu, önbellek boyutu göstergesi ve tek dokunuşla optimizasyon tetikleyici.

### Aşama 8: Not Çoğaltma (Duplicate Note) & Günlük Yazma Hedefi (Daily Word Goal)
- [x] **Not Çoğaltma (Duplicate Note):** `duplicateCurrentNote` motoru ile notun başlık ("(Kopya)"), formatlı içerik, medya ekleri ve kategorisi korunarak anında yeni kopya üretimi.
- [x] **Günlük Kelime Hedefi Takibi (Daily Writing Target):** `AppSettingsManager` üzerinden özelleştirilebilir hedef (100 - 1000 kelime), `StatisticsScreen` üzerinde motivasyonel rozet ve canlı animasyonlu ilerleme çubuğu (`DailyWordGoalCard`).
- [x] **Not Detay Hızlı Kilit Kısayolu:** `NoteDetailScreen` TopAppBar üzerinde anında kilit açma/kapama ikonu ve güvenlik durumu göstergesi.
- [x] **İstatistik Test Doğrulaması:** `StatisticsCalculatorTest` ile `todayWords` hesaplaması doğrulandı.

### Aşama 9: Toplu Seçim Modunda Renk Değiştirme & Gelişmiş Etiket Yönetimi
- [x] **Toplu Renk Değiştirme (Bulk Color Change):** Çoklu seçim eylem çubuğuna doğrudan `Palette` ikonu eklendi; `showBatchColorDialog` ile 12 palet rengi ve sıfırlama seçeneği sunularak tüm seçilen notlar tek seferde güncellenir (`updateColorForSelected`).
- [x] **Toplu Kategori Atama (Bulk Category Move):** Seçili notlar `showBatchCategoryDialog` ile tek dokunuşla kategorilendirilir veya kategorisiz duruma getirilir.
- [x] **Toplu PDF Birleştirme (Batch Export):** `NoteExporter.exportMultipleToPdf` motoru ile seçilen tüm notlar (başlık, tarih, kategori, etiketler ve formatlı içerik) tek bir dokümanda alt alta estetik biçimde birleştirilip PDF olarak dışa aktarılır ve paylaşılır.

### Aşama 10: Ses Kaydı Oynatıcı Kontrolleri (+5s/-5s Sarma & 1.0x-2.0x Hızlandırma)
- [x] **Ses Sarma Butonları:** Not içi ses oynatıcıya 5 saniye ileri (+5s) ve 5 saniye geri (-5s) sarma kontrolleri (`AudioPlayer.seekBy` ve `AttachmentList`).
- [x] **Oynatma Hızı Çarpanı:** Ses notları için 1.0x, 1.25x, 1.5x ve 2.0x değişken oynatma hızları (`AudioPlayer.setPlaybackSpeed` ve `cycleAudioPlaybackSpeed`).
- [x] **Son Doğrulama & Sürüm Bütünlüğü:** Tüm modüllerin kapsamlı derleme testi ve APK paketleme doğrulaması (`testDebugUnitTest` ve `assembleDebug` %100 başarılı).

### Aşama 11: Çöp Kutusu Otomatik Temizleme Politikası & Okuma / Sunum Modu (Trash Auto-Purge & Presentation Mode)
- [x] **Çöp Kutusu Saklama Politikası (Retention Policy):** `AppSettingsManager` üzerinden 7 gün, 14 gün, 30 gün veya Asla (Devre Dışı) seçenekleri (`TrashRetentionPolicy`).
- [x] **Otomatik Süresi Dolan Not Temizliği:** Uygulama açılışında veya Çöp Kutusu ekranına girildiğinde belirlenen süreden eski silinmiş notların arka planda sessizce kalıcı silinmesi (`cleanUpTrash`).
- [x] **Çöp Kutusu Bilgi Başlığı (Info Banner):** `NotesScreen` üzerinde kullanıcının seçtiği otomatik temizleme süresini gösteren ve tek dokunuşla ayarlara yönlendiren bilgi kartı.
- [x] **Ayarlar Ekranında Saklama Politikası Seçici:** `SettingsScreen` üzerinde 4'lü segmented seçim çipleri ve detaylı açıklama.
- [x] **Tam Ekran Okuma / Sunum Modu (Reader Presentation View):** Notu sadece görüntüleme amacıyla kazara düzenlemeleri engelleyen, font boyutu (A-/A+) dinamik ayarlanabilir, okuma süresi rozetli ve konforlu kenar boşlukları sunan sunum görünümü (`isPresentationMode`).
- [x] **Birim Testleri:** `TrashRetentionTest` ile tüm eşik değer ve retention gün hesaplamaları test edildi (%100 Başarılı).

### Aşama 12: Arama İçi Anahtar Kelime Vurgulama & Son Aramalar Geçmişi (Search Highlighting & History)
- [x] **Arama Geçmişi Saklama (Recent Search Queries):** Kullanıcının yaptığı son aramaların SharedPreferences üzerinde saklanması (`app_search_history`), en fazla 8 aramanın güncel tutulması ve arama çubuğu boşken etkileşimli yatay çipleriyle sunulması.
- [x] **Son Arama Yönetimi:** Çipten tek dokunuşla arama yapma, çipten kaldırma (`✕`) ve tüm geçmişi tek dokunuşla temizleme aksiyonları (`saveSearchQuery`, `removeRecentSearch`, `clearRecentSearches`).
- [x] **Arama Sonuçlarında Kelime Vurgulama (Keyword Highlighting):** `TextHighlightHelper.highlightQuery` motoru ile tüm not listelerinde (`NoteCard` ve `CompactNoteCard`) aranan kelimenin başlıkta ve içerik önizlemesinde renkli vurgulu arka planla gösterilmesi.
- [x] **Birim Testleri:** `TextHighlightHelperTest` ile büyük/küçük harf duyarsızlığı, tekli/çoklu eşleşme indisleri ve boş sorgu durumları %100 doğrulandı.

### Aşama 13: Çoklu Not Birleştirme Motoru (Note Merge Engine) & Toplu Aksiyonlar
- [x] **Çoklu Seçimde Not Birleştirme (Merge Notes):** 2 veya daha fazla not seçildiğinde üst aksiyon çubuğunda ve eylemler menüsünde "Notları Birleştir" butonu (`CallMerge`).
- [x] **Akıllı İçerik & Medya Birleşimi:** `NoteMerger.mergeNotes` motoru ile seçilen notların başlıklarını H2 Markdown başlığı (`## Başlık`) yaparak, içeriklerini ayraçlarla (`---`) bağlama ve tüm görsel/çizim/ses eklerini kayıpsız tekrarsız bir araya getirme.
- [x] **Orijinal Not Politikası Seçici Diyalog:** Birleştirme onayında kullanıcıya "Orijinal notları çöp kutusuna taşı" veya "Orijinal notları koru" seçeneği sunma (`showMergeNotesConfirm`).
- [x] **Birim Testleri:** `NoteMergerTest` ile kronolojik sıralama, içerik ayrımı, kategori korunumu ve medya tekilleştirmesi %100 doğrulandı.

### Aşama 14: Detay Ekranı Canlı Tipografi HUD & Hızlı H1/H2/H3 Başlık Formatlama
- [x] **Hızlı Markdown Başlık Butonları (H1, H2, H3, Alıntı, Çizgi):** Not düzenleme araç çubuğuna doğrudan `H1`, `H2`, `H3`, `>` (Blok Alıntı) ve `---` (Ayraç Çizgi) hızlı biçimlendirme butonları eklendi (`NoteDetailScreen`).
- [x] **Canlı Tipografi HUD (Word & Reading Time Tracker):** Not detay düzenleme ekranında anlık kelime sayısı, karakter sayısı ve tahmini okuma süresini gösteren şık kompakt durum rozeti (`$wordCount kelime · ~${readingTime} dk`).
- [x] **Birim Testleri & Derleme Doğrulaması:** `MarkdownWrapTest` ve `assembleDebug` ile tüm markdown biçimlendirme senaryoları %100 doğrulandı.

### Aşama 15: Kategoriye Özel Renk Paletleri & Renkli Kategori Çipleri (Colored Category Badges)
- [x] **Kategori Renk Seçici:** Yeni kategori oluştururken veya var olanı düzenlerken 8 estetik palet renginden seçim yapabilme (`Category.DEFAULT_COLORS`, `CategoryColorPicker`).
- [x] **Kategori Düzenleme Yeteneği:** Kategorileri Yönet ekranından mevcut kategorilerin adını ve rengini anında düzenleyebilme (`categoryToEdit`, `onUpdateCategoryColor`).
- [x] **Görsel Kategori Rozetleri:** Menü çekmecesinde, filtre çiplerinde, not detayında ve tüm liste modlarında (ızgara, liste, kompakt) kategorinin rengiyle uyumlu zarif renkli rozet ve nokta göstergeleri (`CategoryBadge`).
- [x] **Birim Testleri:** `CategoryColorTest` ile palet tekilliği, varsayılan değerler ve renk güncelleme senaryoları %100 doğrulandı.

### Aşama 16: Notu Bildirim Çubuğuna Sabitleme (Sticky Note Notification) & Stilize HTML Dışa Aktarım
- [x] **Bildirime Sabitleme Motoru (Sticky Notification):** Not detay ekranından tek dokunuşla notun Android bildirim paneline kalıcı/sabit (ongoing) olarak iğnelenmesi (`NotificationHelper.showStickyNotification`).
- [x] **Bildirim Aksiyonları:** Bildirim üzerinden tek dokunuşla notu açma veya sabitlemeyi kaldırma ("📌 Kaldır" / `ACTION_UNPIN_STICKY` ve `cancelStickyNotification`).
- [x] **Stilize HTML Web Dokümanı Dışa Aktarımı:** Notları responsive, modern koyu/açık tema destekli CSS, başlıklar, kontrol listeleri ve blok alıntılar içeren tekil `.html` dosyası olarak kaydedip tarayıcıda açabilme veya Cihaz İndirilenler klasörüne aktarabilme (`NoteExporter.exportToHtml`).
- [x] **Birim Testleri & Doğrulama:** `HtmlExporterTest` ve `StickyNotificationTest` ile HTML markdown ayrıştırma, XSS koruması ve bildirim ID senaryoları %100 doğrulandı.

### Aşama 17: Kilitli Notlar Otomatik Zaman Aşımı Politikası (Auto-Relock Timeout Policy)
- [x] **Zaman Aşımı Modeli & Ayarı:** `AppSettingsManager` üzerinden Anında (Varsayılan), 1 Dakika, 5 Dakika, 15 Dakika kilit koruma süresi (`LockTimeoutPolicy`).
- [x] **Ayarlar Ekranı Seçici:** `SettingsScreen` üzerinde güvenlik kartına kilit zaman aşımı seçenekleri ve dinamik açıklamalar eklendi.
- [x] **Akıllı Yeniden Kilitleme:** Kullanıcı kilitli notu açtıktan sonra grace period süresince tekrar şifre/biyometrik doğrulaması gerekmeden çalışır; süre dolduğunda veya uygulama kapatıldığında otomatik kilitlenir (`lastUnlockTimestamp`, `backgroundTimestamp`).
- [x] **Birim Testleri:** `LockTimeoutPolicyTest` ile zaman aşımı hesaplayıcı ve süre eşik senaryoları %100 doğrulandı.

### Aşama 18: Not Detayında Bul ve Değiştir (In-Note Find & Replace Toolbar)
- [x] **Açılabilir Arama & Değiştirme Çubuğu:** Not düzenleme ekranında klavye üstünde veya TopAppBar altında kompakt Find & Replace dock (`FindAndReplaceBar`).
- [x] **Canlı Eşleşme ve Navigasyon:** Aranan kelimenin not içeriğindeki tüm indislerini bulma, aktif eşleşmeyi vurgulama (`3/8`) ve sonraki (`↓`)/önceki (`↑`) eşleşmeye odaklanma (`FindReplaceEngine`).
- [x] **Tekil ve Toplu Değiştirme:** "Değiştir" ve "Tümünü Değiştir" butonlarıyla anında metin ikamesi, imleç ve seçim koruması, büyük/küçük harf duyarlılığı (`Aa`).
- [x] **Birim Testleri:** `FindReplaceEngineTest` ile büyük/küçük harf duyarsızlığı, tekil/tümünü değiştirme ve eşleşme navigasyonu testleri %100 doğrulandı.

### Aşama 19: Okuma Modunda Metin Boyutu ve Çift Sütun / Gazete Düzeni (Typography Reading Layouts)
- [x] **Dinamik Font Boyutu & Satır Aralığı:** Okuma/sunum modunda satır yüksekliği çarpanı (`1.3x`, `1.5x`, `1.8x`) döngüsü ve harf aralığı kontrolü (`MarkdownPreview`).
- [x] **Tablet / Yatay Ekran Çift Sütun Desteği:** Geniş ekranlarda (>=600dp) otomatik aktifleşen veya kullanıcı butonuyla açılıp kapanabilen, anlamsal blokları bölmeyen gazete okuma düzeni (`ReadingColumnSplitter.splitIntoBalancedColumns`).
- [x] **Birim Testleri:** `ReadingColumnSplitterTest` ile kod bloklarının bölünmezliği, paragraf dengeleme ve başlık hiyerarşisi %100 doğrulandı.

### Aşama 20: Çöp Kutusu Arka Plan Otomatik Temizlik Zamanlayıcısı (WorkManager Trash Auto-Cleanup Worker)
- [x] **Periyodik Arka Plan Görevi:** `WorkManager` ile günde 1 kez çalışan, saklama süresi (Retention Policy: 7/14/30 gün) dolmuş çöp notlarını ve diskteki bağlı eklerini temizleyen işçi sınıfı (`TrashCleanupWorker`, `TrashCleanupScheduler`).
- [x] **Ayarlar & Manuel Tetikleme:** Ayarlar bakım sekmesinde WorkManager arka plan zamanlayıcısı durumu rozeti ve tek dokunuşla hemen temizleme butonu ("Süresi Dolan Çöpleri Şimdi Temizle").
- [x] **Birim Testleri:** `TrashCleanupWorkerTest` ile zaman aşımı eşik hesaplamaları, miras (legacy) kayıt yönetimi ve çöp filtreleme %100 doğrulandı.

### Aşama 21: Not İçeriklerini JSON / ZIP Yedekleme ve Geri Yükleme Motoru (Full Backup & Restore Engine)
- [x] **Tam Kapsamlı Yedek Paketi (.zip):** Tüm notlar, kategoriler, `note_history` versiyon revizyonları, özel şablonlar (`templates.json`), `deletedAt` çöp zaman damgaları ve tüm medya eklerinin eksiksiz ZIP arşivi olarak paketlenmesi (`BackupManager.buildZipArchiveBytes`).
- [x] **Güvenli ve Bütünlüklü Geri Yükleme:** Cihazdan seçilen veya masaüstünden senkronize edilen yedek dosyasından şablonların, revizyon geçmişinin ve medya dosyalarının eksiksiz çözülüp Room veritabanına aktarılması (`BackupManager.restoreBackup`).
- [x] **Birim Testleri:** `BackupRestoreEngineTest` ile ZIP paketleme/ayrıştırma, JSON şema bütünlüğü ve Vault Magic doğrulamaları %100 test edildi.

### Aşama 22: Not Sabitleme & Çizim / Karalama Kartı İyileştirmeleri (Canvas Drawing & Visual Polish)
- [x] **Çizim / Karalama Kartı Önizlemeleri:** Not listesinde (`NoteCard` ve `CompactNoteCard`) ve not detay ekranında (`AttachmentList`) el çizimlerinin kırpılmadan (`ContentScale.Fit`), yüksek kontrastlı temiz arka planla ve `Icons.Default.Brush` rozetiyle ("🎨 Çizim") şık bir şekilde sunulması.
- [x] **Görsel Önizleme ve Etiketleme:** Kompakt listede el çizimi içeren notlara fırça ikonu yerleştirilmesi, kartlarda çizim tipinin anında ayırt edilmesi.

### Aşama 23: Markdown Tablo Ayrıştırıcı & Canlı Tablo Düzenleyici (Markdown Table Editor)
- [x] **Görsel Tablo Kartı:** Not detayında Markdown tablolarını (`| Başlık 1 | Başlık 2 |`) okunabilir şık Material 3 ızgara formatında render etme (`MarkdownTableView` & `MarkdownTableParser`).
- [x] **Hızlı Hücre Düzenleme:** Tablo hücrelerine tek dokunuşla satır/sütun ekleme, hücre düzenleme ve silme araç çubuğu (`MarkdownTableParser.addRow`, `addColumn`, `deleteRow`, `deleteColumn`, `updateCell`).
- [x] **Birim Testleri:** `MarkdownTableParserTest` ile tablo tespiti, hizalama, hücre güncelleme ve serileştirme testleri %100 doğrulandı.

### Aşama 24: Not İçi Ses Kaydı Canlı Dalga Formu & Dokunmatik Sarma (Live Audio Visualizer & Waveform)
- [x] **Canlı Ses Dalga Formu:** Ses kaydederken mikrofon genliğine (amplitude) göre gerçek zamanlı dinamik dalga çubukları render etme (`AudioRecordingVisualizerBar` & `AudioRecorder.getMaxAmplitude`).
- [x] **Oynatıcı Dalga İlerlemesi & Dokunmatik Sarma (Waveform Scrubber):** Not detayında ve ekler listesinde ses oynatılırken dinamik dalga formu çubukları, oynatılan çubuk renk değişimi, dokunarak ve sürükleyerek (scrubbing) anında sarma ve zaman göstergeleri (`AudioWaveformScrubber` & `NoteDetailViewModel.seekAudioToFraction`).
- [x] **Birim Testleri:** `WaveformHelperTest` ile genlik normalizasyonu, deterministik dalga formu üretimi, zaman biçimlendirme ve çubuk hesaplamaları %100 doğrulandı.

### Aşama 25: Android Dinamik Kısayolları & Ana Ekrana Sabitleme (Dynamic App Shortcuts & Quick Launchers)
- [x] **Launcher Dinamik Kısayolları:** Android ana ekran ikonuna uzun basıldığında "Yeni Not", "Ses Kaydı", "Fotoğraflı Not", "Çizim Yap" hızlı eylem kısayollarının dinamik sunulması (`AppShortcutManager.initDynamicShortcuts` & `ShortcutManagerCompat`).
- [x] **Hızlı Başlatma Yönlendirmeleri:** Kısayollara tıklandığında `MainActivity`'de `autoAction` parametresiyle ilgili not oluşturma ekranının doğrudan açılması.
- [x] **Ana Ekrana Not Sabitleme (Pin to Home Screen):** Not detay overflow menüsünden tek dokunuşla seçili notun Android ana ekranına bağımsız kısayol olarak sabitlenmesi (`AppShortcutManager.pinNoteToHomeScreen`).
- [x] **Birim Testleri:** `AppShortcutManagerTest` ile kısayol ID'leri ve intent yönlendirme eşleştirici testleri %100 doğrulandı.

### Aşama 26: Notları Şifreli / Parolalı Dışa Aktarma (Encrypted Note Export / Vault Container)
- [x] **AES-256 Parolalı Konteyner (.notevault):** Seçilen notları veya tüm veritabanını kullanıcının belirleyeceği parola ile PBKDF2 türetimli AES-256-GCM şifreleyerek taşınabilir dosya formatında dışa aktarma (`VaultExportEngine.createVault` & `ExportVaultDialog`).
- [x] **Kasa İçe Aktarma & Çözme (Vault Importer):** Şifreli kasa dosyasını parolası girildiğinde doğrulayarak güvenle içeri aktarma ve notları Room'a yazma (`VaultExportEngine.readVault` & `BackupManager.restoreBackup`).
- [x] **Birim Testleri:** `VaultExportEngineTest` ile şifreleme/çözme, yanlış parola reddi, başlık doğrulaması ve bozuk dosya koruma testleri %100 doğrulandı.

### Aşama 27: Kontrol Listesi Canlı İlerleme Halkası & Akıllı Sıralayıcı (Checklist Progress Ring & Sorter)
- [x] **Not İçi Canlı Kontrol Listesi Özeti:** Not detay ekranında başlık yanında veya tipografi HUD'ında şık minyatür dairesel ilerleme halkası (`ChecklistProgressRing`: örn: %75 tamamlandı, 3/4) ve tıklanabilir hızlı aksiyon menüsü.
- [x] **Tamamlananları Otomatik / Manuel Sıralama:** Not düzenleme ekranında tamamlanan checklist maddelerini (`- [x]`) tek dokunuşla en alta taşıma motoru (`ChecklistSorter.moveCheckedToBottom`), toplu tamamlama (`toggleAllItems`) ve tamamlananları temizleme (`removeCompletedItems`).
- [x] **Birim Testleri:** `ChecklistSorterTest` ile girintileme korunumu, normal paragrafların etkilenmemesi, blok sıralama ve toplu tamamlama testleri %100 doğrulandı.

### Aşama 28: Canlı Markdown Tablo Otomatik Hizalayıcı (Markdown Table Prettifier & Auto-Formatter)
- [x] **Tablo Otomatik Hizalama & Biçimlendirme:** Düzensiz boru (`|`) boşluklarına sahip Markdown tablolarını sütun genişliklerine göre en uzun hücre boyutuna hizalayıp dikeyde boruları tam hizalı ve kusursuz hale getirme (`MarkdownTableParser.prettifyTable`, `toMarkdown`, `formatAllTablesInText`).
- [x] **Görsel Tablo Eylemleri:** Tablo görünümünde sütun silme (`deleteColumn`), başlık hizalamasını (Sol, Orta, Sağ) görsel olarak filtre çipleriyle seçebilme (`cycleAlignment`, `setAlignment`) ve dinamik düzenleme.
- [x] **Birim Testleri:** `MarkdownTableParserTest` ile sütun genişlik hesaplamaları, prettify serileştirme ve hizalama döngü testleri %100 doğrulandı.

### Aşama 29: Not Başına Özel Kelime Hedefi & İlerleme Rozeti (In-Note Word Goal & Circular Progress HUD)
- [x] **Not Başına Özel Kelime Hedefi:** Her not için isteğe bağlı olarak özel kelime hedefi (örn: 100, 250, 500, 1000, 2000 kelime veya özel sayı) belirleyebilme (`NoteGoalManager` & `SetWordGoalDialog`).
- [x] **Canlı Hedef İlerleme Rozeti:** Not detay ekranında hedefe ne kadar yaklaşıldığını gösteren canlı animasyonlu dairesel ilerleme rozeti (`WordGoalProgressBadge`), kalan kelime göstergesi ve hedef tamamlandığında parıldayan başarı durumu.
- [x] **Birim Testleri:** `WordGoalCalculatorTest` ile kelime sayımı, hedef yüzdesi, kalan kelime sayısı ve tamamlama eşikleri testleri %100 doğrulandı.

### Aşama 30: Görsel Üzerine Çizim & İşaretleme (Image Annotation & Marker Sketch on Attached Images)
- [x] **Görsel Üzerine Çizim & İşaretleme:** Ekler listesinde (`AttachmentList`) her görsel için "🎨 Çizim & İşaretleme Yap" ve tam ekran görsel önizleme diyaloğunda "İşaretle & Çiz" seçeneği.
- [x] **Tuval Zeminine Görsel Aktarımı:** Çizim ekranında (`DrawingScreen`) arka plan olarak seçilen fotoğrafı yükleme, en-boy oranını kusursuz koruyarak ortalama (`DrawingScaleHelper.calculateAspectFit`) ve üzerine fırça, fosforlu kalem, ok veya şekillerle serbest çizim yapabilme.
- [x] **Korumalı Kaydetme & Geri Alma Desteği:** İşaretlenen görseli yeni kopya olarak nota ekleme (`edited_image_pair`), orijinal fotoğrafı güvenle saklama ve istendiğinde tek dokunuşla geri alabilme (`revertImageEdit`).
- [x] **Birim Testleri:** `DrawingScaleHelperTest` ile en-boy oranı koruma, dikey/yatay ortalama ve sınır hesaplamaları testleri %100 doğrulandı.

### Aşama 31: Akıllı Etiket Otomatik Tamamlama & Etiket Yönetim Sayfası (Smart Tag Autocomplete & Tag Manager)
- [x] **İçerik İçi `#etiket` Otomatik Tamamlama:** Not düzenleme ekranında kullanıcı `#` yazdığında mevcut kullanılan etiketleri açılır dinamik çip/öneri menüsünde listeleme (`TagAutocompleteBar`) ve tek dokunuşla akıllı boşluk korumalı tamamlama (`TagSuggestionEngine.applyTagSuggestion`).
- [x] **Etiketleri Yönet BottomSheet Paneli:** `TagManagerBottomSheet` ile kullanılan tüm `#etiketleri` sayılarıyla listeleme, etiket adını tüm notlarda toplu değiştirme (rename/refactor: `renameTagAcrossNotes`) veya silme yeteneği (`deleteTagAcrossNotes`).
- [x] **Birim Testleri:** `TagSuggestionEngineTest` ile imleç sorgu algılama, akıllı etiket yerleştirme, alt dizgi korumalı regex refaktör ve etiket silme testleri %100 doğrulandı.

### Aşama 32: Çoklu Görsel Galeri Izgarası & Tam Ekran Kaydırmalı Görüntüleyici (Multi-Image Gallery & Swipeable Viewer)
- [x] **Galeri Izgarası (Collage / Photo Grid):** Notta birden fazla görsel olduğunda ekleri tek tek dikey liste yerine estetik 1x2, 1+2 veya 2x2 collage ızgarasında sunma (`GalleryGridCalculator`). 4'ten fazla görselde 4. görsel üzerine şık `+X` taşma rozeti ekleme.
- [x] **Tam Ekran Sağa/Sola Kaydırmalı Görüntüleyici (Horizontal Pager Photo Viewer):** Tam ekran görsel görüntüleyicide notun tüm fotoğrafları arasında kesintisiz parmakla sağa/sola kaydırma (`HorizontalPager`), sayfa sayaç rozeti (`2 / 5`), yakınlaştırma (pinch-zoom) ve fotoğraf bazlı dinamik eylemler ("İşaretle & Çiz", "Düzenle").
- [x] **Birim Testleri:** `GalleryGridCalculatorTest` ile 1'li, 2'li, 3'lü, 4'lü ve 5+ görsel taşma matrisi ve ses/görsel ayrıştırma testleri %100 doğrulandı.

### Aşama 33: Akıllı Not Kopyalama & Markdown Panoya Kopyalama Araçları (Smart Copy & Markdown Clipboard Suite)
- [x] **Zengin Kopyalama Seçenekleri (Copy Suite):** Not detay ekranında ve ana ekranda seçili notta tek dokunuşla açılan `SmartCopyBottomSheet`: "Düz Metin Olarak Kopyala", "Formatlı Markdown Olarak Kopyala", "Yalnızca Başlığı Kopyala", "Hızlı Özet Kopyala (İlk 3 Cümle)", "Kalan Görevleri Kopyala ([ ])", "Tamamlanan Görevleri Kopyala ([x])" (`ClipboardHelper`).
- [x] **Hızlı Kopyalama Toast & Haptik Geri Bildirim:** Kopyalama işlemi sonrasında kelime/madde sayısını belirten zarif toast ve titreşimli haptik geri bildirim (`ClipboardHelper.copyToClipboard`).
- [x] **Birim Testleri:** `ClipboardHelperTest` ile markdown temizleme, başlık/içerik birleştirme, özet sınırlaması, checklist ayıklama ve kelime sayım testleri %100 doğrulandı.

### Aşama 34: Takvim & Tarih Bazlı Not Ajandası Görünümü (Calendar & Note Agenda View)
- [x] **Ajanda & Takvim Gruplayıcı Motoru (`CalendarNoteGrouper`):** Notları oluşturulma, güncellenme ve hatırlatıcı tarihlerine göre `LocalDate` bazında gruplama, her gün için not etkinlik/yoğunluk haritası (`calculateMonthActivity`), 7 günlük hafta dilimleri (`getWeekDates`), tam takvim matrisi (`getMonthCalendarDays`) ve kullanıcı dostu Türkçe tarih etiketleri.
- [x] **İnteraktif Aylık / Haftalık Takvim Çubuğu (`CalendarAgendaBar`):** Ana ekranda filtre çubuğuna entegre "Ajanda" çipi, kompakt 7 günlük yatay kaydırma ve genişletilebilir tam aylık takvim ızgarası, notu olan günlerde renkli etkinlik noktaları, "Bugün" hızlı atlama butonu ve seçilen güne ait notları anında süzme.
- [x] **Birim Testleri:** `CalendarNoteGrouperTest` ile tarih eşleştirme, gün bazlı gruplama, sınır tarihleri, hafta başlangıç/bitiş günleri ve etkinlik yoğunluk testleri %100 doğrulandı.

### Aşama 35: Not Versiyon Karşılaştırma & Görsel Diff İnceleyici (Note Version Diff & Change Visualizer)
- [x] **Görsel Metin Fark Motoru (`NoteDiffEngine`):** İki not versiyonu veya mevcut içerik ile geçmiş versiyon arasındaki satır/kelime bazlı farkları (eklenen `+`, silinen `-`, korunan satırlar) hesaplama, LCS algoritması ve değişim istatistiği çıkarma (+X ekleme, -Y silme).
- [x] **Görsel Değişiklik İnceleme Penceresi (`NoteDiffDialog`):** `NoteHistoryBottomSheet` geçmiş listesinde her versiyon için "Farkı İncele (Diff)" butonu; yeşil/kırmızı zemin ve satır numaralarıyla vurgulanmış satır bazlı karşılaştırma ekranı, başlık farkı tespiti ve tek tıkla geri yükleme.
- [x] **Birim Testleri:** `NoteDiffEngineTest` ile ekleme, silme, değiştirme, başlık değişimi, boşluk koruma ve satır hizalama testleri %100 doğrulandı.

### Aşama 36: Akıllı Otomatik Kaydetme & Taslak Kurtarma Motoru (Auto-Save Resilience & Draft Recovery)
- [ ] **Korumalı Taslak Kurtarma Motoru (`DraftRecoveryManager`):** Kullanıcı not düzenlerken her değişiklikte güvenli yerel taslak oluşturma (`note_draft_recovery`), kaza eseri çıkış veya uygulama kapanması durumunda not açıldığında "Kaydedilmemiş taslak bulundu" uyarısı ve tek tıkla kurtarma seçeneği sunma.
- [ ] **Temizleme ve Güvenli Taslak Döngüsü:** Not başarıyla kaydedildiğinde veya kullanıcı taslağı bilinçli sildiğinde taslak önbelleğini otomatik temizleme.
- [ ] **Birim Testleri:** `DraftRecoveryManagerTest` ile taslak kaydetme, kurtarma uygunluğu, zaman aşımı ve temizleme testleri.

---

## 📌 İlerleme Günlüğü (Progress Log)

* **[Aşama 1-5 Tamamlandı]** Şablonlar, versiyon geçmişi geri yükleme, yazma istatistikleri ve analitik ekranı, Zen odaklanma modu ve sentetik ses motoru, Room v5 migrasyonu ve birim testler başarıyla entegre edildi.
* **[Aşama 6 Tamamlandı]** Etkileşimli filtre çipleri, not kartı kontrol ilerleme çubukları, stilize PDF motoru, Downloads klasörüne kaydetme ve renge göre sıralama özellikleri tamamlanıp test edildi.
* **[Aşama 7 Tamamlandı]** Çok kademeli bildirim erteleme eylemleri (15 dk & 1 saat), SQLite WAL checkpoint & VACUUM optimizasyon motoru ve Ayarlar bakım paneli devreye alındı.
* **[Aşama 8 Tamamlandı]** Günlük kelime hedefi takip kartı, not detay hızlı kilit ikonu, not çoğaltma ve birim testleri tamamlandı.
* **[Aşama 9 Tamamlandı]** Çoklu seçimde toplu renk değiştirme diyaloğu, toplu notları tek PDF dokümanında birleştirme motoru ve kategori atamaları tamamlandı.
* **[Aşama 10 Tamamlandı]** Ses oynatıcı kontrolleri (+5s/-5s sarma, 1.0x-2.0x değişken hız çarpanları) ve derleme doğrulaması tamamlandı.
* **[Aşama 11 Tamamlandı]** Çöp kutusu saklama/otomatik temizleme politikası, ayarlar paneli ve not tam ekran okuma/sunum modu, birim testleriyle birlikte tamamlandı.
* **[Aşama 12 Tamamlandı]** Arama geçmişi yönetimi, son aramalar çip şeridi ve not kartlarında anlık arama kelimesi vurgulama motoru birim testleriyle tamamlandı.
* **[Aşama 13 Tamamlandı]** Çoklu not birleştirme motoru (Note Merge Engine), onay diyaloğu, orijinal not saklama/çöpe taşıma politikası ve birim testleri tamamlandı.
* **[Aşama 14 Tamamlandı]** Hızlı H1/H2/H3 başlık ve ayraç araçları ile canlı okuma süresi tipografi HUD rozeti tamamlandı.
* **[Aşama 15 Tamamlandı]** Kategori oluşturma ve düzenlemede 8 estetik palet rengi, dinamik renkli kategori rozetleri ve birim testleri tamamlandı.
* **[Aşama 16 Tamamlandı]** Notu bildirim çubuğuna sabitleme (Sticky Note) ve modern stilize HTML dışa aktarma tamamlandı.
* **[Aşama 17 Tamamlandı]** Kilitli notlar için otomatik zaman aşımı politikası (Auto-Relock Timeout Policy) ve birim testleri tamamlandı.
* **[Aşama 18 Tamamlandı]** Not detayında bul ve değiştir motoru (FindReplaceEngine), FindAndReplaceBar, dinamik arama vurgulaması ve birim testleri tamamlandı.
* **[Aşama 19 Tamamlandı]** Okuma modunda satır yüksekliği kontrolü, akıllı çift sütun gazete düzeni (ReadingColumnSplitter) ve birim testleri tamamlandı.
* **[Aşama 20 Tamamlandı]** WorkManager periyodik çöp kutusu temizleyicisi (TrashCleanupWorker), anlık manuel tetikleme ve bağlı dosya temizliği tamamlandı.
* **[Aşama 21 Tamamlandı]** Tam kapsamlı ZIP/JSON yedekleme motoru (notlar, kategoriler, versiyon geçmişi, özel şablonlar, medya ekleri), güvenli geri yükleme ve birim testleri tamamlandı.
* **[Aşama 22 Tamamlandı]** El çizimi ve karalamaların kartlarda kırpılmadan Fit ölçeklemesi, yüksek kontrastlı arka planı, `🎨 Çizim` rozeti ve kompakt göstergeleri tamamlandı.
* **[Aşama 23 Tamamlandı]** Markdown tablo ayrıştırıcı motoru (MarkdownTableParser), görsel Material 3 tablo düzenleyicisi (MarkdownTableView), hücre/satır/sütun yönetimi ve birim testleri tamamlandı.
* **[Aşama 24 Tamamlandı]** Not içi canlı ses kaydı görselleştirici barı (AudioRecordingVisualizerBar), dokunmatik dalga formu sarıcısı (AudioWaveformScrubber), WaveformHelper motoru ve birim testleri tamamlandı.
* **[Aşama 25 Tamamlandı]** Android dinamik launcher kısayolları (AppShortcutManager), ana ekrana not sabitleme (pinNoteToHomeScreen), intent rota çözümleyici ve birim testleri tamamlandı.
* **[Aşama 26 Tamamlandı]** AES-256-GCM şifreli taşınabilir not kasası motoru (.notevault), dışa/içe aktarım diyalogları (ExportVaultDialog, ImportVaultDialog), BackupManager entegrasyonu ve birim testleri tamamlandı.
* **[Aşama 27 Tamamlandı]** Kontrol listesi canlı ilerleme halkası (ChecklistProgressRing), akıllı checklist sıralayıcı ve temizleyici (ChecklistSorter) ve birim testleri tamamlandı.
* **[Aşama 28 Tamamlandı]** Markdown tablo otomatik hizalayıcı motoru (prettifyTable, formatAllTablesInText), görsel sütun hizalama seçicisi ve silme eylemleri tamamlandı.
* **[Aşama 29 Tamamlandı]** Not başına özel kelime hedefi motoru (WordGoalCalculator, NoteGoalManager), canlı ilerleme rozeti (WordGoalProgressBadge), SetWordGoalDialog ve birim testleri tamamlandı.
* **[Aşama 30 Tamamlandı]** Görsel üzerine serbest çizim ve işaretleme (DrawingScaleHelper, DrawingScreen background canvas, AttachmentList ve tam ekran entegrasyonu) ve birim testleri tamamlandı.
* **[Aşama 31 Tamamlandı]** Akıllı etiket otomatik tamamlama (TagSuggestionEngine, TagAutocompleteBar), etiket yönetim alt sayfası (TagManagerBottomSheet), toplu etiket refaktörü/silme ve birim testleri tamamlandı.
* **[Aşama 32 Tamamlandı]** Çoklu görsel kolaj ızgarası (GalleryGridCalculator, AttachmentList), +X taşma rozeti, tam ekran sağa/sola kaydırmalı fotoğraf görüntüleyici (HorizontalPager) ve birim testleri tamamlandı.
* **[Aşama 33 Tamamlandı]** Akıllı not kopyalama paketi (ClipboardHelper, SmartCopyBottomSheet), düz metin, markdown, özet, görev listesi kopyalama, haptik titreşim ve birim testleri tamamlandı.
* **[Aşama 34 Tamamlandı]** Takvim & not ajandası görünümü (CalendarNoteGrouper, CalendarAgendaBar), haftalık/aylık etkinlik haritası, güne göre filtreleme ve birim testleri tamamlandı.
* **[Aşama 35 Tamamlandı]** Not versiyon karşılaştırma ve görsel diff inceleyici (NoteDiffEngine, NoteDiffDialog), satır bazlı ekleme/silme vurgulaması, istatistik rozetleri ve birim testleri tamamlandı.
