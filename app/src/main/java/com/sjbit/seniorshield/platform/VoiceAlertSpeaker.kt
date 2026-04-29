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

object VoiceAlertSpeaker : TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translator = HfTranslationClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingLanguageTag: String = "hi-IN"

    fun speak(context: Context, text: String, languageTag: String) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }

        if (!ready) {
            pendingText = text
            pendingLanguageTag = languageTag
            return
        }
        speakReady(text, languageTag)
    }

    private fun speakReady(text: String, languageTag: String) {
        val locale = localeFromTag(languageTag)
        val shouldTranslateToHindi = locale.language == "hi" && !containsDevanagari(text)
        if (shouldTranslateToHindi) {
            scope.launch {
                val spokenText = translator.translateToHindi(text) ?: text
                mainHandler.post {
                    applyBestLanguage(locale)
                    tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "senior-shield-global")
                }
            }
        } else {
            applyBestLanguage(locale)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "senior-shield-global")
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pendingText?.let { queued ->
            speakReady(queued, pendingLanguageTag)
            pendingText = null
        }
    }

    private fun localeFromTag(tag: String): Locale {
        if (tag.equals("system", ignoreCase = true)) return Locale.getDefault()
        return runCatching { Locale.forLanguageTag(tag) }.getOrDefault(Locale.getDefault())
    }

    private fun containsDevanagari(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    private fun applyBestLanguage(requested: Locale) {
        val current = tts ?: return
        val status = current.setLanguage(requested)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = current.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                current.setLanguage(Locale.US)
            }
        }
    }
}
