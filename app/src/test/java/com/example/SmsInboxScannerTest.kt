package com.example

import com.example.detection.DemoSamples
import com.example.detection.engine.RuleBasedScamEngine
import com.example.domain.models.RiskLevel
import com.example.domain.models.ScanInput
import com.example.integrations.sms.RawDeviceMessage
import com.example.integrations.sms.SmsInboxScanner
import com.example.privacy.encryption.EncryptedPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsInboxScannerTest {

    private val engine = RuleBasedScamEngine(FileSystemRuleSource())

    private fun fakeEncrypt(text: String): EncryptedPayload {
        return EncryptedPayload(
            cipherBase64 = "enc:${text.hashCode()}",
            ivBase64 = "iv_stub"
        )
    }

    @Test
    fun `batch scan separates risky scam SMS from legitimate SMS and computes accurate report`() {
        val messages = listOf(
            RawDeviceMessage(
                id = "sms_1",
                sender = "+919876543210",
                body = DemoSamples.messageSamples[0].payload, // Fake SBI KYC lookalike (Critical)
                timestamp = 1_700_000_005_000L
            ),
            RawDeviceMessage(
                id = "sms_2",
                sender = "+919123456789",
                body = DemoSamples.messageSamples[8].payload, // AnyDesk 9-digit remote access (Critical)
                timestamp = 1_700_000_004_000L
            ),
            RawDeviceMessage(
                id = "sms_3",
                sender = "+919988776655",
                body = DemoSamples.messageSamples[7].payload, // Electricity cut threat (High)
                timestamp = 1_700_000_003_000L
            ),
            RawDeviceMessage(
                id = "sms_4",
                sender = "HDFCBK",
                body = DemoSamples.messageSamples[10].payload, // Legitimate HDFC OTP warning (Low)
                timestamp = 1_700_000_002_000L
            ),
            RawDeviceMessage(
                id = "sms_5",
                sender = "AMAZON",
                body = DemoSamples.messageSamples[11].payload, // Legitimate Amazon shipped (Low)
                timestamp = 1_700_000_001_000L
            )
        )

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val output = SmsInboxScanner.scanMessageBatch(
            messages = messages,
            existingIds = emptySet(),
            engine = engine,
            outputLanguage = "en",
            alertThreshold = 60,
            encryptFn = ::fakeEncrypt,
            onProgress = { scanned, total -> progressUpdates.add(scanned to total) }
        )

        assertEquals(5, output.report.totalRead)
        assertEquals(5, output.report.newlyScanned)
        assertEquals(0, output.report.skippedDuplicates)
        assertEquals(3, output.report.riskyCount)
        assertEquals(2, output.report.safeCount)
        assertTrue(output.report.criticalCount >= 2)
        assertEquals(5, progressUpdates.size)
        assertEquals(5 to 5, progressUpdates.last())

        val byId = output.collectedEntities.associateBy { it.id }
        assertTrue(byId.getValue("sms_1").isRisky)
        assertTrue(byId.getValue("sms_1").alertTriggered)
        assertEquals(RiskLevel.CRITICAL.name, byId.getValue("sms_1").riskLevel)

        assertTrue(byId.getValue("sms_2").isRisky)
        assertTrue(byId.getValue("sms_3").isRisky)

        assertFalse(byId.getValue("sms_4").isRisky)
        assertFalse(byId.getValue("sms_4").alertTriggered)
        assertEquals(RiskLevel.LOW.name, byId.getValue("sms_4").riskLevel)
        // OTP 123456 in sample 11 must be masked in preview snippet
        assertFalse(byId.getValue("sms_4").snippetPreview.contains("123456"))
        assertTrue(byId.getValue("sms_4").snippetPreview.contains("******"))

        assertFalse(byId.getValue("sms_5").isRisky)
        assertNotNull(output.report.highestRiskItem)
        assertTrue(output.report.highestRiskItem!!.riskScore >= 80)
    }

    @Test
    fun `batch scan deduplicates already scanned message IDs`() {
        val messages = listOf(
            RawDeviceMessage(
                id = "sms_already_scanned",
                sender = "+919876543210",
                body = DemoSamples.messageSamples[0].payload,
                timestamp = 1_700_000_002_000L
            ),
            RawDeviceMessage(
                id = "sms_new_legit",
                sender = "IRCTC",
                body = "Your IRCTC ticket PNR 4821940212 is confirmed for 24 Nov. Have a safe journey!",
                timestamp = 1_700_000_001_000L
            )
        )

        val output = SmsInboxScanner.scanMessageBatch(
            messages = messages,
            existingIds = setOf("sms_already_scanned"),
            engine = engine,
            outputLanguage = "en",
            alertThreshold = 60,
            encryptFn = ::fakeEncrypt
        )

        assertEquals(2, output.report.totalRead)
        assertEquals(1, output.report.newlyScanned)
        assertEquals(1, output.report.skippedDuplicates)
        assertEquals(0, output.report.riskyCount)
        assertEquals(1, output.collectedEntities.size)
        assertEquals("sms_new_legit", output.collectedEntities.first().id)
    }

    @Test
    fun `batch scan handles empty inbox cleanly`() {
        val output = SmsInboxScanner.scanMessageBatch(
            messages = emptyList(),
            existingIds = emptySet(),
            engine = engine,
            outputLanguage = "en",
            alertThreshold = 60,
            encryptFn = ::fakeEncrypt
        )

        assertEquals(0, output.report.totalRead)
        assertEquals(0, output.report.newlyScanned)
        assertEquals(0, output.report.riskyCount)
        assertNull(output.report.highestRiskItem)
        assertTrue(output.collectedEntities.isEmpty())
    }

    @Test
    fun `maskSnippet masks 4 to 8 digit OTPs and truncates long bodies`() {
        val masked = SmsInboxScanner.maskSnippet("Use OTP 849201 or PIN 4321 for login", maxChars = 110)
        assertFalse(masked.contains("849201"))
        assertFalse(masked.contains("4321"))
        assertTrue(masked.contains("******"))
        assertTrue(masked.contains("****"))

        val longText = "Important update: " + "a".repeat(200)
        val truncated = SmsInboxScanner.maskSnippet(longText, maxChars = 50)
        assertEquals(50, truncated.length)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `isLikelyUnknownNumericSender identifies numeric phone numbers vs alphanumeric sender IDs`() {
        assertTrue(SmsInboxScanner.isLikelyUnknownNumericSender("+91 98765-43210"))
        assertTrue(SmsInboxScanner.isLikelyUnknownNumericSender("9876543210"))
        assertFalse(SmsInboxScanner.isLikelyUnknownNumericSender("AD-HDFCBK"))
        assertFalse(SmsInboxScanner.isLikelyUnknownNumericSender("AMAZON"))
    }

    @Test
    fun `isRiskyResult honors alertThreshold and high or critical risk levels`() {
        val lowRes = engine.analyze(ScanInput(DemoSamples.messageSamples[10].payload))
        assertFalse(SmsInboxScanner.isRiskyResult(lowRes, alertThreshold = 60))

        val highRes = engine.analyze(ScanInput(DemoSamples.messageSamples[0].payload))
        assertTrue(SmsInboxScanner.isRiskyResult(highRes, alertThreshold = 90))
    }

    @Test
    fun `batch scan separates Inbox and Sent SMS counts accurately`() {
        val messages = listOf(
            RawDeviceMessage(
                id = "inbox_1",
                sender = "+919876543210",
                body = "Hey, reaching home in 15 mins.",
                timestamp = 1_700_000_002_000L,
                sourceChannel = RawDeviceMessage.CHANNEL_SMS_INBOX,
                sourceApp = "SMS Inbox"
            ),
            RawDeviceMessage(
                id = "sent_1",
                sender = "+919876543210",
                body = "Please share the KYC verification details later.",
                timestamp = 1_700_000_003_000L,
                sourceChannel = RawDeviceMessage.CHANNEL_SMS_SENT,
                sourceApp = "SMS Sent"
            )
        )

        val output = SmsInboxScanner.scanMessageBatch(
            messages = messages,
            existingIds = emptySet(),
            engine = engine,
            outputLanguage = "en",
            alertThreshold = 60,
            encryptFn = ::fakeEncrypt
        )

        assertEquals(2, output.report.totalRead)
        assertEquals(1, output.report.inboxCount)
        assertEquals(1, output.report.sentCount)
        assertEquals(2, output.report.newlyScanned)

        val sentEntity = output.collectedEntities.first { it.id == "sent_1" }
        assertEquals(RawDeviceMessage.CHANNEL_SMS_SENT, sentEntity.sourceChannel)
        assertEquals("SMS Sent", sentEntity.sourceApp)
        assertFalse(sentEntity.isRisky)
    }

    @Test
    fun `notification listener maps messaging apps and parses inbox style lines`() {
        assertEquals("WhatsApp", com.example.integrations.notifications.MessageNotificationListenerService.friendlyAppName("com.whatsapp"))
        assertEquals("Telegram", com.example.integrations.notifications.MessageNotificationListenerService.friendlyAppName("org.telegram.messenger"))
        assertEquals("Instagram", com.example.integrations.notifications.MessageNotificationListenerService.friendlyAppName("com.instagram.android"))

        val parsed = com.example.integrations.notifications.MessageNotificationListenerService.parseInboxStyleLine(
            line = "Aarav: Verify this link https://example.com",
            fallbackSender = "Group Chat"
        )
        assertEquals("Aarav", parsed.first)
        assertEquals("Verify this link https://example.com", parsed.second)

        val fallback = com.example.integrations.notifications.MessageNotificationListenerService.parseInboxStyleLine(
            line = "https://example.com check this",
            fallbackSender = "Instagram DM"
        )
        assertEquals("Instagram DM", fallback.first)
        assertEquals("https://example.com check this", fallback.second)
    }
}
