package com.sjbit.seniorshield.repo

import android.content.Context
import com.sjbit.seniorshield.cloud.HfPhishingClient
import com.sjbit.seniorshield.cloud.HfPhishingVerdict
import com.sjbit.seniorshield.cloud.N8nScanClient
import com.sjbit.seniorshield.detection.PhishingDetector
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.AlertEvent
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.RiskLevel
import com.sjbit.seniorshield.platform.AlertNotifier
import com.sjbit.seniorshield.platform.SeniorShieldPreferences
import com.sjbit.seniorshield.platform.SmsAlertSender
import com.sjbit.seniorshield.platform.VoiceAlertSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AnalysisRepository {
    private val detector = PhishingDetector()
    private val hfClient = HfPhishingClient()
    private val n8nClient = N8nScanClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = MutableStateFlow<List<AnalysisEntry>>(emptyList())
    private val activeAlert = MutableStateFlow<AlertEvent?>(null)

    fun entries(): StateFlow<List<AnalysisEntry>> = entries.asStateFlow()
    fun activeAlert(): StateFlow<AlertEvent?> = activeAlert.asStateFlow()
    fun clearActiveAlert() {
        activeAlert.value = null
    }

    fun analyze(context: Context, input: MessageInput): AnalysisEntry {
        val result = detector.analyze(input)
        val entry = AnalysisEntry(input = input, result = result)
        entries.value = listOf(entry) + entries.value
        val settings = SeniorShieldPreferences(context).load()
        val webhookAuthoritative = input.source == MessageSource.SMS && n8nClient.isConfigured()

        if (!webhookAuthoritative && input.source == MessageSource.SMS && result.riskLevel != RiskLevel.SAFE) {
            AlertNotifier(context).show(entry)
        }

        if (!webhookAuthoritative && input.source == MessageSource.SMS && result.riskLevel != RiskLevel.SAFE) {
            VoiceAlertSpeaker.speak(context, entry.result.plainLanguageExplanation, settings.voiceLanguageTag, settings.ttsRate)
            val autoAlertStatus = if (
                settings.autoFamilyAlert &&
                settings.trustedContact.isNotBlank() &&
                result.riskLevel == RiskLevel.HIGH_RISK
            ) {
                SmsAlertSender.sendFamilyAlert(settings.trustedContact, entry).fold(
                    onSuccess = { "Family alert sent automatically to $it." },
                    onFailure = { "Could not auto-send family alert. Check SMS permission or SIM availability." }
                )
            } else {
                null
            }

            activeAlert.value = AlertEvent(
                id = System.currentTimeMillis(),
                entry = entry,
                autoAlertStatus = autoAlertStatus
            )
        }

        if (input.source == MessageSource.SMS) {
            scope.launch {
                val verdict = hfClient.classify(input.body) ?: return@launch
                var finalEntry = mergeWithCloud(entry, verdict)
                val confirmedPhishing = isConfirmedPhishing(verdict, finalEntry)
                var usedRemoteExplanation = false
                var remoteApplied = false

                if (confirmedPhishing && n8nClient.isConfigured()) {
                    val remote = n8nClient.scan(
                        input = input,
                        preferredUiLanguage = settings.appLanguageTag,
                        preferredVoiceLanguage = settings.voiceLanguageTag,
                        trigger = "auto_sms_phishing",
                        phishingScore = (verdict.phishingProbability * 100).toInt(),
                        messageLanguage = detectLanguage(input.body),
                        modelLabel = verdict.label
                    )
                    if (remote != null) {
                        finalEntry = if (webhookAuthoritative) {
                            entry.copy(
                                result = entry.result.copy(
                                    riskLevel = remote.riskLevel,
                                    score = remote.finalScore,
                                    reasons = (entry.result.reasons + remote.indicators.map { "AI layer: $it" }).distinct(),
                                    plainLanguageExplanation = if (remote.explanation.isNotBlank()) remote.explanation else entry.result.plainLanguageExplanation,
                                    suggestedAction = if (remote.suggestedAction.isNotBlank()) remote.suggestedAction else entry.result.suggestedAction
                                )
                            )
                        } else {
                            mergeWithRemote(finalEntry, remote)
                        }
                        usedRemoteExplanation = remote.explanation.isNotBlank()
                        remoteApplied = true
                    }
                }

                val shouldUpdateEntry = if (webhookAuthoritative) {
                    remoteApplied
                } else {
                    finalEntry.result.riskLevel > entry.result.riskLevel || usedRemoteExplanation
                }
                if (shouldUpdateEntry) {
                    entries.value = listOf(finalEntry) + entries.value.filterNot { it === entry }
                    if (finalEntry.result.riskLevel == RiskLevel.HIGH_RISK && (!webhookAuthoritative || remoteApplied)) {
                        AlertNotifier(context).show(finalEntry)
                        VoiceAlertSpeaker.speak(context, finalEntry.result.plainLanguageExplanation, settings.voiceLanguageTag, settings.ttsRate)
                    }

                    val autoAlertStatus = if (
                        settings.autoFamilyAlert &&
                        settings.trustedContact.isNotBlank() &&
                        finalEntry.result.riskLevel == RiskLevel.HIGH_RISK
                    ) {
                        SmsAlertSender.sendFamilyAlert(settings.trustedContact, finalEntry).fold(
                            onSuccess = { "Family alert sent automatically to $it." },
                            onFailure = { "Could not auto-send family alert. Check SMS permission or SIM availability." }
                        )
                    } else {
                        null
                    }

                    activeAlert.value = if (finalEntry.result.riskLevel == RiskLevel.HIGH_RISK) {
                        AlertEvent(
                            id = System.currentTimeMillis(),
                            entry = finalEntry,
                            autoAlertStatus = autoAlertStatus
                        )
                    } else {
                        null
                    }
                } else if (webhookAuthoritative) {
                    activeAlert.value = null
                }
            }
        }

        return entry
    }

    private fun mergeWithCloud(localEntry: AnalysisEntry, verdict: HfPhishingVerdict): AnalysisEntry {
        val mergedRisk = if (localEntry.result.riskLevel.ordinal >= verdict.riskLevel.ordinal) {
            localEntry.result.riskLevel
        } else {
            verdict.riskLevel
        }

        if (mergedRisk == localEntry.result.riskLevel) return localEntry

        val mergedReasons = localEntry.result.reasons.toMutableList().apply {
            add(
                when (verdict.riskLevel) {
                    RiskLevel.HIGH_RISK -> "AI phishing model flagged this message as phishing (${(verdict.confidence * 100).toInt()}% confidence)."
                    RiskLevel.CAUTION -> "AI phishing model found suspicious patterns (${(verdict.confidence * 100).toInt()}% confidence)."
                    RiskLevel.SAFE -> "AI phishing model marked this as benign (${(verdict.confidence * 100).toInt()}% confidence)."
                }
            )
        }.distinct()

        return localEntry.copy(
            result = localEntry.result.copy(
                riskLevel = mergedRisk,
                score = maxOf(localEntry.result.score, (verdict.phishingProbability * 100).toInt()),
                reasons = mergedReasons,
                plainLanguageExplanation = when (mergedRisk) {
                    RiskLevel.HIGH_RISK -> "This message looks dangerous. Cloud and local checks found strong warning signs."
                    RiskLevel.CAUTION -> "This message needs caution. Cloud or local checks found suspicious patterns."
                    RiskLevel.SAFE -> "This message looks mostly safe."
                },
                suggestedAction = when (mergedRisk) {
                    RiskLevel.HIGH_RISK -> "Do not click anything. Ask a family member before taking action."
                    RiskLevel.CAUTION -> "Pause before acting. Verify with the official website or a trusted person."
                    RiskLevel.SAFE -> "No major red flags found, but never share OTP or passwords."
                }
            )
        )
    }

    private fun mergeWithRemote(localEntry: AnalysisEntry, remote: com.sjbit.seniorshield.model.RemoteScanResult): AnalysisEntry {
        val mergedRisk = if (localEntry.result.riskLevel.ordinal >= remote.riskLevel.ordinal) {
            localEntry.result.riskLevel
        } else {
            remote.riskLevel
        }

        return localEntry.copy(
            result = localEntry.result.copy(
                riskLevel = mergedRisk,
                score = maxOf(localEntry.result.score, remote.finalScore),
                reasons = (localEntry.result.reasons + remote.indicators.map { "AI layer: $it" }).distinct(),
                plainLanguageExplanation = if (remote.explanation.isNotBlank()) remote.explanation else localEntry.result.plainLanguageExplanation,
                suggestedAction = if (remote.suggestedAction.isNotBlank()) remote.suggestedAction else localEntry.result.suggestedAction
            )
        )
    }

    private fun isConfirmedPhishing(verdict: HfPhishingVerdict, entry: AnalysisEntry): Boolean {
        val labelLower = verdict.label.lowercase()
        return verdict.riskLevel == RiskLevel.HIGH_RISK ||
            verdict.phishingProbability >= 0.4f ||
            labelLower.contains("phishing") ||
            entry.result.riskLevel == RiskLevel.HIGH_RISK
    }

    private fun detectLanguage(text: String): String {
        if (text.any { it in '\u0900'..'\u097F' }) return "hi"
        if (text.any { it in '\u0C80'..'\u0CFF' }) return "kn"
        return "en"
    }
}
