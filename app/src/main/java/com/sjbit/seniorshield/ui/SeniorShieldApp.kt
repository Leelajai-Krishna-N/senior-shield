package com.sjbit.seniorshield.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjbit.seniorshield.BuildConfig
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

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(t.appName, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
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
                        voiceLanguageTag = uiState.voiceLanguageTag,
                        appLanguageTag = uiState.appLanguageTag,
                        autoFamilyAlert = uiState.autoFamilyAlert,
                        alertStatus = uiState.alertStatus,
                        cloudStatus = uiState.cloudStatus,
                        isCloudChecking = uiState.isCloudChecking,
                        onSenderChanged = viewModel::updateSender,
                        onMessageChanged = viewModel::updateMessage,
                        onTrustedContactChanged = viewModel::updateTrustedContact,
                        onAnalyze = viewModel::analyzeManualMessage,
                        onVoiceLanguageChanged = viewModel::setVoiceLanguageTag,
                        onAppLanguageChanged = viewModel::setAppLanguageTag,
                        onToggleAutoFamilyAlert = viewModel::toggleAutoFamilyAlert,
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
                    item { ResultCard(result) }
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
                    "सीनियर शील्ड" -> "धोखाधड़ी से सुरक्षित रहें"
                    "ಸೀನಿಯರ್ ಶೀಲ್ಡ್" -> "ಮೋಸದಿಂದ ಸುರಕ್ಷಿತರಾಗಿ"
                    else -> "Stay safe from fraud"
                },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                when (t.appName) {
                    "सीनियर शील्ड" -> "ऐप नए SMS अपने-आप देखता है, खतरनाक संदेश पर लाल चेतावनी देता है, आवाज़ में समझाता है और हाई-रिस्क पर परिवार को अलर्ट करता है।"
                    "ಸೀನಿಯರ್ ಶೀಲ್ಡ್" -> "ಈ ಆಪ್ ಹೊಸ SMSಗಳನ್ನು ಸ್ವಯಂಚಾಲಿತವಾಗಿ ನೋಡುತ್ತದೆ, ಅಪಾಯಕಾರಿ ಸಂದೇಶಗಳಿಗೆ ಕೆಂಪು ಎಚ್ಚರಿಕೆ ನೀಡುತ್ತದೆ, ಧ್ವನಿಯಲ್ಲಿ ವಿವರಿಸುತ್ತದೆ ಮತ್ತು ಹೈ-ರಿಸ್ಕ್ ಸಂದೇಶಗಳಿಗೆ ಕುಟುಂಬಕ್ಕೆ ಅಲರ್ಟ್ ಕಳುಹಿಸುತ್ತದೆ."
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
private fun ReviewCard(
    sender: String,
    message: String,
    trustedContact: String,
    voiceLanguageTag: String,
    appLanguageTag: String,
    autoFamilyAlert: Boolean,
    alertStatus: String?,
    cloudStatus: String?,
    isCloudChecking: Boolean,
    onSenderChanged: (String) -> Unit,
    onMessageChanged: (String) -> Unit,
    onTrustedContactChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onVoiceLanguageChanged: (String) -> Unit,
    onAppLanguageChanged: (String) -> Unit,
    onToggleAutoFamilyAlert: () -> Unit,
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
            Text(t.defaultVoiceLanguage, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("hi-IN" to "Hindi", "kn-IN" to "Kannada", "en-US" to "English", "system" to "System")
                options.forEachIndexed { index, (tag, label) ->
                    SegmentedButton(
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { onVoiceLanguageChanged(tag) },
                        selected = voiceLanguageTag == tag
                    ) {
                        Text(label)
                    }
                }
            }
            Text(t.appLanguage, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("hi-IN" to "Hindi", "kn-IN" to "Kannada", "en-US" to "English", "system" to "System")
                options.forEachIndexed { index, (tag, label) ->
                    SegmentedButton(
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { onAppLanguageChanged(tag) },
                        selected = appLanguageTag == tag
                    ) {
                        Text(label)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.autoAlertFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Switch(checked = autoFamilyAlert, onCheckedChange = { onToggleAutoFamilyAlert() })
            }
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Spacer(modifier = Modifier.padding(6.dp))
                Text(t.checkMessage, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onImNotSure,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(t.imNotSure, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSpeak,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(t.readAloud, fontSize = 18.sp)
                }
                Button(
                    onClick = onSpeakLocal,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(t.readLocal, fontSize = 18.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSpeakFull,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(t.readFullMessage, fontSize = 18.sp)
                }
                Button(
                    onClick = onSpeakFullLocal,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(t.readFullLocal, fontSize = 16.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAlertFamily,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(t.alertFamily, fontSize = 18.sp)
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
                Text("Alert Family")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss")
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
                    text = "Sender: ${event.entry.input.sender}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                event.autoAlertStatus?.let {
                    Text(it, color = Color(0xFF183A37), fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = onReadAloud, shape = RoundedCornerShape(16.dp)) {
                    Text(t.readAloudAgain)
                }
                Button(onClick = onReadLocal, shape = RoundedCornerShape(16.dp)) {
                    Text(t.readInLocalLanguage)
                }
                Button(onClick = onReadFull, shape = RoundedCornerShape(16.dp)) {
                    Text(t.readFullMessage)
                }
                Button(onClick = onReadFullLocal, shape = RoundedCornerShape(16.dp)) {
                    Text(t.readFullLocal)
                }
                Button(onClick = onImNotSure, shape = RoundedCornerShape(16.dp)) {
                    Text(t.imNotSure)
                }
            }
        },
        containerColor = Color(0xFFFFE5E5)
    )
}

@Composable
private fun ResultCard(entry: AnalysisEntry) {
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
            Text("What to do: ${entry.result.suggestedAction}", fontSize = 18.sp, color = Color.White)
            entry.result.reasons.take(4).forEach { reason ->
                Text("- $reason", fontSize = 17.sp, color = Color.White)
            }
            if (entry.result.linkAnalyses.isNotEmpty()) {
                Text("Link check", fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold)
                entry.result.linkAnalyses.forEach { link ->
                    Text(
                        "${link.finalHost.ifBlank { link.normalizedUrl }}: ${link.riskLevel.name.replace('_', ' ')}",
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    link.nestedTargets.take(2).forEach { nested ->
                        Text("Nested target: $nested", fontSize = 15.sp, color = Color(0xFFFDEBD0))
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
    val imNotSure: String = "I'm not sure about it"
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
            appName = "सीनियर शील्ड",
            recentAlerts = "हाल की चेतावनियाँ",
            manualReviewTitle = "मैनुअल जाँच और परिवार सेटअप",
            sender = "भेजने वाला",
            pasteMessage = "यहाँ SMS या ईमेल पेस्ट करें",
            familyNumber = "परिवार का नंबर (+91 ठीक है)",
            defaultVoiceLanguage = "डिफ़ॉल्ट आवाज़ भाषा",
            appLanguage = "ऐप भाषा",
            autoAlertFamily = "परिवार को ऑटो अलर्ट",
            checkMessage = "संदेश जाँचें",
            readAloud = "आवाज़ में पढ़ें",
            readLocal = "लोकल में पढ़ें",
            readFullMessage = "पूरा संदेश पढ़ें",
            readFullLocal = "पूरा लोकल पढ़ें",
            alertFamily = "परिवार को अलर्ट",
            dangerDetected = "खतरनाक SMS मिला",
            readAloudAgain = "फिर से पढ़ें",
            readInLocalLanguage = "लोकल भाषा में पढ़ें"
        )
    } else if (isKannada) {
        AppStrings(
            appName = "ಸೀನಿಯರ್ ಶೀಲ್ಡ್",
            recentAlerts = "ಇತ್ತೀಚಿನ ಅಲರ್ಟ್‌ಗಳು",
            manualReviewTitle = "ಕೈಯಾರೆ ಪರಿಶೀಲನೆ ಮತ್ತು ಕುಟುಂಬ ಸೆಟಪ್",
            sender = "ಕಳುಹಿಸಿದವರು",
            pasteMessage = "ಇಲ್ಲಿ SMS ಅಥವಾ ಇಮೇಲ್ ಪೇಸ್ಟ್ ಮಾಡಿ",
            familyNumber = "ಕುಟುಂಬ ಸಂಪರ್ಕ ಸಂಖ್ಯೆ (+91 ಸರಿಯೇ)",
            defaultVoiceLanguage = "ಡಿಫಾಲ್ಟ್ ಓದುವ ಭಾಷೆ",
            appLanguage = "ಆಪ್ ಭಾಷೆ",
            autoAlertFamily = "ಕುಟುಂಬಕ್ಕೆ ಸ್ವಯಂ ಅಲರ್ಟ್",
            checkMessage = "ಸಂದೇಶ ಪರಿಶೀಲಿಸಿ",
            readAloud = "ಜೋರಾಗಿ ಓದಿ",
            readLocal = "ಸ್ಥಳೀಯದಲ್ಲಿ ಓದಿ",
            readFullMessage = "ಪೂರ್ಣ ಸಂದೇಶ ಓದಿ",
            readFullLocal = "ಪೂರ್ಣ ಸ್ಥಳೀಯ ಓದಿ",
            alertFamily = "ಕುಟುಂಬಕ್ಕೆ ಅಲರ್ಟ್",
            dangerDetected = "ಅಪಾಯಕಾರಿ SMS ಪತ್ತೆಯಾಯಿತು",
            readAloudAgain = "ಮತ್ತೆ ಓದಿ",
            readInLocalLanguage = "ಸ್ಥಳೀಯ ಭಾಷೆಯಲ್ಲಿ ಓದಿ"
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
            readInLocalLanguage = "Read in Local Language"
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
