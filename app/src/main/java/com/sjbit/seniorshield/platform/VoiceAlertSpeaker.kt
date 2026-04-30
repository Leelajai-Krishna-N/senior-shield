package com.sjbit.seniorshield.platform

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.sjbit.seniorshield.cloud.HfTranslationClient
import com.sjbit.seniorshield.cloud.SarvamTtsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

object VoiceAlertSpeaker : TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translator = HfTranslationClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var sarvamTts: SarvamTtsClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingLanguageTag: String = "hi-IN"
    private var pendingSpeechRate: Float = 0.82f

    fun speak(context: Context, text: String, languageTag: String, speechRate: Float = 0.82f) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
        if (sarvamTts == null) {
            sarvamTts = SarvamTtsClient(context.applicationContext)
        }
        speakReady(text, languageTag, speechRate)
    }

    private fun speakReady(text: String, languageTag: String, speechRate: Float) {
        val locale = localeFromTag(languageTag)
        val targetLanguage = when {
            locale.language == "hi" && !containsDevanagari(text) -> "hi"
            locale.language == "kn" && !containsKannada(text) -> "kn"
            else -> null
        }
        scope.launch {
            val spokenText = if (targetLanguage != null) {
                translator.translate(text, targetLanguage = targetLanguage) ?: text
            } else {
                text
            }
            val audioFile = sarvamTts?.synthesize(spokenText, locale.toLanguageTag(), speechRate)
            if (audioFile != null) {
                playAudioFile(audioFile)
            } else {
                fallbackToAndroidTts(spokenText, locale, speechRate)
            }
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pendingText?.let { queued ->
            fallbackToAndroidTts(queued, localeFromTag(pendingLanguageTag), pendingSpeechRate)
            pendingText = null
        }
    }

    private fun playAudioFile(file: File) {
        mainHandler.post {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        it.release()
                        if (mediaPlayer === it) mediaPlayer = null
                        file.delete()
                    }
                    setOnErrorListener { player, _, _ ->
                        player.release()
                        if (mediaPlayer === player) mediaPlayer = null
                        file.delete()
                        true
                    }
                    prepare()
                    start()
                }
            }.onFailure {
                file.delete()
            }
        }
    }

    private fun fallbackToAndroidTts(text: String, locale: Locale, speechRate: Float) {
        mainHandler.post {
            val current = tts
            if (!ready || current == null) {
                pendingText = text
                pendingLanguageTag = locale.toLanguageTag()
                pendingSpeechRate = speechRate
                return@post
            }
            current.setSpeechRate(speechRate.coerceIn(0.6f, 0.95f))
            current.setPitch(0.95f)
            applyBestLanguage(locale)
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "senior-shield-global")
        }
    }

    private fun localeFromTag(tag: String): Locale {
        if (tag.equals("system", ignoreCase = true)) return Locale.getDefault()
        return runCatching { Locale.forLanguageTag(tag) }.getOrDefault(Locale.getDefault())
    }

    private fun containsDevanagari(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    private fun containsKannada(text: String): Boolean {
        return text.any { it.code in 0x0C80..0x0CFF }
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
        pickBestVoiceFor(current, requested)?.let { best ->
            runCatching { current.voice = best }
        }
    }

    private fun pickBestVoiceFor(current: TextToSpeech, locale: Locale): Voice? {
        val voices = runCatching { current.voices }.getOrNull() ?: return null
        return voices
            .asSequence()
            .filter { it.locale.language == locale.language }
            .filterNot { it.name.contains("embedded", ignoreCase = true) }
            .sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenByDescending { it.latency }
            )
            .firstOrNull()
    }
}
