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
}

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playFile(file: File, onComplete: () -> Unit = {}) {
        stop()
        MediaPlayer.create(context, android.net.Uri.fromFile(file))?.apply {
            player = this
            setOnCompletionListener {
                stop()
                onComplete()
            }
            start()
        }
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
    }

    val isPlaying: Boolean
        get() = player?.isPlaying == true
}
