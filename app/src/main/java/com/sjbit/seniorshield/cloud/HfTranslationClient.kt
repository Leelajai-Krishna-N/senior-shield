package com.sjbit.seniorshield.cloud

import com.sjbit.seniorshield.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HfTranslationClient {
    suspend fun translateToHindi(text: String): String? {
        val token = BuildConfig.HF_API_TOKEN
        if (token.isBlank() || text.isBlank()) return null

        return runCatching {
            val connection = (URL(apiUrl()).openConnection() as HttpURLConnection).apply {
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

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: return null

            val response = stream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parse(response)
        }.getOrNull()
    }

    private fun apiUrl(): String {
        return "https://router.huggingface.co/hf-inference/models/${BuildConfig.HF_TRANSLATE_EN_HI_MODEL_ID}"
    }

    private fun parse(body: String): String? {
        val array = JSONArray(body)
        if (array.length() == 0) return null
        val obj = array.optJSONObject(0) ?: return null
        return obj.optString("translation_text").takeIf { it.isNotBlank() }
    }
}
