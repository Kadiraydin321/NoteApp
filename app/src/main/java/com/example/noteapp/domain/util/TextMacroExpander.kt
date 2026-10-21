package com.example.noteapp.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Metin parçacıkları kategorileri.
 */
enum class SnippetCategory(val title: String) {
    ALL("Tümü"),
    DATE_TIME("Tarih & Zaman"),
    CALLOUT("Bilgi Blokları"),
    PRODUCTIVITY("Üretkenlik & Plan"),
    FORMATTING("Format & Tablo")
}

/**
 * Dinamik metin parçacığı / makro modeli.
 */
data class MacroSnippet(
    val id: String,
    val trigger: String,
    val title: String,
    val category: SnippetCategory,
    val template: String,
    val description: String,
    val iconEmoji: String = "⚡"
)

/**
 * Tetikleyici genişletme sonucu.
 */
data class ExpansionResult(
    val trigger: String,
    val expandedText: String,
    val triggerLength: Int,
    val newCursorOffset: Int
)

/**
 * Dinamik Metin Genişletici & Akıllı Not Parçacıkları Motoru.
 * Sık kullanılan şablon bloklarını, çağrı kutularını (admonitions / callouts)
 * ve dinamik tarih/saat değişkenlerini (`{date}`, `{time}`, `{weekday}`, `{title}`)
 * anında genişletir.
 */
object TextMacroExpander {

