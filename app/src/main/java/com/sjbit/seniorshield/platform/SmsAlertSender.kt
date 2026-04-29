package com.sjbit.seniorshield.platform

import android.telephony.SmsManager
import com.sjbit.seniorshield.model.AnalysisEntry

object SmsAlertSender {
    fun sendFamilyAlert(contact: String, entry: AnalysisEntry): Result<String> {
        val normalizedContact = normalizePhoneNumber(contact)
            ?: return Result.failure(IllegalArgumentException("Invalid phone number"))

        val body = buildString {
            append("Possible phishing or harmful message detected.\n")
            append("Sender: ${entry.input.sender}\n")
            append("Risk: ${entry.result.riskLevel}\n")
            append("Reason: ${entry.result.plainLanguageExplanation}\n")
            append("Message: ${entry.input.body.take(240)}")
        }

        return runCatching {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(normalizedContact, null, parts, null, null)
            normalizedContact
        }
    }

    fun normalizePhoneNumber(raw: String): String? {
        val trimmed = raw.trim().replace(" ", "").replace("-", "")
        if (trimmed.isBlank()) return null

        return when {
            trimmed.startsWith("+") -> trimmed.takeIf { it.drop(1).all(Char::isDigit) && it.length in 11..15 }
            trimmed.startsWith("00") -> "+${trimmed.drop(2)}".takeIf { it.drop(1).all(Char::isDigit) && it.length in 11..15 }
            trimmed.length == 10 && trimmed.all(Char::isDigit) -> "+91$trimmed"
            trimmed.length in 11..15 && trimmed.all(Char::isDigit) -> "+$trimmed"
            else -> null
        }
    }
}
