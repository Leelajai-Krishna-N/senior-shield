package com.sjbit.seniorshield.cloud

import com.sjbit.seniorshield.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CallGuardAnalysis(
    val decision: String,
    val confidence: Int,
    val reasoning: String,
    val warning: String,
    val tactics: List<String> = emptyList(),
    val legitimacySignal: String = "unknown"
) {
    val isScam: Boolean
        get() = decision.equals("scam", ignoreCase = true) && confidence >= 60
}

class GroqCallAnalysisClient {
    fun isConfigured(): Boolean = BuildConfig.GROQ_API_KEY.isNotBlank()
    private fun hasOpenAiStt(): Boolean = BuildConfig.OPENAI_STT_API_KEY.isNotBlank()
    private fun hasGroqStt(): Boolean = BuildConfig.GROQ_API_KEY.isNotBlank()

    suspend fun transcribeAudio(file: File, languageHint: String? = null): String? {
        if ((!hasOpenAiStt() && !hasGroqStt()) || !file.exists() || file.length() <= 0L) return null
        fun requestTranscription(url: String, token: String, model: String, lang: String?): String? = runCatching {
            val boundary = "----SeniorShield${UUID.randomUUID()}"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 120000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            connection.outputStream.use { output ->
                output.write(textPart(boundary, "model", model))
                output.write(textPart(boundary, "response_format", "json"))
                output.write(filePart(boundary, "file", file.name, "audio/mp4", file.readBytes()))
                output.write("--$boundary--\r\n".toByteArray())
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            JSONObject(responseText).optString("text").trim().takeIf { it.isNotBlank() }
        }.getOrNull()

        val providers = buildList {
            if (hasOpenAiStt()) add(
                Triple(
                    BuildConfig.OPENAI_STT_URL,
                    BuildConfig.OPENAI_STT_API_KEY,
                    BuildConfig.OPENAI_STT_MODEL.ifBlank { "whisper-1" }
                )
            )
            if (hasGroqStt()) add(
                Triple(
                    STT_URL,
                    BuildConfig.GROQ_API_KEY,
                    BuildConfig.GROQ_STT_MODEL.ifBlank { "whisper-large-v3-turbo" }
                )
            )
        }

        val candidates = mutableListOf<String>()
        for ((url, token, model) in providers) {
            requestTranscription(url, token, model, null)?.let { candidates += it }
        }
        return chooseBestTranscript(candidates)
    }

    suspend fun analyzeTranscript(transcript: String): CallGuardAnalysis? {
        if (!isConfigured() || transcript.isBlank()) return null
        return runCatching {
            val payload = JSONObject().apply {
                put("model", BuildConfig.GROQ_CHAT_MODEL)
                put("temperature", 0)
                put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject().apply {
                                put("role", "system")
                                put(
                                    "content",
                                    """
                                    You protect senior citizens in India from scam phone calls.
                                    Analyze the transcript and return ONLY strict JSON with:
                                    {"decision":"safe|scam|unsure","confidence":0-100,"reasoning":"plain explanation","warning":"one short warning sentence","tactics":["..."],"legitimacySignal":"likely_legit|unknown|highly_suspicious"}
                                    The transcript can be in English, Hindi, Kannada, Hinglish, or mixed language.
                                    Mark as scam if the caller pressures for OTP, PIN, UPI approval, QR scan, remote access, bank details, urgent transfer, fake police/bank/KYC threats, emergency money requests, impersonation, or coercive fear tactics.
                                    Important: even if caller claims to be bank/police/employee, asking OTP/PIN/UPI/remote app install is highly_suspicious.
                                    """.trimIndent()
                                )
                            }
                        )
                        .put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", "Transcript chunk:\n$transcript")
                            }
                        )
                )
            }

            val responseText = postJson(CHAT_URL, payload.toString())
            val content = extractChatContent(responseText)
            val obj = JSONObject(stripCodeFence(content))
            val tactics = obj.optJSONArray("tactics")?.toStringList().orEmpty().filter { it.isNotBlank() }
            CallGuardAnalysis(
                decision = obj.optString("decision", "unsure"),
                confidence = obj.optInt("confidence", 0).coerceIn(0, 100),
                reasoning = obj.optString("reasoning", "This call chunk needs caution.").trim(),
                warning = obj.optString("warning", "Possible scam call detected.").trim(),
                tactics = tactics,
                legitimacySignal = obj.optString("legitimacySignal", "unknown").trim().ifBlank { "unknown" }
            )
        }.getOrNull()
    }

    private fun postJson(url: String, payload: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 90000
            setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.bufferedWriter().use { it.write(payload) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return responseText
    }

    private fun extractChatContent(body: String): String {
        val obj = JSONObject(body)
        return obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun stripCodeFence(value: String): String {
        return value
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun chooseBestTranscript(candidates: List<String>): String? {
        return candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot(::isLikelyFillerTranscript)
            .maxByOrNull(::transcriptScore)
    }

    private fun transcriptScore(value: String): Int {
        val lower = value.lowercase()
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        val scamTerms = listOf(
            "otp", "pin", "upi", "kyc", "bank", "police", "illegal", "marijuana", "drug",
            "arrest", "pay", "crore", "lakh", "money", "transfer", "parcel", "case"
        )
        val scamBoost = scamTerms.count { lower.contains(it) } * 20
        return words.size + scamBoost
    }

    private fun isLikelyFillerTranscript(value: String): Boolean {
        val normalized = value
            .lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return true
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val fillerWords = setOf("thank", "thanks", "you", "ok", "okay")
        return words.isNotEmpty() && words.size <= 12 && words.all { it in fillerWords }
    }

    private fun JSONArray.toStringList(): List<String> {
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            items += optString(index)
        }
        return items
    }

    private fun textPart(boundary: String, name: String, value: String): ByteArray {
        return buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
            append(value).append("\r\n")
        }.toByteArray()
    }

    private fun filePart(boundary: String, name: String, filename: String, contentType: String, bytes: ByteArray): ByteArray {
        val header = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"").append(filename).append("\"\r\n")
            append("Content-Type: ").append(contentType).append("\r\n\r\n")
        }.toByteArray()
        val footer = "\r\n".toByteArray()
        return header + bytes + footer
    }

    private companion object {
        const val STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    }
}
