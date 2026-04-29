package com.sjbit.seniorshield.platform

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sjbit.seniorshield.AlertActivity
import com.sjbit.seniorshield.model.AnalysisEntry
import com.sjbit.seniorshield.model.RiskLevel

class AlertNotifier(private val context: Context) {
    fun show(entry: AnalysisEntry) {
        ensureChannel()

        val title = when (entry.result.riskLevel) {
            RiskLevel.HIGH_RISK -> "Dangerous message detected"
            RiskLevel.CAUTION -> "Suspicious message detected"
            RiskLevel.SAFE -> "Message reviewed"
        }

        val openIntent = Intent(context, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_SENDER, entry.input.sender)
            putExtra(AlertActivity.EXTRA_BODY, entry.input.body)
            putExtra(AlertActivity.EXTRA_EXPLANATION, entry.result.plainLanguageExplanation)
            putExtra(AlertActivity.EXTRA_RISK, entry.result.riskLevel.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            entry.input.body.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(entry.result.plainLanguageExplanation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setVibrate(longArrayOf(0, 450, 200, 450))
            .setAutoCancel(true)
            .setFullScreenIntent(contentPendingIntent, entry.result.riskLevel == RiskLevel.HIGH_RISK)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(entry.input.body.hashCode(), notification)
        }

        if (entry.result.riskLevel == RiskLevel.HIGH_RISK) {
            // Extra fallback: directly open the red warning screen in case full-screen notification is suppressed.
            runCatching { context.startActivity(openIntent) }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Senior Shield Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "senior_shield_alerts"
    }
}
