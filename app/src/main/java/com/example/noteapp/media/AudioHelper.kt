package com.example.noteapp.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun start(outputFile: File) {
        createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(outputFile).fd)

            prepare()
            start()
            recorder = this
        }
    }

    fun stop() {
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.reset()
        recorder = null
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

    fun playFile(file: File, speed: Float = 1.0f, onComplete: () -> Unit = {}) {
        stop()
        currentSpeed = speed
        MediaPlayer.create(context, android.net.Uri.fromFile(file))?.apply {
            player = this
            setOnCompletionListener {
                stop()
                onComplete()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                try {
                    playbackParams = playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            start()
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

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    val isPlaying: Boolean
        get() = try { player?.isPlaying == true } catch (_: Exception) { false }

    val currentPosition: Int
        get() = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    val duration: Int
        get() = try { player?.duration ?: 0 } catch (_: Exception) { 0 }
}
