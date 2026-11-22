package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.CloudSchemaBounds
import com.example.data.firebase.CloudUiState
import com.example.data.firebase.CloudUserEntry
import com.example.data.firebase.UserProfile
import com.example.data.firebase.signOutFromGoogle
import com.example.domain.models.RiskLevel
import com.example.presentation.CloudSyncViewModel
import com.example.presentation.RakshakUiState
import com.example.presentation.RakshakViewModel
import com.example.presentation.components.SectionCard
import com.example.presentation.components.riskColor
import com.example.presentation.components.riskSoftContainer
import com.example.presentation.theme.RakshakColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfileEntryFilter(val label: String, val typeKey: String?) {
    ALL("All Entries", null),
    COLLECTED_MESSAGE("SMS & Msgs", "COLLECTED_MESSAGE"),
    VAULT("Vault", "VAULT"),
    QUARANTINE("Quarantine", "QUARANTINE"),
    PRE_ACTION("Pre-Action", "PRE_ACTION"),
    ALLOWLIST("Allowlist", "ALLOWLIST"),
    EMERGENCY_NOTE("Emergency", "EMERGENCY_NOTE")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    uiState: RakshakUiState,
    viewModel: RakshakViewModel,
    cloudSyncViewModel: CloudSyncViewModel,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    userUid: String,
    onOpenPrivacyCentre: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by cloudSyncViewModel.profileState.collectAsStateWithLifecycle()
    val scansState by cloudSyncViewModel.scansState.collectAsStateWithLifecycle()
    val userEntriesState by cloudSyncViewModel.userEntriesState.collectAsStateWithLifecycle()
    val contactsState by cloudSyncViewModel.contactsState.collectAsStateWithLifecycle()
    val incidentsState by cloudSyncViewModel.incidentsState.collectAsStateWithLifecycle()

    val cloudProfile: UserProfile? = (profileState as? CloudUiState.Success)?.data
    val cloudScansCount = (scansState as? CloudUiState.Success)?.data?.size ?: 0
    val cloudEntries = (userEntriesState as? CloudUiState.Success)?.data.orEmpty()
    val cloudContactsCount = (contactsState as? CloudUiState.Success)?.data?.size ?: 0
    val cloudIncidentsCount = (incidentsState as? CloudUiState.Success)?.data?.size ?: 0

    var hydratedFromCloud by remember(userUid) { mutableStateOf(false) }
    var nameInput by remember(userUid) { mutableStateOf(userDisplayName.ifBlank { "RakshakAI Guardian" }) }
    var phoneInput by remember(userUid) { mutableStateOf("") }
    var cityInput by remember(userUid) { mutableStateOf("") }
    var bioInput by remember(userUid) { mutableStateOf("") }
    var emergencyNotesInput by remember(userUid) { mutableStateOf("") }
    var guardianRoleInput by remember(userUid) { mutableStateOf("Self Protection") }

    LaunchedEffect(cloudProfile) {
        if (cloudProfile != null && !hydratedFromCloud) {
            nameInput = cloudProfile.displayName.ifBlank { userDisplayName.ifBlank { "RakshakAI Guardian" } }
            phoneInput = cloudProfile.phoneNumber.orEmpty()
            cityInput = cloudProfile.city.orEmpty()
            bioInput = cloudProfile.bio.orEmpty()
            emergencyNotesInput = cloudProfile.emergencyNotes.orEmpty()
            guardianRoleInput = cloudProfile.guardianRole.ifBlank { "Self Protection" }
            hydratedFromCloud = true
        }
    }

    var selectedFilter by remember { mutableStateOf(ProfileEntryFilter.ALL) }
    val filteredEntries = remember(cloudEntries, selectedFilter) {
        if (selectedFilter.typeKey == null) cloudEntries
        else cloudEntries.filter { it.entryType == selectedFilter.typeKey }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Guardian Identity Hero Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_hero_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(RakshakColors.DeepNavy, RakshakColors.DeepNavyContainer)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Image(
                                    painter = painterResource(id = R.drawable.img_guardian_avatar),
                                    contentDescription = "Guardian Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF7CE8E2), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(
                                            if (profileState is CloudUiState.Success) RakshakColors.SafeGreen
                                            else RakshakColors.CautionAmber
                                        )
                                        .border(2.dp, RakshakColors.DeepNavy, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cloudProfile?.displayName?.ifBlank { nameInput } ?: nameInput,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val effectiveEmail = cloudProfile?.email ?: userEmail
                                if (!effectiveEmail.isNullOrBlank()) {
                                    Text(
                                        text = effectiveEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC8D7EA),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        color = RakshakColors.TealPrimary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = cloudProfile?.guardianRole?.ifBlank { guardianRoleInput } ?: guardianRoleInput,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF7CE8E2),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    val displayCity = cloudProfile?.city?.takeIf { it.isNotBlank() } ?: cityInput.takeIf { it.isNotBlank() }
                                    if (displayCity != null) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = displayCity,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF7CE8E2),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val statusText = when (profileState) {
                                    is CloudUiState.Loading -> "Connecting real-time DB..."
                                    is CloudUiState.Error -> "Sync paused — offline queue active"
                                    is CloudUiState.Success -> "Real-time database connected"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7CE8E2)
                                )
                            }
                            val joinedDate = cloudProfile?.createdAt?.toDate()?.let { date ->
                                SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(date)
                            }
                            Text(
                                text = if (joinedDate != null) "Joined $joinedDate" else "ID: ${userUid.take(6)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB6C6DD)
                            )
                        }
                    }
                }
            }
        }

        // 2. Real-Time Database Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStatTile(
                    label = "Scans",
                    value = cloudScansCount.toString(),
                    accent = RakshakColors.DeepNavy,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatTile(
                    label = "Entries",
                    value = cloudEntries.size.toString(),
                    accent = RakshakColors.TealPrimary,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatTile(
                    label = "Contacts",
                    value = cloudContactsCount.toString(),
                    accent = RakshakColors.SafeGreen,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatTile(
                    label = "Incidents",
                    value = cloudIncidentsCount.toString(),
                    accent = RakshakColors.CautionAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Personal & Emergency Profile Form
        item {
            SectionCard(title = "Personal Details") {

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 80) nameInput = it },
                    label = { Text("Full Name / Display Name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { if (it.length <= 32) phoneInput = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+91...") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_phone_input")
                    )
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { if (it.length <= 80) cityInput = it },
                        label = { Text("City / State") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_city_input")
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Badge,
                        contentDescription = null,
                        tint = RakshakColors.DeepNavy,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardian Role:", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CloudSchemaBounds.GUARDIAN_ROLES.forEach { role ->
                        FilterChip(
                            selected = guardianRoleInput == role,
                            onClick = { guardianRoleInput = role },
                            label = { Text(role) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bioInput,
                    onValueChange = { if (it.length <= 240) bioInput = it },
                    label = { Text("About / Bio (optional)") },
                    placeholder = { Text("e.g. Protecting family phones from UPI & KYC scams") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emergencyNotesInput,
                    onValueChange = { if (it.length <= 240) emergencyNotesInput = it },
                    label = { Text("Emergency / Bank Helpline Note (optional)") },
                    placeholder = { Text("e.g. Primary bank: SBI, notify Son first if alert triggered") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_emergency_notes_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        cloudSyncViewModel.updatePersonalProfile(
                            displayName = nameInput,
                            phoneNumber = phoneInput,
                            city = cityInput,
                            bio = bioInput,
                            emergencyNotes = emergencyNotesInput,
                            guardianRole = guardianRoleInput
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.TealPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("profile_save_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Profile to Cloud", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 4. Account Preferences Summary
        item {
            SectionCard(title = "Synced Preferences & Safety Summary") {
                val langLabel = if (uiState.settings.appLanguage.startsWith("hi")) "हिन्दी (Hindi)" else "English"
                val tipCount = cloudProfile?.bookmarkedTips?.size ?: uiState.settings.bookmarkedTips.size
                val domainCount = cloudProfile?.trustedDomains?.size ?: uiState.allowlist.size
                Text("• Language: $langLabel", style = MaterialTheme.typography.bodySmall)
                Text("• Elderly Mode: ${if (uiState.settings.elderlyMode) "Enabled" else "Off"}", style = MaterialTheme.typography.bodySmall)
                Text("• Alert Risk Threshold: ${uiState.settings.notificationThreshold}/100", style = MaterialTheme.typography.bodySmall)
                Text("• Bookmarked Safety Lessons: $tipCount · Trusted Allowlist: $domainCount", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profile_settings_btn")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings")
                    }
                    OutlinedButton(
                        onClick = onOpenPrivacyCentre,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profile_privacy_btn")
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy")
                    }
                }
            }
        }

        // 5. Real-Time Cloud User Entries Section Header & Filters
        item {
            SectionCard(
                title = "Synced User Entries · ${cloudEntries.size}",
                containerColor = RakshakColors.TealSoft,
                borderColor = RakshakColors.TealPrimary.copy(alpha = 0.45f)
            ) {
                OutlinedButton(
                    onClick = {
                        val decContacts = uiState.trustedContacts.map { viewModel.repository.decryptContact(it) }
                        cloudSyncViewModel.syncAllLocalRecords(
                            displayName = nameInput.ifBlank { userDisplayName },
                            email = userEmail,
                            appLanguage = uiState.settings.appLanguage,
                            elderlyMode = uiState.settings.elderlyMode,
                            notificationThreshold = uiState.settings.notificationThreshold,
                            photoUrl = userPhotoUrl,
                            history = uiState.history,
                            contacts = decContacts,
                            incidents = uiState.incidents,
                            vaultItems = uiState.vaultItems,
                            quarantineItems = uiState.quarantineItems,
                            collectedMessages = uiState.collectedMessages,
                            allowlist = uiState.allowlist,
                            bookmarkedTips = uiState.settings.bookmarkedTips
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("profile_sync_all_btn")
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync All Local Entries Now")
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileEntryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("profile_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        when (val entriesState = userEntriesState) {
            is CloudUiState.Loading -> {
                item {
                    SectionCard {
                        Text("Loading real-time entries from Cloud Firestore...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is CloudUiState.Error -> {
                item {
                    SectionCard(
                        containerColor = RakshakColors.DangerSoft,
                        borderColor = RakshakColors.DangerRed
                    ) {
                        Text(
                            text = "Could not load real-time entries: ${entriesState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = RakshakColors.CriticalDarkRed
                        )
                    }
                }
            }
            is CloudUiState.Success -> {
                if (filteredEntries.isEmpty()) {
                    item {
                        SectionCard {
                            Text(
                                text = "No synced entries yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        CloudUserEntryCard(
                            entry = entry,
                            onDelete = { cloudSyncViewModel.deleteCloudUserEntry(entry.id) }
                        )
                    }
                }
            }
        }

        // 6. Sign Out Card
        item {
            SectionCard {
                OutlinedButton(
                    onClick = {
                        signOutFromGoogle(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    border = BorderStroke(1.dp, RakshakColors.DangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RakshakColors.DangerRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("profile_sign_out_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Google Account")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProfileStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CloudUserEntryCard(
    entry: CloudUserEntry,
    onDelete: () -> Unit
) {
    val level = runCatching { RiskLevel.valueOf(entry.riskLevel) }.getOrDefault(RiskLevel.LOW)
    val badgeColor = riskColor(level)
    val badgeBg = riskSoftContainer(level)
    val updatedStr = entry.updatedAt?.toDate()?.let { date ->
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
    } ?: "Syncing..."

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RakshakColors.SurfaceMuted,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.entryType.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = RakshakColors.DeepNavy,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${entry.riskScore}/100 · ${entry.riskLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.category} · ${entry.sourceLabel} · $updatedStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = RakshakColors.InkSecondary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete synced entry",
                    tint = RakshakColors.InkSecondary
                )
            }
        }
    }
}
