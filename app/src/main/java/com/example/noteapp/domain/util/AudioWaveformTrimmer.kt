package com.example.noteapp.domain.util

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Kırpma penceresi modeli.
 */
data class TrimWindow(
    val startFraction: Float,
    val endFraction: Float,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
) {
    val formattedDuration: String
        get() = AudioWaveformTrimmer.formatDuration(durationMs)
}

/**
 * Ses Kaydı Dalga Formu Hassas Kırpıcı & Aralık Seçici Motoru.
 * Ses notlarının başlangıç ve bitiş yüzdelerine göre süre aralığını hesaplar,
 * dalga formu genlik dizisini hassas biçimde dilimler ve normalize eder.
 */
object AudioWaveformTrimmer {

    const val DEFAULT_MIN_TRIM_DURATION_MS = 500L

    /**
     * Başlangıç ve bitiş oranlarına (0.0f..1.0f) göre güvenli kırpma penceresini hesaplar.
     */
    fun calculateTrimWindow(
        totalDurationMs: Long,
        rawStartFraction: Float,
        rawEndFraction: Float,
        minDurationMs: Long = DEFAULT_MIN_TRIM_DURATION_MS
    ): TrimWindow {
        if (totalDurationMs <= 0) {
            return TrimWindow(0f, 1f, 0L, 0L, 0L)
        }

        var start = rawStartFraction.coerceIn(0f, 1f)
        var end = rawEndFraction.coerceIn(0f, 1f)

        if (start > end) {
            val tmp = start
            start = end
            end = tmp
        }

        val minFraction = (minDurationMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        if (end - start < minFraction) {
            if (start + minFraction <= 1f) {
                end = start + minFraction
            } else {
                start = (1f - minFraction).coerceAtLeast(0f)
                end = 1f
            }
        }

        val startMs = (start * totalDurationMs).roundToLong().coerceIn(0L, totalDurationMs)
        val endMs = (end * totalDurationMs).roundToLong().coerceIn(startMs, totalDurationMs)
        val durationMs = endMs - startMs

        return TrimWindow(
            startFraction = start,
            endFraction = end,
            startMs = startMs,
            endMs = endMs,
            durationMs = durationMs
        )
    }

    /**
     * Orijinal dalga formu genlik örneklerini kırpma aralığına göre dilimler ve
     * hedef örnekleme adedine (targetSampleCount) göre yeniden örnekler.
     */
    fun sliceAmplitudes(
        amplitudes: List<Int>,
        startFraction: Float,
        endFraction: Float,
        targetSampleCount: Int = 40
    ): List<Int> {
        if (amplitudes.isEmpty() || targetSampleCount <= 0) return emptyList()

        val start = startFraction.coerceIn(0f, 1f)
        val end = endFraction.coerceIn(0f, 1f)
        val (safeStart, safeEnd) = if (start <= end) start to end else end to start

        val startIndex = (safeStart * amplitudes.size).roundToInt().coerceIn(0, amplitudes.size - 1)
        val endIndex = (safeEnd * amplitudes.size).roundToInt().coerceIn(startIndex + 1, amplitudes.size)

        val sliced = amplitudes.subList(startIndex, endIndex)
        if (sliced.isEmpty()) return emptyList()

        if (sliced.size == targetSampleCount) return sliced

        // Hedef sayıya yeniden örnekle (downsample / upsample via linear step)
        val result = mutableListOf<Int>()
        val step = sliced.size.toDouble() / targetSampleCount.toDouble()
        for (i in 0 until targetSampleCount) {
            val srcIdx = (i * step).roundToInt().coerceIn(0, sliced.size - 1)
            result.add(sliced[srcIdx])
        }
        return result
    }

    /**
     * Milisaniye süresini "mm:ss" formatında okunabilir dizeye dönüştürür.
     */
    fun formatDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSec = safeMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.ROOT, "%02d:%02d", min, sec)
    }

    /**
     * Dosya boyutu üzerinden yaklaşık bayt aralığını hesaplar.
     */
    fun calculateByteRange(
        totalFileSize: Long,
        startFraction: Float,
        endFraction: Float,
        headerOffset: Long = 44L
    ): Pair<Long, Long> {
        if (totalFileSize <= headerOffset) return Pair(0L, totalFileSize)

        val dataSize = totalFileSize - headerOffset
        val startByte = headerOffset + (startFraction.coerceIn(0f, 1f) * dataSize).roundToLong()
        val endByte = headerOffset + (endFraction.coerceIn(0f, 1f) * dataSize).roundToLong()
        return Pair(startByte.coerceIn(headerOffset, totalFileSize), endByte.coerceIn(startByte, totalFileSize))
    }
}
