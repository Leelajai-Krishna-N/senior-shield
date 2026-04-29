package com.sjbit.seniorshield.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sjbit.seniorshield.model.MessageInput
import com.sjbit.seniorshield.model.MessageSource
import com.sjbit.seniorshield.repo.AnalysisRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString(separator = " ") { it.messageBody.orEmpty() }.trim()
        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()

        if (body.isBlank()) return

        val input = MessageInput(
            source = MessageSource.SMS,
            sender = sender.ifBlank { "Unknown sender" },
            body = body,
            receivedAtLabel = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        )
        AnalysisRepository.analyze(context, input)
    }
}
