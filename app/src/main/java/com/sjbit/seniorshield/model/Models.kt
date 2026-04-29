package com.sjbit.seniorshield.model

enum class MessageSource {
    SMS,
    MANUAL,
    SHARE_INTENT
}

enum class RiskLevel {
    SAFE,
    CAUTION,
    HIGH_RISK
}

data class MessageInput(
    val source: MessageSource,
    val sender: String,
    val body: String,
    val receivedAtLabel: String
)

data class LinkAnalysis(
    val originalUrl: String,
    val normalizedUrl: String,
    val finalHost: String,
    val riskLevel: RiskLevel,
    val reasons: List<String>,
    val redirectChain: List<String>,
    val nestedTargets: List<String>
)

data class PhishingAnalysisResult(
    val riskLevel: RiskLevel,
    val score: Int,
    val reasons: List<String>,
    val plainLanguageExplanation: String,
    val suggestedAction: String,
    val containsSuspiciousLink: Boolean,
    val linkAnalyses: List<LinkAnalysis>
)

data class AnalysisEntry(
    val input: MessageInput,
    val result: PhishingAnalysisResult
)

data class AlertEvent(
    val id: Long,
    val entry: AnalysisEntry,
    val autoAlertStatus: String? = null
)

data class RemoteScanResult(
    val riskLevel: RiskLevel,
    val finalScore: Int,
    val confidence: Int,
    val explanation: String,
    val suggestedAction: String,
    val indicators: List<String>,
    val familyAlertRecommended: Boolean,
    val sourceLabel: String
)
