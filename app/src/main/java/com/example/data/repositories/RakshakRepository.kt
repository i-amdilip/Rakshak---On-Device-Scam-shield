package com.example.data.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.database.CollectedMessageScanEntity
import com.example.data.database.FeedbackEntity
import com.example.data.database.IncidentEntity
import com.example.data.database.QuarantineEntity
import com.example.data.database.RakshakDao
import com.example.data.database.ScanHistoryEntity
import com.example.data.database.TrustedAllowlistEntity
import com.example.data.database.TrustedContactEntity
import com.example.data.database.VaultItemEntity
import com.example.data.preferences.RakshakSettings
import com.example.data.preferences.UserPreferencesRepository
import com.example.detection.engine.RuleBasedScamEngine
import com.example.domain.models.CoercionAnswers
import com.example.domain.models.ConversationAnalysisResult
import com.example.domain.models.ConversationMessage
import com.example.domain.models.FeedbackOption
import com.example.domain.models.PreActionDecision
import com.example.domain.models.PreActionInput
import com.example.domain.models.RiskLevel
import com.example.domain.models.ScanInput
import com.example.domain.models.ScanResult
import com.example.domain.models.Source
import com.example.integrations.sms.RawDeviceMessage
import com.example.integrations.sms.SmsInboxScanReport
import com.example.integrations.sms.SmsInboxScanner
import com.example.privacy.data_management.PrivacyAuditHelper
import com.example.privacy.encryption.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DecryptedVaultItem(
    val entity: VaultItemEntity,
    val decryptedText: String,
    val decryptedNote: String
)

data class DecryptedQuarantineItem(
    val entity: QuarantineEntity,
    val decryptedText: String
)

data class DecryptedTrustedContact(
    val id: String,
    val displayName: String,
    val relationship: String,
    val phoneNumber: String,
    val createdAt: Long
)

