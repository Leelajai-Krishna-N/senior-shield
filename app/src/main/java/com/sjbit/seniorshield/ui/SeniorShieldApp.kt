package com.sjbit.seniorshield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjbit.seniorshield.BuildConfig
import com.sjbit.seniorshield.R
import com.sjbit.seniorshield.model.AlertEvent
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.RiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorShieldApp(viewModel: SeniorShieldViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val entries by viewModel.recentEntries.collectAsState()
    val t = appStrings(uiState.appLanguageTag)
    var lastDismissedAlertId by remember { mutableLongStateOf(-1L) }
    var showSettings by remember { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.senior_shield_logo),
                                contentDescription = "Senior Shield logo",
                                modifier = Modifier.height(40.dp)
                            )
                            Spacer(modifier = Modifier.padding(6.dp))
                            Text(t.appName, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSettings = true }) {
                            Text(t.settings)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFF9F4E8), Color(0xFFFCE7D3), Color(0xFFFFFFFF))
                        )
                    )
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { SafetyHero(t) }
                item {
                    ReviewCard(
                        sender = uiState.sender,
                        message = uiState.message,
                        trustedContact = uiState.trustedContact,
                        alertStatus = uiState.alertStatus,
                        cloudStatus = uiState.cloudStatus,
                        isCloudChecking = uiState.isCloudChecking,
                        onSenderChanged = viewModel::updateSender,
                        onMessageChanged = viewModel::updateMessage,
                        onTrustedContactChanged = viewModel::updateTrustedContact,
                        onAnalyze = viewModel::analyzeManualMessage,
                        onSpeak = viewModel::speakResult,
                        onSpeakLocal = viewModel::speakResultInLocalLanguage,
                        onSpeakFull = viewModel::speakFullMessage,
                        onSpeakFullLocal = viewModel::speakFullMessageInLocalLanguage,
                        onImNotSure = viewModel::runImNotSureCheck,
                        onAlertFamily = viewModel::sendFamilyAlert,
                        t = t
                    )
                }
                uiState.lastResult?.let { result ->
                    item { ResultCard(result, t) }
                }
                if (entries.isNotEmpty()) {
                    item {
                        Text(
                            t.recentAlerts,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(entries.take(6)) { entry ->
                        RecentEntryCard(entry)
                    }
                }
                item {
                    Text(
                        text = BuildConfig.APP_VERSION_LABEL,
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            val activeAlert = uiState.activeAlertEvent
            if (activeAlert != null && activeAlert.id != lastDismissedAlertId) {
                DangerPopup(
                    event = activeAlert,
                    onDismiss = {
                        lastDismissedAlertId = activeAlert.id
                        viewModel.dismissActiveAlert()
                    },
                    onAlertFamily = viewModel::sendFamilyAlert,
                    onReadAloud = viewModel::speakResult,
                    onReadLocal = viewModel::speakResultInLocalLanguage,
                    onReadFull = viewModel::speakFullMessage,
                    onReadFullLocal = viewModel::speakFullMessageInLocalLanguage,
                    onImNotSure = viewModel::runImNotSureCheck,
                    t = t
                )
            }

            if (showSettings) {
                SettingsDialog(
                    appLanguageTag = uiState.appLanguageTag,
                    voiceLanguageTag = uiState.voiceLanguageTag,
                    ttsRate = uiState.ttsRate,
                    autoFamilyAlert = uiState.autoFamilyAlert,
                    onDismiss = { showSettings = false },
                    onAppLanguageChanged = viewModel::setAppLanguageTag,
                    onVoiceLanguageChanged = viewModel::setVoiceLanguageTag,
                    onTtsRateChanged = viewModel::setTtsRate,
                    onToggleAutoFamilyAlert = viewModel::toggleAutoFamilyAlert,
                    t = t
                )
            }

            if (!uiState.onboardingCompleted) {
                OnboardingDialog(
                    appLanguageTag = uiState.appLanguageTag,
                    voiceLanguageTag = uiState.voiceLanguageTag,
                    ttsRate = uiState.ttsRate,
                    onAppLanguageChanged = viewModel::setAppLanguageTag,
                    onVoiceLanguageChanged = viewModel::setVoiceLanguageTag,
                    onTtsRateChanged = viewModel::setTtsRate,
                    onContinue = viewModel::completeOnboarding,
                    t = t
                )
            }
        }
    }
}

@Composable
private fun SafetyHero(t: AppStrings) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        color = Color(0xFF183A37)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when (t.appName) {
                    "\u0938\u0940\u0928\u093f\u092f\u0930 \u0936\u0940\u0932\u094d\u0921" -> "\u0927\u094b\u0916\u093e\u0927\u0921\u093c\u0940 \u0938\u0947 \u0938\u0941\u0930\u0915\u094d\u0937\u093f\u0924 \u0930\u0939\u0947\u0902"
                    "\u0cb8\u0cc0\u0ca8\u0cbf\u0caf\u0cb0\u0ccd \u0cb6\u0cc0\u0cb2\u0ccd\u0ca1\u0ccd" -> "\u0cae\u0ccb\u0cb8\u0ca6\u0cbf\u0c82\u0ca6 \u0cb8\u0cc1\u0cb0\u0c95\u0ccd\u0cb7\u0cbf\u0ca4\u0cb0\u0cbe\u0c97\u0cbf"
                    else -> "Stay safe from fraud"
                },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                when (t.appName) {
                    "\u0938\u0940\u0928\u093f\u092f\u0930 \u0936\u0940\u0932\u094d\u0921" -> "\u090f\u0948\u092a \u0928\u090f SMS \u0905\u092a\u0928\u0947-\u0906\u092a \u0926\u0947\u0916\u0924\u093e \u0939\u0948, \u0916\u0924\u0930\u0928\u093e\u0915 \u0938\u0902\u0926\u0947\u0936 \u092a\u0930 \u0932\u093e\u0932 \u091a\u0947\u0924\u093e\u0935\u0928\u0940 \u0926\u0947\u0924\u093e \u0939\u0948, \u0906\u0935\u093e\u091c\u093c \u092e\u0947\u0902 \u0938\u092e\u091d\u093e\u0924\u093e \u0939\u0948 \u0914\u0930 \u0939\u093e\u0908-\u0930\u093f\u0938\u094d\u0915 \u092a\u0930 \u092a\u0930\u093f\u0935\u093e\u0930 \u0915\u094b \u0905\u0932\u0930\u094d\u091f \u0915\u0930\u0924\u093e \u0939\u0948\u0964"
                    "\u0cb8\u0cc0\u0ca8\u0cbf\u0caf\u0cb0\u0ccd \u0cb6\u0cc0\u0cb2\u0ccd\u0ca1\u0ccd" -> "\u0c88 \u0c86\u0caa\u0ccd \u0cb9\u0cca\u0cb8 SMS\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0cb8\u0ccd\u0cb5\u0caf\u0c82\u0c9a\u0cbe\u0cb2\u0cbf\u0ca4\u0cb5\u0cbe\u0c97\u0cbf \u0ca8\u0ccb\u0ca1\u0cc1\u0ca4\u0ccd\u0ca4\u0ca6\u0cc6, \u0c85\u0caa\u0cbe\u0caf\u0c95\u0cbe\u0cb0\u0cbf \u0cb8\u0c82\u0ca6\u0cc7\u0cb6\u0c97\u0cb3\u0cbf\u0c97\u0cc6 \u0c95\u0cc6\u0c82\u0caa\u0cc1 \u0c8e\u0c9a\u0ccd\u0c9a\u0cb0\u0cbf\u0c95\u0cc6 \u0ca8\u0cc0\u0ca1\u0cc1\u0ca4\u0ccd\u0ca4\u0ca6\u0cc6, \u0ca7\u0ccd\u0cb5\u0ca8\u0cbf\u0caf\u0cb2\u0ccd\u0cb2\u0cbf \u0cb5\u0cbf\u0cb5\u0cb0\u0cbf\u0cb8\u0cc1\u0ca4\u0ccd\u0ca4\u0ca6\u0cc6 \u0cae\u0ca4\u0ccd\u0ca4\u0cc1 \u0cb9\u0cc8-\u0cb0\u0cbf\u0cb8\u0ccd\u0c95\u0ccd \u0cb8\u0c82\u0ca6\u0cc7\u0cb6\u0c97\u0cb3\u0cbf\u0c97\u0cc6 \u0c95\u0cc1\u0c9f\u0cc1\u0c82\u0cac\u0c95\u0ccd\u0c95\u0cc6 \u0c85\u0cb2\u0cb0\u0ccd\u0c9f\u0ccd \u0c95\u0cb3\u0cc1\u0cb9\u0cbf\u0cb8\u0cc1\u0ca4\u0ccd\u0ca4\u0ca6\u0cc6."
                    else -> "The app watches new SMS automatically, warns in red for dangerous messages, explains the scam aloud, and can alert family for high-risk texts."
                },
                color = Color(0xFFF5EEDC),
                fontSize = 18.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun OnboardingDialog(
    appLanguageTag: String,
    voiceLanguageTag: String,
    ttsRate: Float,
    onAppLanguageChanged: (String) -> Unit,
    onVoiceLanguageChanged: (String) -> Unit,
    onTtsRateChanged: (Float) -> Unit,
    onContinue: () -> Unit,
    t: AppStrings
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(t.setupTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(t.setupSubtitle, fontSize = 18.sp, lineHeight = 24.sp)
                LanguagePicker(
                    title = t.appLanguage,
                    selectedTag = appLanguageTag,
                    onSelected = onAppLanguageChanged,
                    t = t
                )
                LanguagePicker(
                    title = t.defaultVoiceLanguage,
                    selectedTag = voiceLanguageTag,
                    onSelected = onVoiceLanguageChanged,
                    t = t
                )
                Text(t.ttsSpeed, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = ttsRate,
                    onValueChange = onTtsRateChanged,
                    valueRange = 0.6f..0.95f
                )
                Text(t.ttsSpeedHint, fontSize = 15.sp, color = Color(0xFF4A5568))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(t.continueButton, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = Color(0xFFFFF8F0)
    )
}

@Composable
private fun SettingsDialog(
    appLanguageTag: String,
    voiceLanguageTag: String,
    ttsRate: Float,
    autoFamilyAlert: Boolean,
    onDismiss: () -> Unit,
    onAppLanguageChanged: (String) -> Unit,
    onVoiceLanguageChanged: (String) -> Unit,
    onTtsRateChanged: (Float) -> Unit,
    onToggleAutoFamilyAlert: () -> Unit,
    t: AppStrings
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.settings, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LanguagePicker(t.appLanguage, appLanguageTag, onAppLanguageChanged, t)
                LanguagePicker(t.defaultVoiceLanguage, voiceLanguageTag, onVoiceLanguageChanged, t)
                Text(t.ttsSpeed, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = ttsRate, onValueChange = onTtsRateChanged, valueRange = 0.6f..0.95f)
                Text(t.ttsSpeedHint, fontSize = 14.sp, color = Color(0xFF4A5568))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(t.autoAlertFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = autoFamilyAlert, onCheckedChange = { onToggleAutoFamilyAlert() })
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(t.done) }
        }
    )
}

