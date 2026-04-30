package com.sjbit.seniorshield.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.telephony.TelephonyManager
import android.content.ActivityNotFoundException
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.PhishingAnalysisResult
import com.sjbit.seniorshield.model.RiskLevel
import com.sjbit.seniorshield.cloud.GroqCallAnalysisClient
import com.sjbit.seniorshield.cloud.HfTranslationClient
import com.sjbit.seniorshield.platform.AlertNotifier
import com.sjbit.seniorshield.platform.VoiceAlertSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class CallGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = MainScope()
    private val groqClient = GroqCallAnalysisClient()
    private val translator = HfTranslationClient()
    private var monitorJobActive = false
    private var currentVoiceLanguageTag = "en-US"
    private var currentAppLanguageTag = "en-US"
    private var currentTtsRate = 0.82f
    private var recorder: MediaRecorder? = null
    private var lastWarnedTranscript: String = ""
    private var trustedProfiles: Set<String> = emptySet()
    private val transcriptionMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopGuard("Call Guard stopped.")
            ACTION_START -> {
                currentVoiceLanguageTag = intent.getStringExtra(EXTRA_VOICE_LANGUAGE_TAG).orEmpty().ifBlank { "en-US" }
                currentAppLanguageTag = intent.getStringExtra(EXTRA_APP_LANGUAGE_TAG).orEmpty().ifBlank { "en-US" }
                currentTtsRate = intent.getFloatExtra(EXTRA_TTS_RATE, 0.82f)
                trustedProfiles = parseTrustedProfiles(intent.getStringExtra(EXTRA_TRUSTED_CALLERS_CSV).orEmpty())
                startGuard()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        scope.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    private fun startGuard() {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Call Guard is listening for scam patterns."))
        if (!groqClient.isConfigured()) {
            CallGuardRuntime.update(false, "Groq API key missing. Add it locally to enable Call Guard.")
            stopSelf()
            return
        }
        if (monitorJobActive) {
            CallGuardRuntime.update(true, "Call Guard is already running.")
            return
        }
        monitorJobActive = true
        CallGuardRuntime.update(
            running = true,
            status = "Call Guard started. Turn on speakerphone for full two-side monitoring.",
            latestTranscript = "",
            latestDecision = "idle",
            latestConfidence = 0,
            highlightedTactics = emptyList()
        )
        scope.launch {
            val contextWindow = ArrayDeque<String>()
            val contextMutex = Mutex()
            var idlePollCount = 0
            while (isActive) {
                if (!isCallActive()) {
                    idlePollCount += 1
                    if (idlePollCount >= 2) {
                        contextMutex.withLock { contextWindow.clear() }
                        lastWarnedTranscript = ""
                        updateStatus(
                            status = "Waiting for an active phone call...",
                            latestTranscript = "",
                            latestDecision = "idle",
                            latestConfidence = 0,
                            highlightedTactics = emptyList()
                        )
                    } else {
                        updateStatus("Waiting for an active phone call...")
                    }
                    delay(IDLE_POLL_MS)
                    continue
                }
                idlePollCount = 0

                val speakerHint = if (isSpeakerphoneOn()) {
                    "Listening to the live call..."
                } else {
                    "Call detected. Turn on speakerphone so both sides can be heard."
                }
                updateStatus(speakerHint)

                val audioFile = recordChunk()
                if (audioFile == null) {
                    updateStatus("Could not access the microphone during the call. Retrying...")
                    delay(RETRY_DELAY_MS)
                    continue
                }

                updateStatus("Live audio captured. Transcribing while listening continues...")
                scope.launch {
                    runCatching {
                        processCallChunk(audioFile, contextWindow, contextMutex)
                    }.onFailure {
                        updateStatus("Call transcription worker recovered from an error.")
                    }
                }
            }
        }
    }

    private fun stopGuard(status: String) {
        monitorJobActive = false
        CallGuardRuntime.update(false, status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processCallChunk(
        audioFile: File,
        contextWindow: ArrayDeque<String>,
        contextMutex: Mutex
    ) {
        transcriptionMutex.withLock {
            val transcript = transcribeWithFallback(audioFile).orEmpty()
            audioFile.delete()
            if (transcript.isBlank()) {
                updateStatus("Heard audio, but could not transcribe this live chunk.")
                return
            }
            if (isLowSignalHallucination(transcript, CallGuardRuntime.state().value.latestTranscript)) {
                updateStatus("Ignored low-signal transcript chunk. Keep speaking near speakerphone.")
                return
            }

            val combinedTranscript = contextMutex.withLock {
                contextWindow += transcript
                while (contextWindow.size > MAX_CONTEXT_CHUNKS) contextWindow.removeFirst()
                contextWindow.joinToString("\n")
            }
            val displayTranscript = mergedTranscriptForDisplay(
                previous = CallGuardRuntime.state().value.latestTranscript,
                combined = combinedTranscript
            )
            updateStatus(
                status = "Live transcript updated.",
                latestTranscript = displayTranscript,
                latestDecision = "analyzing",
                latestConfidence = 0,
                highlightedTactics = emptyList()
            )

            val analysis = groqClient.analyzeTranscript(combinedTranscript)
            if (analysis == null) {
                updateStatus("Groq could not analyze the latest live transcript.")
                return
            }
            val trustedMatched = isTrustedProfileMentioned(combinedTranscript)
            val hasCriticalTactic = hasCriticalScamTactic(analysis, combinedTranscript)
            val finalDecision = when {
                analysis.isScam && trustedMatched && !hasCriticalTactic -> "safe"
                else -> analysis.decision
            }
            val finalConfidence = if (analysis.isScam && trustedMatched && !hasCriticalTactic) {
                (100 - analysis.confidence).coerceAtLeast(55)
            } else {
                analysis.confidence
            }
            val tacticsForUi = analysis.tactics.toMutableList().apply {
                if (trustedMatched) add(0, "Trusted caller profile matched")
                if (trustedMatched && !hasCriticalTactic) add(1, "No critical scam tactic (OTP/PIN/UPI/remote access) detected")
            }.distinct()

            updateStatus(
                status = "Live call checked: ${finalDecision.uppercase()} ($finalConfidence%). ${analysis.legitimacySignal}.",
                latestTranscript = displayTranscript,
                latestDecision = finalDecision,
                latestConfidence = finalConfidence,
                highlightedTactics = tacticsForUi
            )
            if (finalDecision.equals("scam", ignoreCase = true) && combinedTranscript != lastWarnedTranscript) {
                lastWarnedTranscript = combinedTranscript
                issueScamWarning(combinedTranscript, analysis.reasoning, finalConfidence)
            }
        }
    }

    private suspend fun transcribeWithFallback(audioFile: File): String? {
        updateStatus("Trying free on-device live speech...")
        val localTranscript = captureDeviceSpeechSnippet().orEmpty()
        if (localTranscript.isNotBlank() &&
            !isLowSignalHallucination(localTranscript, CallGuardRuntime.state().value.latestTranscript)
        ) {
            return localTranscript
        }

        updateStatus("Device speech weak. Trying cloud transcript...")
        val cloudTranscript = groqClient.transcribeAudio(audioFile, normalizeLanguage(currentAppLanguageTag)).orEmpty()
        if (cloudTranscript.isNotBlank() &&
            !isLowSignalHallucination(cloudTranscript, CallGuardRuntime.state().value.latestTranscript)
        ) {
            return cloudTranscript
        }
        return null
    }

    private suspend fun captureDeviceSpeechSnippet(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine { continuation ->
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(this@CallGuardService)
                    val best = StringBuilder()
                    val listener = object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resume(best.toString().trim().ifBlank { "" })
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val text = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                                .trim()
                            if (text.length > best.length) {
                                best.clear()
                                best.append(text)
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            val text = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                                .trim()
                            val finalText = if (text.length > best.length) text else best.toString()
                            if (continuation.isActive) continuation.resume(finalText.ifBlank { "" })
                        }
                    }
                    continuation.invokeOnCancellation {
                        runCatching { recognizer.cancel() }
                        runCatching { recognizer.destroy() }
                    }
                    recognizer.setRecognitionListener(listener)
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTagToBcp47(currentAppLanguageTag))
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                        }
                        recognizer.startListening(intent)
                    } catch (_: ActivityNotFoundException) {
                        if (continuation.isActive) continuation.resume("")
                    } catch (_: Throwable) {
                        if (continuation.isActive) continuation.resume("")
                    }
                }
            }
        }
    }

    private suspend fun issueScamWarning(transcript: String, reasoning: String, confidence: Int) {
        val targetLanguage = normalizeLanguage(currentAppLanguageTag)
        val localizedReasoning = if (targetLanguage == "en") {
            reasoning
        } else {
            translator.translate(reasoning, targetLanguage) ?: reasoning
        }
        val localizedAction = if (targetLanguage == "en") {
            "This call may be a scam. Do not share OTP, PIN, UPI approval, or bank details."
        } else {
            translator.translate(
                "This call may be a scam. Do not share OTP, PIN, UPI approval, or bank details.",
                targetLanguage
            ) ?: "This call may be a scam. Do not share OTP, PIN, UPI approval, or bank details."
        }

        val entry = AnalysisEntry(
            input = MessageInput(
                source = MessageSource.MANUAL,
                sender = "Live call monitor",
                body = transcript.take(320),
                receivedAtLabel = timestamp()
            ),
            result = PhishingAnalysisResult(
                riskLevel = RiskLevel.HIGH_RISK,
                score = confidence,
                reasons = listOf(localizedReasoning),
                plainLanguageExplanation = localizedReasoning,
                suggestedAction = localizedAction,
                containsSuspiciousLink = false,
                linkAnalyses = emptyList()
            )
        )

        AlertNotifier(this).show(entry)
        VoiceAlertSpeaker.speak(this, localizedReasoning, currentVoiceLanguageTag, currentTtsRate)
        updateStatus("Scam warning spoken aloud for the active call.")
    }

    private suspend fun recordChunk(): File? {
        return runCatching {
            val outputFile = File(cacheDir, "call-guard-${System.currentTimeMillis()}.m4a")
            withContext(Dispatchers.Main) {
                recorder = createRecorder(outputFile)
                recorder?.start()
            }
            if (recorder == null) return@runCatching null
            delay(CHUNK_DURATION_MS)
            withContext(Dispatchers.Main) {
                recorder?.runCatching { stop() }
                recorder?.release()
                recorder = null
            }
            if (outputFile.length() <= 2048L) null else outputFile
        }.getOrNull()
    }

    private fun createRecorder(outputFile: File): MediaRecorder? {
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        for (source in sources) {
            val candidate = runCatching {
                MediaRecorder().apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                }
            }.getOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun isCallActive(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioModeActive = audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val phoneStateActive = runCatching {
            telephonyManager?.callState == TelephonyManager.CALL_STATE_OFFHOOK
        }.getOrDefault(false)
        return audioModeActive || phoneStateActive
    }

    private fun isSpeakerphoneOn(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isSpeakerphoneOn
    }

    private fun updateStatus(
        status: String,
        latestTranscript: String? = null,
        latestDecision: String? = null,
        latestConfidence: Int? = null,
        highlightedTactics: List<String>? = null
    ) {
        val current = CallGuardRuntime.state().value
        CallGuardRuntime.update(
            running = true,
            status = status,
            latestTranscript = latestTranscript ?: current.latestTranscript,
            latestDecision = latestDecision ?: current.latestDecision,
            latestConfidence = latestConfidence ?: current.latestConfidence,
            highlightedTactics = highlightedTactics ?: current.highlightedTactics
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Senior Shield Call Guard")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Senior Shield Call Guard",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun normalizeLanguage(tag: String): String {
        return when {
            tag.startsWith("hi", ignoreCase = true) -> "hi"
            tag.startsWith("kn", ignoreCase = true) -> "kn"
            else -> "en"
        }
    }

    private fun localeTagToBcp47(tag: String): String {
        return when {
            tag.startsWith("hi", ignoreCase = true) -> "hi-IN"
            tag.startsWith("kn", ignoreCase = true) -> "kn-IN"
            else -> "en-IN"
        }
    }

    private fun parseTrustedProfiles(value: String): Set<String> {
        return value.split(",")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.length >= 3 }
            .toSet()
    }

    private fun isTrustedProfileMentioned(text: String): Boolean {
        if (trustedProfiles.isEmpty()) return false
        val haystack = text.lowercase(Locale.getDefault())
        return trustedProfiles.any { haystack.contains(it) }
    }

    private fun hasCriticalScamTactic(analysis: com.sjbit.seniorshield.cloud.CallGuardAnalysis, transcript: String): Boolean {
        val haystack = (analysis.tactics.joinToString(" ") + " " + transcript).lowercase(Locale.getDefault())
        return listOf("otp", "pin", "upi", "cvv", "password", "remote", "anydesk", "teamviewer", "qr code", "kyc")
            .any { haystack.contains(it) }
    }

    private fun mergedTranscriptForDisplay(previous: String, combined: String): String {
        val cleanedCombined = combined.trim()
        if (cleanedCombined.isBlank()) return previous
        val combinedWords = cleanedCombined.split(Regex("\\s+")).size
        val previousWords = previous.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val shouldKeepPrevious = combinedWords <= 2 && previousWords > combinedWords
        if (shouldKeepPrevious) return previous
        return cleanedCombined.takeLast(1200)
    }

    private fun isLowSignalHallucination(chunk: String, previousDisplay: String): Boolean {
        val rawLower = chunk.lowercase(Locale.getDefault())
        val normalized = chunk
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return true

        val scamRelevantTerms = listOf(
            "otp", "pin", "upi", "kyc", "aadhar", "aadhaar", "pan", "cvv", "password",
            "bank", "account", "card", "police", "crime", "illegal", "substance",
            "marijuana", "drug", "parcel", "courier", "arrest", "jail", "case",
            "money", "pay", "payment", "transfer", "crore", "lakh", "loan", "tax",
            "refund", "blocked", "verify", "remote", "anydesk", "teamviewer"
        )
        if (scamRelevantTerms.any { rawLower.contains(it) }) return false

        val repetitivePhrases = setOf("thank you", "thanks", "ok thanks", "thankyou", "thank thank")
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val uniqueWords = words.toSet()
        val isPoliteFiller = repetitivePhrases.any { phrase ->
            normalized == phrase ||
                normalized == "$phrase $phrase" ||
                normalized.replace(" ", "") == phrase.replace(" ", "")
        }
        val allWordsArePoliteFiller = words.isNotEmpty() &&
            words.all { it in setOf("thank", "thanks", "you", "ok", "okay") }
        val isRepetitiveShortChunk = words.size <= 8 && uniqueWords.size <= 2
        val repeatedInExistingTranscript = previousDisplay
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .contains(normalized)

        return isPoliteFiller || allWordsArePoliteFiller || (isRepetitiveShortChunk && repeatedInExistingTranscript)
    }

    companion object {
        private const val CHANNEL_ID = "senior_shield_call_guard"
        private const val NOTIFICATION_ID = 4401
        private const val CHUNK_DURATION_MS = 12_000L
        private const val MAX_CONTEXT_CHUNKS = 8
        private const val IDLE_POLL_MS = 2_000L
        private const val RETRY_DELAY_MS = 3_000L

        private const val ACTION_START = "com.sjbit.seniorshield.call.START"
        private const val ACTION_STOP = "com.sjbit.seniorshield.call.STOP"
        private const val EXTRA_VOICE_LANGUAGE_TAG = "extra_voice_language_tag"
        private const val EXTRA_APP_LANGUAGE_TAG = "extra_app_language_tag"
        private const val EXTRA_TTS_RATE = "extra_tts_rate"
        private const val EXTRA_TRUSTED_CALLERS_CSV = "extra_trusted_callers_csv"

        fun startIntent(context: Context, voiceLanguageTag: String, appLanguageTag: String, ttsRate: Float, trustedCallersCsv: String): Intent {
            return Intent(context, CallGuardService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VOICE_LANGUAGE_TAG, voiceLanguageTag)
                putExtra(EXTRA_APP_LANGUAGE_TAG, appLanguageTag)
                putExtra(EXTRA_TTS_RATE, ttsRate)
                putExtra(EXTRA_TRUSTED_CALLERS_CSV, trustedCallersCsv)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, CallGuardService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
