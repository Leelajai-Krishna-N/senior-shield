package com.sjbit.seniorshield.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.sjbit.seniorshield.cloud.HfTranslationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translator = HfTranslationClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var pendingTag: String = "system"
    private val deviceLanguageTag = Locale.getDefault().toLanguageTag()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pendingText?.let { queued ->
            speak(queued, pendingTag)
            pendingText = null
        }
    }

    fun speak(text: String, languageTag: String) {
        if (!ready) {
            pendingText = text
            pendingTag = languageTag
            return
        }
        val locale = localeFromTag(languageTag)
        val shouldTranslateToHindi = locale.language == "hi" && !containsDevanagari(text)
        if (shouldTranslateToHindi) {
            scope.launch {
                val spokenText = translator.translateToHindi(text) ?: text
                mainHandler.post {
                    applyBestLanguage(locale)
                    tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "senior-shield")
                }
            }
        } else {
            applyBestLanguage(locale)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "senior-shield")
        }
    }

    fun speakInDeviceLanguage(text: String) {
        speak(text, deviceLanguageTag)
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }

    private fun localeFromTag(tag: String): Locale {
        if (tag.equals("system", ignoreCase = true)) return Locale.getDefault()
        return runCatching { Locale.forLanguageTag(tag) }.getOrDefault(Locale.getDefault())
    }

    private fun containsDevanagari(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    private fun applyBestLanguage(requested: Locale) {
        val status = tts.setLanguage(requested)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
    }
}
