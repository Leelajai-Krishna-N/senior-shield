package com.sjbit.seniorshield

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.model.PhishingAnalysisResult
import com.sjbit.seniorshield.model.RiskLevel
import com.sjbit.seniorshield.platform.SeniorShieldPreferences
import com.sjbit.seniorshield.platform.SmsAlertSender
import com.sjbit.seniorshield.platform.VoiceAlertSpeaker

class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val explanation = intent.getStringExtra(EXTRA_EXPLANATION).orEmpty()
        val risk = intent.getStringExtra(EXTRA_RISK).orEmpty()
        val settings = SeniorShieldPreferences(this).load()

        if (explanation.isNotBlank()) {
            VoiceAlertSpeaker.speak(this, explanation, settings.voiceLanguageTag)
        }

        setContent {
            MaterialTheme {
                AlertScreen(
                    sender = sender,
                    body = body,
                    explanation = explanation,
                    risk = risk,
                    onReadAloud = {
                        if (explanation.isNotBlank()) {
                            VoiceAlertSpeaker.speak(this, explanation, settings.voiceLanguageTag)
                        }
                    },
                    onAlertFamily = {
                        val contact = settings.trustedContact
                        if (contact.isBlank()) {
                            Toast.makeText(this, "Set family number in app first.", Toast.LENGTH_LONG).show()
                            return@AlertScreen
                        }
                        val entry = AnalysisEntry(
                            input = MessageInput(
                                source = MessageSource.SMS,
                                sender = sender.ifBlank { "Unknown sender" },
                                body = body,
                                receivedAtLabel = ""
                            ),
                            result = PhishingAnalysisResult(
                                riskLevel = when (risk.uppercase()) {
                                    "HIGH_RISK" -> RiskLevel.HIGH_RISK
                                    "CAUTION" -> RiskLevel.CAUTION
                                    else -> RiskLevel.SAFE
                                },
                                score = 100,
                                reasons = listOf(explanation),
                                plainLanguageExplanation = explanation,
                                suggestedAction = "Do not act until verified.",
                                containsSuspiciousLink = body.contains("http"),
                                linkAnalyses = emptyList()
                            )
                        )
                        SmsAlertSender.sendFamilyAlert(contact, entry).onSuccess {
                            Toast.makeText(this, "Family alert sent to $it", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(this, "Could not send family alert.", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_EXPLANATION = "extra_explanation"
        const val EXTRA_RISK = "extra_risk"
    }
}

@Composable
private fun AlertScreen(
    sender: String,
    body: String,
    explanation: String,
    risk: String,
    onReadAloud: () -> Unit,
    onAlertFamily: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7F1D1D))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Dangerous Message Detected", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Risk: $risk", color = Color(0xFFFFE4E6), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Sender: $sender", color = Color.White, fontSize = 18.sp)
        Text(explanation, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(body.take(260), color = Color(0xFFFEE2E2), fontSize = 16.sp)
        Button(onClick = onReadAloud, modifier = Modifier.fillMaxWidth()) {
            Text("Read Aloud Again")
        }
        Button(onClick = onAlertFamily, modifier = Modifier.fillMaxWidth()) {
            Text("Alert Family Now")
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("I Understand")
        }
    }
}
