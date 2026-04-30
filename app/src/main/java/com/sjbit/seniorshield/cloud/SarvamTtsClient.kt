package com.sjbit.seniorshield.cloud

import android.content.Context
import android.util.Base64
import com.sjbit.seniorshield.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SarvamTtsClient(private val context: Context) {
    suspend fun synthesize(text: String, languageTag: String, speechRate: Float): File? {
        val apiKey = BuildConfig.SARVAM_API_KEY
        if (apiKey.isBlank() || text.isBlank()) return null

        return runCatching {
            val payload = JSONObject().apply {
                put("text", text.take(2400))
                put("target_language_code", toSarvamLanguageCode(languageTag))
                put("model", "bulbul:v3")
                put("speaker", speakerFor(languageTag))
                put("pace", speechRate.coerceIn(0.65f, 1.1f).toDouble())
                put("output_audio_codec", "wav")
            }

            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("api-subscription-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            parseToAudioFile(response)
        }.getOrNull()
    }

    private fun parseToAudioFile(body: String): File? {
        if (body.isBlank()) return null
        val root = JSONObject(body)
        val audios = root.optJSONArray("audios") ?: return null
        val audioBase64 = audios.optString(0).takeIf { it.isNotBlank() } ?: return null
        val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val outFile = File.createTempFile("sarvam_tts_", ".wav", context.cacheDir)
        outFile.writeBytes(audioBytes)
        return outFile
    }

    private fun toSarvamLanguageCode(languageTag: String): String {
        return when {
            languageTag.startsWith("hi", ignoreCase = true) -> "hi-IN"
            languageTag.startsWith("kn", ignoreCase = true) -> "kn-IN"
            else -> "en-IN"
        }
    }

    private fun speakerFor(languageTag: String): String {
        return when {
            languageTag.startsWith("hi", ignoreCase = true) -> "priya"
            languageTag.startsWith("kn", ignoreCase = true) -> "kavitha"
            else -> "shubh"
        }
    }

    private companion object {
        const val API_URL = "https://api.sarvam.ai/text-to-speech"
    }
}
