package com.example.noteapp.domain.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Akıllı Not Kopyalama & Markdown Panoya Kopyalama Motoru.
 * Markdown temizleme, özet çıkarma, görev ayıklama ve panoya kopyalama işlemlerini yönetir.
 */
object ClipboardHelper {

    enum class CopyOption {
        TITLE_ONLY,
        PLAIN_TEXT,
        FORMATTED_MARKDOWN,
        SUMMARY,
        REMAINING_CHECKLIST,
        COMPLETED_CHECKLIST,
        FULL_NOTE
    }

    data class CopyResult(
        val text: String,
        val wordCount: Int,
        val charCount: Int,
        val option: CopyOption
    )

    /**
     * Markdown etiket ve sembollerini temizleyerek düz, okunabilir metne dönüştürür.
     */
    fun stripMarkdown(markdown: String): String {
        if (markdown.isBlank()) return ""

        var result = markdown

        // 1. Kod bloklarını temizle: ```lang ... ``` -> iç kod
        result = result.replace(Regex("```[a-zA-Z0-9_-]*\n?([\\s\\S]*?)\n?```"), "$1")

        // 2. Satır içi kod: `kod` -> kod
        result = result.replace(Regex("`([^`]+)`"), "$1")

        // 3. Görseller: ![alt](url) -> Görsel alt metni veya boşluk
        result = result.replace(Regex("!\\[([^\\]]*)\\]\\([^)]+\\)"), "$1")

        // 4. Bağlantılar: [text](url) -> text (url) veya text
        result = result.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { match ->
            val text = match.groupValues[1]
            val url = match.groupValues[2]
            if (text.equals(url, ignoreCase = true) || text.isBlank()) {
                url
            } else {
                "$text ($url)"
            }
        }

        // 5. Wikilinks: [[Not Başlığı]] -> Not Başlığı
        result = result.replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")

        // 6. Başlıklar: #, ##, ### ... satır başı kaldır
        result = result.lines().joinToString("\n") { line ->
            line.replace(Regex("^#{1,6}\\s+"), "")
        }

        // 7. Alıntılar: > kaldır
        result = result.lines().joinToString("\n") { line ->
            line.replace(Regex("^>+\\s*"), "")
        }

        // 8. Kalın, İtalik ve Üstü Çizili: **kalın**, *italik*, ~~çizili~~
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        result = result.replace(Regex("__([^_]+)__"), "$1")
        result = result.replace(Regex("~~([^~]+)~~"), "$1")
        result = result.replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "$1")
        result = result.replace(Regex("(?<!_)_([^_\\n]+)_(?!_)"), "$1")

