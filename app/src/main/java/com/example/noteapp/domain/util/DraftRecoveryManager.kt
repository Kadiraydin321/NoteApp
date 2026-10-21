package com.example.noteapp.domain.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Taslak verisi modeli.
 */
data class NoteDraft(
    val noteId: Long?,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Taslak depolama soyutlaması (Platformdan bağımsız birim testleri için).
 */
interface DraftStorage {
    fun putString(key: String, value: String)
    fun putLong(key: String, value: Long)
    fun getString(key: String, defValue: String?): String?
    fun getLong(key: String, defValue: Long): Long
    fun remove(vararg keys: String)
    fun clear()
}

/**
 * Android SharedPreferences tabanlı depolama gerçekleştirimi.
 */
class SharedPreferencesDraftStorage(private val prefs: SharedPreferences) : DraftStorage {
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return prefs.getString(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return prefs.getLong(key, defValue)
    }

    override fun remove(vararg keys: String) {
        val editor = prefs.edit()
        for (k in keys) editor.remove(k)
        editor.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * Bellek içi depolama (Birim testleri için).
 */
class InMemoryDraftStorage : DraftStorage {
    private val stringMap = mutableMapOf<String, String>()
    private val longMap = mutableMapOf<String, Long>()

    override fun putString(key: String, value: String) {
        stringMap[key] = value
    }

    override fun putLong(key: String, value: Long) {
        longMap[key] = value
    }

    override fun getString(key: String, defValue: String?): String? {
        return stringMap[key] ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return longMap[key] ?: defValue
    }

    override fun remove(vararg keys: String) {
        for (k in keys) {
            stringMap.remove(k)
            longMap.remove(k)
        }
    }

    override fun clear() {
        stringMap.clear()
        longMap.clear()
    }
}

/**
 * Akıllı Otomatik Kaydetme & Karalama Taslağı Kurtarma Yöneticisi.
 * Not yazılırken kaybolmayı önlemek için periyodik taslaklar oluşturur ve kaza eseri kapanmalarda kurtarma sağlar.
 */
class DraftRecoveryManager(
    private val storage: DraftStorage
) {
    companion object {
        const val PREFS_NAME = "note_draft_recovery_prefs"
        private const val KEY_NEW_NOTE = "draft_new_note"

        fun create(context: Context): DraftRecoveryManager {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return DraftRecoveryManager(SharedPreferencesDraftStorage(prefs))
        }
    }

    private fun keyForNote(noteId: Long?): String {
        return if (noteId == null || noteId <= 0L) KEY_NEW_NOTE else "draft_$noteId"
    }

    /**
     * Not için taslak kaydeder. Başlık ve içerik tamamen boşsa varsa taslağı siler.
     */
    fun saveDraft(
        noteId: Long?,
        title: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (title.isBlank() && content.isBlank()) {
            clearDraft(noteId)
            return
        }
        val key = keyForNote(noteId)
        storage.putString("${key}_title", title)
        storage.putString("${key}_content", content)
        storage.putLong("${key}_time", timestamp)
    }

    /**
     * Not için kaydedilmiş taslağı getirir.
     */
    fun getDraft(noteId: Long?): NoteDraft? {
        val key = keyForNote(noteId)
        val time = storage.getLong("${key}_time", 0L)
        if (time <= 0L) return null

        val title = storage.getString("${key}_title", "") ?: ""
        val content = storage.getString("${key}_content", "") ?: ""

        if (title.isBlank() && content.isBlank()) return null

        val normalizedId = if (noteId == null || noteId <= 0L) null else noteId
        return NoteDraft(
            noteId = normalizedId,
            title = title,
            content = content,
            timestamp = time
        )
    }

    /**
     * Kayıtlı bir taslağın mevcut veritabanı içeriğinden farklı ve kurtarılabilir olup olmadığını kontrol eder.
     */
    fun hasRecoverableDraft(
        noteId: Long?,
        currentDbTitle: String,
        currentDbContent: String,
        currentDbTimestamp: Long = 0L
    ): Boolean {
        val draft = getDraft(noteId) ?: return false

        // Taslak mevcut veritabanı kaydından daha eski ise kurtarma sunma
        if (currentDbTimestamp > 0L && draft.timestamp <= currentDbTimestamp) {
            return false
        }

        // Metin veya başlıkta gerçek bir fark var mı?
        val titleDiffers = draft.title.trim() != currentDbTitle.trim()
        val contentDiffers = draft.content.trim() != currentDbContent.trim()

        return titleDiffers || contentDiffers
    }

    /**
     * Belirtilen notun taslağını siler (Not temiz kaydedildiğinde çağrılır).
     */
    fun clearDraft(noteId: Long?) {
        val key = keyForNote(noteId)
        storage.remove("${key}_title", "${key}_content", "${key}_time")
    }

    /**
     * Tüm taslakları siler.
     */
    fun clearAllDrafts() {
        storage.clear()
    }

    /**
     * Taslağın ne kadar süre önce kaydedildiğini kullanıcı dostu dilde ifade eder.
     */
    fun getDraftAgeDescription(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diffMs = maxOf(0L, now - timestamp)
        val minutes = diffMs / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L

        return when {
            minutes < 1 -> "Az önce"
            minutes < 60 -> "$minutes dakika önce"
            hours < 24 -> "$hours saat önce"
            days == 1L -> "Dün"
            else -> "$days gün önce"
        }
    }
}
