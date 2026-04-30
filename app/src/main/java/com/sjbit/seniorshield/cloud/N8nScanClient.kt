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
    private val linkInspector: LinkInspector = LinkInspector(),
    private val translator: HfTranslationClient = HfTranslationClient()
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

    private suspend fun parseResponse(body: String, preferredUiLanguage: String): RemoteScanResult? {
        if (body.isBlank()) return null
        val trimmedBody = body.trim()
        if (trimmedBody.startsWith("[")) {
            return parseArrayResponse(trimmedBody, preferredUiLanguage)
        }
        if (!trimmedBody.startsWith("{")) {
            return parsePlainTextResponse(trimmedBody, preferredUiLanguage)
        }

        val obj = JSONObject(body)
        val finalDecision = parseWebhookFinalDecision(obj)
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

        val explanation = selected?.optString("explanation").orEmpty().ifBlank {
            obj.optString("explanation", "Deep scan finished, but did not return a clear explanation.")
        }
        val suggestedAction = selected?.optString("action").orEmpty().ifBlank {
            obj.optString("suggestedAction", "Pause before acting and verify with a trusted person.")
        }

        val resolvedRisk = finalDecision?.let { if (it) RiskLevel.HIGH_RISK else RiskLevel.SAFE } ?: RiskLevel.SAFE

        val translatedIndicators = indicators
            .filter { it.isNotBlank() }
            .map { translateForUi(it, preferredUiLanguage) }

        return RemoteScanResult(
            riskLevel = resolvedRisk,
            finalScore = obj.optInt("finalScore", obj.optInt("score", 0)),
            confidence = obj.optInt("confidence", 0),
            explanation = translateForUi(explanation, preferredUiLanguage),
            suggestedAction = translateForUi(suggestedAction, preferredUiLanguage),
            indicators = translatedIndicators,
            familyAlertRecommended = resolvedRisk == RiskLevel.HIGH_RISK,
            sourceLabel = "n8n"
        )
    }

    private suspend fun parseArrayResponse(body: String, preferredUiLanguage: String): RemoteScanResult {
        val array = JSONArray(body)
        val first = array.optJSONObject(0)
        val output = first?.optString("output").orEmpty().ifBlank { body }
        return parsePlainTextResponse(output, preferredUiLanguage)
    }

    private suspend fun parsePlainTextResponse(body: String, preferredUiLanguage: String): RemoteScanResult {
        val lower = body.lowercase()
        val first3 = lower.take(3)
        val isScam = when {
            first3 == "yes" -> true
            lower.startsWith("no") -> false
            else -> false
        }
        val explanation = body.trim()
        val action = if (isScam) {
            "Do not click links or share OTP. Verify with a trusted family member."
        } else {
            "Message marked safe by webhook. Stay careful and never share OTP or PIN."
        }
        return RemoteScanResult(
            riskLevel = if (isScam) RiskLevel.HIGH_RISK else RiskLevel.SAFE,
            finalScore = if (isScam) 99 else 10,
            confidence = if (isScam) 99 else 90,
            explanation = translateForUi(explanation, preferredUiLanguage),
            suggestedAction = translateForUi(action, preferredUiLanguage),
            indicators = listOf(
                translateForUi(
                    "Webhook text decision: ${if (isScam) "YES" else "NO"}",
                    preferredUiLanguage
                )
            ),
            familyAlertRecommended = isScam,
            sourceLabel = "n8n"
        )
    }

    private suspend fun translateForUi(text: String, preferredUiLanguage: String): String {
        if (text.isBlank() || preferredUiLanguage == "en") return text
        return translator.translate(text, preferredUiLanguage) ?: text
    }

    private fun parseWebhookFinalDecision(obj: JSONObject): Boolean? {
        if (obj.has("isScam")) {
            return obj.optBoolean("isScam")
        }
        if (obj.has("isSafe")) {
            return !obj.optBoolean("isSafe")
        }

        val candidateFields = listOf(
            obj.optString("output"),
            obj.optString("finalDecision"),
            obj.optString("decision"),
            obj.optString("result"),
            obj.optString("verdict"),
            obj.optString("message"),
            obj.optString("explanation")
        )

        for (field in candidateFields) {
            val value = field.trim().lowercase()
            if (value.length < 3) continue
            val first3 = value.take(3)
            if (first3 == "yes") return true
            if (value.startsWith("no")) return false
        }
        return null
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
