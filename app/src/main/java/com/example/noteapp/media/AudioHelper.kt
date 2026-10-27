package com.example.noteapp.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var onInterruptedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    var isPaused: Boolean = false
        private set

    val isRecording: Boolean
        get() = recorder != null && !isPaused

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Telefon çağrısı veya harici ses kesintisinde kaydı güvenle durdur ve donanımı serbest bırak
                handleInterruption()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (_: Exception) {}
    }

    private fun handleInterruption() {
        if (recorder == null) return
        stop()
        onInterruptedCallback?.invoke()
    }

    fun setOnInterruptedListener(listener: (() -> Unit)?) {
        onInterruptedCallback = listener
    }

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorCallback = listener
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    @Synchronized
    fun start(
        outputFile: File,
        onError: ((String) -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null
    ) {
        stop()
        if (onError != null) onErrorCallback = onError
        if (onInterrupted != null) onInterruptedCallback = onInterrupted

        // 1. İzin Kontrolü (Permission Revocation / Check)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onErrorCallback?.invoke("Mikrofon izni verilmedi veya iptal edildi")
            return
        }

        // 2. Ses Odağı (Telefon çağrısı kesintilerini yakalamak için)
        if (!requestAudioFocus()) {
            onErrorCallback?.invoke("Ses odağı alınamadı (arama veya başka kayıt etkin)")
            return
        }

        try {
            val rec = createRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(FileOutputStream(outputFile).fd)

                setOnErrorListener { _, what, extra ->
                    handleInterruption()
                    onErrorCallback?.invoke("MediaRecorder donanım hatası: $what, $extra")
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                        handleInterruption()
                    }
                }

                prepare()
                start()
            }
            recorder = rec
            isPaused = false
        } catch (e: Exception) {
            stop()
            onErrorCallback?.invoke("Ses kaydı başlatılamadı: ${e.localizedMessage ?: e.message}")
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recorder != null && !isPaused) {
            try {
                recorder?.pause()
                isPaused = true
            } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recorder != null && isPaused) {
            try {
                recorder?.resume()
                isPaused = false
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun stop() {
        abandonAudioFocus()
        recorder?.let { r ->
            try {
                r.stop()
            } catch (_: Exception) {}
            try {
                r.reset()
            } catch (_: Exception) {}
            try {
                r.release() // Donanımı kesin serbest bırak, bellek sızıntısını ve kilitlenmeyi önle
            } catch (_: Exception) {}
        }
        recorder = null
        isPaused = false
    }

    fun release() {
        stop()
        onInterruptedCallback = null
        onErrorCallback = null
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }
}

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var currentSpeed: Float = 1.0f
    private var audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var onInterruptedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Telefon çağrısı geldiğinde oynatmayı durdur ve serbest bırak
                handleInterruption()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Bildirim geldiğinde sesi geçici olarak kıs
                try {
                    player?.setVolume(0.2f, 0.2f)
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Bildirim bitince sesi geri yükselt
                try {
                    player?.setVolume(1.0f, 1.0f)
                } catch (_: Exception) {}
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (_: Exception) {}
    }

    private fun handleInterruption() {
        stop()
        onInterruptedCallback?.invoke()
    }

    fun setOnInterruptedListener(listener: (() -> Unit)?) {
        onInterruptedCallback = listener
    }

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorCallback = listener
    }

    @Synchronized
    fun playFile(
        file: File,
        speed: Float = 1.0f,
        onComplete: () -> Unit = {},
        onError: ((String) -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null
    ) {
        stop()
        if (onError != null) onErrorCallback = onError
        if (onInterrupted != null) onInterruptedCallback = onInterrupted

        if (!file.exists() || file.length() == 0L) {
            onErrorCallback?.invoke("Ses dosyası bulunamadı veya boş")
            return
        }

        currentSpeed = speed
        requestAudioFocus()

        try {
            val mp = MediaPlayer.create(context, android.net.Uri.fromFile(file))
            if (mp == null) {
                abandonAudioFocus()
                onErrorCallback?.invoke("MediaPlayer başlatılamadı")
                return
            }

            mp.apply {
                setOnCompletionListener {
                    stop()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    stop()
                    onErrorCallback?.invoke("Oynatma hatası: $what, $extra")
                    true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                    try {
                        playbackParams = playbackParams.setSpeed(speed)
                    } catch (_: Exception) {}
                }
                start()
            }
            player = mp
        } catch (e: Exception) {
            stop()
            onErrorCallback?.invoke("Oynatma başlatılamadı: ${e.localizedMessage ?: e.message}")
        }
    }

    fun seekBy(deltaMs: Int) {
        player?.let { p ->
            try {
                val newPos = (p.currentPosition + deltaMs).coerceIn(0, p.duration)
                p.seekTo(newPos)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        player?.let { p ->
            try {
                p.seekTo(positionMs.coerceIn(0, p.duration))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        player?.let { p ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    p.playbackParams = p.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        abandonAudioFocus()
        player?.let { p ->
            try {
                if (p.isPlaying) {
                    p.stop()
                }
            } catch (_: Exception) {}
            try {
                p.reset()
            } catch (_: Exception) {}
            try {
                p.release() // Donanımı kesin serbest bırak, bellek sızıntısını önle
            } catch (_: Exception) {}
        }
        player = null
    }

    fun release() {
        stop()
        onInterruptedCallback = null
        onErrorCallback = null
    }

    val isPlaying: Boolean
        get() = try { player?.isPlaying == true } catch (_: Exception) { false }

    val currentPosition: Int
        get() = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    val duration: Int
        get() = try { player?.duration ?: 0 } catch (_: Exception) { 0 }
}
