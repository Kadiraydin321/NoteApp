package com.example.noteapp.media

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechHelper @Inject constructor(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private val tag = "TextToSpeechHelper"

    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentUtteranceId: String? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            // Android 11+ ve arka plan bağlamı güvenliği için Application Context kullanıyoruz
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "TTS Başlatılamadı: ${e.message}", e)
            _isReady.value = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts
            if (ttsEngine != null) {
                // Öncelik: Türkçe desteği
                val turkishLocale = Locale("tr", "TR")
                val langResult = ttsEngine.setLanguage(turkishLocale)

                val languageAvailable = when (langResult) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
                    else -> {
                        // Türkçe doğrudan hazır değilse sistem varsayılan dilini dene
                        val fallbackResult = ttsEngine.setLanguage(Locale.getDefault())
                        fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
                    }
                }

                if (languageAvailable) {
                    ttsEngine.setSpeechRate(1.0f)
                    ttsEngine.setPitch(1.0f)

                    ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == currentUtteranceId) {
                                _isSpeaking.value = false
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == currentUtteranceId) {
                                _isSpeaking.value = false
                            }
                            Log.e(tag, "TTS Oynatma Hatası - utteranceId: $utteranceId")
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId == currentUtteranceId) {
                                _isSpeaking.value = false
                            }
                            Log.e(tag, "TTS Oynatma Hatası (Kod: $errorCode) - utteranceId: $utteranceId")
                        }
                    })

                    _isReady.value = true
                    Log.d(tag, "TTS Başarıyla Başlatıldı.")
                } else {
                    Log.w(tag, "TTS Dil Paketi Eksik veya Desteklenmiyor. Kod: $langResult")
                    _isReady.value = false
                }
            }
        } else {
            Log.e(tag, "TTS Motoru Başlatılamadı (status: $status)")
            _isReady.value = false
        }
    }

    /**
     * Not metnini seslendirir veya ses çalıyorsa durdurur (Play/Stop iki yönlü eylem).
     * @return true ise işlem başlatıldı/durduruldu, false ise motor hazır değil veya metin boş
     */
    fun toggle(
        title: String,
        content: String,
        onFeedback: ((isSpeaking: Boolean, warningMessage: String?) -> Unit)? = null
    ): Boolean {
        if (_isSpeaking.value) {
            stop()
            onFeedback?.invoke(false, null)
            return true
        }

        if (!_isReady.value || tts == null) {
            // Motor henüz hazır değil veya yeniden başlatılıyor olabilir
            initializeTts()
            onFeedback?.invoke(false, "Metin okuma motoru hazırlanıyor veya Türkçe dil paketi eksik")
            return false
        }

        val combinedText = buildString {
            if (title.isNotBlank()) {
                append(title.trim())
                append(". ")
            }
            if (content.isNotBlank()) {
                append(content.trim())
            }
        }

        val sanitizedText = cleanMarkdownForSpeech(combinedText)
        if (sanitizedText.isBlank()) {
            onFeedback?.invoke(false, "Okunacak metin bulunamadı")
            return false
        }

        return speakInternal(sanitizedText, onFeedback)
    }

    private fun speakInternal(
        text: String,
        onFeedback: ((isSpeaking: Boolean, warningMessage: String?) -> Unit)?
    ): Boolean {
        val ttsEngine = tts ?: return false

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        return if (result == TextToSpeech.SUCCESS) {
            _isSpeaking.value = true
            onFeedback?.invoke(true, null)
            true
        } else {
            _isSpeaking.value = false
            onFeedback?.invoke(false, "Metin okuma başlatılamadı")
            false
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(tag, "TTS Durdurulurken hata: ${e.message}")
        } finally {
            _isSpeaking.value = false
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(tag, "TTS Kapatılırken hata: ${e.message}")
        } finally {
            tts = null
            _isSpeaking.value = false
            _isReady.value = false
        }
    }

    companion object {
        /**
         * Not gövdesindeki Markdown sembollerini, checkboxları, kod bloklarını
         * ve wikilinkleri doğal konuşma akışına dönüştürür.
         */
        fun cleanMarkdownForSpeech(raw: String): String {
            if (raw.isBlank()) return ""

            var text = raw

            // 1. Kod bloklarını ve inline kodları temizle (```code``` -> "")
            text = text.replace(Regex("```[\\s\\S]*?```"), " ")
            text = text.replace(Regex("`([^`]+)`"), "$1")

            // 2. Wikilinks: [[Hedef|Görünen]] -> Görünen, [[Hedef]] -> Hedef
            text = text.replace(Regex("\\[\\[([^|\\]]+)\\|([^\\]]+)\\]\\]"), "$2")
            text = text.replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")

            // 3. Standart Markdown Linkleri: [Görünen Metin](https://url) -> Görünen Metin
            text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

            // 4. Checkbox'lar: - [ ] veya - [x] veya * [ ]
            text = text.replace(Regex("(?m)^\\s*[-*+]?\\s*\\[[ xX]?\\]\\s*"), "")

            // 5. Başlıklar: # Başlık -> Başlık
            text = text.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")

            // 6. Kalın ve İtalik: **metin**, *metin*, __metin__, _metin_, ~~strikethrough~~
            text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
            text = text.replace(Regex("__([^_]+)__"), "$1")
            text = text.replace(Regex("_([^_]+)_"), "$1")
            text = text.replace(Regex("~~([^~]+)~~"), "$1")

            // 7. Alıntı ve Liste İmleri: > alıntı, * liste, - liste, 1. liste
            text = text.replace(Regex("(?m)^\\s*>\\s*"), "")
            text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
            text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")

            // 8. Yatay Çizgiler: ---, ***, ___
            text = text.replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), " ")

            // 9. HTML etiketleri: <br>, <b> vb.
            text = text.replace(Regex("<[^>]*>"), " ")

            // 10. URL'leri doğrudan temizle
            text = text.replace(Regex("https?://\\S+"), "")

            // 11. Fazla boşlukları ve ardışık boş satırları toparla
            text = text.replace(Regex("[ \\t]+"), " ")
            text = text.replace(Regex("(\\r?\\n){2,}"), ". ")
            text = text.replace(Regex("\\r?\\n"), " ")

            return text.trim()
        }
    }
}
