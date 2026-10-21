package com.example.noteapp

import com.example.noteapp.domain.util.AudioWaveformTrimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformTrimmerTest {

    @Test
    fun calculateTrimWindow_standard_range_calculates_correct_milliseconds() {
        val totalDurationMs = 10_000L // 10 saniye
        val window = AudioWaveformTrimmer.calculateTrimWindow(
            totalDurationMs = totalDurationMs,
            rawStartFraction = 0.2f,
            rawEndFraction = 0.8f
        )

        assertEquals(0.2f, window.startFraction, 0.01f)
        assertEquals(0.8f, window.endFraction, 0.01f)
        assertEquals(2000L, window.startMs)
        assertEquals(8000L, window.endMs)
        assertEquals(6000L, window.durationMs)
    }

    @Test
    fun calculateTrimWindow_swaps_inverted_fractions() {
        val totalDurationMs = 20_000L
        val window = AudioWaveformTrimmer.calculateTrimWindow(
            totalDurationMs = totalDurationMs,
            rawStartFraction = 0.7f,
            rawEndFraction = 0.3f
        )

        assertTrue(window.startFraction <= window.endFraction)
        assertEquals(6000L, window.startMs)
        assertEquals(14000L, window.endMs)
        assertEquals(8000L, window.durationMs)
    }

    @Test
    fun calculateTrimWindow_enforces_minimum_duration_threshold() {
        val totalDurationMs = 10_000L
        val window = AudioWaveformTrimmer.calculateTrimWindow(
            totalDurationMs = totalDurationMs,
            rawStartFraction = 0.5f,
            rawEndFraction = 0.51f, // 100ms aralık
            minDurationMs = 1000L // Minimum 1 saniye isteniyor
        )

        assertTrue(window.durationMs >= 1000L)
    }

    @Test
    fun calculateTrimWindow_handles_zero_or_negative_duration() {
        val window = AudioWaveformTrimmer.calculateTrimWindow(0L, 0.2f, 0.8f)
        assertEquals(0L, window.durationMs)
        assertEquals(0L, window.startMs)
        assertEquals(0L, window.endMs)
    }

    @Test
    fun sliceAmplitudes_slices_and_resamples_to_target_count() {
        val originalAmplitudes = (0 until 100).toList() // 100 örnek
        val sliced = AudioWaveformTrimmer.sliceAmplitudes(
            amplitudes = originalAmplitudes,
            startFraction = 0.25f,
            endFraction = 0.75f,
            targetSampleCount = 30
        )

        assertEquals(30, sliced.size)
        // İlk örnek ~25 civarında, son örnek ~74 civarında olmalı
        assertTrue(sliced.first() in 24..26)
        assertTrue(sliced.last() in 73..75)
    }

    @Test
    fun formatDuration_formats_minutes_and_seconds() {
        assertEquals("00:00", AudioWaveformTrimmer.formatDuration(0L))
        assertEquals("00:05", AudioWaveformTrimmer.formatDuration(5_000L))
        assertEquals("01:30", AudioWaveformTrimmer.formatDuration(90_000L))
        assertEquals("05:00", AudioWaveformTrimmer.formatDuration(300_000L))
    }

    @Test
    fun calculateByteRange_respects_header_offset() {
        val fileSize = 100_044L
        val (startByte, endByte) = AudioWaveformTrimmer.calculateByteRange(
            totalFileSize = fileSize,
            startFraction = 0.1f,
            endFraction = 0.5f,
            headerOffset = 44L
        )

        assertEquals(44L + 10_000L, startByte)
        assertEquals(44L + 50_000L, endByte)
    }
}