@Composable
private fun LanguagePicker(
    title: String,
    selectedTag: String,
    onSelected: (String) -> Unit,
    t: AppStrings
) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val options = listOf(
            "hi-IN" to t.languageHindi,
            "kn-IN" to t.languageKannada,
            "en-US" to t.languageEnglish,
            "system" to t.languageSystem
        )
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelected(tag) },
                selected = selectedTag == tag
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ReviewCard(
    sender: String,
    message: String,
    trustedContact: String,
    alertStatus: String?,
    cloudStatus: String?,
    isCloudChecking: Boolean,
    onSenderChanged: (String) -> Unit,
    onMessageChanged: (String) -> Unit,
    onTrustedContactChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakLocal: () -> Unit,
    onSpeakFull: () -> Unit,
    onSpeakFullLocal: () -> Unit,
    onImNotSure: () -> Unit,
    onAlertFamily: () -> Unit,
    t: AppStrings
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t.manualReviewTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                value = sender,
                onValueChange = onSenderChanged,
                label = { Text(t.sender) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChanged,
                label = { Text(t.pasteMessage) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )
            OutlinedTextField(
                value = trustedContact,
                onValueChange = onTrustedContactChanged,
                label = { Text(t.familyNumber) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            if (alertStatus != null) {
                Text(
                    text = alertStatus,
                    color = Color(0xFF183A37),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (cloudStatus != null) {
                Text(
                    text = cloudStatus,
                    color = Color(0xFF4A5568),
                    fontSize = 15.sp
                )
            }
            if (isCloudChecking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Spacer(modifier = Modifier.padding(6.dp))
                Text(t.checkMessage, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onImNotSure,
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(t.imNotSure, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSpeak,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(t.readAloud, fontSize = 19.sp)
                }
                Button(
                    onClick = onSpeakLocal,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(t.readLocal, fontSize = 19.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSpeakFull,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(t.readFullMessage, fontSize = 18.sp)
                }
                Button(
                    onClick = onSpeakFullLocal,
                    modifier = Modifier.weight(1f).height(68.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(t.readFullLocal, fontSize = 16.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAlertFamily,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(t.alertFamily, fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun DangerPopup(
    event: AlertEvent,
    onDismiss: () -> Unit,
    onAlertFamily: () -> Unit,
    onReadAloud: () -> Unit,
    onReadLocal: () -> Unit,
    onReadFull: () -> Unit,
    onReadFullLocal: () -> Unit,
    onImNotSure: () -> Unit,
    t: AppStrings
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onAlertFamily) {
                Text(t.alertFamily)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(t.dismiss)
            }
        },
        title = {
            Text(
                text = t.dangerDetected,
                color = Color(0xFF9F1D35),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = event.entry.result.plainLanguageExplanation,
                    color = Color(0xFF4A0E1D),
                    fontSize = 18.sp
                )
                Text(
                    text = "${t.sender}: ${event.entry.input.sender}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                event.autoAlertStatus?.let {
                    Text(it, color = Color(0xFF183A37), fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = onReadAloud, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(t.readAloudAgain)
                }
                Button(onClick = onReadLocal, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(t.readInLocalLanguage)
                }
                Button(onClick = onReadFull, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(t.readFullMessage)
                }
                Button(onClick = onReadFullLocal, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(t.readFullLocal)
                }
                Button(onClick = onImNotSure, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(t.imNotSure)
                }
            }
        },
        containerColor = Color(0xFFFFE5E5)
    )
}

@Composable
private fun ResultCard(entry: AnalysisEntry, t: AppStrings) {
    val color = when (entry.result.riskLevel) {
        RiskLevel.HIGH_RISK -> Color(0xFF9F1D35)
        RiskLevel.CAUTION -> Color(0xFFF4A300)
        RiskLevel.SAFE -> Color(0xFF2D6A4F)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = entry.result.riskLevel.name.replace('_', ' '),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(entry.result.plainLanguageExplanation, fontSize = 20.sp, color = Color.White, lineHeight = 28.sp)
            Text("${t.whatToDo}: ${entry.result.suggestedAction}", fontSize = 18.sp, color = Color.White)
            entry.result.reasons.take(4).forEach { reason ->
                Text("- $reason", fontSize = 17.sp, color = Color.White)
            }
            if (entry.result.linkAnalyses.isNotEmpty()) {
                Text(t.linkCheck, fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold)
                entry.result.linkAnalyses.forEach { link ->
                    Text(
                        "${link.finalHost.ifBlank { link.normalizedUrl }}: ${link.riskLevel.name.replace('_', ' ')}",
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    link.nestedTargets.take(2).forEach { nested ->
                        Text("${t.nestedTarget}: $nested", fontSize = 15.sp, color = Color(0xFFFDEBD0))
                    }
                }
            }
        }
    }
}

private data class AppStrings(
    val appName: String,
    val recentAlerts: String,
    val manualReviewTitle: String,
    val sender: String,
    val pasteMessage: String,
    val familyNumber: String,
    val defaultVoiceLanguage: String,
    val appLanguage: String,
    val autoAlertFamily: String,
    val checkMessage: String,
    val readAloud: String,
    val readLocal: String,
    val readFullMessage: String,
    val readFullLocal: String,
    val alertFamily: String,
    val dangerDetected: String,
    val readAloudAgain: String,
    val readInLocalLanguage: String,
    val imNotSure: String,
    val settings: String,
    val setupTitle: String,
    val setupSubtitle: String,
    val ttsSpeed: String,
    val ttsSpeedHint: String,
    val continueButton: String,
    val reopenSetup: String,
    val done: String,
    val dismiss: String,
    val whatToDo: String,
    val linkCheck: String,
    val nestedTarget: String,
    val languageHindi: String,
    val languageKannada: String,
    val languageEnglish: String,
    val languageSystem: String
)

private fun appStrings(tag: String): AppStrings {
    val normalizedTag = tag.lowercase()
    val deviceLang = java.util.Locale.getDefault().language
    val isHindi = when (normalizedTag) {
        "hi", "hi-in" -> true
        "system" -> deviceLang == "hi"
        else -> false
    }
    val isKannada = when (normalizedTag) {
        "kn", "kn-in" -> true
        "system" -> deviceLang == "kn"
        else -> false
    }
    return if (isHindi) {
        AppStrings(
            appName = "\u0938\u0940\u0928\u093f\u092f\u0930 \u0936\u0940\u0932\u094d\u0921",
            recentAlerts = "\u0939\u093e\u0932 \u0915\u0940 \u091a\u0947\u0924\u093e\u0935\u0928\u093f\u092f\u093e\u0901",
            manualReviewTitle = "\u092e\u0948\u0928\u0941\u0905\u0932 \u091c\u093e\u0901\u091a \u0914\u0930 \u092a\u0930\u093f\u0935\u093e\u0930 \u0938\u0947\u091f\u0905\u092a",
            sender = "\u092d\u0947\u091c\u0928\u0947 \u0935\u093e\u0932\u093e",
            pasteMessage = "\u092f\u0939\u093e\u0901 SMS \u092f\u093e \u0908\u092e\u0947\u0932 \u092a\u0947\u0938\u094d\u091f \u0915\u0930\u0947\u0902",
            familyNumber = "\u092a\u0930\u093f\u0935\u093e\u0930 \u0915\u093e \u0928\u0902\u092c\u0930 (+91 \u0920\u0940\u0915 \u0939\u0948)",
            defaultVoiceLanguage = "\u0921\u093f\u095e\u093e\u0932\u094d\u091f \u0906\u0935\u093e\u095b \u092d\u093e\u0937\u093e",
            appLanguage = "\u0910\u092a \u092d\u093e\u0937\u093e",
            autoAlertFamily = "\u092a\u0930\u093f\u0935\u093e\u0930 \u0915\u094b \u0911\u091f\u094b \u0905\u0932\u0930\u094d\u091f",
            checkMessage = "\u0938\u0902\u0926\u0947\u0936 \u091c\u093e\u0901\u091a\u0947\u0902",
            readAloud = "\u0906\u0935\u093e\u095b \u092e\u0947\u0902 \u092a\u095d\u0947\u0902",
            readLocal = "\u0932\u094b\u0915\u0932 \u092e\u0947\u0902 \u092a\u095d\u0947\u0902",
            readFullMessage = "\u092a\u0942\u0930\u093e \u0938\u0902\u0926\u0947\u0936 \u092a\u095d\u0947\u0902",
            readFullLocal = "\u092a\u0942\u0930\u093e \u0932\u094b\u0915\u0932 \u092a\u095d\u0947\u0902",
            alertFamily = "\u092a\u0930\u093f\u0935\u093e\u0930 \u0915\u094b \u0905\u0932\u0930\u094d\u091f",
            dangerDetected = "\u0916\u0924\u0930\u0928\u093e\u0915 SMS \u092e\u093f\u0932\u093e",
            readAloudAgain = "\u095e\u093f\u0930 \u0938\u0947 \u092a\u095d\u0947\u0902",
            readInLocalLanguage = "\u0932\u094b\u0915\u0932 \u092d\u093e\u0937\u093e \u092e\u0947\u0902 \u092a\u095d\u0947\u0902",
            imNotSure = "\u092e\u0941\u091d\u0947 \u092f\u0915\u0940\u0928 \u0928\u0939\u0940\u0902 \u0939\u0948",
            settings = "\u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938",
            setupTitle = "\u0936\u0941\u0930\u0941\u0906\u0924\u0940 \u0938\u0947\u091f\u0905\u092a",
            setupSubtitle = "\u090f\u0915 \u092c\u093e\u0930 \u092d\u093e\u0937\u093e \u0914\u0930 \u092a\u095d\u0928\u0947 \u0915\u0940 \u0917\u0924\u093f \u091a\u0941\u0928 \u0932\u0947\u0902. \u092c\u093e\u0926 \u092e\u0947\u0902 \u0907\u0938\u0947 \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938 \u092e\u0947\u0902 \u092c\u0926\u0932 \u0938\u0915\u0924\u0947 \u0939\u0948\u0902.",
            ttsSpeed = "\u092a\u095d\u0928\u0947 \u0915\u0940 \u0917\u0924\u093f",
            ttsSpeedHint = "\u0927\u0940\u092e\u0940 \u0917\u0924\u093f \u092c\u0941\u091c\u0941\u0930\u094d\u0917\u094b\u0902 \u0915\u0947 \u0932\u093f\u090f \u092a\u095d\u0928\u093e \u0906\u0938\u093e\u0928 \u092c\u0928\u093e\u0924\u0940 \u0939\u0948.",
            continueButton = "\u091c\u093e\u0930\u0940 \u0930\u0916\u0947\u0902",
            reopenSetup = "\u0938\u0947\u091f\u0905\u092a \u095e\u093f\u0930 \u0938\u0947 \u0916\u094b\u0932\u0947\u0902",
            done = "\u0939\u094b \u0917\u092f\u093e",
            dismiss = "\u092c\u0902\u0926 \u0915\u0930\u0947\u0902",
            whatToDo = "\u0905\u092c \u0915\u094d\u092f\u093e \u0915\u0930\u0947\u0902",
            linkCheck = "\u0932\u093f\u0902\u0915 \u091c\u093e\u0901\u091a",
            nestedTarget = "\u0905\u0902\u0926\u0930\u0942\u0928\u0940 \u0932\u0915\u094d\u0937\u094d\u092f",
            languageHindi = "\u0939\u093f\u0928\u094d\u0926\u0940",
            languageKannada = "\u0915\u0928\u094d\u0928\u0921",
            languageEnglish = "\u0905\u0902\u0917\u094d\u0930\u0947\u091c\u093c\u0940",
            languageSystem = "\u0938\u093f\u0938\u094d\u091f\u092e"
        )
    } else if (isKannada) {
        AppStrings(
            appName = "\u0cb8\u0cc0\u0ca8\u0cbf\u0caf\u0cb0\u0ccd \u0cb6\u0cc0\u0cb2\u0ccd\u0ca1\u0ccd",
            recentAlerts = "\u0c87\u0ca4\u0ccd\u0ca4\u0cc0\u0c9a\u0cbf\u0ca8 \u0c85\u0cb2\u0cb0\u0ccd\u0c9f\u0ccd\u200c\u0c97\u0cb3\u0cc1",
            manualReviewTitle = "\u0c95\u0cc8\u0caf\u0cbe\u0cb0\u0cc6 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0ca8\u0cc6 \u0cae\u0ca4\u0ccd\u0ca4\u0cc1 \u0c95\u0cc1\u0c9f\u0cc1\u0c82\u0cac \u0cb8\u0cc6\u0c9f\u0caa\u0ccd",
            sender = "\u0c95\u0cb3\u0cc1\u0cb9\u0cbf\u0cb8\u0cbf\u0ca6\u0cb5\u0cb0\u0cc1",
            pasteMessage = "\u0c87\u0cb2\u0ccd\u0cb2\u0cbf SMS \u0c85\u0ca5\u0cb5\u0cbe \u0c87\u0cae\u0cc7\u0cb2\u0ccd \u0caa\u0cc7\u0cb8\u0ccd\u0c9f\u0ccd \u0cae\u0cbe\u0ca1\u0cbf",
            familyNumber = "\u0c95\u0cc1\u0c9f\u0cc1\u0c82\u0cac \u0cb8\u0c82\u0caa\u0cb0\u0ccd\u0c95 \u0cb8\u0c82\u0c96\u0ccd\u0caf\u0cc6 (+91 \u0cb8\u0cb0\u0cbf\u0caf\u0cc7)",
            defaultVoiceLanguage = "\u0ca1\u0cbf\u0cab\u0cbe\u0cb2\u0ccd\u0c9f\u0ccd \u0c93\u0ca6\u0cc1\u0cb5 \u0cad\u0cbe\u0cb7\u0cc6",
            appLanguage = "\u0c86\u0caa\u0ccd \u0cad\u0cbe\u0cb7\u0cc6",
            autoAlertFamily = "\u0c95\u0cc1\u0c9f\u0cc1\u0c82\u0cac\u0c95\u0ccd\u0c95\u0cc6 \u0cb8\u0ccd\u0cb5\u0caf\u0c82 \u0c85\u0cb2\u0cb0\u0ccd\u0c9f\u0ccd",
            checkMessage = "\u0cb8\u0c82\u0ca6\u0cc7\u0cb6 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0cbf\u0cb8\u0cbf",
            readAloud = "\u0c9c\u0ccb\u0cb0\u0cbe\u0c97\u0cbf \u0c93\u0ca6\u0cbf",
            readLocal = "\u0cb8\u0ccd\u0ca5\u0cb3\u0cc0\u0caf\u0ca6\u0cb2\u0ccd\u0cb2\u0cbf \u0c93\u0ca6\u0cbf",
            readFullMessage = "\u0caa\u0cc2\u0cb0\u0ccd\u0ca3 \u0cb8\u0c82\u0ca6\u0cc7\u0cb6 \u0c93\u0ca6\u0cbf",
            readFullLocal = "\u0caa\u0cc2\u0cb0\u0ccd\u0ca3 \u0cb8\u0ccd\u0ca5\u0cb3\u0cc0\u0caf \u0c93\u0ca6\u0cbf",
            alertFamily = "\u0c95\u0cc1\u0c9f\u0cc1\u0c82\u0cac\u0c95\u0ccd\u0c95\u0cc6 \u0c85\u0cb2\u0cb0\u0ccd\u0c9f\u0ccd",
            dangerDetected = "\u0c85\u0caa\u0cbe\u0caf\u0c95\u0cbe\u0cb0\u0cbf SMS \u0caa\u0ca4\u0ccd\u0ca4\u0cc6\u0caf\u0cbe\u0caf\u0cbf\u0ca4\u0cc1",
            readAloudAgain = "\u0cae\u0ca4\u0ccd\u0ca4\u0cc6 \u0c93\u0ca6\u0cbf",
            readInLocalLanguage = "\u0cb8\u0ccd\u0ca5\u0cb3\u0cc0\u0caf \u0cad\u0cbe\u0cb7\u0cc6\u0caf\u0cb2\u0ccd\u0cb2\u0cbf \u0c93\u0ca6\u0cbf",
            imNotSure = "\u0ca8\u0ca8\u0c97\u0cc6 \u0c96\u0c9a\u0cbf\u0ca4\u0cb5\u0cbe\u0c97\u0cbf \u0ca4\u0cbf\u0cb3\u0cbf\u0ca6\u0cbf\u0cb2\u0ccd\u0cb2",
            settings = "\u0cb8\u0cc6\u0c9f\u0ccd\u0c9f\u0cbf\u0c82\u0c97\u0ccd\u0cb8\u0ccd",
            setupTitle = "\u0c86\u0cb0\u0c82\u0cad\u0cbf\u0c95 \u0cb8\u0cc6\u0c9f\u0caa\u0ccd",
            setupSubtitle = "\u0c92\u0cae\u0ccd\u0cae\u0cc6 \u0cad\u0cbe\u0cb7\u0cc6 \u0cae\u0ca4\u0ccd\u0ca4\u0cc1 \u0c93\u0ca6\u0cc1\u0cb5 \u0cb5\u0cc7\u0c97 \u0c86\u0caf\u0ccd\u0c95\u0cc6 \u0cae\u0cbe\u0ca1\u0cbf. \u0ca8\u0c82\u0ca4\u0cb0 \u0cb8\u0cc6\u0c9f\u0ccd\u0c9f\u0cbf\u0c82\u0c97\u0ccd\u0cb8\u0ccd \u0ca8\u0cb2\u0ccd\u0cb2\u0cbf \u0cac\u0ca6\u0cb2\u0cbf\u0cb8\u0cac\u0cb9\u0cc1\u0ca6\u0cc1.",
            ttsSpeed = "\u0c93\u0ca6\u0cc1\u0cb5 \u0cb5\u0cc7\u0c97",
            ttsSpeedHint = "\u0ca8\u0cbf\u0ca7\u0cbe\u0ca8\u0cb5\u0cbe\u0ca6 \u0cb5\u0cc7\u0c97 \u0cb5\u0cc3\u0ca6\u0ccd\u0ca7\u0cb0\u0cbf\u0c97\u0cc6 \u0c95\u0cc7\u0cb3\u0cb2\u0cc1 \u0cb8\u0cc1\u0cb2\u0cad\u0cb5\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0ca6\u0cc6.",
            continueButton = "\u0cae\u0cc1\u0c82\u0ca6\u0cc1\u0cb5\u0cb0\u0cbf\u0cb8\u0cbf",
            reopenSetup = "\u0cb8\u0cc6\u0c9f\u0caa\u0ccd \u0cae\u0ca4\u0ccd\u0ca4\u0cc6 \u0ca4\u0cc6\u0cb0\u0cc6\u0caf\u0cbf\u0cb0\u0cbf",
            done = "\u0c86\u0caf\u0cbf\u0ca4\u0cc1",
            dismiss = "\u0cae\u0cc1\u0c9a\u0ccd\u0c9a\u0cbf",
            whatToDo = "\u0cae\u0cc1\u0c82\u0ca6\u0cc7 \u0c8f\u0ca8\u0cc1 \u0cae\u0cbe\u0ca1\u0cac\u0cc7\u0c95\u0cc1",
            linkCheck = "\u0cb2\u0cbf\u0c82\u0c95\u0ccd \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0ca8\u0cc6",
            nestedTarget = "\u0c92\u0cb3\u0c97\u0cbf\u0ca8 \u0c97\u0cc1\u0cb0\u0cbf",
            languageHindi = "\u0cb9\u0cbf\u0c82\u0ca6\u0cc0",
            languageKannada = "\u0c95\u0ca8\u0ccd\u0ca8\u0ca1",
            languageEnglish = "\u0c87\u0c82\u0c97\u0ccd\u0cb2\u0cbf\u0cb7\u0ccd",
            languageSystem = "\u0cb8\u0cbf\u0cb8\u0ccd\u0c9f\u0cae\u0ccd"
        )
    } else {
        AppStrings(
            appName = "Senior Shield",
            recentAlerts = "Recent Alerts",
            manualReviewTitle = "Manual review and family setup",
            sender = "Sender",
            pasteMessage = "Paste SMS or email here",
            familyNumber = "Family contact number (+91 okay)",
            defaultVoiceLanguage = "Default read aloud language",
            appLanguage = "App language",
            autoAlertFamily = "Auto alert family",
            checkMessage = "Check message",
            readAloud = "Read aloud",
            readLocal = "Read local",
            readFullMessage = "Read full msg",
            readFullLocal = "Full msg local",
            alertFamily = "Alert family",
            dangerDetected = "Dangerous SMS Detected",
            readAloudAgain = "Read Aloud Again",
            readInLocalLanguage = "Read in Local Language",
            imNotSure = "I'm not sure about it",
            settings = "Settings",
            setupTitle = "Initial Setup",
            setupSubtitle = "Pick your language and reading speed once. You can change them later in settings.",
            ttsSpeed = "Reading speed",
            ttsSpeedHint = "A slower pace is easier for seniors to follow.",
            continueButton = "Continue",
            reopenSetup = "Reopen setup",
            done = "Done",
            dismiss = "Dismiss",
            whatToDo = "What to do",
            linkCheck = "Link check",
            nestedTarget = "Nested target",
            languageHindi = "Hindi",
            languageKannada = "Kannada",
            languageEnglish = "English",
            languageSystem = "System"
        )
    }
}

@Composable
private fun RecentEntryCard(entry: AnalysisEntry) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.input.sender, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(entry.input.receivedAtLabel, fontSize = 14.sp, color = Color.Gray)
            Text(entry.input.body.take(140), fontSize = 17.sp)
            Text(
                entry.result.riskLevel.name.replace('_', ' '),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (entry.result.riskLevel) {
                    RiskLevel.HIGH_RISK -> Color(0xFF9F1D35)
                    RiskLevel.CAUTION -> Color(0xFFC77D00)
                    RiskLevel.SAFE -> Color(0xFF2D6A4F)
                }
            )
        }
    }
}
