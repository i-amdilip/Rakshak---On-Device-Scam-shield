package com.example.integrations.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.KavachApplication
import com.example.integrations.notifications.NotificationsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SmsScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val app = context.applicationContext as? KavachApplication
        if (app == null) {
            pendingResult.finish()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString("") { it.messageBody.orEmpty() }.trim()
        if (body.isBlank()) {
            pendingResult.finish()
            return
        }
        val sender = messages.firstOrNull()?.displayOriginatingAddress?.takeIf { it.isNotBlank() } ?: "Unknown"
        val receivedAt = messages.firstOrNull()?.timestampMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = app.repository.settingsFlow.first()
                if (!settings.smsAutoScanEnabled) return@launch

                // Dedupe identical SMS bodies within 60 seconds
                val keyHash = "$sender|${body.hashCode()}".hashCode()
                val now = System.currentTimeMillis()
                val prevTs = recentSmsHashes[keyHash]
                if (prevTs != null && now - prevTs < 60_000L) {
                    return@launch
                }
                recentSmsHashes[keyHash] = now
                if (recentSmsHashes.size > 50) {
                    recentSmsHashes.entries.removeIf { now - it.value > 120_000L }
                }

                val deterministicId = "sms_live_${sender.hashCode()}_${receivedAt}_${body.hashCode()}"
                val result = app.repository.processIncomingMessage(
                    RawDeviceMessage(
                        id = deterministicId,
                        sender = sender,
                        body = body,
                        timestamp = receivedAt,
                        sourceChannel = RawDeviceMessage.CHANNEL_SMS_LIVE,
                        sourceApp = "SMS"
                    )
                )
                if (result.detectorAvailable && SmsInboxScanner.isRiskyResult(result, settings.notificationThreshold)) {
                    NotificationsHelper.showSmsScamAlert(
                        context = context,
                        riskScore = result.riskScore,
                        riskLevel = result.riskLevel,
                        categoryName = result.scamCategory ?: "Suspicious Message",
                        previewTextForInAppResult = result.sourceText,
                        sourceLabel = "SMS"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val recentSmsHashes = ConcurrentHashMap<Int, Long>()
    }
}