        // 9. Tablolar: Ayırıcı satırları (|---|---|) sil, hücreleri ayır
        result = result.lines().filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("|") && trimmed.endsWith("|") &&
                trimmed.replace(Regex("[:\\-|\\s]"), "").isEmpty()
        }.joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                trimmed.removeSurrounding("|")
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("  •  ")
            } else {
                line
            }
        }

        // 10. Kontrol listeleri: - [ ] Görev -> • Görev
        result = result.replace(Regex("^[ \t]*[-*+]\\s*\\[[ xX]\\]\\s*", RegexOption.MULTILINE), "• ")

        // 11. Standart madde işaretleri: - madde, * madde -> • madde
        result = result.replace(Regex("^[ \t]*[-*+]\\s+", RegexOption.MULTILINE), "• ")

        // 12. Yatay çizgiler (---, ***, ___)
        result = result.replace(Regex("^[ \t]*([-*_]){3,}[ \t]*$", RegexOption.MULTILINE), "")

        // 13. Fazla ardışık boş satırları birleştir ve trim yap
        return result.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * Başlık ve içeriği düz metin olarak birleştirir.
     */
    fun toPlainText(title: String, content: String): String {
        val cleanTitle = stripMarkdown(title).trim()
        val cleanContent = stripMarkdown(content).trim()

        return buildString {
            if (cleanTitle.isNotBlank()) {
                appendLine(cleanTitle)
                appendLine()
            }
            append(cleanContent)
        }.trim()
    }

    /**
     * Başlık ve içeriği temiz, standart Markdown formatına dönüştürür.
     */
    fun formatAsMarkdown(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        timestamp: Long? = null
    ): String {
        return buildString {
            val trimmedTitle = title.trim()
            if (trimmedTitle.isNotBlank()) {
                if (trimmedTitle.startsWith("#")) {
                    appendLine(trimmedTitle)
                } else {
                    appendLine("# $trimmedTitle")
                }
                appendLine()
            }

            if (timestamp != null && timestamp > 0) {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                appendLine("> 📅 ${sdf.format(Date(timestamp))}")
                appendLine()
            }

            val trimmedContent = content.trim()
            if (trimmedContent.isNotBlank()) {
                appendLine(trimmedContent)
            }

            if (tags.isNotEmpty()) {
                val existingTags = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")
                    .findAll("$trimmedTitle $trimmedContent")
                    .map { it.value.lowercase() }
                    .toSet()

                val missingTags = tags
                    .map { if (it.startsWith("#")) it else "#$it" }
                    .filterNot { existingTags.contains(it.lowercase()) }

                if (missingTags.isNotEmpty()) {
                    appendLine()
                    appendLine("---")
                    appendLine("Etiketler: ${missingTags.joinToString(" ")}")
                }
            }
        }.trim()
    }

    /**
     * İçerikten ilk [maxSentences] cümleyi veya en fazla [maxWords] kelimelik özeti ayıklar.
     */
    fun extractSummary(content: String, maxSentences: Int = 3, maxWords: Int = 60): String {
        val cleanText = stripMarkdown(content).trim()
        if (cleanText.isBlank()) return ""

        // Cümle sınırlarını bul (. ! ? sonrası boşluk veya satır sonu)
        val sentenceRegex = Regex("(?<=[.!?])(\\s+|\n+)")
        val sentences = cleanText.split(sentenceRegex).map { it.trim() }.filter { it.isNotBlank() }

        val candidateText = if (sentences.isNotEmpty()) {
            sentences.take(maxSentences).joinToString(" ")
        } else {
            cleanText
        }

        // Kelime limit kontrolü
        val words = candidateText.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size > maxWords) {
            words.take(maxWords).joinToString(" ") + "..."
        } else {
            candidateText
        }
    }

    /**
     * İçerikteki henüz tamamlanmamış (- [ ] veya [ ]) maddeleri ayıklar.
     */
    fun extractRemainingChecklist(content: String, formatAsBullets: Boolean = false): String {
        if (content.isBlank()) return ""
        val checklistRegex = Regex("^[ \t]*[-*+]?\\s*\\[ \\]\\s*(.*)$")
        val items = content.lines().mapNotNull { line ->
            checklistRegex.find(line.trim())?.groupValues?.get(1)?.trim()
        }.filter { it.isNotBlank() }

        if (items.isEmpty()) return ""

        val prefix = if (formatAsBullets) "• " else "[ ] "
        return items.joinToString("\n") { "$prefix$it" }
    }

    /**
     * İçerikteki tamamlanmış (- [x] veya [x]) maddeleri ayıklar.
     */
    fun extractCompletedChecklist(content: String, formatAsBullets: Boolean = false): String {
        if (content.isBlank()) return ""
        val checklistRegex = Regex("^[ \t]*[-*+]?\\s*\\[[xX]\\]\\s*(.*)$")
        val items = content.lines().mapNotNull { line ->
            checklistRegex.find(line.trim())?.groupValues?.get(1)?.trim()
        }.filter { it.isNotBlank() }

        if (items.isEmpty()) return ""

        val prefix = if (formatAsBullets) "✓ " else "[x] "
        return items.joinToString("\n") { "$prefix$it" }
    }

    /**
     * Notun kontrol listesi maddesi içerip içermediğini kontrol eder.
     */
    fun hasChecklistItems(content: String): Boolean {
        return content.lines().any { line ->
            val trimmed = line.trim()
            trimmed.contains("[ ]") || trimmed.contains("[x]") || trimmed.contains("[X]")
        }
    }

    /**
     * Metindeki kelime sayısını hesaplar.
     */
    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    /**
     * Metni panoya kopyalar, isteğe bağlı haptik titreşim ve toast bildirimi verir.
     */
    fun copyToClipboard(
        context: Context,
        label: String,
        text: String,
        option: CopyOption,
        showToast: Boolean = true,
        performHaptic: Boolean = true
    ): CopyResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)

        val wordCount = countWords(text)
        val charCount = text.length

        if (performHaptic) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(35)
                    }
                }
            } catch (_: Exception) {
                // Donanım veya izin kısıtlamasında hatayı yut
            }
        }

        if (showToast) {
            val message = when (option) {
                CopyOption.TITLE_ONLY -> "Başlık kopyalandı ($wordCount kelime)"
                CopyOption.PLAIN_TEXT -> "Düz metin kopyalandı ($wordCount kelime)"
                CopyOption.FORMATTED_MARKDOWN -> "Markdown kopyalandı ($wordCount kelime)"
                CopyOption.SUMMARY -> "Özet kopyalandı ($wordCount kelime)"
                CopyOption.REMAINING_CHECKLIST -> "Kalan görevler kopyalandı (${text.lines().size} madde)"
                CopyOption.COMPLETED_CHECKLIST -> "Tamamlananlar kopyalandı (${text.lines().size} madde)"
                CopyOption.FULL_NOTE -> "Not panoya kopyalandı ($wordCount kelime)"
            }
            Toast.makeText(context, "📋 $message", Toast.LENGTH_SHORT).show()
        }

        return CopyResult(
            text = text,
            wordCount = wordCount,
            charCount = charCount,
            option = option
        )
    }
}
