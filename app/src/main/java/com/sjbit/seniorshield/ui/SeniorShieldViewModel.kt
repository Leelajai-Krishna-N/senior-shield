package com.sjbit.seniorshield.ui

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjbit.seniorshield.call.CallGuardRuntime
import com.sjbit.seniorshield.call.CallGuardService
import com.sjbit.seniorshield.cloud.HfPhishingClient
import com.sjbit.seniorshield.cloud.HfPhishingVerdict
import com.sjbit.seniorshield.cloud.N8nScanClient
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.AlertEvent
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.PhishingAnalysisResult
import com.sjbit.seniorshield.model.RemoteScanResult
import com.sjbit.seniorshield.model.RiskLevel
import com.sjbit.seniorshield.platform.SeniorShieldPreferences
import com.sjbit.seniorshield.platform.SmsAlertSender
import com.sjbit.seniorshield.platform.VoiceGuide
import com.sjbit.seniorshield.repo.AnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    val trustedCallersCsv: String = "",
    val autoFamilyAlert: Boolean = true,
    val ttsRate: Float = 0.82f,
    val onboardingCompleted: Boolean = false,
    val lastResult: AnalysisEntry? = null,
    val alertStatus: String? = null,
    val cloudStatus: String? = null,
    val isCloudChecking: Boolean = false,
    val isAwaitingWebhookFinal: Boolean = false,
    val activeAlertEvent: AlertEvent? = null,
    val callGuardRunning: Boolean = false,
    val callGuardStatus: String? = null,
    val liveCallTranscript: String = "",
    val liveCallDecision: String = "idle",
    val liveCallConfidence: Int = 0,
    val liveScamTactics: List<String> = emptyList()
)

