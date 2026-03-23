package com.fierro.mensajeria.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onFinished: () -> Unit) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(url))
                prepareAsync()
                setOnPreparedListener {
                    start()
                }
                setOnCompletionListener {
                    onFinished()
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "Error: $what, $extra")
                    onFinished()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error al reproducir: ${e.message}")
            onFinished()
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
    }
}
