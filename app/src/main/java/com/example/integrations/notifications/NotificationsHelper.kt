package com.example.integrations.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.models.RiskLevel

object NotificationsHelper {
    const val CHANNEL_ID = "rakshakai_alerts"
    const val EXTRA_NAV_SCAN_RESULT_TEXT = "extra_nav_scan_text"
    const val EXTRA_NAV_SCAN_SOURCE = "extra_nav_scan_source"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RakshakAI Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "On-device warnings for high-risk SMS messages (content hidden on lock screen)."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showSmsScamAlert(
        context: Context,
        riskScore: Int,
        riskLevel: RiskLevel,
        categoryName: String,
        previewTextForInAppResult: String,
        sourceLabel: String = "SMS"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_SCAN_RESULT_TEXT, previewTextForInAppResult)
            putExtra(EXTRA_NAV_SCAN_SOURCE, sourceLabel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            riskScore + (System.currentTimeMillis() % 1000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Public version hides all message details on lock screen
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RakshakAI Alert")
            .setContentText("Possible scam message detected — open RakshakAI")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val privateNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${riskLevel.name} risk $sourceLabel (${riskScore}/100)")
            .setContentText("$categoryName — Tap to view full on-device breakdown")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() and 0x7FFF).toInt(), privateNotification)
    }

    fun showBatchScanRiskAlert(
        context: Context,
        riskyCount: Int,
        highestScore: Int,
        highestCategory: String
    ) {
        if (riskyCount <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_SCAN_SOURCE, "INBOX_ALERT")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9021,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RakshakAI Inbox Alert")
            .setContentText("$riskyCount risky message(s) found — open RakshakAI to review")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val privateNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$riskyCount Risky Message(s) Found (Peak ${highestScore}/100)")
            .setContentText("Top alert: $highestCategory — Tap to review safely")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(9021, privateNotification)
    }
}

