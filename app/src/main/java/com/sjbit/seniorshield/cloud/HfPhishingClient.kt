package com.sjbit.seniorshield.cloud

import com.sjbit.seniorshield.BuildConfig
import com.sjbit.seniorshield.model.RiskLevel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HfPhishingVerdict(
    val riskLevel: RiskLevel,
    val confidence: Float,
    val phishingProbability: Float,
    val label: String
)

class HfPhishingClient {
    suspend fun classify(text: String): HfPhishingVerdict? {
        if (text.isBlank()) return null

        val liveVerdict = runCatching {
            val live = postJson(localModelApiUrl(), JSONObject().put("text", text).toString())
            parseLocalModelResponse(live)
        }.getOrNull()
        if (liveVerdict != null) return liveVerdict

        val token = BuildConfig.HF_API_TOKEN
        if (token.isBlank()) return null

        val customVerdict = runCatching {
            val custom = postWithBearer(token, customApiUrl(), JSONObject().put("inputs", text).toString())
            parsePhishingResponse(custom, "custom")
        }.getOrNull()

        val primaryVerdict = runCatching {
            val primary = postWithBearer(token, phishingApiUrl(), JSONObject().put("inputs", text).toString())
            parsePhishingResponse(primary, "fallback")
        }.getOrNull()

        val zeroShotVerdict = runCatching {
            val zeroShot = postWithBearer(
                token = token,
                url = zeroShotApiUrl(),
                payload = JSONObject()
                    .put("inputs", text)
                    .put(
                        "parameters",
                        JSONObject()
                            .put(
                                "candidate_labels",
                                JSONArray(
                                    listOf(
                                        "phishing scam",
                                        "financial fraud",
                                        "threatening message",
                                        "sexual lure scam",
                                        "legitimate otp notification"
                                    )
                                )
                            )
                            .put("multi_label", true)
                    )
                    .toString()
            )
            parseZeroShotResponse(zeroShot)
        }.getOrNull()

        return mergeAll(listOf(customVerdict, primaryVerdict, zeroShotVerdict))
    }

    private fun postWithBearer(token: String, url: String, payload: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload)
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        val responseText = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return responseText
    }

    private fun postJson(url: String, payload: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload)
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        val responseText = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return responseText
    }

    private fun localModelApiUrl(): String = BuildConfig.LIVE_MODEL_API_URL

    private fun customApiUrl(): String {
        return "https://router.huggingface.co/hf-inference/models/${BuildConfig.HF_CUSTOM_MODEL_ID}"
    }

    private fun phishingApiUrl(): String {
        return "https://router.huggingface.co/hf-inference/models/${BuildConfig.HF_MODEL_ID}"
    }

    private fun zeroShotApiUrl(): String {
        return "https://router.huggingface.co/hf-inference/models/${BuildConfig.HF_ZERO_SHOT_MODEL_ID}"
    }

    private fun parsePhishingResponse(body: String, source: String): HfPhishingVerdict? {
        val outer = JSONArray(body)
        if (outer.length() == 0) return null

        val predictions = outer.optJSONArray(0) ?: return null
        var bestLabel = ""
        var bestScore = 0f

        for (index in 0 until predictions.length()) {
            val item = predictions.optJSONObject(index) ?: continue
            val score = item.optDouble("score", 0.0).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestLabel = item.optString("label")
            }
        }

        val normalizedLabel = bestLabel.lowercase()
        val riskLevel = when (normalizedLabel) {
            "phishing" -> if (bestScore >= 0.8f) RiskLevel.HIGH_RISK else RiskLevel.SAFE
            "benign" -> RiskLevel.SAFE
            "label_1" -> if (bestScore >= 0.75f) RiskLevel.HIGH_RISK else RiskLevel.SAFE
            "label_0" -> RiskLevel.SAFE
            else -> null
        } ?: return null

        return HfPhishingVerdict(
            riskLevel = riskLevel,
            confidence = bestScore,
            phishingProbability = when (normalizedLabel) {
                "benign", "label_0" -> 1f - bestScore
                else -> bestScore
            },
            label = "$source:$bestLabel"
        )
    }

    private fun parseZeroShotResponse(body: String): HfPhishingVerdict? {
        val obj = JSONObject(body)
        val labels = obj.optJSONArray("labels") ?: return null
        val scores = obj.optJSONArray("scores") ?: return null
        var bestLabel = ""
        var bestScore = 0f

        for (index in 0 until minOf(labels.length(), scores.length())) {
            val label = labels.optString(index)
            val score = scores.optDouble(index, 0.0).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestLabel = label
            }
        }

        val riskLevel = when (bestLabel.lowercase()) {
            "phishing scam", "financial fraud", "sexual lure scam" ->
                if (bestScore >= 0.65f) RiskLevel.HIGH_RISK else RiskLevel.SAFE
            "threatening message" ->
                if (bestScore >= 0.55f) RiskLevel.HIGH_RISK else RiskLevel.SAFE
            "legitimate otp notification" ->
                RiskLevel.SAFE
            else -> null
        } ?: return null

        return HfPhishingVerdict(
            riskLevel = riskLevel,
            confidence = bestScore,
            phishingProbability = if (bestLabel.equals("legitimate otp notification", ignoreCase = true)) {
                1f - bestScore
            } else {
                bestScore
            },
            label = bestLabel
        )
    }

    private fun parseLocalModelResponse(body: String): HfPhishingVerdict? {
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        val result = obj.optJSONObject("result") ?: return null
        val label = result.optString("label").lowercase()
        val confidence = result.optDouble("confidence", 0.0).toFloat()
        val phishingProbability = result.optJSONObject("probabilities")
            ?.optDouble("phishing", confidence.toDouble())
            ?.toFloat()
            ?: confidence

        val riskLevel = when (label) {
            "phishing" -> if (phishingProbability >= 0.75f) RiskLevel.HIGH_RISK else RiskLevel.SAFE
            "benign" -> RiskLevel.SAFE
            else -> null
        } ?: return null

        return HfPhishingVerdict(
            riskLevel = riskLevel,
            confidence = when (label) {
                "phishing" -> phishingProbability
                "benign" -> result.optJSONObject("probabilities")
                    ?.optDouble("benign", (1f - phishingProbability).toDouble())
                    ?.toFloat()
                    ?: (1f - phishingProbability)
                else -> phishingProbability
            },
            phishingProbability = phishingProbability,
            label = "live-model:$label"
        )
    }

    private fun mergeAll(verdicts: List<HfPhishingVerdict?>): HfPhishingVerdict? {
        val filtered = verdicts.filterNotNull()
        if (filtered.isEmpty()) return null

        val bestRisk = filtered.maxByOrNull { it.riskLevel.ordinal } ?: return null
        val bestConfidence = filtered.maxByOrNull { it.confidence } ?: bestRisk
        return HfPhishingVerdict(
            riskLevel = bestRisk.riskLevel,
            confidence = bestConfidence.confidence,
            phishingProbability = filtered.maxOf { it.phishingProbability.toDouble() }.toFloat(),
            label = filtered.joinToString(" | ") { it.label }
        )
    }
}
