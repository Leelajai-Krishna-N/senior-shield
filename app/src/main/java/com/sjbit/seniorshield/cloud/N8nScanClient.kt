package com.sjbit.seniorshield.cloud

import com.sjbit.seniorshield.BuildConfig
import com.sjbit.seniorshield.detection.LinkInspector
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.RemoteScanResult
import com.sjbit.seniorshield.model.RiskLevel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class N8nScanClient(
    private val linkInspector: LinkInspector = LinkInspector()
) {
    fun isConfigured(): Boolean = BuildConfig.N8N_SCAN_WEBHOOK_URL.isNotBlank()

    suspend fun scan(
        input: MessageInput,
        preferredUiLanguage: String,
        preferredVoiceLanguage: String,
        trigger: String = "im_not_sure",
        phishingScore: Int? = null,
        messageLanguage: String? = null,
        modelLabel: String? = null
    ): RemoteScanResult? {
        val webhookUrl = BuildConfig.N8N_SCAN_WEBHOOK_URL
        if (webhookUrl.isBlank()) return null

        return runCatching {
            val bodyLanguage = messageLanguage ?: detectLanguage(input.body)
            val payload = JSONObject().apply {
                put("sender", input.sender)
                put("message", input.body)
                put("source", input.source.name.lowercase())
                put("trigger", trigger)
                put("preferredUiLanguage", normalizeLanguage(preferredUiLanguage))
                put("preferredVoiceLanguage", normalizeLanguage(preferredVoiceLanguage))
                put("messageLanguage", bodyLanguage)
                put("smsBody", input.body)
                phishingScore?.let { put("phishingScore", it) }
                modelLabel?.let { put("modelLabel", it) }
                put("links", JSONArray(linkInspector.extractLinks(input.body)))
            }

            val connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            parseResponse(text, normalizeLanguage(preferredUiLanguage))
        }.getOrNull()
    }

    private fun parseResponse(body: String, preferredUiLanguage: String): RemoteScanResult? {
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        val localized = obj.optJSONObject("localized")
        val selected = localized?.optJSONObject(preferredUiLanguage)
            ?: localized?.optJSONObject("en")

        val indicators = mutableListOf<String>()
        val evidence = obj.optJSONObject("evidence")
        evidence?.optJSONArray("indicators")?.let { array ->
            for (index in 0 until array.length()) {
                indicators += array.optString(index)
            }
        }
        if (indicators.isEmpty()) {
            obj.optJSONArray("indicators")?.let { array ->
                for (index in 0 until array.length()) {
                    indicators += array.optString(index)
                }
            }
        }

        return RemoteScanResult(
            riskLevel = parseRiskLevel(obj.optString("verdict")),
            finalScore = obj.optInt("finalScore", obj.optInt("score", 0)),
            confidence = obj.optInt("confidence", 0),
            explanation = selected?.optString("explanation").orEmpty().ifBlank {
                obj.optString("explanation", "Deep scan finished, but did not return a clear explanation.")
            },
            suggestedAction = selected?.optString("action").orEmpty().ifBlank {
                obj.optString("suggestedAction", "Pause before acting and verify with a trusted person.")
            },
            indicators = indicators.filter { it.isNotBlank() },
            familyAlertRecommended = obj.optBoolean("familyAlertRecommended", false),
            sourceLabel = "n8n"
        )
    }

    private fun parseRiskLevel(value: String): RiskLevel {
        return when (value.lowercase()) {
            "phishing", "high_risk", "high-risk" -> RiskLevel.HIGH_RISK
            "caution", "suspicious" -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
    }

    private fun normalizeLanguage(tag: String): String {
        return when {
            tag.startsWith("hi", ignoreCase = true) -> "hi"
            tag.startsWith("kn", ignoreCase = true) -> "kn"
            else -> "en"
        }
    }

    private fun detectLanguage(text: String): String {
        if (text.any { it in '\u0900'..'\u097F' }) return "hi"
        if (text.any { it in '\u0C80'..'\u0CFF' }) return "kn"
        return "en"
    }
}
