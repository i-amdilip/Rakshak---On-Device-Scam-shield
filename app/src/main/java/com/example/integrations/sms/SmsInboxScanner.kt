package com.example.integrations.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.example.data.database.CollectedMessageScanEntity
import com.example.detection.engine.RuleBasedScamEngine
import com.example.domain.models.RiskLevel
import com.example.domain.models.ScanInput
import com.example.domain.models.ScanResult
import com.example.domain.models.Source
import com.example.privacy.encryption.EncryptedPayload
import com.example.privacy.encryption.KeystoreCrypto

data class RawDeviceMessage(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val sourceChannel: String = CHANNEL_SMS_INBOX,
    val sourceApp: String = "SMS"
) {
    companion object {
        const val CHANNEL_SMS_INBOX = "SMS_INBOX"
        const val CHANNEL_SMS_SENT = "SMS_SENT"
        const val CHANNEL_SMS_LIVE = "SMS_LIVE"
        const val CHANNEL_MESSENGER_NOTIF = "MESSENGER_NOTIF"
    }
}

data class SmsReadOutcome(
    val messages: List<RawDeviceMessage> = emptyList(),
    val inboxCount: Int = 0,
    val sentCount: Int = 0,
    val permissionGranted: Boolean = true,
    val errorMessage: String? = null
)

data class SmsInboxScanReport(
    val totalRead: Int = 0,
    val newlyScanned: Int = 0,
    val skippedDuplicates: Int = 0,
    val inboxCount: Int = 0,
    val sentCount: Int = 0,
    val riskyCount: Int = 0,
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val safeCount: Int = 0,
    val highestRiskItem: CollectedMessageScanEntity? = null,
    val permissionGranted: Boolean = true,
    val errorMessage: String? = null,
    val scannedAt: Long = System.currentTimeMillis()
)

data class BatchScanOutput(
    val collectedEntities: List<CollectedMessageScanEntity>,
    val report: SmsInboxScanReport
)

object SmsInboxScanner {

    private val OTP_DIGITS_REGEX = Regex("\\b\\d{4,8}\\b")
    private val NUMERIC_SENDER_REGEX = Regex("^\\+?\\d{7,15}$")

