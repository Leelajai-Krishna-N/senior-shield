package com.sjbit.seniorshield.detection

import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.LinkAnalysis
import com.sjbit.seniorshield.model.PhishingAnalysisResult
import com.sjbit.seniorshield.model.RiskLevel

class PhishingDetector(
    private val linkInspector: LinkInspector = LinkInspector()
) {
    private val urgentPhrases = listOf(
        "account suspended", "kyc update", "verify now", "click immediately",
        "urgent", "lottery", "claim reward", "free prize", "upi blocked", "otp",
        "pan update", "aadhaar update", "aadhar update", "re-kyc", "kyc pending",
        "come fast", "service available"
    )
    private val emergencyScamPhrases = listOf(
        "save me", "help me", "i need help", "i am in trouble", "i'm in trouble",
        "kidnap", "kidnapped", "accident", "hospital", "police station",
        "send money urgently", "send money now", "new number", "don't call",
        "call me on this number", "rescue me", "stranded", "please help urgently"
    )
    private val threatPhrases = listOf(
        "you are in danger", "ur in danger", "you're in danger", "your life is in danger",
        "i will kill you", "i'll kill you", "we will kill you", "we'll kill you",
        "i will hurt you", "i'll hurt you", "we will hurt you", "we'll hurt you",
        "i will find you", "we will find you", "you will die", "you are dead",
        "we have your family", "we have your son", "we have your daughter",
        "fuck you", "fuck u", "fuck you up", "fuck u up", "die now", "pay or else"
    )
    private val lurePhrases = listOf(
        "click this link", "click link", "see my location", "meet me now",
        "private photos", "adult video", "sexy", "hot girl", "pink cat",
        "i am near you", "1km away", "send location", "open this site"
    )
    private val protectedBrands = setOf(
        "microsoft", "elevenlabs", "google", "amazon", "paypal", "whatsapp", "phonepe", "gpay"
    )
    private val trustedSenderHints = listOf(
        "HDFC", "ICICI", "AXIS", "SBI", "KOTAK", "BOB", "PNB", "YESBANK",
        "AIRTEL", "JIO", "VI", "BSNL", "PAYTM", "PHONEPE", "GPAY",
        "AMAZON", "FLIPKART", "GOOGLE", "IRCTC", "UIDAI"
    )
    private val trustedLinkHosts = setOf(
        "hdfcbank.com", "icicibank.com", "axisbank.com", "sbi.co.in", "onlinesbi.sbi",
        "kotak.com", "pnbindia.in", "uidai.gov.in", "google.com", "amazon.in", "amazon.com",
        "phonepe.com", "paytm.com", "whatsapp.com"
    )

    fun analyze(input: MessageInput): PhishingAnalysisResult {
        val reasons = mutableListOf<String>()
        var score = 0
        val lower = input.body.lowercase()
        val senderUpper = input.sender.uppercase()
        val links = linkInspector.extractLinks(input.body)

        urgentPhrases.forEach { phrase ->
            if (lower.contains(phrase)) {
                reasons += "The message uses scam language like '$phrase'."
                score += 12
            }
        }

        emergencyScamPhrases.forEach { phrase ->
            if (lower.contains(phrase)) {
                reasons += "The message uses an emergency-pressure phrase like '$phrase'."
                score += 18
            }
        }

        threatPhrases.forEach { phrase ->
            if (lower.contains(phrase)) {
                reasons += "The message contains direct threat language like '$phrase'."
                score += 22
            }
        }

        lurePhrases.forEach { phrase ->
            if (lower.contains(phrase)) {
                reasons += "The message uses temptation or lure language like '$phrase'."
                score += 18
            }
        }

        if (Regex("""\b(?:i|ill|i'll|we|you|u|ur)\b.{0,18}\bfuck\b.{0,18}\b(?:you|u|up|badly)\b""")
                .containsMatchIn(lower)
        ) {
            reasons += "The message includes abusive violence language."
            score += 28
        }

        if (Regex("""\b(?:click|open|visit)\b.{0,22}\b(?:link|site|url|location)\b""")
                .containsMatchIn(lower)
        ) {
            reasons += "The message pushes you to open a link or site."
            score += 16
        }

        if (Regex("""\b(?:\d{2,4}\s?(?:m|km)|nearby|near you|away from you|come fast)\b""")
                .containsMatchIn(lower) &&
            Regex("""\b(?:come fast|urgent|immediately|right now|meet)\b""")
                .containsMatchIn(lower)
        ) {
            reasons += "The message uses nearby-distance urgency pressure, a common lure pattern."
            score += 22
        }

        if (Regex("""\bservice available\b""").containsMatchIn(lower) &&
            Regex("""\b\d{2,4}\s?(?:m|km)\b""").containsMatchIn(lower) &&
            Regex("""\bcome fast\b""").containsMatchIn(lower)
        ) {
            reasons += "The message uses location bait with urgent action pressure."
            score += 20
        }

        if (Regex("""\b(?:pan|aadhaar|aadhar)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:kyc|re-kyc|rekyc|update|pending|block|suspend)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:click|open|link|verify)\b""").containsMatchIn(lower)
        ) {
            reasons += "The message combines PAN/Aadhaar KYC pressure with a click action."
            score += 28
        }

        if (Regex("""\b(?:account|bank account)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:block|blocked|suspend|suspended)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:\d+\s*(?:hour|hours|min|mins|minutes)|immediately|right now)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:click|verify|update|login|link)\b""").containsMatchIn(lower)
        ) {
            reasons += "The message uses account-block fear, deadline pressure, and forced action."
            score += 30
        }

        if (Regex("""\b(?:sex|sexy|nude|adult|dick|cat)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:click|open|link|location)\b""").containsMatchIn(lower)
        ) {
            reasons += "The message mixes sexual bait with a click action."
            score += 26
        }

        if (Regex("""\b(?:danger|threat|threatening|attack|kidnap|hurt|kill|murder|die)\b""")
                .containsMatchIn(lower)
        ) {
            reasons += "The message uses personal danger or violence wording."
            score += 16
        }

        if (Regex("""\b(?:you|your|ur|u)\b""").containsMatchIn(lower) &&
            Regex("""\b(?:danger|die|dead|hurt|kill|attack)\b""").containsMatchIn(lower)
        ) {
            reasons += "The message targets the reader personally with danger language."
            score += 18
        }

        if (lower.contains("do not call") || lower.contains("dont call")) {
            reasons += "The message tries to stop you from verifying the emergency."
            score += 20
        }

        if (Regex("""\b(?:mom|mother|dad|father|amma|appa|grandma|grandpa|son|daughter)\b""")
                .containsMatchIn(lower) && Regex("""\b(?:help|money|urgent|trouble|hospital|kidnap)\b""")
                .containsMatchIn(lower)
        ) {
            reasons += "The message mixes family language with urgency, which is common in panic scams."
            score += 18
        }

        findLookalikeBrands(lower).forEach { brand ->
            reasons += "The message may be impersonating $brand with a lookalike spelling."
            score += 24
        }

        if (Regex("""\b(?:otp|pin|password)\b""").containsMatchIn(lower)) {
            reasons += "It asks for private security information."
            score += 18
        }

        if (Regex("""\b(?:pay|send money|transfer|refund)\b""").containsMatchIn(lower)) {
            reasons += "It pushes a money-related action."
            score += 16
        }

        val linkAnalyses = links.map(linkInspector::inspect)
        val hasUrgentTone = Regex("""\b(?:urgent|immediately|right now|asap|blocked|suspended|within \d+ (?:hour|hours|mins|minutes)|come fast)\b""")
            .containsMatchIn(lower)
        val hasActionPressure = Regex("""\b(?:click|open|verify|update|login|confirm|submit)\b""")
            .containsMatchIn(lower)
        if (linkAnalyses.isNotEmpty() && hasUrgentTone && hasActionPressure) {
            reasons += "The message combines urgency with a link and a forced action."
            score += 26
        }

        linkAnalyses.forEach { link ->
            when (link.riskLevel) {
                RiskLevel.HIGH_RISK -> score += 28
                RiskLevel.CAUTION -> score += 14
                RiskLevel.SAFE -> score += 0
            }
            reasons += link.reasons
        }

        if (input.sender.any { it.isDigit() }.not() && input.sender.length < 4) {
            reasons += "The sender name is unusually short or unclear."
            score += 10
        }

        applyTrustedOtpGuard(
            senderUpper = senderUpper,
            bodyLower = lower,
            linkAnalyses = linkAnalyses,
            reasons = reasons
        )?.let { adjusted ->
            score = adjusted
        } ?: run {
            score = score.coerceAtMost(100)
        }

        val riskLevel = when {
            score >= 55 -> RiskLevel.HIGH_RISK
            score >= 25 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }

        return PhishingAnalysisResult(
            riskLevel = riskLevel,
            score = score,
            reasons = reasons.distinct().ifEmpty {
                listOf("No obvious phishing patterns were found.")
            },
            plainLanguageExplanation = buildExplanation(riskLevel, reasons.distinct(), linkAnalyses.isNotEmpty()),
            suggestedAction = suggestedAction(riskLevel),
            containsSuspiciousLink = linkAnalyses.any { it.riskLevel != RiskLevel.SAFE },
            linkAnalyses = linkAnalyses
        )
    }

    private fun buildExplanation(
        riskLevel: RiskLevel,
        reasons: List<String>,
        hasLinks: Boolean
    ): String {
        val intro = when (riskLevel) {
            RiskLevel.HIGH_RISK -> "This message looks dangerous."
            RiskLevel.CAUTION -> "This message needs caution."
            RiskLevel.SAFE -> "This message looks mostly safe."
        }
        val linkNote = if (hasLinks) " I also checked the link inside the message." else ""
        val reason = reasons.firstOrNull().orEmpty()
        return "$intro$linkNote $reason".trim()
    }

    private fun suggestedAction(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.HIGH_RISK -> "Do not click anything. Ask a family member before taking action."
            RiskLevel.CAUTION -> "Pause before acting. Verify with the official website or a trusted person."
            RiskLevel.SAFE -> "No major red flags found, but never share OTP or passwords."
        }
    }

    private fun findLookalikeBrands(text: String): List<String> {
        val tokens = Regex("""[a-z0-9.-]{4,}""").findAll(text).map { it.value }.toList()
        return protectedBrands.filter { brand ->
            tokens.any { token ->
                token != brand && normalizeLookalikeToken(token) == brand
            }
        }
    }

    private fun normalizeLookalikeToken(token: String): String {
        return token.lowercase()
            .replace("rn", "m")
            .replace("vv", "w")
            .replace('0', 'o')
            .replace('1', 'l')
            .replace('3', 'e')
            .replace('5', 's')
            .replace('7', 't')
    }

    private fun applyTrustedOtpGuard(
        senderUpper: String,
        bodyLower: String,
        linkAnalyses: List<LinkAnalysis>,
        reasons: MutableList<String>
    ): Int? {
        val isTrustedSender = trustedSenderHints.any { senderUpper.contains(it) }
        val hasOtp = Regex("""\botp\b|\bone[- ]?time password\b""").containsMatchIn(bodyLower)
        val hasDangerSignals = Regex(
            """\b(?:click|link|verify now|suspended|danger|kill|hurt|lottery|prize|refund|pan update|aadhaar update|aadhar update|kyc update)\b"""
        ).containsMatchIn(bodyLower)
        val linksTrusted = linkAnalyses.isEmpty() || linkAnalyses.all { link ->
            trustedLinkHosts.any { trusted ->
                link.finalHost.equals(trusted, ignoreCase = true) ||
                    link.finalHost.endsWith(".$trusted", ignoreCase = true)
            }
        }

        if (isTrustedSender && hasOtp && linksTrusted && !hasDangerSignals) {
            reasons += "Trusted sender OTP pattern detected without risky link or coercion language."
            return 8
        }
        return null
    }
}
