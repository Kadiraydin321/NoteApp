package com.example.noteapp

import com.example.noteapp.domain.util.WaveformHelper
import org.junit.Assert.*
import org.junit.Test

class WaveformHelperTest {

    @Test
    fun normalizeAmplitude_handlesBoundariesCorrectly() {
        // Sıfır veya negatif
        assertEquals(0.08f, WaveformHelper.normalizeAmplitude(0), 0.001f)
        assertEquals(0.08f, WaveformHelper.normalizeAmplitude(-500), 0.001f)

        // Maksimum
        assertEquals(1.0f, WaveformHelper.normalizeAmplitude(32767), 0.001f)
        assertEquals(1.0f, WaveformHelper.normalizeAmplitude(50000), 0.001f)

        // Orta değerler
        val mid = WaveformHelper.normalizeAmplitude(8192)
        assertTrue(mid > 0.08f && mid < 1.0f)
    }

    @Test
    fun generateConsistentWaveform_isDeterministicAndBounded() {
        val bars1 = WaveformHelper.generateConsistentWaveform(123456L, 36)
        val bars2 = WaveformHelper.generateConsistentWaveform(123456L, 36)
        val barsDiff = WaveformHelper.generateConsistentWaveform(987654L, 36)

        assertEquals(36, bars1.size)
        assertEquals(bars1, bars2) // Aynı tohum aynı profili üretmeli
        assertNotEquals(bars1, barsDiff) // Farklı tohum farklı profil üretmeli

        bars1.forEach { amp ->
            assertTrue("Genlik 0.12 ile 1.0 arasında olmalı: $amp", amp in 0.12f..1.0f)
        }
    }

    @Test
    fun formatTime_formatsMinutesAndSecondsAccurately() {
        assertEquals("00:00", WaveformHelper.formatTime(0))
        assertEquals("00:05", WaveformHelper.formatTime(5000))
        assertEquals("01:05", WaveformHelper.formatTime(65000))
        assertEquals("12:34", WaveformHelper.formatTime((12 * 60 + 34) * 1000))
    }

    @Test
    fun calculateActiveBarCount_calculatesProportionalBars() {
        assertEquals(0, WaveformHelper.calculateActiveBarCount(0.0f, 32))
        assertEquals(16, WaveformHelper.calculateActiveBarCount(0.5f, 32))
        assertEquals(32, WaveformHelper.calculateActiveBarCount(1.0f, 32))
        assertEquals(32, WaveformHelper.calculateActiveBarCount(1.5f, 32))
        assertEquals(0, WaveformHelper.calculateActiveBarCount(-0.2f, 32))
    }
}
