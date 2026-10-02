# Derinlemesine Özellik Analizi: Logseq
**Uygulama Adı:** Logseq  
**Geliştirici:** Logseq Inc. & Açık Kaynak Topluluğu  
**İlk Çıkış:** 2020  
**Kategori:** Yerel-Öncelikli Anahat Düzenleyici (Outliner), Günlük Odaklı Graf, Aralıklı Tekrar (Spaced Repetition)  

---

## 1. Temel Felsefe: "Günün Akışında Düşün, Atomik Bloklarla Bağla"

Logseq, Roam Research'ün başlattığı "Ağsal Düşünce" (Networked Thought) ve klasik anahat düzenleyicilerin (Workflowy) gücünü açık kaynaklı, yerel-öncelikli bir formatta birleştirir.

Felsefesi: **"Hiçbir zaman boş bir beyaz sayfayla karşılaşıp ne yazacağını düşünme; her gün Günlük (Journals) sayfasında başlar."**

Kullanıcı sabah uygulamayı açtığında bugünün tarihli sayfası hazırdır. Aklına gelen her madde girintili bir madde imi (Bullet point) olarak yazılır ve ilgili konulara çift yönlü etiketlerle bağlanır.

---

## 2. Mimari: Blok Seviyesinde Granülerlik

Logseq'te her satır bağımsız bir UUID'ye sahip bir **Bloktur**. Dosya sistemi düzeyinde standart Markdown (`.md`) veya Emacs Org-mode (`.org`) kullanılır.

```markdown
- Bugün [[Proje Alpha]] toplantısı yapıldı #toplantı
  - Alınan kararlar:
    - TODO Bütçe revizyonunu hazırla #acil
    - [[Ahmet]] API dokümantasyonunu gönderecek
```

```
+-------------------------------------------------------------+
|                      LOGSEQ BLOK MİMARİSİ                   |
+-------------------------------------------------------------+
|  Page (örn: journals/2026_10_02.md)                         |
|    ├── Block (UUID-01): Toplantı notu                       |
|    │     ├── Child Block (UUID-02): Alınan kararlar         |
|    │     │     ├── Child Block (UUID-03): TODO Bütçe        |
|    │     │     └── Child Block (UUID-04): [[Ahmet]] API     |
+-------------------------------------------------------------+
```

---

## 3. Detaylı Özellik İncelemesi

### A. Anahat Düzenleyici (Outliner Paradigm)
- Her satır bir maddedir (Bullet).
- `Tab` tuşu ile girintileme (Indent), `Shift + Tab` ile çıkıntı oluşturma (Outdent).
- Herhangi bir maddenin mermi simgesine tıklandığında ekran o maddeye odaklanır (Zoom In), alt dallar tek başına bir sayfa gibi genişler.
- Başlıkları daraltma (Collapse) ve açma (Expand) ile devasa notlar okunabilir tutulur.

### B. Günlük Odaklı İş Akışı (Daily Journals Workflow)
- Yeni not oluşturmak için klasör veya isim seçmeye gerek yoktur.
- Günlük sayfasında her şey kronolojik olarak akar.
- İçerik içinde `[[Müşteri A]]` veya `[[Yapay Zeka]]` dendiğinde, o sayfaların geri bağlantılarına (Backlinks) bu günlük notları otomatik olarak dökülür.

### C. Flashcard ve Aralıklı Tekrar (Spaced Repetition Flashcards)
- Öğrenilen bir bilginin sonuna `#card` etiketi eklendiğinde Logseq bunu otomatik olarak bir öğrenme kartına dönüştürür.
- Yerleşik **SuperMemo SM-2** algoritması ile kullanıcıya aralıklı tekrar seansları sunar:
  - "Kolay", "Orta", "Zor" veya "Unuttum" seçenekleriyle hatırlama periyotları optimize edilir.

### D. Gelişmiş Datalog ve SQL Sorguları
Kullanıcı notlarının içine dinamik arama tabloları gömebilir:
```clojure
{{query (and (task TODO DOING) [[Proje Alpha]])}}
```
Bu sorgu kasa içindeki tüm "Proje Alpha" ile ilgili aktif görevleri anında çeker.

### E. PDF Ek Açıklamaları ve Derin Alıntılar
- Not içine bir PDF yüklendiğinde, PDF yan panelde açılır.
- PDF içindeki bir cümlenin altı çizildiğinde, o cümlenin tam sayfa ve koordinat referansı not içine bir blok bağlantısı olarak yapışır. Tıklandığında PDF tam o satıra zıplar.

---

## 4. Güçlü ve Zayıf Yönler Analizi

### Güçlü Yönler:
- Günlük iş akışı sayesinde sıfır karar yorgunluğu (decision fatigue).
- Blok seviyesinde referans verebilme (`((block-id))`).
- Dahili flaş kart ve aralıklı tekrar sistemi.
- %100 yerel, gizlilik odaklı ve açık kaynak.

### Zayıf Yönler:
- Geleneksel uzun makale ve doküman yazımı için outliner (maddeli) format herkes için uygun değildir.
- Mobil performansı büyük kasalarda yavaşlayabilir (Graph indexleme süresi).
- Datalog sorguları ileri düzey teknik bilgi gerektirir.

---

## 5. NoteApp İçin Çıkarılan Dersler ve Uygulama Mimarisi

NoteApp için Logseq'ten aktarılacak devrimsel fikirler:
1. **Tek Dokunuşla "Günün Notu" (Daily Journal Capture):** Ana ekranda "Bugünün Notu" aksiyonu. Eğer bugünün tarihli (`YYYY-MM-DD`) notu varsa onu açar, yoksa şablonla otomatik oluşturur.
2. **Hızlı Görev Döngüsü:** Kontrol listesi maddelerinde tek dokunuşla TODO -> DOING -> DONE durum döngüsü desteği.
3. **Maddeli Girintileme Kolaylığı:** Zengin metin araç çubuğunda madde imlerini hızlıca girintileme/çıkıntılama desteği.
