# NoteApp Otonom İlerleme Yol Haritası ve Mimari Standartlar
**Hazırlayan:** Antigravity Autonomous Progression Engine  
**Hedef:** Küresel 10 Not Alma Uygulamasının En İyilerini Tek Bir Çatıda Birleştirmek  

---

## 1. Vizyon ve Mimari Strateji

NoteApp, piyasadaki tek tip uygulamaların handikaplarını aşmak üzere **"Hibrit Mobil Güç Merkezi"** mimarisini benimsemiştir:
1. **Google Keep'ten:** Sıfır gecikmeli renkli kartlar, 12 pastel tema, çok boyutlu widget'lar, anlık hızlı yakalama.
2. **Obsidian & Logseq'ten:** Çift yönlü bağlantılar (`[[Not Başlığı]]`), geri bağlantılar (Backlinks), bağlantısız bahsetmeler ve günün notu (Daily Journal).
3. **Bear'den:** Metin içi etiket çıkarma (`#etiket`, `#iç-içe/etiket`), zarif tipografi ve kelime/okuma süresi istatistikleri.
4. **Apple Notes'tan:** Akıllı dinamik filtreler (Kilitli, Görselli, Sesli, Hatırlatıcılı), tam siyah gizlilik kalkanı ve biyometrik güvenlik.
5. **Joplin'den:** Yerel-öncelikli Room DB, AES-256-GCM kilitli kasa ve evrensel Markdown (`.md`) dışa aktarma / paylaşma.
6. **Samsung Notes & OneNote'tan:** Serbest çizim kanvası, resim üzerine çizim/metin işleme ve zaman damgalı ses işaretleri (`[MM:SS]`).
7. **Evernote'tan:** Cihaz üzerinde (On-device) gizlilik dostu ML Kit OCR metin çıkarma.

---

## 2. Otonom İlerleme Motoru (scripts/progress_engine.py)

Projenin kendi kendine ilerlemesini sağlamak ve araştırma hedeflerine uyumunu sürekli denetlemek için `scripts/progress_engine.py` geliştirilmiştir.

### Çalışma Modları:
```bash
# Kod tabanını araştırma kriterlerine göre denetler ve olgunluk skorunu hesaplar:
python3 scripts/progress_engine.py --audit

# Sıradaki en yüksek getirili (ROI) özelliği ve mimari yönergeleri belirler:
python3 scripts/progress_engine.py --next
```

---

## 3. Fazlandırılmış Kilometre Taşları (Milestones)

### Faz 1: Hızlı Yakalama & Multimodal Çekirdek (TAMAMLANDI ✅)
- [x] Room SQLite yerel depolama
- [x] Masonry mizanpaj ve 12 Keep pastel rengi
- [x] Ses kaydı ve çalma (AudioHelper)
- [x] Serbest çizim kanvası (DrawingScreen)
- [x] Resim üzeri çizim ve işaretleme (ImageEditScreen)
- [x] Google ML Kit yerel OCR (OcrHelper)
- [x] Biyometrik ve Master PIN koruması (NoteCryptoManager)
- [x] 1x1, 3x2 ve kaydırılabilir Android Widget'ları
- [x] Markdown biçimlendirme araç çubuğu ve önizleme

### Faz 2: Ağsal Bilgi & Dinamik Organizasyon (TAMAMLANDI ✅)
- [x] Çift yönlü bağlantılar (`[[Not Başlığı]]` Wikilinks)
- [x] Otomatik hedef not bulma veya yeni not oluşturup zıplama
- [x] Bu nota referans verenlerin listesi (Geri Bağlantılar / Backlinks)
- [x] Metin içi `#etiket` tespiti ve dinamik hashtag filtre çipleri
- [x] Günün Notu (Daily Journal) tek tıkla şablonlu oluşturma
- [x] Apple Notes tarzı akıllı filtreler (Sabitli, Kilitli, Görselli, Sesli, Hatırlatıcılı)
- [x] Evrensel Markdown (`.md`) dışa aktarma ve paylaşma
- [x] Canlı okuma süresi, sözcük ve karakter sayacı

### Faz 3: İleri Düzey Senkronizasyon & Görsel Graf (GELECEK PLAN)
- [ ] 2 Boyutlu İnteraktif Bilgi Grafı (Graph View) Canvas modülü
- [ ] WebDAV / Nextcloud tabanlı E2EE bulut senkronizasyonu
- [ ] Zip formatında toplu Kasa (Vault) dışa aktarımı / içe aktarımı
- [ ] Flaş kart (Spaced Repetition Flashcards) çalışma modu
