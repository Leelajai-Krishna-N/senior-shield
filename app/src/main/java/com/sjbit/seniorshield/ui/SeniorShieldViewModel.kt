package com.sjbit.seniorshield.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjbit.seniorshield.cloud.HfPhishingClient
import com.sjbit.seniorshield.cloud.HfPhishingVerdict
import com.sjbit.seniorshield.cloud.N8nScanClient
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.AlertEvent
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.RemoteScanResult
import com.sjbit.seniorshield.model.RiskLevel
import com.sjbit.seniorshield.platform.SeniorShieldPreferences
import com.sjbit.seniorshield.platform.SmsAlertSender
import com.sjbit.seniorshield.platform.VoiceGuide
import com.sjbit.seniorshield.repo.AnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SeniorShieldUiState(
    val sender: String = "",
    val message: String = "",
    val voiceLanguageTag: String = "hi-IN",
    val appLanguageTag: String = "system",
    val trustedContact: String = "",
    val autoFamilyAlert: Boolean = true,
    val lastResult: AnalysisEntry? = null,
    val alertStatus: String? = null,
    val cloudStatus: String? = null,
    val isCloudChecking: Boolean = false,
    val activeAlertEvent: AlertEvent? = null
)

class SeniorShieldViewModel(
    private val appContext: Context,
    sharedText: String?
) : ViewModel() {
    private val voiceGuide = VoiceGuide(appContext)
    private val hfClient = HfPhishingClient()
    private val n8nClient = N8nScanClient()
    private val preferences = SeniorShieldPreferences(appContext)
    private val _uiState = MutableStateFlow(
        preferences.load().let { settings ->
            SeniorShieldUiState(
                message = sharedText.orEmpty(),
                trustedContact = settings.trustedContact,
                voiceLanguageTag = settings.voiceLanguageTag,
                appLanguageTag = settings.appLanguageTag,
                autoFamilyAlert = settings.autoFamilyAlert
            )
        }
    )
    val uiState: StateFlow<SeniorShieldUiState> = _uiState.asStateFlow()
    val recentEntries = AnalysisRepository.entries().stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            AnalysisRepository.activeAlert().collect { alertEvent ->
                if (alertEvent == null) return@collect
                _uiState.value = _uiState.value.copy(
                    lastResult = alertEvent.entry,
                    activeAlertEvent = alertEvent,
                    alertStatus = alertEvent.autoAlertStatus ?: _uiState.value.alertStatus,
                    cloudStatus = null,
                    isCloudChecking = false
                )
            }
        }
    }

    fun updateSender(value: String) {
        _uiState.value = _uiState.value.copy(sender = value, alertStatus = null, cloudStatus = null)
    }

    fun updateMessage(value: String) {
        _uiState.value = _uiState.value.copy(message = value, alertStatus = null, cloudStatus = null)
    }

    fun updateTrustedContact(value: String) {
        preferences.saveTrustedContact(value)
        _uiState.value = _uiState.value.copy(trustedContact = value, alertStatus = null)
    }

    fun setVoiceLanguageTag(value: String) {
        preferences.saveVoiceLanguageTag(value)
        _uiState.value = _uiState.value.copy(voiceLanguageTag = value)
    }

    fun setAppLanguageTag(value: String) {
        preferences.saveAppLanguageTag(value)
        _uiState.value = _uiState.value.copy(appLanguageTag = value)
    }

    fun toggleAutoFamilyAlert() {
        val updated = !_uiState.value.autoFamilyAlert
        preferences.saveAutoFamilyAlert(updated)
        _uiState.value = _uiState.value.copy(autoFamilyAlert = updated)
    }

    fun analyzeManualMessage() {
        val state = _uiState.value
        val input = buildInputFromState(state) ?: return

        val entry = AnalysisRepository.analyze(appContext, input)
        _uiState.value = state.copy(
            lastResult = entry,
            alertStatus = null,
            cloudStatus = "Checking with AI model fallback...",
            isCloudChecking = true
        )
        if (entry.result.riskLevel != RiskLevel.SAFE) {
            voiceGuide.speak(entry.result.plainLanguageExplanation, _uiState.value.voiceLanguageTag)
        }

        viewModelScope.launch {
            val verdict = withContext(Dispatchers.IO) {
                hfClient.classify(input.body)
            }
            applyCloudVerdict(entry, verdict)
        }
    }

    fun runImNotSureCheck() {
        val state = _uiState.value
        val input = buildInputFromState(state) ?: return
        val baseEntry = if (state.lastResult?.input?.body == input.body) {
            state.lastResult
        } else {
            AnalysisRepository.analyze(appContext, input)
        } ?: return

        _uiState.value = state.copy(
            lastResult = baseEntry,
            alertStatus = null,
            cloudStatus = if (n8nClient.isConfigured()) {
                "Running deep AI and link scan through n8n..."
            } else {
                "n8n not configured. Falling back to direct AI model scan..."
            },
            isCloudChecking = true
        )

        viewModelScope.launch {
            val remoteResult = withContext(Dispatchers.IO) {
                n8nClient.scan(
                    input = input,
                    preferredUiLanguage = _uiState.value.appLanguageTag,
                    preferredVoiceLanguage = _uiState.value.voiceLanguageTag
                )
            }

            if (remoteResult != null) {
                applyRemoteScanResult(baseEntry, remoteResult)
                return@launch
            }

            val verdict = withContext(Dispatchers.IO) {
                hfClient.classify(input.body)
            }
            applyCloudVerdict(baseEntry, verdict)
        }
    }

    fun speakResult() {
        val result = _uiState.value.lastResult?.result ?: return
        voiceGuide.speak(result.plainLanguageExplanation, _uiState.value.voiceLanguageTag)
    }

    fun speakResultInLocalLanguage() {
        val result = _uiState.value.lastResult?.result ?: return
        voiceGuide.speakInDeviceLanguage(result.plainLanguageExplanation)
    }

    fun speakFullMessage() {
        val message = _uiState.value.lastResult?.input?.body
            ?: _uiState.value.message
        if (message.isBlank()) return
        voiceGuide.speak(message, _uiState.value.voiceLanguageTag)
    }

    fun speakFullMessageInLocalLanguage() {
        val message = _uiState.value.lastResult?.input?.body
            ?: _uiState.value.message
        if (message.isBlank()) return
        voiceGuide.speakInDeviceLanguage(message)
    }

    fun sendFamilyAlert() {
        val state = _uiState.value
        val entry = state.lastResult ?: run {
            _uiState.value = state.copy(alertStatus = "Check a message first before alerting family.")
            return
        }
        if (state.trustedContact.isBlank()) {
            _uiState.value = state.copy(alertStatus = "Enter a family contact number first.")
            return
        }

        SmsAlertSender.sendFamilyAlert(state.trustedContact, entry).onSuccess { normalizedContact ->
            preferences.saveTrustedContact(normalizedContact)
            _uiState.value = state.copy(
                trustedContact = normalizedContact,
                alertStatus = "Family alert sent to $normalizedContact."
            )
        }.onFailure {
            _uiState.value = state.copy(
                alertStatus = if (it is IllegalArgumentException) {
                    "That phone number format looks invalid."
                } else {
                    "Could not send alert automatically. Check SMS permission or SIM availability."
                }
            )
        }
    }

    fun dismissActiveAlert() {
        AnalysisRepository.clearActiveAlert()
        _uiState.value = _uiState.value.copy(activeAlertEvent = null)
    }

    override fun onCleared() {
        voiceGuide.release()
        super.onCleared()
    }

    private fun timestamp(): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun buildInputFromState(state: SeniorShieldUiState): MessageInput? {
        if (state.message.isBlank()) return null
        val source = if (state.sender.isBlank()) MessageSource.MANUAL else MessageSource.SHARE_INTENT
        return MessageInput(
            source = source,
            sender = state.sender.ifBlank { "Shared or pasted message" },
            body = state.message,
            receivedAtLabel = timestamp()
        )
    }

    private fun applyCloudVerdict(localEntry: AnalysisEntry, verdict: HfPhishingVerdict?) {
        val currentState = _uiState.value
        if (currentState.lastResult?.input?.body != localEntry.input.body) return

        if (verdict == null) {
            _uiState.value = currentState.copy(
                cloudStatus = "Hugging Face check unavailable. Using on-device result.",
                isCloudChecking = false
            )
            return
        }

        val mergedRisk = maxRisk(localEntry.result.riskLevel, verdict.riskLevel)
        val mergedReasons = localEntry.result.reasons.toMutableList().apply {
            add(
                when (verdict.riskLevel) {
                    RiskLevel.HIGH_RISK -> "Hugging Face phishing model flagged this message as phishing (${formatConfidence(verdict.confidence)} confidence)."
                    RiskLevel.CAUTION -> "Hugging Face phishing model found suspicious patterns (${formatConfidence(verdict.confidence)} confidence)."
                    RiskLevel.SAFE -> "Hugging Face phishing model marked this as benign (${formatConfidence(verdict.confidence)} confidence)."
                }
            )
        }.distinct()

        val mergedExplanation = when (mergedRisk) {
            RiskLevel.HIGH_RISK -> "This message looks dangerous. The on-device detector or Hugging Face phishing model found strong warning signs."
            RiskLevel.CAUTION -> "This message needs caution. One of the detectors found suspicious patterns."
            RiskLevel.SAFE -> "This message looks mostly safe. Both checks did not find strong phishing signs."
        }

        val mergedEntry = localEntry.copy(
            result = localEntry.result.copy(
                riskLevel = mergedRisk,
                score = maxOf(localEntry.result.score, (verdict.confidence * 100).toInt()),
                reasons = mergedReasons,
                plainLanguageExplanation = mergedExplanation,
                suggestedAction = when (mergedRisk) {
                    RiskLevel.HIGH_RISK -> "Do not click anything. Ask a family member before taking action."
                    RiskLevel.CAUTION -> "Pause before acting. Verify with the official website or a trusted person."
                    RiskLevel.SAFE -> "No major red flags found, but never share OTP or passwords."
                }
            )
        )

        _uiState.value = currentState.copy(
            lastResult = mergedEntry,
            cloudStatus = "Hugging Face result: ${verdict.label} (${formatConfidence(verdict.confidence)})",
            isCloudChecking = false
        )

        if (mergedRisk > localEntry.result.riskLevel) {
            voiceGuide.speak(mergedEntry.result.plainLanguageExplanation, currentState.voiceLanguageTag)
        }
    }

    private fun applyRemoteScanResult(localEntry: AnalysisEntry, remote: RemoteScanResult) {
        val currentState = _uiState.value
        if (currentState.lastResult?.input?.body != localEntry.input.body) return

        val mergedRisk = maxRisk(localEntry.result.riskLevel, remote.riskLevel)
        val mergedEntry = localEntry.copy(
            result = localEntry.result.copy(
                riskLevel = mergedRisk,
                score = maxOf(localEntry.result.score, remote.finalScore),
                reasons = (localEntry.result.reasons + remote.indicators.map { "Deep scan: $it" }).distinct(),
                plainLanguageExplanation = remote.explanation.ifBlank { localEntry.result.plainLanguageExplanation },
                suggestedAction = remote.suggestedAction.ifBlank { localEntry.result.suggestedAction }
            )
        )

        var alertStatus = currentState.alertStatus
        if (
            remote.familyAlertRecommended &&
            currentState.autoFamilyAlert &&
            currentState.trustedContact.isNotBlank() &&
            mergedRisk == RiskLevel.HIGH_RISK
        ) {
            alertStatus = SmsAlertSender.sendFamilyAlert(currentState.trustedContact, mergedEntry).fold(
                onSuccess = { "Deep scan warned family at $it." },
                onFailure = { "Deep scan marked this high risk, but family alert could not be sent automatically." }
            )
        } else if (remote.familyAlertRecommended) {
            alertStatus = "Deep scan recommends alerting a family member before acting."
        }

        _uiState.value = currentState.copy(
            lastResult = mergedEntry,
            alertStatus = alertStatus,
            cloudStatus = "Deep scan complete via ${remote.sourceLabel} (${remote.confidence}% confidence)",
            isCloudChecking = false
        )

        if (mergedRisk >= RiskLevel.CAUTION) {
            voiceGuide.speak(mergedEntry.result.plainLanguageExplanation, currentState.voiceLanguageTag)
        }
    }

    private fun maxRisk(left: RiskLevel, right: RiskLevel): RiskLevel {
        return if (left.ordinal >= right.ordinal) left else right
    }

    private fun formatConfidence(confidence: Float): String {
        return "${(confidence * 100).toInt()}%"
    }
}
