package com.sjbit.seniorshield.detection

import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhishingDetectorTest {
    private val detector = PhishingDetector()

    @Test
    fun flagsFraudLanguageAndNestedRedirectLink() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "VK-BANK",
                body = "Urgent KYC update. Verify now: https://bit.ly/demo?url=https%3A%2F%2Ffake-login.example%2Fverify",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.containsSuspiciousLink)
        assertTrue(result.linkAnalyses.first().nestedTargets.isNotEmpty())
    }

    @Test
    fun keepsNormalMessageSafe() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Airtel",
                body = "Your plan is renewed successfully. Thank you for using Airtel.",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.SAFE, result.riskLevel)
    }

    @Test
    fun flagsEmergencyHelpStyleKidnapScam() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Unknown",
                body = "Mom save me please. I am kidnapped. Don't call anyone. Send money urgently to this number.",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("emergency-pressure") })
    }

    @Test
    fun flagsLookalikeMicrosoftAndElevenlabsImpersonation() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Support",
                body = "rnicrosoft security alert. Visit https://rnicrosoft-support.example now. e1evenlabs billing team also needs urgent verification.",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("microsoft", ignoreCase = true) })
        assertTrue(result.reasons.any { it.contains("elevenlabs", ignoreCase = true) })
    }

    @Test
    fun flagsDirectThreatLanguageAsHighRisk() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Unknown",
                body = "Nigga ur in danger fuck u",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("threat", ignoreCase = true) || it.contains("danger", ignoreCase = true) })
    }

    @Test
    fun flagsAbusiveViolencePatternAsHighRisk() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Friend",
                body = "Nigga I'll fuck u up badly",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("violence", ignoreCase = true) || it.contains("threat", ignoreCase = true) })
    }

    @Test
    fun flagsSexualLureAndBareDomainLinkAsHighRisk() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Unknown",
                body = "I am 1km away from you. Click this link to see my location. Openphish.com",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.linkAnalyses.isNotEmpty())
        assertTrue(result.reasons.any { it.contains("lure", ignoreCase = true) || it.contains("open a link", ignoreCase = true) })
    }

    @Test
    fun keepsTrustedBankOtpMessageSafe() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "HDFCBK",
                body = "Your OTP for netbanking login is 472991. Do not share this OTP with anyone.",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.SAFE, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("Trusted sender OTP pattern", ignoreCase = true) })
    }

    @Test
    fun flagsPanAadhaarKycLureAsHighRisk() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "VK-KYCUPD",
                body = "Your PAN and Aadhaar re-KYC is pending. Click this link now to avoid account block: pan-verify.example",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("pan", ignoreCase = true) || it.contains("aadhaar", ignoreCase = true) || it.contains("kyc", ignoreCase = true) })
    }

    @Test
    fun flagsNearbyUrgencyLureMessage() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Unknown",
                body = "Service available 500m. Come fast right now.",
                receivedAtLabel = "now"
            )
        )

        assertTrue(result.riskLevel == RiskLevel.CAUTION || result.riskLevel == RiskLevel.HIGH_RISK)
        assertTrue(result.reasons.any { it.contains("nearby-distance urgency", ignoreCase = true) })
    }

    @Test
    fun flagsUrgentUnknownLinkScamContext() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "Alert",
                body = "Your bank account will be blocked in 2 hours. Click here to verify: http://secure-update-info.com",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.containsSuspiciousLink)
        assertTrue(result.reasons.any { it.contains("urgency", ignoreCase = true) })
    }

    @Test
    fun keepsTrustedOtpWithOfficialLinkSafe() {
        val result = detector.analyze(
            MessageInput(
                source = MessageSource.MANUAL,
                sender = "HDFCBK",
                body = "Your OTP is 817263. Login only at https://netbanking.hdfcbank.com",
                receivedAtLabel = "now"
            )
        )

        assertEquals(RiskLevel.SAFE, result.riskLevel)
    }
}
