package com.example.integrations.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.KavachApplication
import com.example.integrations.sms.RawDeviceMessage
import com.example.integrations.sms.SmsInboxScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class ExtractedNotifMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
)

/**
 * NotificationListenerService that captures incoming messaging notifications
 * (WhatsApp, Telegram, Instagram, Signal, Messenger, SMS apps, etc.) locally on-device
 * after explicit user consent in the app and Android Notification Access settings.
 */
class MessageNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Backfill any active messaging notifications present when listener connects.
        runCatching { activeNotifications }
            .getOrNull()
            ?.forEach { sbn -> processStatusBarNotification(sbn, skipSettingsGateIfJustConnected = true) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processStatusBarNotification(sbn, skipSettingsGateIfJustConnected = false)
    }

    private fun processStatusBarNotification(
        sbn: StatusBarNotification,
        skipSettingsGateIfJustConnected: Boolean
    ) {
        val pkg = sbn.packageName ?: return

        // Never scan RakshakAI's own alerts or Android system banners
        if (pkg == applicationContext.packageName || pkg == "android" || pkg == "com.android.systemui") {
            return
        }

        val notif = sbn.notification ?: return
        // Ignore ongoing (calls/media/backup progress)
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val isMessagingPackage = pkg in SUPPORTED_MESSAGING_PACKAGES
        val isMessagingCategory = notif.category == Notification.CATEGORY_MESSAGE
        if (!isMessagingPackage && !isMessagingCategory) return

        val extractedList = extractMessagesFromNotification(notif, sbn.postTime)
        if (extractedList.isEmpty()) return

        val app = applicationContext as? KavachApplication ?: return
        val now = System.currentTimeMillis()
        val sourceApp = friendlyAppName(applicationContext, pkg)

        val freshMessages = extractedList.mapNotNull { msg ->
            val dedupeKey = "$pkg|${msg.sender}|${msg.body.hashCode()}|${msg.timestamp / 10_000L}".hashCode()
            val prevTs = recentNotifHashes[dedupeKey]
            if (prevTs != null && now - prevTs < 30_000L) {
                null
            } else {
                recentNotifHashes[dedupeKey] = now
                msg
            }
        }
        if (freshMessages.isEmpty()) return

        if (recentNotifHashes.size > 160) {
            recentNotifHashes.entries.removeIf { now - it.value > 180_000L }
        }

        serviceScope.launch {
            val settings = app.repository.settingsFlow.first()
            if (!settings.messageNotifScanEnabled && !skipSettingsGateIfJustConnected) return@launch
            if (skipSettingsGateIfJustConnected && !settings.messageNotifScanEnabled) {
                app.repository.prefs.setMessageNotifScanEnabled(true)
            }

            freshMessages.forEachIndexed { idx, msg ->
                val deterministicId = "notif_${pkg}_${msg.timestamp}_${msg.sender.hashCode()}_${msg.body.hashCode()}_$idx"
                val result = app.repository.processIncomingMessage(
                    raw = RawDeviceMessage(
                        id = deterministicId,
                        sender = msg.sender,
                        body = msg.body,
                        timestamp = msg.timestamp.takeIf { it > 0L } ?: now,
                        sourceChannel = RawDeviceMessage.CHANNEL_MESSENGER_NOTIF,
                        sourceApp = sourceApp
                    )
                )

                if (!skipSettingsGateIfJustConnected &&
                    result.detectorAvailable &&
                    SmsInboxScanner.isRiskyResult(result, settings.notificationThreshold)
                ) {
                    NotificationsHelper.showSmsScamAlert(
                        context = applicationContext,
                        riskScore = result.riskScore,
                        riskLevel = result.riskLevel,
                        categoryName = result.scamCategory ?: "Suspicious $sourceApp Message",
                        previewTextForInAppResult = result.sourceText,
                        sourceLabel = sourceApp
                    )
                }
            }
        }
    }

    companion object {
        private val recentNotifHashes = ConcurrentHashMap<Int, Long>()

        private val NOISE_LINE_REGEX = Regex(
            pattern = "^(\\d+\\s+(new\\s+)?messages?(\\s+from\\s+\\d+\\s+chats?)?|" +
                "checking\\s+for\\s+new\\s+messages|backup\\s+in\\s+progress|" +
                "sending\\s+message|ongoing\\s+voice\\s+call|missed\\s+(voice|video)\\s+call)$",
            option = RegexOption.IGNORE_CASE
        )

        val SUPPORTED_MESSAGING_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "org.telegram.messenger.web" to "Telegram",
            "org.telegram.plus" to "Telegram",
            "org.thunderdog.challegram" to "Telegram X",
            "com.instagram.android" to "Instagram",
            "com.instagram.lite" to "Instagram",
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger",
            "org.thoughtcrime.securesms" to "Signal",
            "com.discord" to "Discord",
            "com.snapchat.android" to "Snapchat",
            "com.viber.voip" to "Viber",
            "jp.naver.line.android" to "LINE",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Samsung Messages",
            "com.android.mms" to "SMS",
            "com.oneplus.mms" to "Messages",
            "com.coloros.mms" to "Messages",
            "com.microsoft.android.smsorganizer" to "SMS Organizer",
            "com.truecaller" to "Truecaller"
        )

        fun friendlyAppName(context: Context?, packageName: String): String {
            SUPPORTED_MESSAGING_PACKAGES[packageName]?.let { return it }
            if (context != null) {
                val appLabel = runCatching {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(info).toString().trim()
                }.getOrNull()
                if (!appLabel.isNullOrBlank()) return appLabel
            }
            return "Messaging App"
        }

        fun friendlyAppName(packageName: String): String {
            return SUPPORTED_MESSAGING_PACKAGES[packageName] ?: "Messaging App"
        }

        fun extractMessagesFromNotification(
            notification: Notification,
            postTime: Long
        ): List<ExtractedNotifMessage> {
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            return extractMessagesFromBundle(
                extras = notification.extras,
                postTime = postTime,
                isGroupSummary = isGroupSummary
            )
        }

        fun extractMessagesFromBundle(
            extras: Bundle?,
            postTime: Long,
            isGroupSummary: Boolean = false
        ): List<ExtractedNotifMessage> {
            if (extras == null) return emptyList()

            val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            val subText = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim()
            val baseSender = convTitle?.takeIf { it.isNotBlank() }
                ?: title?.takeIf { it.isNotBlank() }
                ?: subText?.takeIf { it.isNotBlank() }
                ?: "Unknown"

            val resolvedTime = postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            val collected = mutableListOf<ExtractedNotifMessage>()

            // 1. MessagingStyle EXTRA_MESSAGES (best structured source for WhatsApp/Telegram/Instagram/Messages)
            val messagesArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    ?.mapNotNull { it as? Bundle }
                    ?.toTypedArray()
            }
            if (!messagesArray.isNullOrEmpty()) {
                for (msgBundle in messagesArray) {
                    val msgText = msgBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    if (isNoiseText(msgText)) continue
                    val msgSender = msgBundle.getCharSequence("sender")?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: baseSender
                    val msgTime = msgBundle.getLong("time", 0L).takeIf { it > 0L } ?: resolvedTime
                    collected.add(
                        ExtractedNotifMessage(
                            sender = msgSender,
                            body = msgText,
                            timestamp = msgTime
                        )
                    )
                }
                if (collected.isNotEmpty()) return collected.distinctBy { "${it.sender}|${it.body}" }
            }

            // 2. InboxStyle EXTRA_TEXT_LINES (used by group summaries on WhatsApp, Instagram, Telegram)
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) {
                for (line in textLines) {
                    val rawLine = line?.toString()?.trim().orEmpty()
                    if (isNoiseText(rawLine)) continue
                    val parsed = parseInboxStyleLine(rawLine, fallbackSender = baseSender)
                    if (!isNoiseText(parsed.second)) {
                        collected.add(
                            ExtractedNotifMessage(
                                sender = parsed.first,
                                body = parsed.second,
                                timestamp = resolvedTime
                            )
                        )
                    }
                }
                if (collected.isNotEmpty()) return collected.distinctBy { "${it.sender}|${it.body}" }
            }

            // Group summaries without EXTRA_MESSAGES or EXTRA_TEXT_LINES usually only hold "3 new messages"
            if (isGroupSummary) return emptyList()

            // 3. Fallback to BIG_TEXT or TEXT
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val plainText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val body = bigText.ifBlank { plainText }
            if (isNoiseText(body)) return emptyList()

            return listOf(
                ExtractedNotifMessage(
                    sender = baseSender,
                    body = body,
                    timestamp = resolvedTime
                )
            )
        }

        /**
         * Backwards-compatible single-message extractor.
         */
        fun extractSenderAndText(notification: Notification): Pair<String, String>? {
            val last = extractMessagesFromNotification(notification, notification.`when`).lastOrNull()
                ?: return null
            return last.sender to last.body
        }

        internal fun parseInboxStyleLine(line: String, fallbackSender: String): Pair<String, String> {
            val colonIdx = line.indexOf(':')
            if (colonIdx in 1..40) {
                val candidateSender = line.substring(0, colonIdx).trim()
                val candidateBody = line.substring(colonIdx + 1).trim()
                val lower = candidateSender.lowercase()
                if (candidateSender.isNotEmpty() &&
                    candidateBody.isNotEmpty() &&
                    !lower.startsWith("http")
                ) {
                    return candidateSender to candidateBody
                }
            }
            return fallbackSender to line
        }

        private fun isNoiseText(text: String): Boolean {
            if (text.isBlank()) return true
            return NOISE_LINE_REGEX.matches(text.trim())
        }

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val cn = ComponentName(context, MessageNotificationListenerService::class.java)
            return enabledListeners.split(":").any {
                it.equals(cn.flattenToString(), ignoreCase = true) ||
                    it.equals(cn.flattenToShortString(), ignoreCase = true)
            }
        }

        fun createNotificationListenerSettingsIntent(context: Context? = null): Intent {
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, MessageNotificationListenerService::class.java).flattenToString()
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (detailIntent.resolveActivity(context.packageManager) != null) {
                    return detailIntent
                }
            }
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
