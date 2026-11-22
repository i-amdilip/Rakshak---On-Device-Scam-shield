package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(128).ifBlank { "item_${System.currentTimeMillis()}" }
    }

    private fun checkValidDocId(id: String) {
        require(id.length in 1..128 && CloudSchemaBounds.VALID_ID_REGEX.matches(id)) {
            "Document ID must match ^[a-zA-Z0-9_\\-]+$ (1..128 chars)"
        }
    }

    // --- REALTIME OBSERVATION STREAMS ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        checkValidDocId(userId)
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSameUser(error, userId, OperationType.GET, path)
                throw error
            }
    }

    fun observeScanSummaries(userId: String): Flow<List<CloudScanSummary>> {
        checkValidDocId(userId)
        val path = "users/$userId/scanSummaries"
        return db.collection("users").document(userId).collection("scanSummaries")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudScanSummary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSameUser(error, userId, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserEntries(userId: String): Flow<List<CloudUserEntry>> {
        checkValidDocId(userId)
        val path = "users/$userId/userEntries"
        return db.collection("users").document(userId).collection("userEntries")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudUserEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSameUser(error, userId, OperationType.LIST, path)
                throw error
            }
    }

    fun observeTrustedContacts(userId: String): Flow<List<CloudTrustedContact>> {
        checkValidDocId(userId)
        val path = "users/$userId/trustedContacts"
        return db.collection("users").document(userId).collection("trustedContacts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudTrustedContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSameUser(error, userId, OperationType.LIST, path)
                throw error
            }
    }

    fun observeIncidents(userId: String): Flow<List<CloudIncident>> {
        checkValidDocId(userId)
        val path = "users/$userId/incidents"
        return db.collection("users").document(userId).collection("incidents")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudIncident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowErrorIfSameUser(error, userId, OperationType.LIST, path)
                throw error
            }
    }

    private fun logFlowErrorIfSameUser(
        error: Throwable,
        uid: String,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    // --- USER PROFILE & SETTINGS SYNC ---

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        appLanguage: String,
        elderlyMode: Boolean,
        notificationThreshold: Int,
        photoUrl: String? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val safeName = displayName.trim().ifBlank { "RakshakAI Guardian" }.take(80)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            val safeLang = if (appLanguage in CloudSchemaBounds.VALID_LANGUAGES) appLanguage else "en"
            val safeThreshold = notificationThreshold.coerceIn(
                CloudSchemaBounds.MIN_THRESHOLD,
                CloudSchemaBounds.MAX_THRESHOLD
            ).toLong()

            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "guardianRole" to "Self Protection",
                    "photoUrl" to safePhoto,
                    "bookmarkedTips" to emptyList<String>(),
                    "trustedDomains" to emptyList<String>(),
                    "appLanguage" to safeLang,
                    "elderlyMode" to elderlyMode,
                    "notificationThreshold" to safeThreshold,
                    "totalScansSynced" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            } else {
                // Preserve user-edited displayName & personal details if already stored
                val existingName = existingSnap.getString("displayName")?.trim().orEmpty()
                val updatePayload = mutableMapOf<String, Any>(
                    "appLanguage" to safeLang,
                    "elderlyMode" to elderlyMode,
                    "notificationThreshold" to safeThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existingName.isBlank()) {
                    updatePayload["displayName"] = safeName
                }
                if (safeEmail != null && existingSnap.getString("email").isNullOrBlank()) {
                    updatePayload["email"] = safeEmail
                }
                if (safePhoto != null && existingSnap.getString("photoUrl").isNullOrBlank()) {
                    updatePayload["photoUrl"] = safePhoto
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserPersonalProfile(
        displayName: String,
        phoneNumber: String,
        city: String,
        bio: String,
        emergencyNotes: String,
        guardianRole: String
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val safeName = displayName.trim().take(80)
            require(safeName.isNotEmpty()) { "Display name cannot be empty." }
            val safePhone = phoneNumber.trim().take(32)
            val safeCity = city.trim().take(80)
            val safeBio = bio.trim().take(240)
            val safeEmergency = emergencyNotes.trim().take(240)
            val safeRole = guardianRole.trim().ifBlank { "Self Protection" }.take(40)

            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val user = auth.currentUser
                val initRes = ensureUserProfile(
                    displayName = safeName,
                    email = user?.email,
                    appLanguage = "en",
                    elderlyMode = false,
                    notificationThreshold = 60,
                    photoUrl = user?.photoUrl?.toString()
                )
                if (initRes.isFailure) return initRes
            }

            docRef.update(
                mapOf(
                    "displayName" to safeName,
                    "phoneNumber" to safePhone,
                    "city" to safeCity,
                    "bio" to safeBio,
                    "emergencyNotes" to safeEmergency,
                    "guardianRole" to safeRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncProfileLists(
        bookmarkedTips: List<String>? = null,
        trustedDomains: List<String>? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val docRef = db.collection("users").document(uid)
            if (!docRef.get().await().exists()) return Result.success(Unit)

            val updateMap = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (bookmarkedTips != null) {
                updateMap["bookmarkedTips"] = bookmarkedTips.distinct().take(30)
            }
            if (trustedDomains != null) {
                updateMap["trustedDomains"] = trustedDomains.distinct().take(50)
            }
            if (updateMap.size > 1) {
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPreferences(
        appLanguage: String,
        elderlyMode: Boolean,
        notificationThreshold: Int
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val safeLang = if (appLanguage in CloudSchemaBounds.VALID_LANGUAGES) appLanguage else "en"
            val safeThreshold = notificationThreshold.coerceIn(
                CloudSchemaBounds.MIN_THRESHOLD,
                CloudSchemaBounds.MAX_THRESHOLD
            ).toLong()
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val user = auth.currentUser
                return ensureUserProfile(
                    displayName = user?.displayName ?: "RakshakAI Guardian",
                    email = user?.email,
                    appLanguage = safeLang,
                    elderlyMode = elderlyMode,
                    notificationThreshold = safeThreshold.toInt(),
                    photoUrl = user?.photoUrl?.toString()
                )
            }
            docRef.update(
                mapOf(
                    "appLanguage" to safeLang,
                    "elderlyMode" to elderlyMode,
                    "notificationThreshold" to safeThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- SUMMARY-ONLY SCAN SYNC ---

    suspend fun syncScanSummary(
        scanId: String,
        source: String,
        riskScore: Int,
        riskLevel: String,
        uncertain: Boolean,
        categoryName: String,
        shortExplanation: String,
        feedbackOption: String? = null
    ): Result<Unit> {
        val safeId = sanitizeDocId(scanId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/scanSummaries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)

            val safeSource = if (source in CloudSchemaBounds.VALID_SOURCES) source else "MANUAL"
            val safeLevel = if (riskLevel in CloudSchemaBounds.VALID_RISK_LEVELS) riskLevel else "MEDIUM"
            val safeScore = riskScore.coerceIn(0, 100).toLong()
            val safeCategory = categoryName.trim().ifBlank { "General Check" }.take(100)
            val safeExplanation = shortExplanation.trim().take(240)
            val safeFeedback = feedbackOption?.takeIf { it in CloudSchemaBounds.VALID_FEEDBACK_OPTIONS }

            val docRef = db.collection("users").document(uid)
                .collection("scanSummaries").document(safeId)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "source" to safeSource,
                    "riskScore" to safeScore,
                    "riskLevel" to safeLevel,
                    "uncertain" to uncertain,
                    "categoryName" to safeCategory,
                    "shortExplanation" to safeExplanation,
                    "feedbackOption" to safeFeedback,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()

                val profileRef = db.collection("users").document(uid)
                if (profileRef.get().await().exists()) {
                    profileRef.update(
                        mapOf(
                            "totalScansSynced" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateScanFeedback(scanId: String, feedbackOption: String): Result<Unit> {
        val safeId = sanitizeDocId(scanId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/scanSummaries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            require(feedbackOption in CloudSchemaBounds.VALID_FEEDBACK_OPTIONS) {
                "Invalid feedback option: $feedbackOption"
            }
            val docRef = db.collection("users").document(uid)
                .collection("scanSummaries").document(safeId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "feedbackOption" to feedbackOption,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScanSummary(scanId: String): Result<Unit> {
        val safeId = sanitizeDocId(scanId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/scanSummaries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            db.collection("users").document(uid)
                .collection("scanSummaries").document(safeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllScanSummaries(): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/scanSummaries"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val snap = db.collection("users").document(uid).collection("scanSummaries").get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- UNIFIED REAL-TIME USER ENTRIES (VAULT, QUARANTINE, PRE_ACTION, COLLECTED_MESSAGE, ALLOWLIST, EMERGENCY_NOTE) ---

    suspend fun upsertUserEntry(
        entryId: String,
        entryType: String,
        title: String,
        category: String,
        sourceLabel: String,
        riskScore: Int,
        riskLevel: String,
        summary: String,
        reviewed: Boolean = false
    ): Result<Unit> {
        val safeId = sanitizeDocId(entryId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/userEntries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            val safeType = if (entryType in CloudSchemaBounds.VALID_ENTRY_TYPES) entryType else "VAULT"
            val safeTitle = title.trim().ifBlank { "User Entry" }.take(120)
            val safeCategory = category.trim().ifBlank { "General" }.take(100)
            val safeSource = sourceLabel.trim().ifBlank { "APP" }.take(40)
            val safeScore = riskScore.coerceIn(0, 100).toLong()
            val safeLevel = if (riskLevel in CloudSchemaBounds.VALID_RISK_LEVELS) riskLevel else "LOW"
            val safeSummary = summary.trim().take(300)

            val docRef = db.collection("users").document(uid)
                .collection("userEntries").document(safeId)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "entryType" to safeType,
                        "title" to safeTitle,
                        "category" to safeCategory,
                        "sourceLabel" to safeSource,
                        "riskScore" to safeScore,
                        "riskLevel" to safeLevel,
                        "summary" to safeSummary,
                        "reviewed" to reviewed,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "category" to safeCategory,
                        "sourceLabel" to safeSource,
                        "riskScore" to safeScore,
                        "riskLevel" to safeLevel,
                        "summary" to safeSummary,
                        "reviewed" to reviewed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markUserEntryReviewed(entryId: String, reviewed: Boolean): Result<Unit> {
        val safeId = sanitizeDocId(entryId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/userEntries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            val docRef = db.collection("users").document(uid)
                .collection("userEntries").document(safeId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "reviewed" to reviewed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteUserEntry(entryId: String): Result<Unit> {
        val safeId = sanitizeDocId(entryId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/userEntries/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            db.collection("users").document(uid)
                .collection("userEntries").document(safeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteUserEntriesByType(entryType: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/userEntries"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val snap = db.collection("users").document(uid)
                .collection("userEntries")
                .whereEqualTo("entryType", entryType)
                .get()
                .await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- TRUSTED FAMILY CONTACTS ---

    suspend fun upsertTrustedContact(
        contactId: String,
        displayName: String,
        relationship: String,
        phoneNumber: String
    ): Result<Unit> {
        val safeId = sanitizeDocId(contactId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/trustedContacts/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            val safeName = displayName.trim()
            val safeRel = relationship.trim().ifBlank { "Family" }.take(40)
            val safePhone = phoneNumber.trim()

            require(safeName.length in 1..80) { "Contact name must be 1-80 characters." }
            require(safePhone.length in 4..32) { "Phone number must be 4-32 characters." }

            val docRef = db.collection("users").document(uid)
                .collection("trustedContacts").document(safeId)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "relationship" to safeRel,
                        "phoneNumber" to safePhone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "relationship" to safeRel,
                        "phoneNumber" to safePhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrustedContact(contactId: String): Result<Unit> {
        val safeId = sanitizeDocId(contactId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/trustedContacts/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            db.collection("users").document(uid)
                .collection("trustedContacts").document(safeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- INCIDENTS (WITH TERMINAL RESOLVED LOCK) ---

    suspend fun upsertOpenIncident(
        incidentId: String,
        title: String,
        highestRiskScore: Int,
        notes: String? = null,
        skipIfResolved: Boolean = false
    ): Result<Unit> {
        val safeId = sanitizeDocId(incidentId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/incidents/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            val safeTitle = title.trim().ifBlank { "Suspicious Incident" }.take(120)
            val safeScore = highestRiskScore.coerceIn(0, 100).toLong()
            val safeNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            val docRef = db.collection("users").document(uid)
                .collection("incidents").document(safeId)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "highestRiskScore" to safeScore,
                    "status" to "OPEN",
                    "notes" to safeNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val existingStatus = existingSnap.getString("status") ?: "OPEN"
                if (existingStatus != "OPEN") {
                    if (skipIfResolved) return Result.success(Unit)
                    error("Resolved incidents are locked against edits.")
                }
                val existingScore = (existingSnap.getLong("highestRiskScore") ?: 0L)
                val updatePayload = mutableMapOf<String, Any>(
                    "title" to safeTitle,
                    "highestRiskScore" to maxOf(existingScore, safeScore),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeNotes != null) {
                    updatePayload["notes"] = safeNotes
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun resolveIncident(incidentId: String, resolutionNote: String? = null): Result<Unit> {
        val safeId = sanitizeDocId(incidentId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/incidents/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            val docRef = db.collection("users").document(uid)
                .collection("incidents").document(safeId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists() && existingSnap.getString("status") == "OPEN") {
                val payload = mutableMapOf<String, Any>(
                    "status" to "RESOLVED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                resolutionNote?.trim()?.takeIf { it.isNotEmpty() }?.take(500)?.let {
                    payload["notes"] = it
                }
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteIncident(incidentId: String): Result<Unit> {
        val safeId = sanitizeDocId(incidentId)
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/incidents/$safeId"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            checkValidDocId(safeId)
            db.collection("users").document(uid)
                .collection("incidents").document(safeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid"
        return try {
            val uid = requireUserId()
            checkValidDocId(uid)
            val userRef = db.collection("users").document(uid)

            val subCollections = listOf("scanSummaries", "userEntries", "trustedContacts", "incidents")
            for (sub in subCollections) {
                val snap = userRef.collection(sub).get().await()
                if (!snap.isEmpty) {
                    val batch = db.batch()
                    snap.documents.forEach { doc -> batch.delete(doc.reference) }
                    batch.commit().await()
                }
            }
            if (userRef.get().await().exists()) {
                userRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
