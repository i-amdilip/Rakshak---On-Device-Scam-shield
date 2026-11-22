package com.example.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.CloudUiState
import com.example.data.firebase.signOutFromGoogle
import com.example.integrations.notifications.MessageNotificationListenerService
import com.example.integrations.sms.SmsInboxScanner
import com.example.presentation.CloudSyncViewModel
import com.example.presentation.RakshakUiState
import com.example.presentation.RakshakViewModel
import com.example.presentation.components.SectionCard
import com.example.presentation.theme.LocalRakshakMetrics
import com.example.presentation.theme.RakshakColors

@Composable
fun MoreHubScreen(
    onOpenProfile: () -> Unit = {},
    onOpenPrivacyCentre: () -> Unit,
    onOpenSafetyCentre: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val metrics = LocalRakshakMetrics.current
    val itemsList = listOf(
        Triple("My Profile & Cloud", RakshakColors.TealPrimary, Icons.Filled.AccountCircle) to onOpenProfile,
        Triple("Privacy & Sync", RakshakColors.SafeGreen, Icons.Filled.PrivacyTip) to onOpenPrivacyCentre,
        Triple("Safety Centre & Quiz", androidx.compose.ui.graphics.Color(0xFF2563EB), Icons.Filled.School) to onOpenSafetyCentre,
        Triple("Settings", RakshakColors.DeepNavy, Icons.Filled.Settings) to onOpenSettings,
        Triple("About & Limitations", RakshakColors.InkSecondary, Icons.Filled.Info) to onOpenAbout
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        items(itemsList.size) { i ->
            val (meta, action) = itemsList[i]
            Card(
                onClick = action,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                modifier = Modifier.fillMaxWidth().heightIn(min = maxOf(metrics.minTouchTarget, 64.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = meta.second.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = meta.third,
                                contentDescription = meta.first,
                                tint = meta.second,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = meta.first,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RakshakColors.DeepNavy,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = RakshakColors.InkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyCentreScreen(
    uiState: RakshakUiState,
    viewModel: RakshakViewModel,
    cloudSyncViewModel: CloudSyncViewModel,
    userDisplayName: String,
    userEmail: String?
) {
    LaunchedEffect(Unit) { viewModel.refreshAudits() }
    val audit = uiState.permissionAudit
    val stats = uiState.storageStats
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val profileState by cloudSyncViewModel.profileState.collectAsStateWithLifecycle()
    val scansState by cloudSyncViewModel.scansState.collectAsStateWithLifecycle()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            SectionCard(
                title = "Google Guardian Account & Firebase Sync",
                containerColor = RakshakColors.TealSoft,
                borderColor = RakshakColors.TealPrimary
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = RakshakColors.TealPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName.ifBlank { "RakshakAI Guardian" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(text = userEmail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when (val prof = profileState) {
                    is CloudUiState.Loading -> Text("Connecting to Cloud Firestore...", style = MaterialTheme.typography.bodySmall)
                    is CloudUiState.Error -> Text(
                        text = "Cloud sync notice: ${prof.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RakshakColors.DangerRed
                    )
                    is CloudUiState.Success -> {
                        val syncedCount = (scansState as? CloudUiState.Success)?.data?.size
                            ?: prof.data?.totalScansSynced?.toInt()
                            ?: 0
                        Text(
                            text = "Connected to Firebase · $syncedCount summary scans backed up",
                            style = MaterialTheme.typography.bodySmall,
                            color = RakshakColors.DeepNavy
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val decContacts = uiState.trustedContacts.map { viewModel.repository.decryptContact(it) }
                            cloudSyncViewModel.syncAllLocalRecords(
                                displayName = userDisplayName,
                                email = userEmail,
                                appLanguage = uiState.settings.appLanguage,
                                elderlyMode = uiState.settings.elderlyMode,
                                notificationThreshold = uiState.settings.notificationThreshold,
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
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("privacy_sync_cloud_btn")
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                    OutlinedButton(
                        onClick = {
                            signOutFromGoogle(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("privacy_sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Live PackageManager Permission Audit",
                containerColor = RakshakColors.SafeSoft,
                borderColor = RakshakColors.SafeGreen
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = RakshakColors.SafeGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Network permissions scoped to Firebase Auth & Cloud Firestore sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• SMS Inbox Read (READ_SMS): ${if (audit?.smsReadGranted == true) "Granted" else "Not granted"}\n" +
                        "• Live SMS Auto-Scan (RECEIVE_SMS): ${if (audit?.smsReceiveGranted == true) "Granted" else "Not granted"}\n" +
                        "• Messenger Notification Shield: ${if (audit?.notificationListenerEnabled == true) "Enabled" else "Off"}\n" +
                        "• Safety Alerts (POST_NOTIFICATIONS): ${if (audit?.notificationsGranted == true) "Granted" else "Not granted"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Declared manifest permissions: ${audit?.declaredPermissions?.joinToString(", ") ?: "Inspecting..."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Three Separate Privacy Guarantees") {
                Text("1. Processing locally: All text, URL, QR, and OCR scam analysis runs on your phone's CPU.")
                Spacer(modifier = Modifier.height(4.dp))
                Text("2. Storing locally: Evidence Vault, Quarantine, and local contacts use Android Keystore AES-256-GCM encryption.")
                Spacer(modifier = Modifier.height(4.dp))
                Text("3. Cloud sync scope: Only summary risk metadata, trusted contacts, and incident titles sync to your Google-authenticated Firebase space. Raw SMS/WhatsApp text is never uploaded.")
            }
        }

        item {
            SectionCard(title = "On-Device Storage Footprint") {
                val kb = stats.totalDiskBytes / 1024.0
                Text("• History summaries: ${stats.historyCount}")
                Text("• Collected SMS & message scans: ${uiState.collectedMessages.size}")
                Text("• Encrypted Vault items: ${stats.vaultCount}")
                Text("• Quarantined copies: ${stats.quarantineCount}")
                Text("• Trusted contacts: ${stats.contactsCount}")
                Text("• Encrypted DB & media size: ${String.format(java.util.Locale.US, "%.1f", kb)} KB")
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { confirmDeleteAll = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DangerRed),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("privacy_delete_everything_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Everything (DB + Files + Prefs + Keys)")
                }
            }
        }

        item {
            SectionCard(title = "Offline Verification Helper") {
                Text(
                    text = "Turn on Airplane Mode on your device and tap below to verify built-in English, Hindi, and paraphrase detection works completely offline:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = viewModel::runOfflineSelfTestSuite,
                    colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Text("Run Offline Self-Test (6 Core Cases)")
                }
                if (uiState.offlineTestResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    uiState.offlineTestResults.forEach { (title, res) ->
                        Text(
                            text = "• $title -> ${res.riskLevel.name} (${res.riskScore}/100, ${res.processingTimeMs}ms)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete ALL RakshakAI Data?") },
            text = {
                Text("This permanently erases your scan history, encrypted Evidence Vault, Quarantine, trusted contacts, settings, and Keystore encryption key.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllData(onCloudWipe = {
                            cloudSyncViewModel.deleteAllCloudData()
                        })
                        confirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DangerRed)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

private data class QuizItem(
    val prompt: String,
    val isScam: Boolean,
    val explanation: String
)

@Composable
fun SafetyCentreScreen(
    bookmarkedTipIds: Set<String>,
    onToggleBookmark: (String) -> Unit
) {
    val lessons = listOf(
        Triple("l_da", "Digital Arrest & Police Impersonation", "Real CBI, Police, or Customs officers NEVER hold you on a video call, show arrest orders over WhatsApp, or demand 'safe account' transfers."),
        Triple("l_kyc", "Fake Bank KYC Suspension", "Banks do not ask you to update KYC via shortened links or random .xyz/.top websites. Open your official bank app directly."),
        Triple("l_upi", "UPI Refund & Collect QR Trap", "You NEVER enter your UPI PIN or scan a QR code to RECEIVE money. Scanning a payment QR only sends money out."),
        Triple("l_job", "Part-Time Job & Prepaid Task Scam", "Never pay a 'registration fee' or 'security deposit' to start a remote job or unlock task earnings."),
        Triple("l_elec", "Electricity / SIM Disconnection Tonight", "Scammers create 12-hour panic ('disconnected tonight'). Check your official utility app instead of calling unknown numbers."),
        Triple("l_remote", "Remote-Access Screen Sharing", "Never install AnyDesk, TeamViewer, or QuickSupport on a caller's request — they can watch your OTPs and bank screens.")
    )

    val quizItems = remember {
        listOf(
            QuizItem("CBI Officer says transfer ₹50,000 to RBI Safe Account to clear your parcel case.", true, "SCAM — Real agencies never ask for safe-account transfers or secrecy."),
            QuizItem("123456 is your OTP for Bank login. Do not share this OTP with anyone.", false, "SAFE TEMPLATE — Standard informational alert reminding you not to share OTP."),
            QuizItem("Scan this QR code and enter your UPI PIN to receive ₹2,000 cashback.", true, "SCAM — Entering a UPI PIN sends money out, never receives money.")
        )
    }
    var quizIdx by remember { mutableIntStateOf(0) }
    var quizAnsweredCorrect by remember { mutableStateOf<Boolean?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            SectionCard(
                title = "Golden Rules of Digital Safety",
                containerColor = RakshakColors.TealSoft,
                borderColor = RakshakColors.TealPrimary
            ) {
                Text("1. Never share an OTP, UPI PIN, ATM PIN, or CVV with anyone.")
                Text("2. Real banks and police never ask you to keep a call secret from family.")
                Text("3. Always open your official bank/utility app yourself instead of tapping SMS links.")
            }
        }

        item {
            val currentQ = quizItems[quizIdx]
            SectionCard(title = "Scam-or-Safe Awareness Quiz (${quizIdx + 1}/${quizItems.size})") {
                Text(currentQ.prompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { quizAnsweredCorrect = currentQ.isScam },
                        colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DangerRed),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("Scam") }
                    Button(
                        onClick = { quizAnsweredCorrect = !currentQ.isScam },
                        colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.SafeGreen),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("Safe") }
                }
                quizAnsweredCorrect?.let { right ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (if (right) "Correct! " else "Watch out: ") + currentQ.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (right) RakshakColors.SafeGreen else RakshakColors.DangerRed
                    )
                    TextButton(onClick = {
                        quizAnsweredCorrect = null
                        quizIdx = (quizIdx + 1) % quizItems.size
                    }) { Text("Next Question") }
                }
            }
        }

        items(lessons, key = { it.first }) { (id, title, body) ->
            val saved = id in bookmarkedTipIds
            SectionCard(title = title) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onToggleBookmark(id) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (saved) "Bookmarked ✓" else "Bookmark Lesson")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SettingsScreen(
    uiState: RakshakUiState,
    viewModel: RakshakViewModel,
    onRequestSmsScan: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = uiState.settings
    val notifListenerOn = uiState.permissionAudit?.notificationListenerEnabled ?: false
    var showNotifConsentDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAudits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true ||
            SmsInboxScanner.hasReceiveSmsPermission(context)
        val readGranted = results[Manifest.permission.READ_SMS] == true ||
            SmsInboxScanner.hasReadSmsPermission(context)
        viewModel.refreshAudits()
        if (receiveGranted || readGranted) {
            viewModel.setSmsAutoScan(true)
            if (readGranted) {
                viewModel.scanAllSmsInbox(showAlertNotification = true)
            }
        } else {
            viewModel.setSmsAutoScan(false)
            viewModel.showToast("SMS permission denied. Manual Share & Paste scanning still works fully.")
        }
    }

    val launchSmsPermRequest: () -> Unit = {
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        smsPermissionsLauncher.launch(perms)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            SectionCard(title = "Language & Accessibility (Elderly Mode)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.setLanguage("en") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (settings.appLanguage == "en") "✓ English" else "English")
                    }
                    OutlinedButton(
                        onClick = { viewModel.setLanguage("hi") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (settings.appLanguage == "hi") "✓ हिन्दी" else "हिन्दी")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "Elderly Mode (Large Text, High Contrast, Simpler Wording)",
                    checked = settings.elderlyMode,
                    onCheckedChange = viewModel::setElderlyMode,
                    testTag = "setting_elderly_mode_switch"
                )
                SettingSwitchRow(
                    title = "Reduced Motion",
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion
                )
            }
        }

        item {
            SectionCard(title = "Scanning, History & Security") {
                SettingSwitchRow(
                    title = "Save Scan History (Summary only — no raw text)",
                    checked = settings.historyEnabled,
                    onCheckedChange = viewModel::setHistoryEnabled
                )
                val hasAnySmsPermission = uiState.permissionAudit?.smsReadGranted == true ||
                    uiState.permissionAudit?.smsReceiveGranted == true
                SettingSwitchRow(
                    title = "SMS Inbox & Real-Time Auto-Scan (On-Device)",
                    checked = settings.smsAutoScanEnabled && hasAnySmsPermission,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (onRequestSmsScan != null) {
                                onRequestSmsScan()
                            } else if (SmsInboxScanner.hasReadSmsPermission(context)) {
                                viewModel.setSmsAutoScan(true)
                                viewModel.scanAllSmsInbox(showAlertNotification = true)
                            } else {
                                launchSmsPermRequest()
                            }
                        } else {
                            viewModel.setSmsAutoScan(false)
                        }
                    },
                    testTag = "setting_sms_autoscan_switch"
                )
                SettingSwitchRow(
                    title = "WhatsApp, Telegram & Instagram Notification Scanning",
                    checked = settings.messageNotifScanEnabled && notifListenerOn,
                    onCheckedChange = { enable ->
                        if (enable && !notifListenerOn) {
                            showNotifConsentDialog = true
                        } else {
                            viewModel.setMessageNotifScan(enable)
                        }
                    },
                    testTag = "setting_notif_scan_switch"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (notifListenerOn) {
                            "Android Notification Access: Active"
                        } else {
                            "Android Notification Access: Required for WhatsApp, Telegram & Instagram"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (notifListenerOn) RakshakColors.SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (notifListenerOn) {
                                runCatching {
                                    context.startActivity(
                                        MessageNotificationListenerService.createNotificationListenerSettingsIntent(context)
                                    )
                                }.onFailure {
                                    viewModel.showToast("Open Android Settings -> Notifications -> Notification Access.")
                                }
                            } else {
                                showNotifConsentDialog = true
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("settings_open_notif_access_btn")
                    ) {
                        Text(if (notifListenerOn) "Manage" else "Allow")
                    }
                }
                Text(
                    text = "Alert notification threshold: ${settings.notificationThreshold}/100",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = settings.notificationThreshold.toFloat(),
                    onValueChange = { viewModel.setNotificationThreshold(it.toInt()) },
                    valueRange = 40f..90f
                )
                SettingSwitchRow(
                    title = "Biometric / Device Lock for Vault & Export",
                    checked = settings.biometricVaultLock,
                    onCheckedChange = viewModel::setBiometricVaultLock
                )
                SettingSwitchRow(
                    title = "Block Screenshots on Result & Vault (FLAG_SECURE)",
                    checked = settings.flagSecureEnabled,
                    onCheckedChange = viewModel::setFlagSecure
                )
            }
        }

        if (uiState.allowlist.isNotEmpty()) {
            item {
                SectionCard(title = "Local Trusted Allowlist") {
                    uiState.allowlist.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(entry.domainOrSender, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.removeAllowlist(entry.domainOrSender) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showNotifConsentDialog) {
        NotificationAccessConsentDialog(
            onConfirm = {
                showNotifConsentDialog = false
                viewModel.setMessageNotifScan(true)
                runCatching {
                    context.startActivity(
                        MessageNotificationListenerService.createNotificationListenerSettingsIntent(context)
                    )
                }.onFailure {
                    viewModel.showToast("Open Android Settings -> Notifications -> Notification Access.")
                }
            },
            onDismiss = { showNotifConsentDialog = false }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
fun AboutLimitationsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            SectionCard(title = "RakshakAI — Your Intelligent Digital Guardian") {
                Text(
                    text = "Privacy-first predictive scam-defence app using deterministic, rule-based offline intelligence.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.limitations_title),
                containerColor = RakshakColors.CautionSoft,
                borderColor = RakshakColors.CautionAmber
            ) {
                Text(
                    text = stringResource(R.string.limitations_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionCard(title = "Safety & Accuracy Note") {
                Text(
                    text = stringResource(R.string.result_footer_disclaimer),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