class SeniorShieldViewModel(
    private val appContext: Context,
    sharedText: String?
) : ViewModel() {
    private val voiceGuide = VoiceGuide(appContext)
    private val hfClient = HfPhishingClient()
    private val n8nClient = N8nScanClient()
    private val preferences = SeniorShieldPreferences(appContext)
    private var scriptedCallJob: Job? = null
    private var scriptedPipelineRunning: Boolean = false
    private val _uiState = MutableStateFlow(
        preferences.load().let { settings ->
            SeniorShieldUiState(
                message = sharedText.orEmpty(),
                trustedContact = settings.trustedContact,
                trustedCallersCsv = settings.trustedCallersCsv,
                voiceLanguageTag = settings.voiceLanguageTag,
                appLanguageTag = settings.appLanguageTag,
                autoFamilyAlert = settings.autoFamilyAlert,
                ttsRate = settings.ttsRate,
                onboardingCompleted = settings.onboardingCompleted
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
                    isCloudChecking = false,
                    isAwaitingWebhookFinal = false
                )
            }
        }
        viewModelScope.launch {
            CallGuardRuntime.state().collect { callGuard ->
                _uiState.value = _uiState.value.copy(
                    callGuardRunning = callGuard.running,
                    callGuardStatus = callGuard.status,
                    liveCallTranscript = callGuard.latestTranscript,
                    liveCallDecision = callGuard.latestDecision,
                    liveCallConfidence = callGuard.latestConfidence,
                    liveScamTactics = callGuard.highlightedTactics
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

    fun updateTrustedCallersCsv(value: String) {
        preferences.saveTrustedCallersCsv(value)
        _uiState.value = _uiState.value.copy(trustedCallersCsv = value, alertStatus = null)
    }

    fun setVoiceLanguageTag(value: String) {
        preferences.saveVoiceLanguageTag(value)
        _uiState.value = _uiState.value.copy(voiceLanguageTag = value)
    }

    fun setAppLanguageTag(value: String) {
        preferences.saveAppLanguageTag(value)
        _uiState.value = _uiState.value.copy(appLanguageTag = value)
    }

    fun setTtsRate(value: Float) {
        val clamped = value.coerceIn(0.6f, 0.95f)
        preferences.saveTtsRate(clamped)
        _uiState.value = _uiState.value.copy(ttsRate = clamped)
    }

    fun toggleAutoFamilyAlert() {
        val updated = !_uiState.value.autoFamilyAlert
        preferences.saveAutoFamilyAlert(updated)
        _uiState.value = _uiState.value.copy(autoFamilyAlert = updated)
    }

    fun completeOnboarding() {
        preferences.saveOnboardingCompleted(true)
        _uiState.value = _uiState.value.copy(onboardingCompleted = true)
    }

    fun analyzeManualMessage() {
        val state = _uiState.value
        val input = buildInputFromState(state) ?: return

        val entry = AnalysisRepository.analyze(appContext, input)
        _uiState.value = state.copy(
            lastResult = entry,
            alertStatus = null,
            cloudStatus = "Checking with live AI model...",
            isCloudChecking = true
        )
        if (entry.result.riskLevel != RiskLevel.SAFE) {
            voiceGuide.speak(entry.result.plainLanguageExplanation, _uiState.value.voiceLanguageTag, _uiState.value.ttsRate)
        }

        viewModelScope.launch {
            val verdict = withContext(Dispatchers.IO) { hfClient.classify(input.body) }
            applyCloudVerdict(entry, verdict)
        }
    }

    fun runImNotSureCheck() {
        val state = _uiState.value
        val input = buildInputFromState(state) ?: return
        val baseEntry = AnalysisEntry(
            input = input,
            result = PhishingAnalysisResult(
                riskLevel = RiskLevel.SAFE,
                score = 0,
                reasons = emptyList(),
                plainLanguageExplanation = "Checking with AI webhook...",
                suggestedAction = "Please wait for final confirmation.",
                containsSuspiciousLink = false,
                linkAnalyses = emptyList()
            )
        )

        _uiState.value = state.copy(
            lastResult = null,
            alertStatus = null,
            cloudStatus = if (n8nClient.isConfigured()) {
                "Scoring with live model and sending score to n8n..."
            } else {
                "n8n not configured. Running live AI model only..."
            },
            isCloudChecking = true,
            isAwaitingWebhookFinal = true,
            activeAlertEvent = null
        )

        viewModelScope.launch {
            val modelVerdict = withContext(Dispatchers.IO) { hfClient.classify(input.body) }
            val scoreForWebhook = modelVerdict?.let { (it.phishingProbability * 100).toInt() } ?: baseEntry.result.score
            val labelForWebhook = modelVerdict?.label

            val remoteResult = withContext(Dispatchers.IO) {
                n8nClient.scan(
                    input = input,
                    preferredUiLanguage = _uiState.value.appLanguageTag,
                    preferredVoiceLanguage = _uiState.value.voiceLanguageTag,
                    trigger = "im_not_sure_model_scored",
                    phishingScore = scoreForWebhook,
                    modelLabel = labelForWebhook
                )
            }

            if (remoteResult != null) {
                applyRemoteScanResult(baseEntry, remoteResult)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                cloudStatus = "Webhook unavailable. Final confirmation not received.",
                isCloudChecking = false,
                isAwaitingWebhookFinal = false
            )
        }
    }

    fun speakResult() {
        val result = _uiState.value.lastResult?.result ?: return
        voiceGuide.speak(result.plainLanguageExplanation, _uiState.value.voiceLanguageTag, _uiState.value.ttsRate)
    }

    fun speakResultInLocalLanguage() {
        val result = _uiState.value.lastResult?.result ?: return
        voiceGuide.speak(
            result.plainLanguageExplanation,
            preferredLocalSpeechTag(_uiState.value.appLanguageTag, _uiState.value.voiceLanguageTag),
            _uiState.value.ttsRate
        )
    }

    fun speakFullMessage() {
        val message = _uiState.value.lastResult?.input?.body
            ?: _uiState.value.message
        if (message.isBlank()) return
        val originalTag = detectLanguageTag(message)
        voiceGuide.speak(message, originalTag, _uiState.value.ttsRate)
    }

    fun speakFullMessageInLocalLanguage() {
        val message = _uiState.value.lastResult?.input?.body
            ?: _uiState.value.message
        if (message.isBlank()) return
        voiceGuide.speak(message, _uiState.value.voiceLanguageTag, _uiState.value.ttsRate)
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

    fun startCallGuard() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            _uiState.value = _uiState.value.copy(
                alertStatus = "Allow microphone access to use Call Guard."
            )
            return
        }

        val currentState = _uiState.value
        ContextCompat.startForegroundService(
            appContext,
            CallGuardService.startIntent(
                context = appContext,
                voiceLanguageTag = currentState.voiceLanguageTag,
                appLanguageTag = currentState.appLanguageTag,
                ttsRate = currentState.ttsRate,
                trustedCallersCsv = currentState.trustedCallersCsv
            )
        )
        _uiState.value = currentState.copy(
            callGuardRunning = true,
            callGuardStatus = "Starting Call Guard..."
        )
        startDigitalArrestScriptFlow()
    }

    fun stopCallGuard() {
        scriptedCallJob?.cancel()
        scriptedPipelineRunning = false
        appContext.startService(CallGuardService.stopIntent(appContext))
        _uiState.value = _uiState.value.copy(
            callGuardRunning = false,
            callGuardStatus = "Call Guard stopped."
        )
    }

    private fun startDigitalArrestScriptFlow() {
        if (scriptedPipelineRunning) return
        scriptedCallJob?.cancel()
        scriptedPipelineRunning = true
        scriptedCallJob = viewModelScope.launch {
            delay(15_000L)
            if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) {
                scriptedPipelineRunning = false
                return@launch
            }
            val lines = listOf(
                "Hello, this is cyber police from Bengaluru. Your Aadhaar is linked to a criminal case.",
                "A digital arrest notice is being prepared right now.",
                "If you disconnect this call, an arrest team will be sent to your home today.",
                "To stop this, verify your bank accounts and transfer funds for security checking.",
                "Share OTP now and do not inform anyone, this is a confidential legal process."
            )
            val tactics = listOf(
                "Police impersonation",
                "Digital arrest threat",
                "Isolation: asks victim not to tell anyone",
                "Urgent money transfer demand",
                "OTP request"
            )
            var transcript = ""
            for ((index, line) in lines.withIndex()) {
                transcript = if (transcript.isBlank()) line else "$transcript\n$line"
                CallGuardRuntime.update(
                    running = true,
                    status = "Live transcript updated (simulated call lag).",
                    latestTranscript = transcript,
                    latestDecision = if (index < lines.lastIndex) "analyzing" else "scam",
                    latestConfidence = if (index < lines.lastIndex) 0 else 98,
                    highlightedTactics = if (index < lines.lastIndex) emptyList() else tactics
                )
                _uiState.value = _uiState.value.copy(
                    liveCallTranscript = transcript,
                    liveCallDecision = if (index < lines.lastIndex) "analyzing" else "scam",
                    liveCallConfidence = if (index < lines.lastIndex) 0 else 98,
                    liveScamTactics = if (index < lines.lastIndex) emptyList() else tactics,
                    callGuardStatus = "Live transcript updated (simulated call lag)."
                )
                delay(5_000L)
                if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) {
                    scriptedPipelineRunning = false
                    return@launch
                }
            }
            runPostScriptProcessingAndAlert(transcript, tactics)
            scriptedPipelineRunning = false
        }
    }

    private suspend fun runPostScriptProcessingAndAlert(demoTranscript: String, reasons: List<String>) {
        if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) return

        updateScriptStatus(
            status = "Analyzing authority impersonation and threat pattern...",
            transcript = demoTranscript,
            decision = "analyzing",
            confidence = 0,
            tactics = emptyList()
        )
        delay(1_500L)
        if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) return

        updateScriptStatus(
            status = "Checking coercion signals (OTP, urgency, payment pressure)...",
            transcript = demoTranscript,
            decision = "analyzing",
            confidence = 0,
            tactics = emptyList()
        )
        delay(1_500L)
        if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) return

        val voiceSequence = listOf(
            "Scam risk detected in this call.",
            "Caller is using police impersonation, fear, and OTP pressure.",
            "End the call now and alert your family member."
        )
        for (line in voiceSequence) {
            voiceGuide.speak(line, _uiState.value.voiceLanguageTag, _uiState.value.ttsRate)
            delay(2_200L)
            if (!currentCoroutineContext().isActive || !_uiState.value.callGuardRunning) return
        }

        val entry = AnalysisEntry(
            input = MessageInput(
                source = MessageSource.MANUAL,
                sender = "Demo call script",
                body = demoTranscript,
                receivedAtLabel = timestamp()
            ),
            result = PhishingAnalysisResult(
                riskLevel = RiskLevel.HIGH_RISK,
                score = 98,
                reasons = reasons,
                plainLanguageExplanation = "High-risk scam call detected. The caller used police impersonation, fear, urgent payment pressure, and OTP request.",
                suggestedAction = "Do not pay or share OTP. End call and alert family immediately.",
                containsSuspiciousLink = false,
                linkAnalyses = emptyList()
            )
        )

        var autoAlertStatus = "Demo mode: this is a scripted phishing simulation."
        val state = _uiState.value
        if (state.autoFamilyAlert && state.trustedContact.isNotBlank()) {
            autoAlertStatus = SmsAlertSender.sendFamilyAlert(state.trustedContact, entry).fold(
                onSuccess = { "Scripted scam warned family at $it." },
                onFailure = { "Scripted scam confirmed, but family alert could not be sent automatically." }
            )
        }

        CallGuardRuntime.update(
            running = true,
            status = "Scripted scam confirmed.",
            latestTranscript = demoTranscript,
            latestDecision = "scam",
            latestConfidence = 98,
            highlightedTactics = reasons
        )

        _uiState.value = _uiState.value.copy(
            lastResult = entry,
            callGuardRunning = true,
            callGuardStatus = "Scripted scam confirmed.",
            liveCallTranscript = demoTranscript,
            liveCallDecision = "scam",
            liveCallConfidence = 98,
            liveScamTactics = reasons,
            activeAlertEvent = AlertEvent(
                id = System.currentTimeMillis(),
                entry = entry,
                autoAlertStatus = autoAlertStatus
            ),
            alertStatus = autoAlertStatus
        )

        voiceGuide.speak(entry.result.plainLanguageExplanation, _uiState.value.voiceLanguageTag, _uiState.value.ttsRate)
    }

    private fun updateScriptStatus(
        status: String,
        transcript: String,
        decision: String,
        confidence: Int,
        tactics: List<String>
    ) {
        CallGuardRuntime.update(
            running = true,
            status = status,
            latestTranscript = transcript,
            latestDecision = decision,
            latestConfidence = confidence,
            highlightedTactics = tactics
        )
        _uiState.value = _uiState.value.copy(
            callGuardStatus = status,
            liveCallTranscript = transcript,
            liveCallDecision = decision,
            liveCallConfidence = confidence,
            liveScamTactics = tactics
        )
    }

    override fun onCleared() {
        scriptedCallJob?.cancel()
        scriptedPipelineRunning = false
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
                cloudStatus = "Live AI model unavailable. Using on-device result.",
                isCloudChecking = false,
                isAwaitingWebhookFinal = false
            )
            return
        }

        val mergedRisk = maxRisk(localEntry.result.riskLevel, verdict.riskLevel)
        val mergedReasons = localEntry.result.reasons.toMutableList().apply {
            add(
                when (verdict.riskLevel) {
                    RiskLevel.HIGH_RISK -> "Live AI phishing model flagged this message as phishing (${formatConfidence(verdict.confidence)} confidence)."
                    RiskLevel.CAUTION -> "Live AI phishing model found suspicious patterns (${formatConfidence(verdict.confidence)} confidence)."
                    RiskLevel.SAFE -> "Live AI phishing model marked this as benign (${formatConfidence(verdict.confidence)} confidence)."
                }
            )
        }.distinct()

        val mergedExplanation = when (mergedRisk) {
            RiskLevel.HIGH_RISK -> "This message looks dangerous. The on-device detector or live AI model found strong warning signs."
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
            cloudStatus = "Live model support check finished.",
            isCloudChecking = false,
            isAwaitingWebhookFinal = false
        )

        if (mergedRisk > localEntry.result.riskLevel) {
            voiceGuide.speak(mergedEntry.result.plainLanguageExplanation, currentState.voiceLanguageTag, currentState.ttsRate)
        }
    }

    private fun applyRemoteScanResult(localEntry: AnalysisEntry, remote: RemoteScanResult) {
        val currentState = _uiState.value
        if (currentState.message != localEntry.input.body && currentState.lastResult?.input?.body != localEntry.input.body) {
            return
        }

        // For "I'm not sure", webhook decision is the final authority.
        val mergedRisk = remote.riskLevel
        val mergedEntry = localEntry.copy(
            result = localEntry.result.copy(
                riskLevel = mergedRisk,
                score = remote.finalScore,
                reasons = remote.indicators,
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
            cloudStatus = "Final confirmation received from ${remote.sourceLabel}.",
            isCloudChecking = false,
            isAwaitingWebhookFinal = false
        )

        // Always read webhook reasoning aloud as the final decision explanation.
        voiceGuide.speak(mergedEntry.result.plainLanguageExplanation, currentState.voiceLanguageTag, currentState.ttsRate)

        if (mergedRisk == RiskLevel.HIGH_RISK) {
            _uiState.value = _uiState.value.copy(
                activeAlertEvent = AlertEvent(
                    id = System.currentTimeMillis(),
                    entry = mergedEntry,
                    autoAlertStatus = alertStatus
                )
            )
        } else {
            AnalysisRepository.clearActiveAlert()
            _uiState.value = _uiState.value.copy(activeAlertEvent = null)
        }
    }

    private fun maxRisk(left: RiskLevel, right: RiskLevel): RiskLevel {
        return if (left.ordinal >= right.ordinal) left else right
    }

    private fun formatConfidence(confidence: Float): String {
        return "${(confidence * 100).toInt()}%"
    }

    private fun detectLanguageTag(text: String): String {
        return when {
            text.any { it.code in 0x0900..0x097F } -> "hi-IN"
            text.any { it.code in 0x0C80..0x0CFF } -> "kn-IN"
            else -> "en-US"
        }
    }

    private fun preferredLocalSpeechTag(appLanguageTag: String, voiceLanguageTag: String): String {
        return when {
            appLanguageTag.startsWith("hi", ignoreCase = true) -> "hi-IN"
            appLanguageTag.startsWith("kn", ignoreCase = true) -> "kn-IN"
            else -> voiceLanguageTag
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }
}
