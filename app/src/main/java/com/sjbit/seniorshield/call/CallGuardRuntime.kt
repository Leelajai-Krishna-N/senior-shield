package com.sjbit.seniorshield.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallGuardState(
    val running: Boolean = false,
    val status: String = "Call Guard is off.",
    val latestTranscript: String = "",
    val latestDecision: String = "idle",
    val latestConfidence: Int = 0,
    val highlightedTactics: List<String> = emptyList()
)

object CallGuardRuntime {
    private val mutableState = MutableStateFlow(CallGuardState())

    fun state(): StateFlow<CallGuardState> = mutableState.asStateFlow()

    fun update(
        running: Boolean,
        status: String,
        latestTranscript: String = mutableState.value.latestTranscript,
        latestDecision: String = mutableState.value.latestDecision,
        latestConfidence: Int = mutableState.value.latestConfidence,
        highlightedTactics: List<String> = mutableState.value.highlightedTactics
    ) {
        mutableState.value = CallGuardState(
            running = running,
            status = status,
            latestTranscript = latestTranscript,
            latestDecision = latestDecision,
            latestConfidence = latestConfidence,
            highlightedTactics = highlightedTactics
        )
    }
}
