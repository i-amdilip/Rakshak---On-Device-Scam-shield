package com.example.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.detection.DemoSamples
import com.example.domain.models.ChatSender
import com.example.domain.models.RiskLevel
import com.example.domain.models.Source
import com.example.integrations.notifications.MessageNotificationListenerService
import com.example.integrations.share.ShareIntentsHelper
import com.example.integrations.sms.SmsInboxScanner
import com.example.integrations.voice.LiveVoiceScamMonitor
import com.example.presentation.RakshakUiState
import com.example.presentation.RakshakViewModel
import com.example.presentation.components.SectionCard
import com.example.presentation.components.SimulatedDemoBadge
import com.example.presentation.components.riskColor
import com.example.presentation.components.riskSoftContainer
import com.example.presentation.theme.LocalRakshakMetrics
import com.example.presentation.theme.RakshakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ScanModeTab(val label: String) {
    INBOX("All SMS & Msgs"),
    MESSAGE("Message"),
    CONVERSATION("Conversation"),
    LINK("Link"),
    SCREENSHOT("Screenshot"),
    CALL("Voice & Call")
}

@Composable
fun ScanHubScreen(
    initialTab: String,
    uiState: RakshakUiState,
    viewModel: RakshakViewModel,
    onRequestSmsScan: (() -> Unit)? = null,
    onNavigateToResult: () -> Unit,
    onNavigateToConversationTimeline: () -> Unit,
    onNavigateToSimulationLab: () -> Unit
) {
    var activeTab by remember {
        mutableStateOf(
            runCatching { ScanModeTab.valueOf(initialTab) }.getOrDefault(ScanModeTab.INBOX)
        )
    }
    LaunchedEffect(initialTab) {
        runCatching { ScanModeTab.valueOf(initialTab) }.getOrNull()?.let { activeTab = it }
    }

    val context = LocalContext.current
    val metrics = LocalRakshakMetrics.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var inboxFilter by remember { mutableStateOf(InboxMessageFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(InboxMessageSort.NEWEST) }
    var showNotifConsentDialog by remember { mutableStateOf(false) }
    var decryptedBodies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(uiState.collectedMessages) {
        decryptedBodies = withContext(Dispatchers.Default) {
            uiState.collectedMessages.associate { msg ->
                msg.id to viewModel.getDecryptedMessageBody(msg)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAudits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeTab, uiState.permissionAudit?.smsReadGranted) {
        if (
            activeTab == ScanModeTab.INBOX &&
            uiState.permissionAudit?.smsReadGranted == true &&
            !uiState.isScanningInbox &&
            uiState.collectedMessages.isEmpty()
        ) {
            viewModel.scanAllSmsInbox(showAlertNotification = false, quietIfNoNew = true)
        }
    }

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readGranted = results[Manifest.permission.READ_SMS] == true ||
            SmsInboxScanner.hasReadSmsPermission(context)
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true ||
            SmsInboxScanner.hasReceiveSmsPermission(context)
        viewModel.refreshAudits()
        if (receiveGranted || readGranted) {
            viewModel.setSmsAutoScan(true)
        }
        if (readGranted) {
            viewModel.scanAllSmsInbox(showAlertNotification = true)
        } else {
            viewModel.showToast("SMS permission denied. You can still use Paste, Share-to-Scan, or Notification Shield.")
        }
    }

    val launchSmsPermissions: () -> Unit = {
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        smsPermissionsLauncher.launch(perms)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal mode selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScanModeTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("scan_tab_${tab.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                ScanModeTab.INBOX -> {
                    inboxMessagesScannerItems(
                        uiState = uiState,
                        viewModel = viewModel,
                        filter = inboxFilter,
                        onFilterChange = { inboxFilter = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        sortOrder = sortOrder,
                        onSortOrderChange = { sortOrder = it },
                        decryptedBodies = decryptedBodies,
                        onRequestScanAllSms = {
                            if (onRequestSmsScan != null) {
                                onRequestSmsScan()
                            } else if (SmsInboxScanner.hasReadSmsPermission(context)) {
                                viewModel.scanAllSmsInbox(showAlertNotification = true)
                            } else {
                                launchSmsPermissions()
                            }
                        },
                        onOpenNotificationAccessSettings = {
                            val isEnabled = uiState.permissionAudit?.notificationListenerEnabled == true ||
                                MessageNotificationListenerService.isNotificationListenerEnabled(context)
                            if (isEnabled) {
                                runCatching {
                                    context.startActivity(
                                        MessageNotificationListenerService.createNotificationListenerSettingsIntent(context)
                                    )
                                }.onFailure {
                                    viewModel.showToast("Open Android Settings -> Notifications -> Notification Access to manage RakshakAI.")
                                }
                            } else {
                                showNotifConsentDialog = true
                            }
                        },
                        onInspectMessage = { entity ->
                            viewModel.inspectCollectedMessage(entity) {
                                onNavigateToResult()
                            }
                        }
                    )
                }

                ScanModeTab.MESSAGE -> {
                    item {
                        SectionCard(title = "Message Scanner") {
                            OutlinedTextField(
                                value = uiState.messageInput,
                                onValueChange = viewModel::updateMessageInput,
                                label = { Text("Paste SMS or chat message") },
                                placeholder = { Text("e.g. Dear customer, your SBI KYC...") },
                                minLines = 5,
                                maxLines = 10,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("message_input_field")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = ShareIntentsHelper.readFromClipboard(context)
                                        if (clip.isNotBlank()) viewModel.updateMessageInput(clip)
                                        else viewModel.showToast("Clipboard is empty.")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = metrics.minTouchTarget)
                                ) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste")
                                }
                                OutlinedButton(
                                    onClick = onNavigateToSimulationLab,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = metrics.minTouchTarget)
                                ) {
                                    Text("Load Demo")
                                }
                                if (uiState.messageInput.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { viewModel.updateMessageInput("") },
                                        modifier = Modifier.heightIn(min = metrics.minTouchTarget)
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Language Hint selector
                            Text("Language override:", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "auto" to "Auto Detect",
                                    "en" to "English",
                                    "hi" to "Hindi",
                                    "hinglish" to "Hinglish",
                                    "mr" to "Marathi",
                                    "bn" to "Bengali",
                                    "ta" to "Tamil",
                                    "te" to "Telugu",
                                    "gu" to "Gujarati",
                                    "es" to "Spanish",
                                    "fr" to "French"
                                ).forEach { (code, label) ->
                                    FilterChip(
                                        selected = uiState.languageHint == code,
                                        onClick = { viewModel.updateLanguageHint(code) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.scanMessage(source = Source.MANUAL) {
                                        onNavigateToResult()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = maxOf(metrics.minTouchTarget, 52.dp))
                                    .testTag("scan_message_submit_button")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze Message Offline")
                            }
                        }
                    }
                }

                ScanModeTab.CONVERSATION -> {
                    item {
                        var addSender by remember { mutableStateOf(ChatSender.THEM) }
                        var addTurnText by remember { mutableStateOf("") }

                        SectionCard(title = "Conversation Scanner") {
                            OutlinedTextField(
                                value = uiState.conversationRawInput,
                                onValueChange = viewModel::updateConversationRawInput,
                                label = { Text("Paste chat transcript") },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversation_raw_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = viewModel::splitConversationFromRaw,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Auto-Split")
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateConversationRawInput(DemoSamples.digitalArrestConversationText)
                                        viewModel.splitConversationFromRaw()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Digital Arrest Demo")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Or add a chat turn manually:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = addSender == ChatSender.THEM,
                                    onClick = { addSender = ChatSender.THEM },
                                    label = { Text("Sender: Them") }
                                )
                                FilterChip(
                                    selected = addSender == ChatSender.ME,
                                    onClick = { addSender = ChatSender.ME },
                                    label = { Text("Sender: Me") }
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = addTurnText,
                                    onValueChange = { addTurnText = it },
                                    label = { Text("Next turn text") },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.addConversationTurn(addSender, addTurnText)
                                        addTurnText = ""
                                    },
                                    modifier = Modifier.heightIn(min = 52.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add message turn")
                                }
                            }
                        }
                    }

                    if (uiState.conversationMessages.isNotEmpty()) {
                        items(uiState.conversationMessages, key = { it.id }) { msg ->
                            Surface(
                                color = if (msg.sender == ChatSender.THEM) RakshakColors.SurfaceMuted else RakshakColors.TealSoft,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = listOfNotNull(msg.senderLabel, msg.timestampLabel).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(text = msg.text, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { viewModel.deleteConversationTurn(msg.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete turn")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = viewModel::clearConversation,
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text("Clear")
                            }
                            Button(
                                onClick = {
                                    viewModel.analyzeConversation {
                                        onNavigateToConversationTimeline()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("analyze_conversation_button")
                            ) {
                                Text("Analyze Timeline & Next Move")
                            }
                        }
                    }
                }

                ScanModeTab.LINK -> {
                    item {
                        val brands = listOf(
                            null to "Auto / None",
                            "sbi" to "SBI",
                            "hdfc" to "HDFC",
                            "icici" to "ICICI",
                            "paytm" to "Paytm",
                            "amazon" to "Amazon",
                            "indiapost" to "India Post",
                            "uidai" to "UIDAI"
                        )
                        SectionCard(title = "Link Scanner") {
                            OutlinedTextField(
                                value = uiState.linkInput,
                                onValueChange = viewModel::updateLinkInput,
                                label = { Text("Paste URL") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("link_input_field")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Optional: Claimed brand in message", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                brands.forEach { (id, label) ->
                                    FilterChip(
                                        selected = uiState.linkClaimedBrandId == id,
                                        onClick = { viewModel.updateLinkClaimedBrand(id) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.scanLink { onNavigateToResult() } },
                                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("scan_link_button")
                            ) {
                                Text("Inspect Link Safely")
                            }
                        }
                    }
                    item {
                        SectionCard(title = "Try Link Demos") {
                            DemoSamples.linkDemos.forEach { sample ->
                                TextButton(
                                    onClick = { viewModel.updateLinkInput(sample.payload) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${sample.title} [${sample.expectedBadge}]", modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                ScanModeTab.SCREENSHOT -> {
                    item {
                        val photoPicker = rememberLauncherForActivityResult(
                            ActivityResultContracts.PickVisualMedia()
                        ) { uri ->
                            if (uri != null) viewModel.inspectScreenshotUri(uri)
                        }

                        SectionCard(title = "Screenshot OCR") {
                            Button(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.TealPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Screenshot (English / Hindi)")
                            }

                            if (uiState.isProcessingOcr) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Running offline OCR & QR detection...")
                                }
                            }

                            if (uiState.ocrEmptyWarning) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "OCR could not extract readable text from that image. You can type or paste the text below.",
                                    color = RakshakColors.CautionAmber,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (uiState.ocrQrPayloads.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("QR code found inside screenshot:", style = MaterialTheme.typography.labelLarge)
                                uiState.ocrQrPayloads.forEach { qr ->
                                    OutlinedButton(
                                        onClick = { viewModel.scanQrPayload(qr) { onNavigateToResult() } },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Inspect QR: ${qr.take(40)}")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = uiState.ocrExtractedText,
                                onValueChange = viewModel::updateOcrText,
                                label = { Text("Extracted text (editable)") },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.scanMessage(
                                        textOverride = uiState.ocrExtractedText,
                                        source = Source.SCREENSHOT
                                    ) { onNavigateToResult() }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                Text("Analyze Extracted Text")
                            }
                        }
                    }
                }

                ScanModeTab.CALL -> {
                    item {
                        val micPermLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                viewModel.startVoiceScamListening()
                            } else {
                                viewModel.showToast("Microphone permission denied. You can still paste a call transcript below.")
                            }
                        }

                        SectionCard(title = "Voice & Call Scam Shield") {
                            Text(
                                text = "Put suspicious calls on speakerphone and listen live, or paste a call transcript to detect impersonation & social engineering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "en-IN" to "English (IN)",
                                    "hi-IN" to "Hindi",
                                    "mr-IN" to "Marathi",
                                    "bn-IN" to "Bengali",
                                    "ta-IN" to "Tamil",
                                    "te-IN" to "Telugu"
                                ).forEach { (tag, label) ->
                                    FilterChip(
                                        selected = uiState.voiceLocaleTag == tag,
                                        onClick = { viewModel.setVoiceLocaleTag(tag) },
                                        enabled = !uiState.isListeningVoice,
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (uiState.isListeningVoice) {
                                        viewModel.stopVoiceScamListening()
                                    } else if (LiveVoiceScamMonitor.hasRecordAudioPermission(context)) {
                                        viewModel.startVoiceScamListening()
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isListeningVoice) RakshakColors.DangerRed else RakshakColors.TealPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = maxOf(metrics.minTouchTarget, 52.dp))
                                    .testTag("voice_live_listen_btn")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isListeningVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (uiState.isListeningVoice) "Stop Live Voice Listening"
                                    else "Start Live Voice Shield (Mic)"
                                )
                            }

                            if (uiState.isListeningVoice) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { uiState.voiceRmsLevel.coerceIn(0.08f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (uiState.voicePartialText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = RakshakColors.TealSoft,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Hearing: \"${uiState.voicePartialText}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RakshakColors.DeepNavy,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            uiState.voiceImpersonationReport?.let { report ->
                                Spacer(modifier = Modifier.height(12.dp))
                                val badgeColor = riskColor(report.riskLevel)
                                val bgColor = riskSoftContainer(report.riskLevel)
                                Surface(
                                    color = bgColor,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("voice_realtime_warning_card")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = null,
                                                tint = badgeColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Live Risk ${report.riskScore}/100 · ${report.riskLevel.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = viewModel::speakVoiceWarningAloud,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("voice_speak_warning_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Speak warning aloud",
                                                    tint = badgeColor
                                                )
                                            }
                                        }
                                        Text(
                                            text = report.contextualHeadline,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = RakshakColors.DeepNavy,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = report.immediateAction,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RakshakColors.InkPrimary
                                        )
                                        if (report.matchedVoiceCues.isNotEmpty() || report.recognizedTactics.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val cuesLabel = if (report.matchedVoiceCues.isNotEmpty()) {
                                                "Cues: ${report.matchedVoiceCues.joinToString(", ")}"
                                            } else ""
                                            val tacticsLabel = if (report.recognizedTactics.isNotEmpty()) {
                                                "Tactics: ${report.recognizedTactics.joinToString(", ") { it.uppercase() }}"
                                            } else ""
                                            Text(
                                                text = listOf(cuesLabel, tacticsLabel).filter { it.isNotEmpty() }.joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.callTranscriptInput,
                                onValueChange = viewModel::updateCallTranscriptInput,
                                label = { Text("Live / Pasted Call Transcript") },
                                placeholder = { Text("Speak live above or paste what the caller said...") },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_transcript_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = viewModel::runScriptedDemoCall,
                                    enabled = !uiState.isCallDemoRunning,
                                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (uiState.isCallDemoRunning) "Playing..." else "Scripted Demo")
                                }
                                Button(
                                    onClick = { viewModel.analyzeCallTranscript { onNavigateToResult() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 50.dp)
                                        .testTag("analyze_voice_transcript_btn")
                                ) {
                                    Text("Full Forensic Report")
                                }
                            }
                            if (uiState.isCallDemoRunning || uiState.callDemoRevealedLines.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SimulatedDemoBadge(text = "SIMULATED CALL DEMO")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
                    viewModel.showToast("Open Android Settings -> Notifications -> Notification Access to enable RakshakAI.")
                }
            },
            onDismiss = {
                showNotifConsentDialog = false
            }
        )
    }
}
