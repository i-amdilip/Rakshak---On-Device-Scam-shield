package com.example.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.database.CollectedMessageScanEntity
import com.example.data.database.IncidentEntity
import com.example.data.database.QuarantineEntity
import com.example.data.database.ScanHistoryEntity
import com.example.data.database.TrustedAllowlistEntity
import com.example.data.database.VaultItemEntity
import com.example.data.firebase.CloudIncident
import com.example.data.firebase.CloudScanSummary
import com.example.data.firebase.CloudSyncRepository
import com.example.data.firebase.CloudTrustedContact
import com.example.data.firebase.CloudUiState
import com.example.data.firebase.CloudUserEntry
import com.example.data.firebase.UserProfile
import com.example.data.repositories.DecryptedTrustedContact
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloudSyncViewModel(
    private val cloudRepository: CloudSyncRepository,
    val currentUserId: String
) : ViewModel() {

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val profileState: StateFlow<CloudUiState<UserProfile?>> =
        cloudRepository.observeUserProfile(currentUserId)
            .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapToUiError("profile", error)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    val scansState: StateFlow<CloudUiState<List<CloudScanSummary>>> =
        cloudRepository.observeScanSummaries(currentUserId)
            .map<List<CloudScanSummary>, CloudUiState<List<CloudScanSummary>>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapToUiError("scan summaries", error)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    val userEntriesState: StateFlow<CloudUiState<List<CloudUserEntry>>> =
        cloudRepository.observeUserEntries(currentUserId)
            .map<List<CloudUserEntry>, CloudUiState<List<CloudUserEntry>>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapToUiError("user entries", error)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    val contactsState: StateFlow<CloudUiState<List<CloudTrustedContact>>> =
        cloudRepository.observeTrustedContacts(currentUserId)
            .map<List<CloudTrustedContact>, CloudUiState<List<CloudTrustedContact>>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapToUiError("trusted contacts", error)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    val incidentsState: StateFlow<CloudUiState<List<CloudIncident>>> =
        cloudRepository.observeIncidents(currentUserId)
            .map<List<CloudIncident>, CloudUiState<List<CloudIncident>>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapToUiError("incidents", error)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    private fun unwrapToUiError(label: String, error: Throwable): CloudUiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        return CloudUiState.Error(rootError.message ?: "Failed to observe $label")
    }

    fun ensureProfileInitialized(
        displayName: String,
        email: String?,
        appLanguage: String,
        elderlyMode: Boolean,
        notificationThreshold: Int,
        photoUrl: String? = null
    ) {
        viewModelScope.launch {
            cloudRepository.ensureUserProfile(
                displayName = displayName,
                email = email,
                appLanguage = appLanguage,
                elderlyMode = elderlyMode,
                notificationThreshold = notificationThreshold,
                photoUrl = photoUrl
            ).onFailure { err ->
                _syncMessage.value = err.localizedMessage ?: "Cloud profile initialization failed."
            }
        }
    }

    fun updatePersonalProfile(
        displayName: String,
        phoneNumber: String,
        city: String,
        bio: String,
        emergencyNotes: String,
        guardianRole: String
    ) {
        if (displayName.isBlank()) {
            _syncMessage.value = "Display name cannot be empty."
            return
        }
        viewModelScope.launch {
            cloudRepository.updateUserPersonalProfile(
                displayName = displayName,
                phoneNumber = phoneNumber,
                city = city,
                bio = bio,
                emergencyNotes = emergencyNotes,
                guardianRole = guardianRole
            ).onSuccess {
                _syncMessage.value = "Profile saved to real-time database."
            }.onFailure { err ->
                _syncMessage.value = err.localizedMessage ?: "Failed to save profile."
            }
        }
    }

    fun syncProfileLists(
        bookmarkedTips: List<String>? = null,
        trustedDomains: List<String>? = null
    ) {
        viewModelScope.launch {
            cloudRepository.syncProfileLists(bookmarkedTips, trustedDomains)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Profile list sync failed."
                }
        }
    }

    fun syncPreferences(appLanguage: String, elderlyMode: Boolean, notificationThreshold: Int) {
        viewModelScope.launch {
            cloudRepository.syncPreferences(appLanguage, elderlyMode, notificationThreshold)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Preference sync failed."
                }
        }
    }

    fun syncScanSummary(
        scanId: String,
        source: String,
        riskScore: Int,
        riskLevel: String,
        uncertain: Boolean,
        categoryName: String,
        shortExplanation: String,
        feedbackOption: String? = null
    ) {
        viewModelScope.launch {
            cloudRepository.syncScanSummary(
                scanId = scanId,
                source = source,
                riskScore = riskScore,
                riskLevel = riskLevel,
                uncertain = uncertain,
                categoryName = categoryName,
                shortExplanation = shortExplanation,
                feedbackOption = feedbackOption
            ).onFailure { err ->
                _syncMessage.value = err.localizedMessage ?: "Scan summary sync failed."
            }
        }
    }

    fun syncFeedback(scanId: String, feedbackOption: String) {
        viewModelScope.launch {
            cloudRepository.updateScanFeedback(scanId, feedbackOption)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Feedback sync failed."
                }
        }
    }

    fun deleteCloudScanSummary(scanId: String) {
        viewModelScope.launch {
            cloudRepository.deleteScanSummary(scanId)
                .onSuccess { _syncMessage.value = "Removed cloud scan summary." }
                .onFailure { err -> _syncMessage.value = err.localizedMessage ?: "Failed to delete cloud summary." }
        }
    }

    fun clearCloudScanSummaries() {
        viewModelScope.launch {
            cloudRepository.deleteAllScanSummaries()
                .onFailure { err -> _syncMessage.value = err.localizedMessage ?: "Failed to clear cloud history." }
        }
    }

    fun syncUserEntry(
        entryId: String,
        entryType: String,
        title: String,
        category: String,
        sourceLabel: String,
        riskScore: Int,
        riskLevel: String,
        summary: String,
        reviewed: Boolean = false
    ) {
        viewModelScope.launch {
            cloudRepository.upsertUserEntry(
                entryId = entryId,
                entryType = entryType,
                title = title,
                category = category,
                sourceLabel = sourceLabel,
                riskScore = riskScore,
                riskLevel = riskLevel,
                summary = summary,
                reviewed = reviewed
            ).onFailure { err ->
                _syncMessage.value = err.localizedMessage ?: "Entry sync failed."
            }
        }
    }

    fun syncCollectedMessageReviewed(messageId: String, reviewed: Boolean) {
        viewModelScope.launch {
            cloudRepository.markUserEntryReviewed("msg_$messageId", reviewed)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Failed to update message review state."
                }
        }
    }

    fun deleteCloudUserEntry(entryId: String) {
        viewModelScope.launch {
            cloudRepository.deleteUserEntry(entryId)
                .onFailure { err -> _syncMessage.value = err.localizedMessage ?: "Failed to remove cloud entry." }
        }
    }

    fun clearCloudUserEntriesByType(entryType: String) {
        viewModelScope.launch {
            cloudRepository.deleteUserEntriesByType(entryType)
                .onFailure { err -> _syncMessage.value = err.localizedMessage ?: "Failed to clear cloud entries." }
        }
    }

    fun syncTrustedContact(contactId: String, displayName: String, relationship: String, phoneNumber: String) {
        viewModelScope.launch {
            cloudRepository.upsertTrustedContact(contactId, displayName, relationship, phoneNumber)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Trusted contact sync failed."
                }
        }
    }

    fun deleteCloudTrustedContact(contactId: String) {
        viewModelScope.launch {
            cloudRepository.deleteTrustedContact(contactId)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Failed to remove cloud contact."
                }
        }
    }

    fun syncIncident(incidentId: String, title: String, highestRiskScore: Int, notes: String? = null) {
        viewModelScope.launch {
            cloudRepository.upsertOpenIncident(incidentId, title, highestRiskScore, notes)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Incident sync failed."
                }
        }
    }

    fun resolveIncident(incidentId: String, notes: String? = null) {
        viewModelScope.launch {
            cloudRepository.resolveIncident(incidentId, notes)
                .onSuccess { _syncMessage.value = "Incident marked as resolved in cloud backup." }
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Failed to resolve incident."
                }
        }
    }

    fun deleteCloudIncident(incidentId: String) {
        viewModelScope.launch {
            cloudRepository.deleteIncident(incidentId)
                .onFailure { err ->
                    _syncMessage.value = err.localizedMessage ?: "Failed to delete cloud incident."
                }
        }
    }

    fun syncAllLocalRecords(
        displayName: String,
        email: String?,
        appLanguage: String,
        elderlyMode: Boolean,
        notificationThreshold: Int,
        photoUrl: String? = null,
        history: List<ScanHistoryEntity>,
        contacts: List<DecryptedTrustedContact>,
        incidents: List<IncidentEntity>,
        vaultItems: List<VaultItemEntity> = emptyList(),
        quarantineItems: List<QuarantineEntity> = emptyList(),
        collectedMessages: List<CollectedMessageScanEntity> = emptyList(),
        allowlist: List<TrustedAllowlistEntity> = emptyList(),
        bookmarkedTips: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            val profileRes = cloudRepository.ensureUserProfile(
                displayName = displayName,
                email = email,
                appLanguage = appLanguage,
                elderlyMode = elderlyMode,
                notificationThreshold = notificationThreshold,
                photoUrl = photoUrl
            )
            if (profileRes.isFailure) {
                _syncMessage.value = profileRes.exceptionOrNull()?.localizedMessage ?: "Sync failed."
                return@launch
            }
            cloudRepository.syncProfileLists(
                bookmarkedTips = bookmarkedTips.toList(),
                trustedDomains = allowlist.map { it.domainOrSender }
            )

            var syncedCount = 0
            history.take(30).forEach { h ->
                if (
                    cloudRepository.syncScanSummary(
                        scanId = h.scanId,
                        source = h.source,
                        riskScore = h.riskScore,
                        riskLevel = h.riskLevel,
                        uncertain = h.uncertain,
                        categoryName = h.categoryName,
                        shortExplanation = h.shortExplanation,
                        feedbackOption = h.feedbackOption
                    ).isSuccess
                ) {
                    syncedCount++
                }
            }
            vaultItems.take(30).forEach { v ->
                if (
                    cloudRepository.upsertUserEntry(
                        entryId = "vault_${v.id}",
                        entryType = "VAULT",
                        title = "Vault: ${v.categoryName}",
                        category = v.categoryName,
                        sourceLabel = v.itemType,
                        riskScore = v.riskScore,
                        riskLevel = v.riskLevel,
                        summary = "Encrypted on device · Stages: ${v.stagesCsv.ifBlank { "Recorded" }}",
                        reviewed = true
                    ).isSuccess
                ) {
                    syncedCount++
                }
            }
            quarantineItems.take(30).forEach { q ->
                if (
                    cloudRepository.upsertUserEntry(
                        entryId = "quar_${q.id}",
                        entryType = "QUARANTINE",
                        title = "Quarantined: ${q.categoryName}",
                        category = q.categoryName,
                        sourceLabel = "QUARANTINE",
                        riskScore = q.riskScore,
                        riskLevel = q.riskLevel,
                        summary = q.previewSummary,
                        reviewed = true
                    ).isSuccess
                ) {
                    syncedCount++
                }
            }
            collectedMessages.take(40).forEach { msg ->
                if (
                    cloudRepository.upsertUserEntry(
                        entryId = "msg_${msg.id}",
                        entryType = "COLLECTED_MESSAGE",
                        title = "From ${msg.sender} (${msg.sourceApp})",
                        category = msg.categoryName,
                        sourceLabel = msg.sourceApp,
                        riskScore = msg.riskScore,
                        riskLevel = msg.riskLevel,
                        summary = msg.shortExplanation.ifBlank { msg.snippetPreview },
                        reviewed = msg.isReviewed
                    ).isSuccess
                ) {
                    syncedCount++
                }
            }
            allowlist.take(30).forEach { entry ->
                if (
                    cloudRepository.upsertUserEntry(
                        entryId = "allow_${entry.domainOrSender}",
                        entryType = "ALLOWLIST",
                        title = "Trusted: ${entry.domainOrSender}",
                        category = "Trusted Allowlist",
                        sourceLabel = "ALLOWLIST",
                        riskScore = 0,
                        riskLevel = "LOW",
                        summary = entry.note,
                        reviewed = true
                    ).isSuccess
                ) {
                    syncedCount++
                }
            }
            contacts.forEach { c ->
                if (c.phoneNumber.length >= 4) {
                    if (cloudRepository.upsertTrustedContact(c.id, c.displayName, c.relationship, c.phoneNumber).isSuccess) {
                        syncedCount++
                    }
                }
            }
            incidents.forEach { inc ->
                if (cloudRepository.upsertOpenIncident(inc.id, inc.title, inc.highestRiskScore, skipIfResolved = true).isSuccess) {
                    syncedCount++
                }
            }
            _syncMessage.value = "Real-time database sync complete ($syncedCount entries verified)."
        }
    }

    suspend fun deleteAllCloudData(): Result<Unit> {
        return cloudRepository.deleteAllUserCloudData()
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    companion object {
        private const val TAG = "CloudSyncVM"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(currentUserId: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CloudSyncViewModel(
                    cloudRepository = CloudSyncRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
