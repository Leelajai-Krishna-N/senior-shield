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

class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translator = HfTranslationClient()
    private val sarvamTts = SarvamTtsClient(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var mediaPlayer: MediaPlayer? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingTag: String = "system"
    private var pendingRate: Float = 0.82f
    private val deviceLanguageTag = Locale.getDefault().toLanguageTag()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pendingText?.let { queued ->
            speak(queued, pendingTag, pendingRate)
            pendingText = null
        }
    }

    fun speak(text: String, languageTag: String, speechRate: Float = 0.82f) {
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
            val audioFile = sarvamTts.synthesize(spokenText, locale.toLanguageTag(), speechRate)
            if (audioFile != null) {
                playAudioFile(audioFile)
            } else {
                fallbackToAndroidTts(spokenText, locale, speechRate)
            }
        }
    }

    fun speakInDeviceLanguage(text: String, speechRate: Float = 0.82f) {
        speak(text, deviceLanguageTag, speechRate)
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        tts.stop()
        tts.shutdown()
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
            if (!ready) {
                pendingText = text
                pendingTag = locale.toLanguageTag()
                pendingRate = speechRate
                return@post
            }
            tts.setSpeechRate(speechRate.coerceIn(0.6f, 0.95f))
            tts.setPitch(0.95f)
            applyBestLanguage(locale)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "senior-shield")
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
        val status = tts.setLanguage(requested)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
        pickBestVoiceFor(requested)?.let { best ->
            runCatching { tts.voice = best }
        }
    }

    private fun pickBestVoiceFor(locale: Locale): Voice? {
        val voices = runCatching { tts.voices }.getOrNull() ?: return null
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