    private val DEFAULT_SNIPPETS = listOf(
        // Tarih & Zaman Makroları
        MacroSnippet(
            id = "date_today",
            trigger = ":bugun",
            title = "Bugünün Tarihi",
            category = SnippetCategory.DATE_TIME,
            template = "{date}",
            description = "Geçerli tarihi 'dd.MM.yyyy' biçiminde ekler.",
            iconEmoji = "📅"
        ),
        MacroSnippet(
            id = "date_iso",
            trigger = ":date",
            title = "ISO Tarih",
            category = SnippetCategory.DATE_TIME,
            template = "{iso_date}",
            description = "Geçerli tarihi 'yyyy-MM-dd' biçiminde ekler.",
            iconEmoji = "📆"
        ),
        MacroSnippet(
            id = "time_now",
            trigger = ":saat",
            title = "Şu Anki Saat",
            category = SnippetCategory.DATE_TIME,
            template = "{time}",
            description = "Geçerli saati 'HH:mm' biçiminde ekler.",
            iconEmoji = "⏰"
        ),
        MacroSnippet(
            id = "timestamp_full",
            trigger = ":zaman",
            title = "Tam Zaman Damgası",
            category = SnippetCategory.DATE_TIME,
            template = "{date} {time}",
            description = "Tarih ve saati birlikte ekler.",
            iconEmoji = "⏱️"
        ),
        MacroSnippet(
            id = "day_name",
            trigger = ":gun",
            title = "Haftanın Günü",
            category = SnippetCategory.DATE_TIME,
            template = "{weekday}",
            description = "Geçerli gün adını ekler (Örn: Pazartesi).",
            iconEmoji = "🗓️"
        ),

        // Çağrı Kutuları (Admonitions & Callouts)
        MacroSnippet(
            id = "callout_note",
            trigger = ":not",
            title = "Bilgi Kutusu (Note)",
            category = SnippetCategory.CALLOUT,
            template = "> [!NOTE]\n> Buraya açıklayıcı bilgi veya not yazın.",
            description = "Standart mavi bilgi çağrı kutusu.",
            iconEmoji = "ℹ️"
        ),
        MacroSnippet(
            id = "callout_tip",
            trigger = ":ipucu",
            title = "İpucu Kutusu (Tip)",
            category = SnippetCategory.CALLOUT,
            template = "> [!TIP]\n> Faydalı bir ipucu veya öneri buraya gelir.",
            description = "Yeşil pratik ipucu vurgu kutusu.",
            iconEmoji = "💡"
        ),
        MacroSnippet(
            id = "callout_important",
            trigger = ":onemli",
            title = "Önemli Kutusu (Important)",
            category = SnippetCategory.CALLOUT,
            template = "> [!IMPORTANT]\n> Mutlaka dikkat edilmesi gereken kritik detay.",
            description = "Mor yüksek öncelikli önem kutusu.",
            iconEmoji = "⭐"
        ),
        MacroSnippet(
            id = "callout_warning",
            trigger = ":uyari",
            title = "Uyarı Kutusu (Warning)",
            category = SnippetCategory.CALLOUT,
            template = "> [!WARNING]\n> Olası aksaklıklar veya riskler için uyarı.",
            description = "Sarı/Turuncu dikkat ve uyarı kutusu.",
            iconEmoji = "⚠️"
        ),
        MacroSnippet(
            id = "callout_caution",
            trigger = ":dikkat",
            title = "Tehlike Kutusu (Caution)",
            category = SnippetCategory.CALLOUT,
            template = "> [!CAUTION]\n> Geri dönüşü olmayan işlemler veya güvenlik uyarısı.",
            description = "Kırmızı kritik tehlike kutusu.",
            iconEmoji = "🚨"
        ),

        // Üretkenlik & Planlama
        MacroSnippet(
            id = "meeting_notes",
            trigger = ":toplanti",
            title = "Toplantı Tutanağı",
            category = SnippetCategory.PRODUCTIVITY,
            template = "### 🎙️ Toplantı Notları: {date} ({weekday})\n**Katılımcılar:** \n**Gündem Konuları:**\n- \n\n**Alınan Kararlar & Aksiyonlar:**\n- [ ] ",
            description = "Toplantı katılımcıları, gündem ve aksiyon maddeleri şablonu.",
            iconEmoji = "👥"
        ),
        MacroSnippet(
            id = "daily_standup",
            trigger = ":standup",
            title = "Günlük Standup / Durum",
            category = SnippetCategory.PRODUCTIVITY,
            template = "### 🚀 Günlük Standup ({date})\n**Dün Neler Yaptım?**\n- \n\n**Bugün Neler Yapacaksam?**\n- [ ] \n\n**Engeller & Blokajlar:**\n- Yok",
            description = "Çevik standup durum formatı.",
            iconEmoji = "🏃"
        ),
        MacroSnippet(
            id = "priority_tasks",
            trigger = ":gorev",
            title = "Öncelikli Görev Listesi",
            category = SnippetCategory.PRODUCTIVITY,
            template = "### 🎯 Günün Öncelikli Görevleri\n- [ ] 🔥 [Yüksek] \n- [ ] ⚡ [Orta] \n- [ ] ☕ [Düşük] ",
            description = "Eisenhower mantığıyla önceliklendirilmiş görev listesi.",
            iconEmoji = "✅"
        ),
        MacroSnippet(
            id = "pros_cons",
            trigger = ":karar",
            title = "Artılar & Eksiler (Karar)",
            category = SnippetCategory.PRODUCTIVITY,
            template = "### ⚖️ Karar Değerlendirmesi\n| Artılar (+) | Eksiler (-) |\n| :--- | :--- |\n| Hızlı uygulanabilir | Maliyetli olabilir |\n| Verimliliği artırır | Zaman gerektirir |",
            description = "Karar verme süreçleri için karşılaştırma tablosu.",
            iconEmoji = "⚖️"
        ),

        // Format & Kod & Tablo
        MacroSnippet(
            id = "code_block",
            trigger = ":kod",
            title = "Kod Bloğu (Kotlin)",
            category = SnippetCategory.FORMATTING,
            template = "```kotlin\n// Kodunuzu buraya yazın\n```",
            description = "Sözdizimi vurgulamalı kod çiti.",
            iconEmoji = "💻"
        ),
        MacroSnippet(
            id = "table_3x3",
            trigger = ":tablo",
            title = "3x3 Tablo",
            category = SnippetCategory.FORMATTING,
            template = "| Başlık 1 | Başlık 2 | Başlık 3 |\n| :--- | :---: | ---: |\n| Veri A | Veri B | Veri C |\n| Veri D | Veri E | Veri F |",
            description = "Hizalamalı 3 sütunlu standart Markdown tablosu.",
            iconEmoji = "📊"
        ),
        MacroSnippet(
            id = "quote_block",
            trigger = ":alinti",
            title = "Özlü Söz / Alıntı",
            category = SnippetCategory.FORMATTING,
            template = "> \"İlham veren bir söz buraya yazılır.\"\n> — *Yazar Adı*",
            description = "Kaynak belirten estetik blok alıntı.",
            iconEmoji = "💬"
        ),
        MacroSnippet(
            id = "divider_line",
            trigger = ":ayrac",
            title = "Yatay Ayraç Çizgisi",
            category = SnippetCategory.FORMATTING,
            template = "\n---\n",
            description = "Bölümleri ayıran yatay çizgi.",
            iconEmoji = "➖"
        )
    )

