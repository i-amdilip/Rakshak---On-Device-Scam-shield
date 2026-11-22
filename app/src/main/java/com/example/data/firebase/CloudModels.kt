package com.example.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object CloudSchemaBounds {
    val VALID_LANGUAGES = setOf("en", "hi")
    val VALID_SOURCES = setOf(
        "MANUAL", "SHARED", "SMS", "URL", "QR",
        "SCREENSHOT", "CONVERSATION", "TRANSCRIPT", "SMS_AUTO"
    )
    val VALID_RISK_LEVELS = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
    val VALID_FEEDBACK_OPTIONS = setOf(
        "CORRECT_SCAM", "FALSE_ALARM", "MISSED_SOMETHING", "NOT_SURE"
    )
    val VALID_INCIDENT_STATUSES = setOf("OPEN", "RESOLVED")
    val VALID_ENTRY_TYPES = setOf(
        "VAULT", "QUARANTINE", "PRE_ACTION", "COLLECTED_MESSAGE", "ALLOWLIST", "EMERGENCY_NOTE"
    )
    val GUARDIAN_ROLES = listOf(
        "Self Protection",
        "Family Guardian",
        "Senior Citizen",
        "Parent / Caregiver"
    )
    val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    const val MIN_THRESHOLD = 40
    const val MAX_THRESHOLD = 90
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val city: String? = null,
    val bio: String? = null,
    val emergencyNotes: String? = null,
    val guardianRole: String = "Self Protection",
    val photoUrl: String? = null,
    val bookmarkedTips: List<String> = emptyList(),
    val trustedDomains: List<String> = emptyList(),
    val appLanguage: String = "en",
    val elderlyMode: Boolean = false,
    val notificationThreshold: Long = 60L,
    val totalScansSynced: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudScanSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val source: String = "MANUAL",
    val riskScore: Long = 0L,
    val riskLevel: String = "LOW",
    val uncertain: Boolean = false,
    val categoryName: String = "General Check",
    val shortExplanation: String = "",
    val feedbackOption: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudUserEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entryType: String = "VAULT",
    val title: String = "",
    val category: String = "General",
    val sourceLabel: String = "APP",
    val riskScore: Long = 0L,
    val riskLevel: String = "LOW",
    val summary: String = "",
    val reviewed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudTrustedContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val relationship: String = "Family",
    val phoneNumber: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudIncident(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val highestRiskScore: Long = 0L,
    val status: String = "OPEN",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
