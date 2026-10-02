# Derinlemesine Özellik Analizi: Obsidian
**Uygulama Adı:** Obsidian  
**Geliştirici:** Dynalist Inc. (Shida Li & Erica Xu)  
**İlk Çıkış:** Mart 2020  
**Kategori:** Kişisel Bilgi Yönetimi (PKM), İkinci Beyin (Second Brain), Yerel-Öncelikli Graf Notları  

---

## 1. Temel Felsefe ve "Local-First" İlkeleri

Obsidian'ın temel felsefesi iki temel slogana dayanır:
1. **"Düşünceleriniz size aittir (You own your data):"** Hiçbir tescilli veritabanı veya bulut kilidi yoktur. Her not kullanıcının cihazındaki bağımsız bir `.md` (Markdown) dosyasıdır.
2. **"Birbirine bağlı düşünce (Connected thought):"** Notlar izole adacıklar değil, birbirine bağlanan sinapslar gibi çalışmalıdır. İnsan beyni klasörlerle değil, çağrışımlarla düşünür.

---

## 2. Mimari ve Depolama Modeli

Obsidian bir Electron/Capacitor uygulamasından çok bir **"Markdown Dosya Sistemi Arayüzü"**dür.

```
+-------------------------------------------------------------+
|                      OBSIDIAN KASA (VAULT) YAPISI            |
+-------------------------------------------------------------+
| /MyVault/                                                   |
|   ├── .obsidian/              # Ayarlar, eklentiler, temalar|
|   ├── Günlükler/                                            |
|   │     └── 2026-10-02.md     # Günlük not                  |
|   ├── Kavramlar/                                            |
|   │     ├── Zettelkasten.md   # Atomik not                  |
|   │     └── Yazılım_Mimarisi.md                             |
|   └── assets/                 # Görseller, sesler, PDF'ler  |
+-------------------------------------------------------------+
```

### Dosya Formatı ve Metadata:
Her not standart CommonMark + GitHub Flavored Markdown (GFM) biçimindedir. Notun en başında YAML formatında `Frontmatter` bulunur:

```markdown
---
id: 1042
title: "Mikroservis Mimarileri"
tags: [yazılım, mimari, backend]
aliases: [Microservices, Dağıtık Sistemler]
created: 2026-10-02 11:28
status: in-progress
---

# Mikroservis Mimarileri
Mikroservisler [[Dağıtık Sistemler]] teorisine dayanır.
Bkz: [[Docker ve Konteynerizasyon#Ağ Yönetimi|Konteyner Ağları]]
```

---

## 3. Detaylı Özellik İncelemesi

### A. Çift Yönlü Bağlantılar (Bidirectional Wikilinks)
- **Sözdizimi:** `[[Hedef Not İsmi]]` yazıldığında sistem anında diğer notlar arasında otomatik tamamlama sunar.
- **Başlık Bağlantısı:** `[[Hedef Not#Bölüm Başlığı]]` ile notun doğrudan belirli bir başlığına zıplanabilir.
- **Takma Ad (Alias):** `[[Hedef Not|Görünen İsim]]` ile cümlenin akışına uygun kelimeler hedeflenebilir.
- **İçerik Gömme (Embed):** `![[Hedef Not]]` ile başka bir notun içeriği veya görseli mevcut notun içine dinamik olarak gömülebilir.

### B. Geri Bağlantılar ve Bağlantısız Bahsetmeler (Backlinks & Unlinked Mentions)
- Her notun altında veya sağ panelinde **Geri Bağlantılar (Backlinks)** listelenir: *"Bu nota başka hangi notlar referans veriyor?"*
- **Bağlantısız Bahsetmeler:** Eğer başka bir notta bu notun başlığı düz metin olarak geçiyorsa Obsidian bunu tespit eder ve tek tıkla `[[...]]` wikilink'e dönüştürme önerisi sunar.

### C. İnteraktif Bilgi Grafı (Interactive Knowledge Graph)
- Tüm not kasasını (Vault) fizik motoruna sahip 2 boyutlu veya 3 boyutlu dinamik bir graf olarak görselleştirir.
- Her not bir düğüm (node), her wikilink ise bir kenardır (edge).
- Etiketlere, klasörlere ve bağlantı derinliğine göre renklendirme ve filtreleme.
- **Yerel Graf (Local Graph):** Yalnızca açık olan notun 1 ila 5 adım uzaklığındaki komşularını göstererek odaklanmayı sağlar.

### D. Kanvas (Obsidian Canvas)
- Sonsuz beyaz tahta üzerinde notları, resimleri, web sayfalarını ve PDF sayfalarını mekansal olarak yerleştirme, oklarla bağlama ve gruplama imkanı.

### E. Eklenti Ekosistemi (Community Plugins)
Obsidian, JavaScript/TypeScript tabanlı açık API'siyle devasa bir eklenti mağazasına sahiptir:
1. **Dataview:** Not kasasını SQL benzeri sorgulanabilir bir veritabanına dönüştürür (`TABLE file.mtime WHERE contains(tags, "proje")`).
2. **Templater:** Dinamik JavaScript şablonları oluşturur.
3. **Excalidraw:** Notlar içine el çizimleri ve diyagramlar entegre eder.

---

## 4. Güçlü ve Zayıf Yönler Analizi

### Güçlü Yönler:
- %100 Geleceğe Güvenli (Future-proof): Şirket batsa dahi tüm notlar standart metin dosyaları olarak bilgisayarınızda kalır.
- Olağanüstü zengin düşünce ağı ve ikinci beyin metodolojisi.
- İnternetsiz çalışma ve sıfır gecikme (zero-latency).
- Güçlü Markdown desteği ve esneklik.

### Zayıf Yönler:
- Yeni başlayanlar için dik öğrenme eğrisi (steep learning curve).
- Mobil uygulamada senkronizasyon (Obsidian Sync ücretlidir veya Git/Syncthing kurulumu teknik bilgi ister).
- Mobil cihazda anlık hızlı yakalama (quick capture) hızı Google Keep'e göre yavaştır.

---

## 5. NoteApp İçin Çıkarılan Dersler ve Uygulama Mimarisi

NoteApp projesine Obsidian'dan entegre edilecek kritik inovasyonlar:
1. **`[[Not Başlığı]]` Wikilink Motoru:** Not içeriğindeki çift köşeli parantezleri regex ile algılayıp, tıklanabilir hale getirmek ve hedef nota anında zıplama sağlamak.
2. **Geri Bağlantılar (Backlinks) Çekmecesi:** Bir not açıldığında, o nota referans veren diğer tüm notları SQLite `LIKE '%[[' || title || ']]%'` sorgusuyla bulup alt panelde listelemek.
3. **Markdown Evrenselliği:** Notları `.md` formatında dışa aktararak Obsidian kasasına aktarılabilir hale getirmek.
