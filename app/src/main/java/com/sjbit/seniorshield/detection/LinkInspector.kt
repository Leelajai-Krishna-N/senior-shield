package com.sjbit.seniorshield.detection

import com.sjbit.seniorshield.model.LinkAnalysis
import com.sjbit.seniorshield.model.RiskLevel
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LinkInspector {
    private val urlRegex = Regex("""https?://[^\s<>()]+""", RegexOption.IGNORE_CASE)
    private val bareDomainRegex = Regex(
        """(?i)\b(?:[a-z0-9-]+\.)+(?:com|net|org|in|co|io|ai|app|info|biz|me|gov|edu)\b(?:/[^\s<>()]*)?"""
    )
    private val shortenerHosts = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "rb.gy", "tiny.one"
    )
    private val suspiciousWords = setOf(
        "kyc", "verify", "suspend", "suspended", "lottery", "winner", "gift",
        "claim", "bank", "upi", "refund", "wallet", "otp", "login", "urgent",
        "help", "rescue", "kidnap", "kidnapped", "hospital", "emergency",
        "pan", "aadhaar", "aadhar", "rekyc", "re-kyc"
    )
    private val sensitiveHostWords = setOf(
        "secure", "update", "verify", "bank", "account", "login", "otp", "kyc", "support"
    )
    private val trustedHosts = setOf(
        "hdfcbank.com", "icicibank.com", "axisbank.com", "onlinesbi.sbi", "sbi.co.in",
        "kotak.com", "pnbindia.in", "uidai.gov.in", "income.gov.in", "incometax.gov.in",
        "google.com", "amazon.in", "amazon.com", "microsoft.com", "elevenlabs.io",
        "phonepe.com", "paytm.com", "whatsapp.com"
    )
    private val knownNestedParams = setOf("url", "u", "redirect", "target", "dest", "destination", "redir")
    private val protectedBrands = setOf(
        "microsoft", "elevenlabs", "google", "amazon", "paypal", "whatsapp", "phonepe", "gpay"
    )

    fun extractLinks(text: String): List<String> {
        val explicit = urlRegex.findAll(text).map { match ->
            match.value.trimEnd('.', ',', ')', ']', '>')
        }.toMutableList()

        val bareDomains = bareDomainRegex.findAll(text).map { match ->
            match.value.trimEnd('.', ',', ')', ']', '>')
        }.filter { found ->
            explicit.none { link -> link.contains(found, ignoreCase = true) }
        }.map { found ->
            if (found.startsWith("http://", true) || found.startsWith("https://", true)) found else "https://$found"
        }

        explicit += bareDomains
        return explicit.distinct()
    }

    fun inspect(url: String): LinkAnalysis {
        val normalized = normalize(url)
        val uri = runCatching { URI(normalized) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase()
        val reasons = mutableListOf<String>()
        val nestedTargets = mutableListOf<String>()
        var score = 0

        if (host.isBlank()) {
            reasons += "The link format looks broken or hidden."
            score += 40
        }

        if (host in shortenerHosts) {
            reasons += "This link uses a shortener, so the real destination is hidden."
            score += 20
        }

        if (host.startsWith("xn--")) {
            reasons += "The link uses punycode, which can hide lookalike domains."
            score += 35
        }

        if (host.count { it == '-' } >= 2) {
            reasons += "The link host uses multiple hyphens, which is common in scam domains."
            score += 12
        }

        if (host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
            reasons += "The link uses a raw IP address instead of a normal website name."
            score += 30
        }

        findLookalikeBrand(host)?.let { brand ->
            reasons += "The link looks like it is pretending to be $brand."
            score += 35
        }

        if (normalized.contains("@")) {
            reasons += "The link contains '@', which can disguise the real destination."
            score += 15
        }

        val pathAndQuery = listOfNotNull(uri?.path, uri?.query).joinToString("?").lowercase()
        suspiciousWords.forEach { word ->
            if (pathAndQuery.contains(word)) {
                reasons += "The link contains scam-related wording like '$word'."
                score += 7
            }
        }

        val hostWordsHit = sensitiveHostWords.count { host.contains(it) }
        val isTrustedHost = trustedHosts.any { trusted -> host == trusted || host.endsWith(".$trusted") }
        if (hostWordsHit >= 2 && !isTrustedHost) {
            reasons += "The link host uses sensitive words often seen in phishing pages."
            score += 20
        }

        parseNestedTargets(uri).forEach { nested ->
            nestedTargets += nested
            reasons += "The link hides another destination inside it."
            score += 18
        }

        val redirectChain = buildList {
            add(normalized)
            addAll(nestedTargets.distinct())
        }

        val riskLevel = when {
            score >= 45 -> RiskLevel.HIGH_RISK
            score >= 20 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }

        return LinkAnalysis(
            originalUrl = url,
            normalizedUrl = normalized,
            finalHost = host,
            riskLevel = riskLevel,
            reasons = reasons.distinct(),
            redirectChain = redirectChain,
            nestedTargets = nestedTargets.distinct()
        )
    }

    private fun normalize(url: String): String {
        return url.replace("&amp;", "&")
    }

    private fun parseNestedTargets(uri: URI?): List<String> {
        if (uri == null || uri.rawQuery.isNullOrBlank()) return emptyList()

        return uri.rawQuery
            .split("&")
            .mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                if (pieces.size != 2) return@mapNotNull null
                val key = pieces[0].lowercase()
                if (key !in knownNestedParams) return@mapNotNull null

                URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
                    .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
    }

    private fun findLookalikeBrand(host: String): String? {
        if (host.isBlank()) return null

        val labels = host.split(".")
        return labels.firstNotNullOfOrNull { label ->
            protectedBrands.firstOrNull { brand ->
                label != brand && normalizeLookalikeToken(label) == brand
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
}
