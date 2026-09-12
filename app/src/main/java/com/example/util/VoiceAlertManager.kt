package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.R
import java.util.Locale

object VoiceAlertManager {

    private const val TAG = "VoiceAlertManager"
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private const val HINDI_CREDIT_TEXT = "पैसे आ गए ओए!"
    private const val HINDI_DEBIT_TEXT = "पैसे कट गए ओए!"

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    isTtsInitialized = true
                }
            }
        }
    }

    fun playVoiceAlert(context: Context, isCredit: Boolean) {
        val rawResId = if (isCredit) R.raw.credit else R.raw.debit
        val fallbackText = if (isCredit) HINDI_CREDIT_TEXT else HINDI_DEBIT_TEXT

        try {
            releaseMediaPlayer()

            val mp = MediaPlayer.create(context.applicationContext, rawResId)
            if (mp != null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mp.setAudioAttributes(attributes)
                mp.setOnCompletionListener { player ->
                    try {
                        player.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                }
                mp.setOnErrorListener { player, what, extra ->
                    Log.w(TAG, "MediaPlayer error ($what, $extra), falling back to TTS")
                    try {
                        player.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                    playTtsFallback(context, fallbackText)
                    true
                }
                mediaPlayer = mp
                mp.start()
            } else {
                Log.w(TAG, "MediaPlayer.create returned null for res $rawResId, using TTS")
                playTtsFallback(context, fallbackText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio resource, falling back to TTS", e)
            playTtsFallback(context, fallbackText)
        }
    }

    private fun playTtsFallback(context: Context, text: String) {
        if (tts == null) {
            initTts(context)
        }

        if (isTtsInitialized && tts != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_alert_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        } else {
            // If TTS was not ready, re-initialize and speak
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale("hi", "IN"))
                    isTtsInitialized = true
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_alert_${System.currentTimeMillis()}")
                }
            }
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
