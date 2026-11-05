# 🚀 Not Defterim v2.0 - 5 Büyük Yenilikçi Özellik Gereksinim Şartnamesi (REQUIREMENTS_V2)

**Doküman Versiyonu:** 2.0.0  
**Tarih:** 05.11.2026  
**Rol:** Kıdemli Gereksinim Mühendisi (`requirements-analyst`)  
**Hedef Platformlar:**  
1. **Masaüstü (Desktop-Native):** Rust (Backend/IPC) + Go (LAN Engine) + Wry/Web (UI)  
2. **Mobil (Android):** Kotlin + Jetpack Compose + Room v6 (Architecture Components)  
**Tasarım Prensibi:** %100 Çevrimdışı Çalışabilirlik (Offline-First), Sıfır Veri Kaybı, Askeri Düzey Güvenlik (AES-256-GCM), Platformlar Arası Tam Semantik Eşdeğerlik.

---

## 📑 Yönetici Özeti ve Özellik Matrisi

| # | Modül / Özellik Adı | Masaüstü Çözüm Mimarisi (Rust/Wry) | Android Çözüm Mimarisi (Compose/Room) | Kritik Bağımlılık & Protokol |
|---|---|---|---|---|
| **1** | **İnteraktif Not İlişki Grafiği (Graph View)** | Rust Link Parser + Wry Canvas Force-Directed Engine | `NoteLinkGraphEngine` + Compose Canvas / Touch Gestures | `[[Wikilink]]` çift yönlü komşuluk matrisi |
| **2** | **Command Palette (Ctrl+K / Cmd+K)** | Global Win32/X11 Hook + Wry Spotlight Overlay | Compose Modal Search Bar + Klavye Kısayolu | Fuzzy Arama + Modüler Eylem Veri Yolu |
| **3** | **Sticky Note / Yapışkan Not Pencere Modu** | Rust Frameless Window (`set_always_on_top`) | Android PiP (Picture-in-Picture) + Compose Overlay | IPC `window:sticky` + Hızlı Senkron |
| **4** | **Matematik & Diyagram (KaTeX & Mermaid)** | Yerel Vendor JS/CSS Bundle (`katex.min.js`, `mermaid.js`) | Offline Asset WebView / SVG Canvas Adapter | LaTeX (`$$...$$`) & Mermaid Code Fence (` ```mermaid `) |
| **5** | **Gelişmiş PDF & Yazdırma Motoru** | CSS `@media print` + Wry `window.print()` / Native PDF | `PrintDocumentAdapter` + Vektörel PDF Çıktısı | Yazdırılabilir A4 Tipografi & Sayfa Bölme |

---

## 1. 🕸️ Modül 1: İnteraktif Not İlişki Grafiği (Graph View)

### 1.1 Amaç ve Kapsam
Notlar arasındaki Obsidian/Logseq tarzı çift yönlü bağlantıların (`[[Not Başlığı]]`) ve etiketlerin (`#etiket`) görsel bir bilgi ağı (Knowledge Graph) olarak modellenmesi ve etkileşimli olarak incelenmesi.

### 1.2 Veri Modeli ve Tipler
```rust
// Masaüstü (Rust Data Model)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphNode {
    pub id: i64,
    pub title: String,
    pub category_id: Option<i64>,
    pub category_color: Option<i32>,
    pub link_count: usize, // Düğüm boyutu için derece
    pub is_locked: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphEdge {
    pub source_id: i64,
    pub target_id: i64,
    pub label: Option<String>, // Alias veya bölüm referansı
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphDataResponse {
    pub nodes: Vec<GraphNode>,
    pub edges: Vec<GraphEdge>,
    pub isolated_count: usize, // Yetim/bağlantısız not sayısı
}
```

```kotlin
// Android (Kotlin Data Model)
data class GraphNode(
    val id: Long,
    val title: String,
    val categoryId: Long?,
    val color: Int,
    val connectionCount: Int,
    val isLocked: Boolean
)

data class GraphEdge(
    val sourceId: Long,
    val targetId: Long,
    val label: String? = null
)

data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)
```

### 1.3 Mimari Gereksinimler

#### A. Masaüstü (Rust + Wry UI)
- **Rust Katmanı (`desktop-native/src/`):**
  - `graph::build_global_graph(&db_conn) -> Result<GraphDataResponse>` fonksiyonu.
  - Regex: `\[\[([^\]\|#]+)(?:#[^\]\|]+)?(?:\|([^\]]+))?\]\]`
  - Başlık eşleştirme: Case-insensitive ve Unicode normalize (Türkçe `İ/i`, `I/ı` uyumlu).
  - IPC Endpoint: `graph:get_global` ve `graph:get_local { note_id: i64, depth: u8 }`.
- **Wry Frontend Katmanı (`desktop-native/ui/`):**
  - Zero-dependency veya ultra hafif Force-Directed Canvas simülatörü (Coulomb itme + Hooke yay çekme).
  - Etkileşimler:
    - **Pan & Zoom:** Fare tekerleği ve sürükleme.
    - **Hover:** Vurgulanan düğümün 1. derece komşuları parlar, alakasız düğümler kararır (%30 opaklık).
    - **Click:** Seçilen not ana düzenleyiciye yüklenir ve açılır.
    - **Filtreler:** Kategoriye göre renklendirme, Yetim (bağlantısız) düğümleri gizleme/gösterme, etiket filtreleri.

#### B. Android (Kotlin Compose + Room)
- **Engine Katmanı:** `NoteLinkGraphEngine.kt` genişletilerek tüm Room veritabanından tek seferde komşuluk matrisi (Adjacency List) çıkarır.
- **UI Katmanı:** Tam ekran `NoteGraphScreen` veya `NoteLinkGraphBottomSheet`.
- **Compose Canvas Simülasyonu:**
  - `PointerInputScope.detectTransformGestures` ile iki parmak pinch-to-zoom ve pan.
  - Arka plan `LaunchedEffect` içinde Euler entegrasyonlu fizik motoru (60 FPS pürüzsüz yerleşim).
  - Kilitli notlar için kilit simgeli özel düğüm görseli.

---

## 2. ⚡ Modül 2: Command Palette (Ctrl+K / Cmd+K Hızlı Komut Paleti)

### 2.1 Amaç ve Kapsam
Kullanıcının klavyeden elini kaldırmadan arama yapmasını, mod değiştirmesini, kasa işlemlerini yürütmesini ve sistem ayarlarını tetiklemesini sağlayan Spotlight/Raycast tarzı merkezi komut arayüzü.

### 2.2 Komut ve Eylem Şeması
```typescript
interface CommandItem {
  id: string;
  category: 'ACTION' | 'NOTE' | 'TAG' | 'CATEGORY' | 'THEME';
  title: string;
  subtitle?: string;
  shortcut?: string;
  icon: string;
  action: () => void | Promise<void>;
  keywords: string[];
}
```

### 2.3 Standart Eylem Kataloğu
1. **Sistem & Not Eylemleri (`>`):**
   - `> Yeni Not Oluştur` (`Ctrl+N`)
   - `> Kasayı Kilitle / PIN ile Koru` (`Ctrl+L`)
   - `> Yapışkan Not Modunu Başlat` (`Ctrl+Shift+S`)
   - `> Wi-Fi Senkronizasyonu Başlat / Durdur`
   - `> Notu Dışa Aktar (PDF / Kasa / MD)`
   - `> Çöp Kutusunu Görüntüle / Boşalt`
2. **Tema & Renk Paleti Değiştirme:**
   - Tema Seçimi: `Açık`, `Koyu`, `Sistem Varsayılanı`
   - Renk Paletleri: `Sıcak Kehribar`, `Gece Gülü`, `Zümrüt Ormanı`, `Derin Okyanus`, `Minimal Arduvaz`, `Buzul Mavisi`
3. **Etiket Filtreleme (`#`):** `#is`, `#toplanti`, `#fikir` yazıldığında listeyi anında daraltma.
4. **Kategoriye Atlama (`@`):** `@Kişisel`, `@İş`, `@Finans`.
5. **Fuzzy Not Arama (Düz Metin):** Not başlıklarında ve içeriklerinde harf hatası tolere eden anlık eşleme.

### 2.4 Mimari Gereksinimler

#### A. Masaüstü (Rust/Wry)
- Pencere olay dinleyicisi: Global `keydown` dinleyici `(e.ctrlKey || e.metaKey) && e.key === 'k'`.
- UI: Ekranın üst-orta kısmında yüzen (floating), arkası `backdrop-filter: blur(12px)` ile bulanıklaştırılmış modern modal.
- Klavye Gezinmesi: `Yukarı / Aşağı` ok tuşları, `Enter` (Seç/Çalıştır), `Esc` (Kapat), `Backspace` (Ön eki temizle).

#### B. Android (Kotlin Compose)
- Üst AppBar üzerinde özel `QuickCommandButton` (Yıldırım ikonu) ve harici Bluetooth/USB klavye için `Modifier.onPreviewKeyEvent` (`Key.K` + `isCtrlPressed`).
- `AnimatedVisibility` ile yukarıdan aşağıya kayarak açılan `CommandPaletteSheet`.

---

## 3. 📌 Modül 3: Sticky Note / Yapışkan Not Pencere Modu

### 3.1 Amaç ve Kapsam
Masaüstünde diğer tüm pencerelerin üzerinde kalabilen (Always-on-Top), çerçevesiz (frameless), yarı saydamlık ayarı yapılabilen, post-it boyutunda odaklanmış mini not düzenleyicisi.

### 3.2 IPC ve Native Pencere Kontratı (Masaüstü)
```rust
// Rust IPC Action Tanımları
"window:spawn_sticky" => {
    // Parametreler: { note_id: Option<i64>, color: Option<i32> }
    // Yeni bir wry::WebView veya mevcut pencereyi mini boyuta çekme
}
"window:set_always_on_top" => {
    // Parametreler: { enabled: bool }
    // winapi::SetWindowPos(HWND_TOPMOST) veya Wry API
}
"window:set_opacity" => {
    // Parametreler: { opacity: f32 } // 0.5 - 1.0 arası
    // SetLayeredWindowAttributes ile saydamlık
}
"window:close_sticky" => {
    // Otomatik kaydetme sonrası pencereyi kapatma
}
```

### 3.3 Mimari Gereksinimler

#### A. Masaüstü (Rust/Wry)
- **Pencere Geometrisi:** Başlangıç boyutu `340x380 px`, minimum `260x260 px`.
- **Çerçevesiz Tasarım:** İşletim sistemi pencere başlığı gizlenir (`decorations: false`).
- **Özel Drag Bar:** CSS `-webkit-app-region: drag` veya Rust fare olaylarıyla sürükleme alanı.
- **Kontrol Çubuğu:**
  - 📌 Always-on-Top raptiye butonu (Aktif/Pasif toggle).
  - 🎨 Renk paleti seçici (Post-it sarısı, nane yeşili, pembe, koyu grafit).
  - 🔍 Saydamlık kaydırıcı (%60 - %100).
  - ✖ Kapat ve ana uygulamaya geri dön.
- **Otomatik Kaydetme:** Her tuş vuruşunda 400ms debounce ile SQLite veritabanına anlık yazma.

#### B. Android Karşılığı (Mobile Adaptation)
- **Picture-in-Picture (PiP) Modu:** Video oynatıcı yerine metin notunu içeren mini PiP aktivitesi (`enterPictureInPictureMode`).
- **Floating Overlay (Kayan Not Balonu):** Android `SYSTEM_ALERT_WINDOW` yetkisiyle ekranın üzerinde yüzen, dokunulduğunda mini düzenleyici açan floating bubble.
- **Quick Settings Tile:** Bildirim çubuğundan tek tıkla doğrudan mini not kaydetme diyaloğu (`QuickNoteTileService`).

---

## 4. 📐 Modül 4: Matematik & Diyagram Desteği (KaTeX & Mermaid Entegrasyonu)

### 4.1 Amaç ve Kapsam
Mühendislik, akademik ve teknik notlar için LaTeX sözdizimli matematik formüllerinin ve Mermaid tabanlı grafik/şemaların çevrimdışı, bağımsız ve yüksek performansla render edilmesi.

### 4.2 Sözdizimi Spesifikasyonu
1. **Satır İçi Matematik (Inline Math):**
   `$E = mc^2$` veya `$\lambda = \frac{h}{p}$`
2. **Blok Matematik (Display Math):**
   ```latex
   $$
   f(x) = \int_{-\infty}^\infty \hat{f}(\xi)\,e^{2 \pi i \xi x}\,d\xi
   $$
   ```
3. **Mermaid Diyagram Bloğu:**
   ````markdown
   ```mermaid
   graph TD
       A[Android App] -->|Wi-Fi Port 8765| B(Rust Desktop)
       B --> C{PIN Doğrulama}
       C -->|Başarılı| D[Room/SQLite Senkronizasyonu]
       C -->|Hatalı| E[401 Unauthorized]
   ```
   ````

### 4.3 Mimari Gereksinimler

#### A. Masaüstü (Wry / Web UI)
- **Vendor Paketleme:** CDN bağımlılığı KESİNLİKLE YASAKTIR.
  - `desktop-native/ui/vendor/katex/katex.min.js` + `katex.min.css` + WOFF2 yazı tipleri.
  - `desktop-native/ui/vendor/mermaid/mermaid.min.js`.
- **Render Döngüsü:**
  - Markdown parse edildikten sonra HTML AST üzerinde `code.language-mermaid` blokları taranır.
  - `mermaid.render()` fonksiyonu SVG çıktısını dinamik olarak oluşturur.
  - Hatalı sözdiziminde uygulama çökmez; kırmızı renkli zarif bir hata uyarısı gösterilir (`SyntaxError: Invalid Mermaid Definition`).
  - Matematik blokları `renderMathInElement` ile taranır.

#### B. Android (Kotlin Compose)
- **MathFormulaBottomSheet:** Kullanıcıya hazır şablonlar sunar (Kök, Kesir, Matris, Türev, İntegral).
- **İzole WebView Renderer (`MarkdownKaTeXMermaidView`):**
  - Android yerel TextView KaTeX/Mermaid SVG'lerini doğrudan çizemez; bu nedenle `app/src/main/assets/editor/` altında yerel HTML şablonu barındırılır.
  - Sayfa içine gömülen JS köprüsü üzerinden Compose metin değişiklikleri anlık aktarılır.
  - Gece/Gündüz teması WebView'e CSS değişkeni olarak enjekte edilir (`prefers-color-scheme`).

---

## 5. 🖨️ Modül 5: Gelişmiş PDF & Yazdırma Dışa Aktarımı

### 5.1 Amaç ve Kapsam
Notların başlık, etiketler, oluşturulma/düzenleme tarihi, zengin Markdown formatları, KaTeX formülleri, Mermaid şemaları ve medya ekleriyle birlikte baskıya hazır, standart A4/Letter PDF belgelerine dönüştürülmesi ve yazıcıya gönderilmesi.

### 5.2 Tipografi & Sayfa Düzeni Standartları
- **Kağıt Boyutları:** A4 (`210 x 297 mm`) ve US Letter (`8.5 x 11 inç`).
- **Kenar Boşlukları (Margins):**
  - Üst / Alt: `20 mm`
  - Sol / Sağ: `15 mm`
- **Sayfa Numaralandırma:** Alt bilgi (Footer) merkezinde `Sayfa X / Y` ve sağ tarafta `Not Defterim Güvenli Belge`.
- **Kapak / Başlık Bloğu:** Not başlığı (`24pt Bold`), Kategori Rozeti, Oluşturulma & Güncellenme Zaman Damgası, Kelime Sayısı.
- **Sayfa Bölme Kuralları (Page Break Optimization):**
  - `h1`, `h2`, `h3` başlıklarının hemen altından sayfa bölünmesi engellenir (`break-after: avoid; page-break-after: avoid;`).
  - Tablolar, kod blokları ve görsel kartlar sayfa ortasında kesilmez (`break-inside: avoid; page-break-inside: avoid;`).

### 5.3 Mimari Gereksinimler

#### A. Masaüstü (Rust/Wry)
- **Baskı CSS Katmanı (`@media print`):**
  - Yazdırma sırasında kenar çubukları, butonlar, arama alanları, pencere kontrolleri tamamen gizlenir (`display: none !important`).
  - Arka plan rengi varsayılan olarak beyaz (`#FFFFFF`), metin rengi yüksek kontrastlı siyah (`#1A1A1A`) yapılır.
- **Tetikleme:** Wry üzerinden `window.print()` çağrısı veya Rust tarafında PDF dosyası olarak kaydetme diyaloğu (`nativefiledialog`).

#### B. Android (Kotlin Compose + Android PrintManager)
- **Mevcut Altyapı:** `NoteExporter.kt` içindeki `exportToPdf` genişletilir.
- **Android Baskı Altyapısı Entegrasyonu:**
  - `PrintManager` servisi ve `PrintDocumentAdapter` kullanımı.
  - Vektörel PDF motoru: `PdfDocument` üzerinden `Canvas` ile metin ve resimlerin çizilmesi.
  - Kullanıcıya sistemin standart "Yazıcı Seç / PDF Olarak Kaydet" ekranı gösterilir.

---

## 6. 🔒 Güvenlik, Veri Bütünlüğü ve Sınır Durumlar (Edge Cases)

| Olay / Sınır Durum | Masaüstü Çözüm Stratejisi | Android Çözüm Stratejisi |
|---|---|---|
| **Kilitli Notun Grafiğe Dahil Edilmesi** | Kilitli notun başlığı grafikte gösterilir ancak içeriğindeki gizli wikilink'ler ve etiketler kasa açılana kadar taranmaz. | Kilitli notlar için `isLocked = true` rozetli nötr düğüm gösterilir. |
| **Bozuk Mermaid / LaTeX Kodu** | UI çökmesi `try-catch` ile izole edilir; yerine sarı/kırmızı uyarı kutusu basılır. | WebView JS hata yakalayıcısı (ConsoleMessage) hatayı yakalar ve fallback kod metnini basar. |
| **Sticky Note Çökme Koruması** | Her karakter değişiminde taslak SQLite `note_drafts` tablosuna yazılır. | Room `NoteDraftEntity` kullanılarak ani kapanmalarda içerik kurtarılır. |
| **Command Palette Hızlı Yazma (Lag)** | Fuzzy search işlemi 50ms debouncelenir ve WebWorker / Rust thread pool üzerinde çalışır. | Kotlin Coroutines `Flow.debounce(100)` ile UI thread rahatlatılır. |
| **Çok Büyük Notların PDF'e Aktarımı** | CSS pagination motoru belleği şişirmeden streaming render yapar. | Android `PrintedPdfDocument` sayfa sayfa oluşturularak OOM (Out Of Memory) önlenir. |

---

## 7. 📅 Uygulama ve Doğrulama Yol Haritası (Faz Planı)

- [ ] **Faz 1: Veri ve Çekirdek Parser:** Rust ve Kotlin için `[[Wikilink]]` ve `#etiket` ayrıştırma testlerinin tamamlanması.
- [ ] **Faz 2: UI Bileşenlerinin İnşası:** Wry ve Compose üzerinde Command Palette ve Graph Canvas çizim prototipleri.
- [ ] **Faz 3: Çevrimdışı Kütüphanelerin Entegrasyonu:** KaTeX & Mermaid yerel asset bundle'larının projeye eklenmesi.
- [ ] **Faz 4: Sticky Note & PDF Baskı:** Win32/X11 Always-on-Top pencere altyapısı ve CSS print şablonlarının optimizasyonu.
- [ ] **Faz 5: Entegrasyon, Test ve Doğrulama:** LAN senkronizasyonu ile yeni özelliklerin cihazlar arasında sıfır kayıpla aktarım testi.
