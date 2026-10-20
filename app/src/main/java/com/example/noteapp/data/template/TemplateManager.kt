package com.example.noteapp.data.template

import android.content.Context
import com.example.noteapp.domain.model.NoteTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("note_templates_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_TEMPLATES = "custom_templates_json"
    }

    val builtInTemplates: List<NoteTemplate> = listOf(
        NoteTemplate(
            id = "builtin_meeting",
            title = "Toplantı Notu",
            description = "Gündem, katılımcılar, kararlar ve görev dağılımı",
            iconEmoji = "📅",
            defaultTitle = "Toplantı: {TITLE}",
            categoryName = "İş",
            contentTemplate = """
# 📅 Toplantı: {TITLE}
**Tarih:** {DATE} - {TIME}
**Katılımcılar:** 
**Toplantı Yöneticisi:** 

---

### 🎯 Gündem Maddeleri
1. [Gündem maddesi 1]
2. [Gündem maddesi 2]
3. [Gündem maddesi 3]

---

### 📝 Toplantı Notları & Tartışmalar
* 

---

### 💡 Alınan Kararlar
* [x] Karar 1: 
* [x] Karar 2: 

---

### ⚡ Aksiyon Listesi (Görevler)
- [ ] Görev 1 (@Sorumlu, Bitiş: {DATE})
- [ ] Görev 2 (@Sorumlu, Bitiş: {DATE})
- [ ] Görev 3 (@Sorumlu, Bitiş: {DATE})
            """.trimIndent()
        ),
        NoteTemplate(
            id = "builtin_journal",
            title = "Günlük & Şükran",
            description = "Günün özeti, şükran duydukların ve yarının hedefleri",
            iconEmoji = "📖",
            defaultTitle = "Günlük - {DATE}",
            categoryName = "Kişisel",
            contentTemplate = """
# 📖 Günlük - {DATE} ({DAY})
**Ruh Hali:** ☀️ Enerjik | 🌿 Dingin | ☕ Odaklanmış

---

### 💭 Bugün Neler Oldu?
* 

---

### 🙏 Bugün Şükrettiğim 3 Şey
1. 
2. 
3. 

---

### 💡 Bugün Öğrendiğim Ders / Farkındalık
* 

---

### 🎯 Yarının En Önemli 3 Hedefi
- [ ] Hedef 1: 
- [ ] Hedef 2: 
- [ ] Hedef 3: 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "builtin_shopping",
            title = "Alışveriş Listesi",
            description = "Kategorilere ayrılmış hazır alışveriş kontrol listesi",
            iconEmoji = "🛒",
            defaultTitle = "Alışveriş Listesi ({DATE})",
            categoryName = "Alışveriş",
            contentTemplate = """
# 🛒 Alışveriş Listesi ({DATE})

### 🍏 Manav / Sebze & Meyve
- [ ] Domates & Salatalık
- [ ] Elma / Muz
- [ ] Yeşillik (Marul, Roka)
- [ ] Patates & Soğan

### 🥛 Temel Gıda & Şarküteri
- [ ] Süt & Yoğurt
- [ ] Peynir & Yumurta
- [ ] Ekmek
- [ ] Zeytinyağı

### 🧴 Temizlik & Kişisel Bakım
- [ ] Bulaşık Deterjanı
- [ ] Şampuan / Sabun
- [ ] Kağıt Havlu

### 💡 Diğer İhtiyaçlar
- [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "builtin_project",
            title = "Proje & Sprint Planı",
            description = "Hedefler, kilometre taşları ve sprint görev panosu",
            iconEmoji = "🚀",
            defaultTitle = "Proje: {TITLE}",
            categoryName = "Projeler",
            contentTemplate = """
# 🚀 Proje: {TITLE}
**Başlangıç:** {DATE}
**Hedef Bitiş:** 

---

### 🎯 Proje Hedefi & Kapsam
* **Amaç:** 
* **Başarı Kriteri:** 

---

### 🏁 Kilometre Taşları (Milestones)
- [ ] M1: Araştırma ve Tasarım ({DATE})
- [ ] M2: İlk MVP Geliştirme
- [ ] M3: Test ve Doğrulama
- [ ] M4: Canlıya Çıkış (Launch)

---

### 📋 Görev Panosu
#### ⏳ Yapılacaklar
- [ ] 

#### 🔄 Devam Edenler
- [ ] 

#### ✅ Tamamlananlar
- [x] Proje başlangıç planı hazırlandı

---

### ⚠️ Riskler & Engeller
* 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "builtin_brainstorm",
            title = "Fikir & Beyin Fırtınası",
            description = "Problem, çözüm, artı/eksi analizi ve ilk adımlar",
            iconEmoji = "💡",
            defaultTitle = "Fikir: {TITLE}",
            categoryName = "Fikirler",
            contentTemplate = """
# 💡 Fikir: {TITLE}
**Tarih:** {DATE}

---

### ❓ Çözülen Problem
* 

---

### ✨ Önerilen Çözüm
* 

---

### ⚖️ Artılar & Eksiler
| ✅ Avantajlar | ⚠️ Dezavantajlar & Riskler |
|---|---|
| • Hızlı uygulanabilir | • Kaynak gereksinimi |
| •  | •  |

---

### 👣 İlk 3 Eylem Adımı
1. [ ] 
2. [ ] 
3. [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "builtin_travel",
            title = "Seyahat & Gezi Rehberi",
            description = "Ulaşım, konaklama, bavul kontrolü ve gezilecek yerler",
            iconEmoji = "✈️",
            defaultTitle = "Seyahat: {TITLE}",
            categoryName = "Seyahat",
            contentTemplate = """
# ✈️ Seyahat: {TITLE}
**Tarihler:** {DATE} - [Dönüş Tarihi]
**Hedef:** 

---

### 🏨 Konaklama & Ulaşım
* **Otel / Konaklama:** 
* **Uçak / Tren / Biletler:** 
* **Rezervasyon No:** 

---

### 🧳 Bavul Kontrol Listesi
#### 📄 Belgeler & Para
- [ ] Kimlik / Pasaport
- [ ] Biletler & Rezervasyon çıktıları
- [ ] Kredi kartları & Nakit

#### 👕 Kıyafet & Kişisel
- [ ] Günlük kıyafetler & İç giyim
- [ ] Şarj aletleri & Powerbank
- [ ] İlaçlar & Kişisel bakım

---

### 📍 Gezilecek Yerler & Aktiviteler
- [ ] 1. Gün: 
- [ ] 2. Gün: 
- [ ] 3. Gün: 

---

### 💰 Bütçe & Harcamalar
* **Tahmini Bütçe:** 
            """.trimIndent()
        )
    )

    fun getCustomTemplates(): List<NoteTemplate> {
        val jsonStr = prefs.getString(KEY_CUSTOM_TEMPLATES, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<NoteTemplate>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    NoteTemplate(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        iconEmoji = obj.optString("iconEmoji", "📝"),
                        contentTemplate = obj.getString("contentTemplate"),
                        defaultTitle = obj.optString("defaultTitle", obj.getString("title")),
                        categoryName = if (obj.has("categoryName") && !obj.isNull("categoryName")) obj.getString("categoryName") else null,
                        isBuiltIn = false
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun getAllTemplates(): List<NoteTemplate> {
        return builtInTemplates + getCustomTemplates()
    }

    fun saveCustomTemplate(
        title: String,
        description: String,
        content: String,
        iconEmoji: String = "📝",
        categoryName: String? = null
    ): NoteTemplate {
        val currentList = getCustomTemplates().toMutableList()
        val newTemplate = NoteTemplate(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            iconEmoji = iconEmoji.ifBlank { "📝" },
            contentTemplate = content,
            defaultTitle = title.trim(),
            categoryName = categoryName,
            isBuiltIn = false
        )
        currentList.add(0, newTemplate)

        val jsonArray = JSONArray()
        currentList.forEach { tmpl ->
            val obj = JSONObject().apply {
                put("id", tmpl.id)
                put("title", tmpl.title)
                put("description", tmpl.description)
                put("iconEmoji", tmpl.iconEmoji)
                put("contentTemplate", tmpl.contentTemplate)
                put("defaultTitle", tmpl.defaultTitle)
                put("categoryName", tmpl.categoryName)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_TEMPLATES, jsonArray.toString()).apply()
        return newTemplate
    }

    fun deleteCustomTemplate(templateId: String): Boolean {
        val currentList = getCustomTemplates().toMutableList()
        val removed = currentList.removeAll { it.id == templateId }
        if (removed) {
            val jsonArray = JSONArray()
            currentList.forEach { tmpl ->
                val obj = JSONObject().apply {
                    put("id", tmpl.id)
                    put("title", tmpl.title)
                    put("description", tmpl.description)
                    put("iconEmoji", tmpl.iconEmoji)
                    put("contentTemplate", tmpl.contentTemplate)
                    put("defaultTitle", tmpl.defaultTitle)
                    put("categoryName", tmpl.categoryName)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_CUSTOM_TEMPLATES, jsonArray.toString()).apply()
        }
        return removed
    }

    /**
     * Yedekten gelen şablonları mevcut şablonlara ekler veya üzerine yazar.
     */
    fun restoreCustomTemplates(templates: List<NoteTemplate>, clearExisting: Boolean = false) {
        val currentList = if (clearExisting) mutableListOf() else getCustomTemplates().toMutableList()
        val existingIds = currentList.map { it.id }.toSet()
        for (tmpl in templates) {
            if (!existingIds.contains(tmpl.id)) {
                currentList.add(tmpl)
            }
        }
        val jsonArray = JSONArray()
        currentList.forEach { tmpl ->
            val obj = JSONObject().apply {
                put("id", tmpl.id)
                put("title", tmpl.title)
                put("description", tmpl.description)
                put("iconEmoji", tmpl.iconEmoji)
                put("contentTemplate", tmpl.contentTemplate)
                put("defaultTitle", tmpl.defaultTitle)
                put("categoryName", tmpl.categoryName)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_TEMPLATES, jsonArray.toString()).apply()
    }

    /**
     * Şablon içerisindeki dinamik etiketleri ({DATE}, {TIME}, {DAY}, {TITLE}) güncel değerlerle doldurur.
     */
    fun interpolateTemplate(
        template: NoteTemplate,
        userTitleInput: String? = null
    ): Pair<String, String> {
        val now = Date()
        val trLocale = Locale("tr", "TR")
        val dateFormatted = SimpleDateFormat("d MMMM yyyy", trLocale).format(now)
        val timeFormatted = SimpleDateFormat("HH:mm", trLocale).format(now)
        val dayFormatted = SimpleDateFormat("EEEE", trLocale).format(now)

        val titleReplacement = userTitleInput?.trim()?.takeIf { it.isNotEmpty() } ?: template.title

        val finalTitle = template.defaultTitle
            .replace("{DATE}", dateFormatted)
            .replace("{TIME}", timeFormatted)
            .replace("{DAY}", dayFormatted)
            .replace("{TITLE}", titleReplacement)

        val finalContent = template.contentTemplate
            .replace("{DATE}", dateFormatted)
            .replace("{TIME}", timeFormatted)
            .replace("{DAY}", dayFormatted)
            .replace("{TITLE}", titleReplacement)

        return Pair(finalTitle, finalContent)
    }
}
