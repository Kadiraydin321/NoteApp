import sqlite3
import time
import os

db_paths = [
    "/home/birin/.notdefterim/notes_desktop.db",
    "/mnt/c/Users/Birin/AppData/Roaming/NotDefterim/notes_desktop.db"
]

for p in db_paths:
    os.makedirs(os.path.dirname(p), exist_ok=True)

def to_int32(hex_val):
    val = int(hex_val, 16)
    if val >= 0x80000000:
        val -= 0x100000000
    return val

c_blue = to_int32("0xFF3B82F6")
c_amber = to_int32("0xFFF59E0B")
c_emerald = to_int32("0xFF10B981")
c_purple = to_int32("0xFF8B5CF6")

n_night_blue = to_int32("0xFF1E293B")
n_emerald = to_int32("0xFF1F2E2B")
n_warm_amber = to_int32("0xFF332A1E")
n_lavender = to_int32("0xFF2A2035")
n_ocean_teal = to_int32("0xFF1E2E38")

categories_data = [
    ("İş & Projeler", c_blue),
    ("Fikirler & Tasarım", c_amber),
    ("Kişisel & Alışveriş", c_emerald),
    ("Kitap & Öğrenme", c_purple),
]

now = int(time.time() * 1000)

for p in db_paths:
    conn = sqlite3.connect(p)
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            color INTEGER NOT NULL DEFAULT 0
        );
    """)
    c.execute("""
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            color INTEGER NOT NULL DEFAULT 0,
            categoryId INTEGER,
            isPinned INTEGER NOT NULL DEFAULT 0,
            isArchived INTEGER NOT NULL DEFAULT 0,
            isDeleted INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            isLocked INTEGER NOT NULL DEFAULT 0,
            reminderTime INTEGER,
            attachments TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL DEFAULT 0,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            backgroundImage TEXT,
            FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
        );
    """)

    # Clean empty notes
    c.execute("DELETE FROM notes WHERE title = '' AND content = ''")

    # Categories
    c.execute("SELECT id, name FROM categories")
    cat_map = {name: id for id, name in c.fetchall()}
    for name, color in categories_data:
        if name not in cat_map:
            c.execute("INSERT INTO categories (name, color) VALUES (?, ?)", (name, color))
            cat_map[name] = c.lastrowid

    notes_data = [
        (
            "🚀 Masaüstü Sürümü Yol Haritası ve Görevler",
            """# Not Defterim Masaüstü Sürümü (v1.0)

Not Defterim'in Windows & Linux platformları için geliştirilen modern masaüstü sürümüne hoş geldiniz! Android uygulamamız ile **%100 veri ve şifreleme uyumludur**.

## 📌 Tamamlanan Temel Özellikler
- [x] Modern 3 panelli arayüz (Kategori menüsü, Not listesi, Markdown editör)
- [x] Kesintisiz zengin metin düzenleme ve Canlı Önizleme
- [x] Yerel Wi-Fi ağı üzerinden QR kod ve PIN ile anlık senkronizasyon (Port: 8765)
- [x] Win32 Native Anti-Screenshot (Ekran Görüntüsü ve Kayıt Koruması)
- [x] Askeri düzeyde AES-256-GCM + PBKDF2 Gizli Kasa şifreleme
- [x] Birebir Android uyumlu .zip ve .notevault tam yedekleme motoru

## 📋 Sıradaki Planlar
- [ ] Zengin metin arama filtrelerinin genişletilmesi
- [ ] Özel etiket (tag) sistemi ve akıllı klasörler
- [ ] Çevrimdışı otomatik eşitleme arka plan servisi

```kotlin
// Android ve Masaüstü Veri Uyumluluğu
val note = Note(
    id = 1,
    title = "Masaüstü Notu",
    content = "Room & SQLite JDBC çift yönlü senkron",
    isPinned = true
)
```

> **İpucu:** Sol alt köşedeki **Wi-Fi Senkronizasyon** butonuna tıklayarak telefonunuzdaki Not Defterim ile saniyeler içinde notlarınızı eşitleyebilirsiniz.""",
            now - 10000,
            n_night_blue,
            cat_map["İş & Projeler"],
            1, # isPinned
            0, # isArchived
            0, # isDeleted
            None,
            0, # isLocked
            now - 10000,
            now - 5000
        ),
        (
            "✨ Markdown Kılavuzu & Biçimlendirme İpuçları",
            """# Markdown Düzenleyici Rehberi

Bu alanda notlarınızı yazarken yukarıdaki araç çubuğunu veya doğrudan Markdown sözdizimini kullanabilirsiniz.

### 1. Metin Vurguları
* **Kalın Metin:** `**kalın**` şeklinde yazılır.
* *İtalik Metin:* `*italik*` şeklinde yazılır.
* ~~Üstü Çizili:~~ `~~çizili~~` şeklinde yazılır.

### 2. Yapılacaklar (Checklist)
Aşağıdaki kutucuklara hem düzenleyiciden hem de önizleme panelinden doğrudan **tıklayarak** işaretleyebilirsiniz:
- [x] Sabah toplantısına katıl
- [x] Haftalık sprint hedeflerini gözden geçir
- [ ] Masaüstü arayüz geri bildirimlerini topla
- [ ] Yeni özellikler için dokümantasyon hazırla

### 3. Alıntılar ve Bilgi Kutuları
> "Tasarım sadece nasıl göründüğü veya hissettirdiği değildir. Tasarım nasıl çalıştığıdır."  
> — *Steve Jobs*

### 4. Kod Blokları
```bash
# Masaüstü uygulamasını terminalden başlatma
./gradlew :desktop:run
```""",
            now - 20000,
            n_emerald,
            cat_map["Fikirler & Tasarım"],
            1, # isPinned
            0,
            0,
            None,
            0,
            now - 20000,
            now - 12000
        ),
        (
            "🛒 Haftalık Alışveriş & Ev İhtiyaçları",
            """# Haftalık Alışveriş Listesi

