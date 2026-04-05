package com.example.myapplication.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"
    }

    private val tts = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isReady) {
                tts.setSpeechRate(0.9f)
                tts.setPitch(1.0f)
                Log.i(TAG, "TTS 初始化成功")
            } else {
                Log.e(TAG, "TTS 语言不支持")
            }
        } else {
            Log.e(TAG, "TTS 初始化失败")
        }
    }

    fun speak(text: String) {
        if (isReady && text.isNotBlank()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak_${System.currentTimeMillis()}")
        }
    }

    fun setSpeechRate(rate: Float) {
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setLocaleUS() {
        tts.setLanguage(Locale.US)
    }

    fun setLocaleUK() {
        tts.setLanguage(Locale.UK)
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