class RakshakRepository(
    private val context: Context,
    private val dao: RakshakDao,
    val prefs: UserPreferencesRepository,
    val engine: RuleBasedScamEngine
) {
    val settingsFlow: Flow<RakshakSettings> = prefs.settingsFlow
    val historyFlow: Flow<List<ScanHistoryEntity>> = dao.observeHistory()
    val vaultFlow: Flow<List<VaultItemEntity>> = dao.observeVaultItems()
    val quarantineFlow: Flow<List<QuarantineEntity>> = dao.observeQuarantineItems()
    val contactsFlow: Flow<List<TrustedContactEntity>> = dao.observeTrustedContacts()
    val incidentsFlow: Flow<List<IncidentEntity>> = dao.observeIncidents()
    val allowlistFlow: Flow<List<TrustedAllowlistEntity>> = dao.observeAllowlist()
    val collectedMessagesFlow: Flow<List<CollectedMessageScanEntity>> = dao.observeCollectedMessages()

    suspend fun analyzeAndMaybeRecord(input: ScanInput): ScanResult = withContext(Dispatchers.Default) {
        val settings = prefs.settingsFlow.first()
        val result = engine.analyze(input, outputLanguage = settings.appLanguage)
        if (settings.historyEnabled && result.detectorAvailable && input.text.isNotBlank()) {
            recordHistorySummary(result)
        }
        result
    }

    suspend fun analyzeUrlAndMaybeRecord(rawUrl: String, claimedBrandId: String?): ScanResult =
        withContext(Dispatchers.Default) {
            val settings = prefs.settingsFlow.first()
            val result = engine.analyzeUrl(rawUrl, claimedBrandId, settings.appLanguage)
            if (settings.historyEnabled && result.detectorAvailable && rawUrl.isNotBlank()) {
                recordHistorySummary(result)
            }
            result
        }

    suspend fun analyzeQrAndMaybeRecord(payload: String, claimedBrandId: String?): ScanResult =
        withContext(Dispatchers.Default) {
            val settings = prefs.settingsFlow.first()
            val result = engine.analyzeQrPayload(payload, claimedBrandId, settings.appLanguage)
            if (settings.historyEnabled && result.detectorAvailable && payload.isNotBlank()) {
                recordHistorySummary(result)
            }
            result
        }

    suspend fun analyzeConversationAndMaybeRecord(
        messages: List<ConversationMessage>,
        coercionAnswers: CoercionAnswers
    ): ConversationAnalysisResult = withContext(Dispatchers.Default) {
        val settings = prefs.settingsFlow.first()
        val convResult = engine.analyzeConversation(messages, coercionAnswers, settings.appLanguage)
        if (settings.historyEnabled && convResult.combinedResult.detectorAvailable && messages.isNotEmpty()) {
            recordHistorySummary(convResult.combinedResult)
        }
        convResult
    }

    suspend fun evaluatePreAction(input: PreActionInput): PreActionDecision = withContext(Dispatchers.Default) {
        val settings = prefs.settingsFlow.first()
        engine.evaluatePreAction(input, isHindi = settings.appLanguage.startsWith("hi"))
    }

    private suspend fun recordHistorySummary(result: ScanResult) {
        val entity = ScanHistoryEntity(
            scanId = result.scanId,
            timestamp = result.timestamp,
            source = result.source.name,
            riskScore = result.riskScore,
            riskLevel = result.riskLevel.name,
            uncertain = result.uncertain,
            categoryName = result.scamCategory ?: "General Check",
            shortExplanation = result.explanation.take(180)
        )
        dao.insertHistory(entity)
    }

    suspend fun scanDeviceSmsInbox(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): SmsInboxScanReport = withContext(Dispatchers.Default) {
        if (!SmsInboxScanner.hasReadSmsPermission(context)) {
            return@withContext SmsInboxScanReport(permissionGranted = false)
        }
        val settings = prefs.settingsFlow.first()
        val readOutcome = SmsInboxScanner.readDeviceSmsMessages(context)
        if (readOutcome.errorMessage != null && readOutcome.messages.isEmpty()) {
            return@withContext SmsInboxScanReport(
                permissionGranted = true,
                errorMessage = readOutcome.errorMessage,
                scannedAt = System.currentTimeMillis()
            )
        }
        val existingIds = dao.getCollectedMessageIds().toSet()
        val batch = SmsInboxScanner.scanMessageBatch(
            messages = readOutcome.messages,
            existingIds = existingIds,
            engine = engine,
            outputLanguage = settings.appLanguage,
            alertThreshold = settings.notificationThreshold,
            onProgress = onProgress
        )
        if (batch.collectedEntities.isNotEmpty()) {
            dao.insertCollectedMessages(batch.collectedEntities)
            if (settings.historyEnabled) {
                batch.collectedEntities
                    .filter { it.isRisky }
                    .take(20)
                    .forEach { risky ->
                        dao.insertHistory(
                            ScanHistoryEntity(
                                scanId = risky.id,
                                timestamp = risky.scannedAt,
                                source = risky.sourceApp,
                                riskScore = risky.riskScore,
                                riskLevel = risky.riskLevel,
                                uncertain = risky.uncertain,
                                categoryName = risky.categoryName,
                                shortExplanation = risky.shortExplanation.take(180)
                            )
                        )
                    }
            }
        }
        val finalReport = batch.report.copy(
            inboxCount = readOutcome.inboxCount,
            sentCount = readOutcome.sentCount,
            errorMessage = readOutcome.errorMessage
        )
        prefs.setLastInboxScanTimestamp(finalReport.scannedAt)
        finalReport
    }

    suspend fun processIncomingMessage(raw: RawDeviceMessage): ScanResult = withContext(Dispatchers.Default) {
        val settings = prefs.settingsFlow.first()
        val isSent = raw.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT
        val res = engine.analyze(
            ScanInput(
                text = raw.body,
                source = Source.SMS,
                unexpectedSenderHint = !isSent && SmsInboxScanner.isLikelyUnknownNumericSender(raw.sender)
            ),
            outputLanguage = settings.appLanguage
        )
        val risky = !isSent && SmsInboxScanner.isRiskyResult(res, settings.notificationThreshold)
        val encrypted = runCatching { KeystoreCrypto.encryptString(raw.body) }
            .getOrNull()
        val signals = buildList {
            addAll(res.manipulation)
            addAll(res.intents)
        }.distinct().take(4).joinToString(", ")

        val entity = CollectedMessageScanEntity(
            id = raw.id,
            sender = raw.sender.take(48),
            sourceChannel = raw.sourceChannel,
            sourceApp = raw.sourceApp,
            receivedAt = raw.timestamp,
            scannedAt = System.currentTimeMillis(),
            encryptedBody = encrypted?.cipherBase64.orEmpty(),
            ivBase64 = encrypted?.ivBase64.orEmpty(),
            snippetPreview = SmsInboxScanner.maskSnippet(raw.body),
            riskScore = res.riskScore,
            riskLevel = res.riskLevel.name,
            uncertain = res.uncertain,
            categoryName = res.scamCategory ?: "General Message",
            shortExplanation = res.explanation.take(180),
            matchedSignalsCsv = signals,
            isRisky = risky,
            alertTriggered = res.detectorAvailable && risky,
            isReviewed = false
        )
        dao.insertCollectedMessage(entity)

        if (settings.historyEnabled && res.detectorAvailable && (risky || res.riskLevel != RiskLevel.LOW)) {
            recordHistorySummary(res)
        }
        res
    }

    fun decryptCollectedMessageBody(entity: CollectedMessageScanEntity): String {
        if (entity.encryptedBody.isBlank() || entity.ivBase64.isBlank()) {
            return entity.snippetPreview
        }
        return runCatching {
            KeystoreCrypto.decryptString(entity.encryptedBody, entity.ivBase64)
        }.getOrElse { entity.snippetPreview }
    }

    suspend fun markCollectedMessageReviewed(id: String, reviewed: Boolean) = withContext(Dispatchers.IO) {
        dao.markCollectedMessageReviewed(id, reviewed)
    }

    suspend fun deleteCollectedMessage(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCollectedMessage(id)
    }

    suspend fun clearCollectedMessages() = withContext(Dispatchers.IO) {
        dao.clearCollectedMessages()
    }

    suspend fun saveFeedback(scanId: String, option: FeedbackOption, allowlistEntry: String? = null) {
        withContext(Dispatchers.IO) {
            dao.insertFeedback(
                FeedbackEntity(
                    id = UUID.randomUUID().toString(),
                    scanId = scanId,
                    option = option.name,
                    createdAt = System.currentTimeMillis()
                )
            )
            dao.updateHistoryFeedback(scanId, option.name)
            if (!allowlistEntry.isNullOrBlank()) {
                dao.insertAllowlist(
                    TrustedAllowlistEntity(
                        domainOrSender = allowlistEntry.trim().lowercase(),
                        note = "Marked trusted by user",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun deleteHistoryItem(scanId: String) = withContext(Dispatchers.IO) {
        dao.deleteHistoryItem(scanId)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }

    suspend fun saveToVault(
        result: ScanResult,
        itemType: String,
        note: String,
        incidentId: String? = null,
        incidentTitle: String? = null,
        screenshotUri: Uri? = null
    ): String? = withContext(Dispatchers.IO) {
        val textBlob = KeystoreCrypto.encryptString(result.sourceText)
        val noteBlob = if (note.isNotBlank()) KeystoreCrypto.encryptString(note) else null

        var mediaFileName: String? = null
        var mediaIvBase64: String? = null
        if (screenshotUri != null) {
            runCatching {
                context.contentResolver.openInputStream(screenshotUri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        bmp.recycle()
                        val (cipherBytes, iv) = KeystoreCrypto.encryptBytes(baos.toByteArray())
                        val fname = "ev_${UUID.randomUUID()}.enc"
                        val outFile = File(PrivacyAuditHelper.vaultMediaDir(context), fname)
                        outFile.writeBytes(cipherBytes)
                        mediaFileName = fname
                        mediaIvBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
                    }
                }
            }
        }

        val resolvedIncidentId = incidentId ?: incidentTitle?.takeIf { it.isNotBlank() }?.let { title ->
            val newId = UUID.randomUUID().toString()
            dao.upsertIncident(
                IncidentEntity(
                    id = newId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    highestRiskScore = result.riskScore
                )
            )
            newId
        }

        if (incidentId != null) {
            val existing = dao.observeIncidents().first().firstOrNull { it.id == incidentId }
            if (existing != null) {
                dao.upsertIncident(
                    existing.copy(
                        updatedAt = System.currentTimeMillis(),
                        highestRiskScore = maxOf(existing.highestRiskScore, result.riskScore)
                    )
                )
            }
        }

        dao.insertVaultItem(
            VaultItemEntity(
                id = UUID.randomUUID().toString(),
                itemType = itemType,
                encryptedBlob = textBlob.cipherBase64,
                ivBase64 = textBlob.ivBase64,
                mediaFileName = mediaFileName,
                mediaIvBase64 = mediaIvBase64,
                createdAt = System.currentTimeMillis(),
                riskScore = result.riskScore,
                riskLevel = result.riskLevel.name,
                categoryName = result.scamCategory ?: "Suspicious Item",
                stagesCsv = result.stagesReached.joinToString(","),
                encryptedNote = noteBlob?.cipherBase64.orEmpty(),
                noteIvBase64 = noteBlob?.ivBase64.orEmpty(),
                incidentId = resolvedIncidentId
            )
        )
        resolvedIncidentId
    }

    fun decryptVaultItem(entity: VaultItemEntity): DecryptedVaultItem {
        val text = runCatching {
            KeystoreCrypto.decryptString(entity.encryptedBlob, entity.ivBase64)
        }.getOrElse { "[Unable to decrypt evidence item]" }
        val note = if (entity.encryptedNote.isNotBlank() && entity.noteIvBase64.isNotBlank()) {
            runCatching { KeystoreCrypto.decryptString(entity.encryptedNote, entity.noteIvBase64) }
                .getOrDefault("")
        } else ""
        return DecryptedVaultItem(entity, text, note)
    }

    suspend fun loadDecryptedScreenshot(entity: VaultItemEntity): Bitmap? = withContext(Dispatchers.IO) {
        val fname = entity.mediaFileName ?: return@withContext null
        val ivStr = entity.mediaIvBase64 ?: return@withContext null
        runCatching {
            val file = File(PrivacyAuditHelper.vaultMediaDir(context), fname)
            if (!file.exists()) return@runCatching null
            val iv = android.util.Base64.decode(ivStr, android.util.Base64.NO_WRAP)
            val plainBytes = KeystoreCrypto.decryptBytes(file.readBytes(), iv)
            BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
        }.getOrNull()
    }

    suspend fun deleteVaultItem(entity: VaultItemEntity) = withContext(Dispatchers.IO) {
        entity.mediaFileName?.let { fname ->
            File(PrivacyAuditHelper.vaultMediaDir(context), fname).delete()
        }
        dao.deleteVaultItem(entity.id)
    }

    suspend fun createVaultExportZip(): File = withContext(Dispatchers.IO) {
        val items = dao.getAllVaultItemsOnce()
        val exportDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        val zipFile = File(exportDir, "rakshakai_evidence_${System.currentTimeMillis()}.zip")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            val summary = buildString {
                appendLine("RakshakAI — Decrypted Evidence Summary")
                appendLine("Generated: ${dateFmt.format(Date())}")
                appendLine("Note: Risk scores are not proof of fraud. Verify all helpline numbers before acting.")
                appendLine("====================================================")
                items.forEachIndexed { idx, item ->
                    val dec = decryptVaultItem(item)
                    appendLine("\n#${idx + 1} [${item.itemType}] ${dateFmt.format(Date(item.createdAt))}")
                    appendLine("Category: ${item.categoryName} | Risk: ${item.riskScore}/100 (${item.riskLevel})")
                    if (item.stagesCsv.isNotBlank()) appendLine("Stages: ${item.stagesCsv}")
                    if (dec.decryptedNote.isNotBlank()) appendLine("Note: ${dec.decryptedNote}")
                    appendLine("Content:\n${dec.decryptedText}")
                    appendLine("----------------------------------------------------")
                }
            }
            zos.putNextEntry(ZipEntry("evidence_summary.txt"))
            zos.write(summary.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Include decrypted screenshots if present
            items.forEachIndexed { idx, item ->
                val fname = item.mediaFileName
                val ivStr = item.mediaIvBase64
                if (fname != null && ivStr != null) {
                    runCatching {
                        val encFile = File(PrivacyAuditHelper.vaultMediaDir(context), fname)
                        if (encFile.exists()) {
                            val iv = android.util.Base64.decode(ivStr, android.util.Base64.NO_WRAP)
                            val plain = KeystoreCrypto.decryptBytes(encFile.readBytes(), iv)
                            zos.putNextEntry(ZipEntry("screenshot_${idx + 1}.jpg"))
                            zos.write(plain)
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        zipFile
    }

    suspend fun moveToQuarantine(result: ScanResult) = withContext(Dispatchers.IO) {
        val enc = KeystoreCrypto.encryptString(result.sourceText)
        dao.insertQuarantine(
            QuarantineEntity(
                id = UUID.randomUUID().toString(),
                encryptedBlob = enc.cipherBase64,
                ivBase64 = enc.ivBase64,
                createdAt = System.currentTimeMillis(),
                riskScore = result.riskScore,
                riskLevel = result.riskLevel.name,
                categoryName = result.scamCategory ?: "Suspicious Message",
                previewSummary = result.explanation.take(120)
            )
        )
    }

    fun decryptQuarantineItem(entity: QuarantineEntity): DecryptedQuarantineItem {
        val text = runCatching { KeystoreCrypto.decryptString(entity.encryptedBlob, entity.ivBase64) }
            .getOrElse { "[Unable to decrypt quarantined content]" }
        return DecryptedQuarantineItem(entity, text)
    }

    suspend fun deleteQuarantineItem(id: String) = withContext(Dispatchers.IO) {
        dao.deleteQuarantineItem(id)
    }

    suspend fun addTrustedContact(name: String, relationship: String, phone: String): String = withContext(Dispatchers.IO) {
        val enc = KeystoreCrypto.encryptString(phone.trim())
        val contactId = UUID.randomUUID().toString()
        dao.insertTrustedContact(
            TrustedContactEntity(
                id = contactId,
                displayName = name.trim(),
                relationship = relationship.trim().ifBlank { "Family" },
                encryptedPhone = enc.cipherBase64,
                ivBase64 = enc.ivBase64,
                createdAt = System.currentTimeMillis()
            )
        )
        contactId
    }

    fun decryptContact(entity: TrustedContactEntity): DecryptedTrustedContact {
        val phone = runCatching { KeystoreCrypto.decryptString(entity.encryptedPhone, entity.ivBase64) }
            .getOrDefault("")
        return DecryptedTrustedContact(
            id = entity.id,
            displayName = entity.displayName,
            relationship = entity.relationship,
            phoneNumber = phone,
            createdAt = entity.createdAt
        )
    }

    suspend fun deleteTrustedContact(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTrustedContact(id)
    }

    suspend fun removeAllowlistEntry(key: String) = withContext(Dispatchers.IO) {
        dao.deleteAllowlist(key)
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        dao.clearHistory()
        dao.clearVault()
        dao.clearQuarantine()
        dao.clearTrustedContacts()
        dao.clearIncidents()
        dao.clearFeedback()
        dao.clearAllowlist()
        dao.clearCollectedMessages()
        PrivacyAuditHelper.deleteAllPrivateFiles(context)
        prefs.clearAllPreferences()
        KeystoreCrypto.wipeKey()
    }
}
