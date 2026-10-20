package com.example.noteapp.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

enum class FocusSoundType(val title: String, val iconEmoji: String) {
    PINK_NOISE("Pembe Gürültü (Yağmur)", "🌧️"),
    WHITE_NOISE("Beyaz Gürültü (Statik)", "💨"),
    SOFT_STREAM("Nehir & Akıntı", "🌊"),
    BINAURAL_ALPHA("Derin Odaklanma (Alfa 10Hz)", "🧘")
}

@Singleton
class FocusAudioSynthesizer @Inject constructor() {

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSound = MutableStateFlow(FocusSoundType.PINK_NOISE)
    val currentSound: StateFlow<FocusSoundType> = _currentSound.asStateFlow()

    private var currentVolume: Float = 0.5f

    fun start(soundType: FocusSoundType = _currentSound.value, volume: Float = currentVolume) {
        stop()
        _currentSound.value = soundType
        currentVolume = volume.coerceIn(0.05f, 1f)

        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.setVolume(currentVolume)
        audioTrack?.play()
        _isPlaying.value = true

        playbackJob = scope.launch {
            val random = Random()
            val buffer = ShortArray(bufferSize / 2)
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var phase = 0.0
            var phaseMod = 0.0

            while (isActive) {
                for (i in buffer.indices) {
                    val sample: Double = when (soundType) {
                        FocusSoundType.WHITE_NOISE -> {
                            (random.nextDouble() * 2.0 - 1.0) * 0.22
                        }
                        FocusSoundType.PINK_NOISE -> {
                            // Paul Kellet filtresi (1/f pembe gürültü)
                            val white = random.nextDouble() * 2.0 - 1.0
                            b0 = 0.99886 * b0 + white * 0.0555179
                            b1 = 0.99332 * b1 + white * 0.0750759
                            b2 = 0.96900 * b2 + white * 0.1538520
                            val pink = b0 + b1 + b2 + white * 0.5362
                            (pink * 0.06).coerceIn(-0.95, 0.95)
                        }
                        FocusSoundType.SOFT_STREAM -> {
                            // Modüle edilmiş nehir akıntısı
                            val white = random.nextDouble() * 2.0 - 1.0
                            b0 = 0.985 * b0 + white * 0.08
                            phaseMod += 0.0003
                            val lfo = (sin(phaseMod) + 1.0) * 0.5 * 0.4 + 0.6
                            (b0 * lfo * 0.08).coerceIn(-0.95, 0.95)
                        }
                        FocusSoundType.BINAURAL_ALPHA -> {
                            // 216Hz yumuşak ton + 10Hz titreşim
                            phase += 2.0 * Math.PI * 216.0 / sampleRate
                            phaseMod += 2.0 * Math.PI * 10.0 / sampleRate
                            if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                            if (phaseMod > 2.0 * Math.PI) phaseMod -= 2.0 * Math.PI
                            val carrier = sin(phase)
                            val tremolo = (sin(phaseMod) * 0.25 + 0.75)
                            carrier * tremolo * 0.12
                        }
                    }
                    buffer[i] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.01f, 1f)
        audioTrack?.setVolume(currentVolume)
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        _isPlaying.value = false
    }
}