    fun hasReadSmsPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasReceiveSmsPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Reads all accessible SMS messages (Inbox + Sent) from the device Telephony ContentProvider.
     * Requires READ_SMS runtime permission. Never sends raw SMS content off-device.
     */
    fun readDeviceSmsMessages(context: Context, limit: Int = 1500): SmsReadOutcome {
        if (!hasReadSmsPermission(context)) {
            return SmsReadOutcome(permissionGranted = false, errorMessage = "READ_SMS permission not granted")
        }

        val collected = LinkedHashMap<String, RawDeviceMessage>()
        var inboxCount = 0
        var sentCount = 0
        var queryError: String? = null

        val allProjection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val unifiedQueried = try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                allProjection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext() && collected.size < limit) {
                    val smsType = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    if (smsType == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue

                    val isSent = smsType == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        smsType == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        smsType == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
                        smsType == Telephony.Sms.MESSAGE_TYPE_FAILED

                    val smsId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty().trim().ifBlank { "Unknown" } else "Unknown"
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty().trim() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx).takeIf { it > 0L } ?: System.currentTimeMillis()
                    else System.currentTimeMillis()

                    if (body.isNotBlank()) {
                        val channel = if (isSent) RawDeviceMessage.CHANNEL_SMS_SENT else RawDeviceMessage.CHANNEL_SMS_INBOX
                        val appLabel = if (isSent) "SMS Sent" else "SMS Inbox"
                        val idPrefix = if (isSent) "sms_sent_" else "sms_inbox_"
                        val deterministicId = if (smsId.isNotBlank()) {
                            "$idPrefix$smsId"
                        } else {
                            "${idPrefix}${sender.hashCode()}_${date}_${body.hashCode()}"
                        }
                        if (!collected.containsKey(deterministicId)) {
                            collected[deterministicId] = RawDeviceMessage(
                                id = deterministicId,
                                sender = sender,
                                body = body,
                                timestamp = date,
                                sourceChannel = channel,
                                sourceApp = appLabel
                            )
                            if (isSent) sentCount++ else inboxCount++
                        }
                    }
                }
            }
            true
        } catch (se: SecurityException) {
            queryError = se.localizedMessage ?: "SMS permission denied by system"
            false
        } catch (e: Exception) {
            queryError = e.localizedMessage ?: "Unable to query unified SMS provider"
            false
        }

        // Fallback: query Inbox and Sent URIs individually if unified Telephony.Sms.CONTENT_URI failed on OEM ROM
        if (!unifiedQueried || collected.isEmpty()) {
            val perBoxLimit = (limit / 2).coerceAtLeast(1)
            val fallbackError1 = querySpecificSmsBox(
                context = context,
                uri = Telephony.Sms.Inbox.CONTENT_URI,
                isSent = false,
                limit = perBoxLimit,
                into = collected
            )
            val fallbackError2 = querySpecificSmsBox(
                context = context,
                uri = Telephony.Sms.Sent.CONTENT_URI,
                isSent = true,
                limit = perBoxLimit,
                into = collected
            )
            inboxCount = collected.values.count { it.sourceChannel == RawDeviceMessage.CHANNEL_SMS_INBOX }
            sentCount = collected.values.count { it.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT }
            if (collected.isNotEmpty()) {
                queryError = null
            } else if (queryError == null) {
                queryError = fallbackError1 ?: fallbackError2
            }
        }

        val sorted = collected.values.sortedByDescending { it.timestamp }
        return SmsReadOutcome(
            messages = sorted,
            inboxCount = inboxCount,
            sentCount = sentCount,
            permissionGranted = true,
            errorMessage = if (sorted.isEmpty()) queryError else null
        )
    }

    private fun querySpecificSmsBox(
        context: Context,
        uri: Uri,
        isSent: Boolean,
        limit: Int,
        into: LinkedHashMap<String, RawDeviceMessage>
    ): String? {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        return try {
            var countInBox = 0
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext() && countInBox < limit) {
                    val smsId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty().trim().ifBlank { "Unknown" } else "Unknown"
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty().trim() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx).takeIf { it > 0L } ?: System.currentTimeMillis()
                    else System.currentTimeMillis()

                    if (body.isNotBlank()) {
                        val idPrefix = if (isSent) "sms_sent_" else "sms_inbox_"
                        val deterministicId = if (smsId.isNotBlank()) {
                            "$idPrefix$smsId"
                        } else {
                            "${idPrefix}${sender.hashCode()}_${date}_${body.hashCode()}"
                        }
                        if (!into.containsKey(deterministicId)) {
                            into[deterministicId] = RawDeviceMessage(
                                id = deterministicId,
                                sender = sender,
                                body = body,
                                timestamp = date,
                                sourceChannel = if (isSent) RawDeviceMessage.CHANNEL_SMS_SENT else RawDeviceMessage.CHANNEL_SMS_INBOX,
                                sourceApp = if (isSent) "SMS Sent" else "SMS Inbox"
                            )
                            countInBox++
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to read ${if (isSent) "Sent" else "Inbox"} SMS"
        }
    }

    fun readDeviceSmsInbox(context: Context, limit: Int = 1000): List<RawDeviceMessage> {
        return readDeviceSmsMessages(context, limit).messages
    }

    fun isLikelyUnknownNumericSender(sender: String): Boolean {
        val cleaned = sender.replace(Regex("[\\s\\-()]"), "")
        return NUMERIC_SENDER_REGEX.matches(cleaned)
    }

    /**
     * Masks 4-8 digit OTP/PIN codes in preview snippets so sensitive codes aren't exposed in list previews.
     */
    fun maskSnippet(body: String, maxChars: Int = 110): String {
        val collapsed = body.replace(Regex("\\s+"), " ").trim()
        val masked = OTP_DIGITS_REGEX.replace(collapsed) { match ->
            "*".repeat(match.value.length)
        }
        return if (masked.length <= maxChars) masked else masked.take(maxChars - 1) + "…"
    }

    fun isRiskyResult(result: ScanResult, alertThreshold: Int): Boolean {
        return result.riskScore >= alertThreshold ||
            result.riskLevel == RiskLevel.HIGH ||
            result.riskLevel == RiskLevel.CRITICAL
    }

    /**
     * Scans a batch of messages with [RuleBasedScamEngine], deduplicating against [existingIds].
     * Pure & deterministic for unit testing when [encryptFn] is supplied.
     */
    fun scanMessageBatch(
        messages: List<RawDeviceMessage>,
        existingIds: Set<String>,
        engine: RuleBasedScamEngine,
        outputLanguage: String,
        alertThreshold: Int,
        encryptFn: (String) -> EncryptedPayload = { KeystoreCrypto.encryptString(it) },
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchScanOutput {
        val total = messages.size
        val collected = mutableListOf<CollectedMessageScanEntity>()
        var skipped = 0
        var riskyCount = 0
        var criticalCount = 0
        var highCount = 0
        var mediumCount = 0
        var safeCount = 0
        var inboxCount = 0
        var sentCount = 0
        var highest: CollectedMessageScanEntity? = null

        messages.forEachIndexed { index, msg ->
            if (msg.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT) {
                sentCount++
            } else {
                inboxCount++
            }

            if (msg.id in existingIds) {
                skipped++
            } else {
                val isSent = msg.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT
                val res = engine.analyze(
                    ScanInput(
                        text = msg.body,
                        source = Source.SMS,
                        unexpectedSenderHint = !isSent && isLikelyUnknownNumericSender(msg.sender)
                    ),
                    outputLanguage = outputLanguage
                )
                val risky = !isSent && isRiskyResult(res, alertThreshold)
                val encrypted = runCatching { encryptFn(msg.body) }
                    .getOrElse { EncryptedPayload(cipherBase64 = "", ivBase64 = "") }

                val signals = buildList {
                    addAll(res.manipulation)
                    addAll(res.intents)
                }.distinct().take(4).joinToString(", ")

                val entity = CollectedMessageScanEntity(
                    id = msg.id,
                    sender = msg.sender.take(48),
                    sourceChannel = msg.sourceChannel,
                    sourceApp = msg.sourceApp,
                    receivedAt = msg.timestamp,
                    scannedAt = System.currentTimeMillis(),
                    encryptedBody = encrypted.cipherBase64,
                    ivBase64 = encrypted.ivBase64,
                    snippetPreview = maskSnippet(msg.body),
                    riskScore = res.riskScore,
                    riskLevel = res.riskLevel.name,
                    uncertain = res.uncertain,
                    categoryName = res.scamCategory ?: "General Message",
                    shortExplanation = res.explanation.take(180),
                    matchedSignalsCsv = signals,
                    isRisky = risky,
                    alertTriggered = risky,
                    isReviewed = false
                )
                collected.add(entity)

                when (res.riskLevel) {
                    RiskLevel.CRITICAL -> criticalCount++
                    RiskLevel.HIGH -> highCount++
                    RiskLevel.MEDIUM -> mediumCount++
                    RiskLevel.LOW -> safeCount++
                }
                if (risky) riskyCount++
                if (highest == null || entity.riskScore > highest!!.riskScore) {
                    highest = entity
                }
            }
            onProgress(index + 1, total)
        }

        val report = SmsInboxScanReport(
            totalRead = total,
            inboxCount = inboxCount,
            sentCount = sentCount,
            newlyScanned = collected.size,
            skippedDuplicates = skipped,
            riskyCount = riskyCount,
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            safeCount = safeCount,
            highestRiskItem = highest,
            permissionGranted = true,
            scannedAt = System.currentTimeMillis()
        )
        return BatchScanOutput(collectedEntities = collected, report = report)
    }
}