    /**
     * Tüm varsayılan parçacık listesini döndürür.
     */
    fun getAllSnippets(): List<MacroSnippet> = DEFAULT_SNIPPETS

    /**
     * Kategoriye ve arama sorgusuna göre parçacıkları filtreler.
     */
    fun filterSnippets(
        query: String = "",
        category: SnippetCategory = SnippetCategory.ALL,
        source: List<MacroSnippet> = DEFAULT_SNIPPETS
    ): List<MacroSnippet> {
        return source.filter { snippet ->
            val matchesCategory = category == SnippetCategory.ALL || snippet.category == category
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            val matchesQuery = cleanQuery.isEmpty() ||
                    snippet.title.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    snippet.trigger.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    snippet.description.lowercase(Locale.getDefault()).contains(cleanQuery)
            matchesCategory && matchesQuery
        }
    }

    /**
     * Parçacık şablonundaki değişkenleri dinamik değerlerle değiştirir.
     */
    fun expandTemplate(
        template: String,
        currentDate: Date = Date(),
        noteTitle: String = "",
        locale: Locale = Locale("tr", "TR")
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", locale)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", locale)
        val timeFormat = SimpleDateFormat("HH:mm", locale)
        val dayFormat = SimpleDateFormat("EEEE", locale)

        return template
            .replace("{date}", dateFormat.format(currentDate))
            .replace("{iso_date}", isoFormat.format(currentDate))
            .replace("{time}", timeFormat.format(currentDate))
            .replace("{datetime}", "${dateFormat.format(currentDate)} ${timeFormat.format(currentDate)}")
            .replace("{weekday}", dayFormat.format(currentDate))
            .replace("{title}", noteTitle)
    }

    /**
     * İmlecin hemen solundaki metinde bir tetikleyici (`:kod`, `:bugun`, vb.) bulunursa
     * bunu genişletip yerine konacak metni ve tetikleyici uzunluğunu hesaplar.
     */
    fun detectAndExpandTrigger(
        textBeforeCursor: String,
        currentDate: Date = Date(),
        noteTitle: String = "",
        snippets: List<MacroSnippet> = DEFAULT_SNIPPETS
    ): ExpansionResult? {
        if (textBeforeCursor.isBlank()) return null

        // Son sözcüğü bul (boşluk veya satır sonu ile ayrılan)
        val lastWord = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
        if (!lastWord.startsWith(":") || lastWord.length < 2) return null

        val matchedSnippet = snippets.firstOrNull { it.trigger.equals(lastWord, ignoreCase = true) }
            ?: return null

        val expanded = expandTemplate(matchedSnippet.template, currentDate, noteTitle)
        return ExpansionResult(
            trigger = matchedSnippet.trigger,
            expandedText = expanded,
            triggerLength = lastWord.length,
            newCursorOffset = expanded.length
        )
    }

    /**
     * Verilen metin ve imleç konumuna göre tetikleyiciyi genişletip güncellenmiş tam metni döndürür.
     */
    fun applyTriggerExpansionAtCursor(
        fullText: String,
        cursorPosition: Int,
        currentDate: Date = Date(),
        noteTitle: String = ""
    ): Pair<String, Int>? {
        val safeCursor = cursorPosition.coerceIn(0, fullText.length)
        val textBefore = fullText.substring(0, safeCursor)
        val textAfter = fullText.substring(safeCursor)

        val result = detectAndExpandTrigger(textBefore, currentDate, noteTitle) ?: return null

        val prefixWithoutTrigger = textBefore.dropLast(result.triggerLength)
        val newText = prefixWithoutTrigger + result.expandedText + textAfter
        val newCursor = prefixWithoutTrigger.length + result.expandedText.length

        return Pair(newText, newCursor)
    }
}