Pazar günü süpermarket ve organik pazardan alınacaklar:

## 🍎 Taze Gıdalar & Manav
- [x] Muz, elma ve mandalina (1'er kg)
- [x] Avokado (2 adet)
- [ ] Bebek ıspanak ve taze roka
- [ ] Domates ve salatalık

## ☕ Kahve & Kahvaltılık
- [x] Filtre kahve çekirdeği (Orta kavrum)
- [x] Yulaf ezmesi ve chia tohumu
- [ ] Badem sütü
- [ ] Ezine peyniri ve zeytin

## 🏠 Ev & Çalışma Alanı
- [ ] A5 eskiz defteri
- [ ] Siyah jel kalem yedeği
- [ ] Çalışma masası için kablo düzenleyici cırt cırtlar

> **Not:** Alışverişe gitmeden önce buzdolabını kontrol etmeyi unutma!""",
            now - 30000,
            n_warm_amber,
            cat_map["Kişisel & Alışveriş"],
            0,
            0,
            0,
            None,
            0,
            now - 30000,
            now - 15000
        ),
        (
            "📖 Atomik Alışkanlıklar - James Clear",
            """# Atomik Alışkanlıklar (Atomic Habits)

Küçük değişikliklerin zaman içinde nasıl olağanüstü sonuçlar doğurduğunu anlatan harika bir başucu kitabı.

### 💡 Ana Fikir:
> "Hedeflerinizin seviyesine yükselmezsiniz; sistemlerinizin seviyesine düşersiniz."

### 🔑 Davranış Değişiminin 4 Temel Kanunu:

1. **Görünür Kılın:**  
   Yeni bir alışkanlık kazanmak istiyorsanız, tetikleyici ortamı göz önünde tutun (örn: Masada kitap bulundurmak).

2. **Cazip Kılın:**  
   Yapmak istediğiniz şey ile yapmanız gereken şeyi eşleştirin.

3. **Kolaylaştırın:**  
   Başlama sürtünmesini en aza indirin. *2 Dakika Kuralı:* Yeni bir alışkanlığa başlarken ilk adımı iki dakikadan az sürecek hale getirin.

4. **Doyurucu Kılın:**  
   Beyin anında gelen ödülleri sever. Tamamlanan her adımda zinciri kırmadığınızı görselleştirin.""",
            now - 40000,
            n_lavender,
            cat_map["Kitap & Öğrenme"],
            0,
            0,
            0,
            None,
            0,
            now - 40000,
            now - 25000
        ),
        (
            "🔐 Sunucu ve Veritabanı Erişim Bilgileri",
            """# 🔐 Korumalı Kasa Notu

Bu not **Kilitli Kasa** koruması altındadır.

### Test Sunucusu Yapılandırması:
* **Host:** 192.168.1.150:8765
* **Protokol:** HTTPS / AES-256-GCM
* **Yedekleme Anahtarı:** `vault-master-sec-2026`

> **Bilgi:** Bu içeriği düzenlemek veya yeni şifreleme uygulamak için sol menüden Kilitli Kasa şifrenizi girebilirsiniz.""",
            now - 50000,
            n_ocean_teal,
            cat_map["İş & Projeler"],
            0,
            0,
            0,
            None,
            1, # isLocked
            now - 50000,
            now - 35000
        ),
        (
            "📦 2025 Yılı Tamamlanan Hedefler",
            """# 2025 Yılı Tamamlanan Hedefler

Geçen yıl başarıyla tamamlanmış ve arşive kaldırılmış görevler:

- [x] Android Not Defterim v1.0 sürümünün yayınlanması
- [x] Room veritabanı ve yedekleme altyapısının kurulması
- [x] Kategori ve etiket mimarisinin oluşturulması
- [x] Kullanıcı testlerinin tamamlanması

Bu kayıtlar geçmiş referans amacıyla arşivde saklanmaktadır.""",
            now - 60000,
            n_night_blue,
            cat_map["İş & Projeler"],
            0,
            1, # isArchived
            0,
            None,
            0,
            now - 60000,
            now - 45000
        ),
        (
            "🗑️ Eski Taslak ve Karalama Notu",
            """Bu not daha önce silindi ve çöp kutusuna taşındı.  
İstediğiniz zaman geri yükleyebilir veya kalıcı olarak silebilirsiniz.""",
            now - 70000,
            0,
            None,
            0,
            0,
            1, # isDeleted
            now - 5000,
            0,
            now - 70000,
            now - 70000
        )
    ]

    for title, content, ts, color, cat_id, is_pin, is_arch, is_del, del_at, is_lock, c_at, u_at in notes_data:
        c.execute("SELECT id FROM notes WHERE title = ?", (title,))
        existing = c.fetchone()
        if not existing:
            c.execute("""
                INSERT INTO notes (
                    title, content, timestamp, color, categoryId,
                    isPinned, isArchived, isDeleted, deletedAt, isLocked,
                    createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (title, content, ts, color, cat_id, is_pin, is_arch, is_del, del_at, is_lock, c_at, u_at))
        else:
            c.execute("""
                UPDATE notes SET
                    content = ?, timestamp = ?, color = ?, categoryId = ?,
                    isPinned = ?, isArchived = ?, isDeleted = ?, deletedAt = ?, isLocked = ?,
                    createdAt = ?, updatedAt = ?
                WHERE id = ?
            """, (content, ts, color, cat_id, is_pin, is_arch, is_del, del_at, is_lock, c_at, u_at, existing[0]))

    conn.commit()
    conn.close()
    print(f"Successfully populated {p}")
