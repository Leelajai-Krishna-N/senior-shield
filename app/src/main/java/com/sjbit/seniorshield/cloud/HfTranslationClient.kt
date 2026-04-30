package com.sjbit.seniorshield.cloud

import com.sjbit.seniorshield.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class HfTranslationClient {
    suspend fun translate(text: String, targetLanguage: String): String? {
        if (text.isBlank()) return null
        val normalizedTarget = targetLanguage.lowercase()
        val sourceLanguage = detectLanguage(text)

        if (sourceLanguage == normalizedTarget) return text

        val google = requestGoogleTranslation(text, normalizedTarget)
        if (!google.isNullOrBlank()) return google

        val token = BuildConfig.HF_API_TOKEN
        if (token.isBlank()) return null

        // Direct routes where possible.
        val direct = when (sourceLanguage to normalizedTarget) {
            "en" to "hi" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_EN_HI_MODEL_ID, token)
            "en" to "kn" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_EN_KN_MODEL_ID, token)
            "hi" to "en" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_HI_EN_MODEL_ID, token)
            "kn" to "en" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_KN_EN_MODEL_ID, token)
            else -> null
        }
        if (!direct.isNullOrBlank()) return direct

        // Hindi <-> Kannada and unknown scripts use English pivot.
        val toEnglish = when (sourceLanguage) {
            "hi" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_HI_EN_MODEL_ID, token)
            "kn" -> requestTranslation(text, BuildConfig.HF_TRANSLATE_KN_EN_MODEL_ID, token)
            else -> text
        } ?: return null

        return when (normalizedTarget) {
            "hi" -> requestTranslation(toEnglish, BuildConfig.HF_TRANSLATE_EN_HI_MODEL_ID, token)
            "kn" -> requestTranslation(toEnglish, BuildConfig.HF_TRANSLATE_EN_KN_MODEL_ID, token)
            "en" -> toEnglish
            else -> null
        }
    }

    private fun requestTranslation(text: String, modelId: String, token: String): String? {
        return runCatching {
            val connection = (URL(apiUrl(modelId)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(JSONObject().put("inputs", text).toString())
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                ?: return null

            val response = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parse(response)
        }.getOrNull()
    }

    private fun detectLanguage(text: String): String {
        if (text.any { it.code in 0x0900..0x097F }) return "hi"
        if (text.any { it.code in 0x0C80..0x0CFF }) return "kn"
        return "en"
    }

    private fun apiUrl(modelId: String): String {
        return "https://router.huggingface.co/hf-inference/models/$modelId"
    }

    private fun requestGoogleTranslation(text: String, targetLanguage: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
            val url =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dt=t&q=$encoded"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "SeniorShield/1.0")
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                ?: return null
            val response = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseGoogle(response)
        }.getOrNull()
    }

    private fun parse(body: String): String? {
        val array = JSONArray(body)
        if (array.length() == 0) return null
        val obj = array.optJSONObject(0) ?: return null
        return obj.optString("translation_text").takeIf { it.isNotBlank() }
    }

    private fun parseGoogle(body: String): String? {
        val root = JSONArray(body)
        val sentences = root.optJSONArray(0) ?: return null
        val builder = StringBuilder()
        for (index in 0 until sentences.length()) {
            val sentence = sentences.optJSONArray(index) ?: continue
            val translated = sentence.optString(0)
            if (translated.isNotBlank()) {
                builder.append(translated)
            }
        }
        return builder.toString().trim().takeIf { it.isNotBlank() }
    }
}
