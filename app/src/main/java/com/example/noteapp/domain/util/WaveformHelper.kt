package com.example.noteapp.domain.util

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ses kayıt genliklerini normalize eden, deterministik dalga formu profilleri üreten
 * ve süre/ilerleme hesaplamalarını gerçekleştiren yardımcı sınıf.
 */
object WaveformHelper {

    /**
     * Ham MediaRecorder genlik değerini (0..32767) 0.08f ile 1.0f arasında
     * insan kulağının logaritmik algısına uygun şekilde normalize eder.
     */
    fun normalizeAmplitude(rawAmplitude: Int, maxExpected: Int = 32767): Float {
        if (rawAmplitude <= 0) return 0.08f
        val clamped = rawAmplitude.coerceIn(0, maxExpected)
        // Karekök eğrisi düşük seslerin de görselleşebilmesini sağlar
        val normalized = sqrt(clamped.toFloat() / maxExpected.toFloat())
        return normalized.coerceIn(0.08f, 1.0f)
    }

    /**
     * Verilen dosya boyutu veya hash tohumuna (seed) göre deterministik,
     * konuşma ritmine benzeyen gerçekçi ve pürüzsüz bir dalga formu (40 çubuk) üretir.
     */
    fun generateConsistentWaveform(seed: Long, barCount: Int = 36): List<Float> {
        val bars = ArrayList<Float>(barCount)
        val nonZeroSeed = if (seed == 0L) 123456789L else abs(seed)

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount.toFloat()
            // Konuşma başlangıcı ve sonu genelde daha sessizdir (envelope)
            val envelope = sin(progress * Math.PI).toFloat().coerceIn(0.3f, 1.0f)

            // Çift sinüs dalgası + tohum modülasyonu
            val wave1 = abs(sin(i * 0.45 + (nonZeroSeed % 100) * 0.1)).toFloat()
            val wave2 = abs(sin(i * 1.15 + (nonZeroSeed % 37) * 0.2)).toFloat()
            val combined = (wave1 * 0.6f + wave2 * 0.4f) * envelope

            bars.add(combined.coerceIn(0.12f, 1.0f))
        }
        return bars
    }

    /**
     * Milisaniye cinsinden süreyi "mm:ss" formatına dönüştürür.
     */
    fun formatTime(timeMs: Int): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Oynatma yüzdesine (0.0f..1.0f) göre kaç çubuğun oynatıldığını hesaplar.
     */
    fun calculateActiveBarCount(progressFraction: Float, totalBars: Int): Int {
        if (progressFraction <= 0f || totalBars <= 0) return 0
        val active = (progressFraction.coerceIn(0f, 1f) * totalBars).toInt()
        return active.coerceIn(0, totalBars)
    }
}
