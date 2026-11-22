package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.CollectedMessageScanEntity
import com.example.domain.models.RiskLevel
import com.example.integrations.sms.RawDeviceMessage
import com.example.presentation.RakshakUiState
import com.example.presentation.RakshakViewModel
import com.example.presentation.components.SectionCard
import com.example.presentation.components.riskColor
import com.example.presentation.components.riskIcon
import com.example.presentation.components.riskSoftContainer
import com.example.presentation.theme.LocalRakshakMetrics
import com.example.presentation.theme.RakshakColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InboxMessageFilter(val labelEn: String, val labelHi: String) {
    ALL("All", "सभी"),
    SMS_INBOX("Inbox SMS", "इनबॉक्स SMS"),
    SMS_SENT("Sent SMS", "भेजे गए SMS"),
    MESSAGING_APPS("Chat Apps", "चैट ऐप्स"),
    RISKY("Risky", "जोखिम"),
    UNREVIEWED("Alerts", "अलर्ट"),
    SAFE("Safe", "सुरक्षित")
}

enum class InboxMessageSort(val labelEn: String, val labelHi: String) {
    NEWEST("Newest", "नवीनतम"),
    RISK_FIRST("Risk First", "जोखिम पहले")
}

@Composable
fun NotificationAccessConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = RakshakColors.TealPrimary
            )
        },
        title = { Text("Enable Notification Access?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "With your consent, RakshakAI reads incoming messaging notifications from WhatsApp, Telegram, Instagram, Signal, and SMS apps.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Displays messages in your organized dashboard with sender, app, date, and time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Scans locally on your device for scam links and fraud tactics. Raw messages are encrypted in Android Keystore and never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• You can turn off Notification Access anytime in Android Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                modifier = Modifier.testTag("confirm_notif_consent_btn")
            ) {
                Text("I Consent · Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("decline_notif_consent_btn")
            ) {
                Text("Decline")
            }
        }
    )
}

fun LazyListScope.inboxMessagesScannerItems(
    uiState: RakshakUiState,
    viewModel: RakshakViewModel,
    filter: InboxMessageFilter,
    onFilterChange: (InboxMessageFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: InboxMessageSort,
    onSortOrderChange: (InboxMessageSort) -> Unit,
    decryptedBodies: Map<String, String>,
    onRequestScanAllSms: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onInspectMessage: (CollectedMessageScanEntity) -> Unit
) {
    val isHindi = uiState.settings.appLanguage.startsWith("hi")
    val audit = uiState.permissionAudit
    val smsReadGranted = audit?.smsReadGranted ?: false
    val smsReceiveGranted = audit?.smsReceiveGranted ?: false
    val notifListenerOn = audit?.notificationListenerEnabled ?: false

    val allMessages = uiState.collectedMessages
    val inboxSmsMessages = allMessages.filter {
        it.sourceChannel == RawDeviceMessage.CHANNEL_SMS_INBOX ||
            it.sourceChannel == RawDeviceMessage.CHANNEL_SMS_LIVE
    }
    val sentSmsMessages = allMessages.filter {
        it.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT
    }
    val chatAppMessages = allMessages.filter {
        it.sourceChannel == RawDeviceMessage.CHANNEL_MESSENGER_NOTIF
    }
    val riskyMessages = allMessages.filter { it.isRisky }
    val unreviewedRisky = riskyMessages.filter { !it.isReviewed }
    val safeMessages = allMessages.filter { !it.isRisky }

    val channelFiltered = when (filter) {
        InboxMessageFilter.ALL -> allMessages
        InboxMessageFilter.SMS_INBOX -> inboxSmsMessages
        InboxMessageFilter.SMS_SENT -> sentSmsMessages
        InboxMessageFilter.MESSAGING_APPS -> chatAppMessages
        InboxMessageFilter.RISKY -> riskyMessages
        InboxMessageFilter.UNREVIEWED -> unreviewedRisky
        InboxMessageFilter.SAFE -> safeMessages
    }

    val cleanQuery = searchQuery.trim().lowercase()
    val searchedList = if (cleanQuery.isEmpty()) {
        channelFiltered
    } else {
        channelFiltered.filter { item ->
            val fullText = decryptedBodies[item.id] ?: item.snippetPreview
            item.sender.lowercase().contains(cleanQuery) ||
                item.sourceApp.lowercase().contains(cleanQuery) ||
                item.categoryName.lowercase().contains(cleanQuery) ||
                item.snippetPreview.lowercase().contains(cleanQuery) ||
                fullText.lowercase().contains(cleanQuery)
        }
    }

    val filteredList = when (sortOrder) {
        InboxMessageSort.NEWEST -> searchedList.sortedByDescending { it.receivedAt }
        InboxMessageSort.RISK_FIRST -> searchedList.sortedWith(
            compareByDescending<CollectedMessageScanEntity> { it.isRisky }
                .thenByDescending { it.riskScore }
                .thenByDescending { it.receivedAt }
        )
    }

    // 1. Minimal Risk Alert Strip
    if (unreviewedRisky.isNotEmpty()) {
        val criticalCount = unreviewedRisky.count { it.riskLevel == RiskLevel.CRITICAL.name }
        val highCount = unreviewedRisky.count { it.riskLevel == RiskLevel.HIGH.name }
        item(key = "inbox_risk_banner") {
            SectionCard(
                containerColor = RakshakColors.DangerSoft,
                borderColor = RakshakColors.DangerRed.copy(alpha = 0.6f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Risky messages detected",
                        tint = RakshakColors.DangerRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "${unreviewedRisky.size} जोखिम भरे संदेश" else "${unreviewedRisky.size} Risky Alert(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RakshakColors.CriticalDarkRed
                        )
                        Text(
                            text = "Critical: $criticalCount · High: $highCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = RakshakColors.InkPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = { onFilterChange(InboxMessageFilter.UNREVIEWED) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (isHindi) "फ़िल्टर" else "Show")
                    }
                }
            }
        }
    }

    // 2. Unified Messages Dashboard Controls Card
    item(key = "inbox_scanner_controls") {
        val metrics = LocalRakshakMetrics.current
        SectionCard(
            title = if (isHindi) "सभी SMS और चैट संदेश" else "Messages Dashboard"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadgeChip(
                    label = if (smsReadGranted) "SMS Inbox & Sent Ready" else "SMS Permission Off",
                    active = smsReadGranted
                )
                StatusBadgeChip(
                    label = when {
                        smsReceiveGranted && uiState.settings.smsAutoScanEnabled -> "Live SMS ON"
                        smsReceiveGranted -> "Live SMS Paused"
                        else -> "Live SMS Off"
                    },
                    active = smsReceiveGranted && uiState.settings.smsAutoScanEnabled
                )
                StatusBadgeChip(
                    label = if (notifListenerOn) "Chat Apps Shield ON" else "Chat Apps Off",
                    active = notifListenerOn
                )
            }

            if (allMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardCountTile(
                        label = "Inbox",
                        count = inboxSmsMessages.size,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardCountTile(
                        label = "Sent",
                        count = sentSmsMessages.size,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardCountTile(
                        label = "Chat Apps",
                        count = chatAppMessages.size,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardCountTile(
                        label = "Risky",
                        count = riskyMessages.size,
                        highlightDanger = riskyMessages.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRequestScanAllSms,
                enabled = !uiState.isScanningInbox,
                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxOf(metrics.minTouchTarget, 50.dp))
                    .testTag("scan_all_sms_button")
            ) {
                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isScanningInbox) {
                        if (isHindi) "स्कैन हो रहा है..." else "Reading & Scanning SMS..."
                    } else if (smsReadGranted) {
                        if (isHindi) "सभी SMS (इनबॉक्स और भेजे गए) पढ़ें" else "Refresh Inbox & Sent SMS"
                    } else {
                        if (isHindi) "अनुमति दें और सभी SMS पढ़ें" else "Allow & Read All SMS"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (uiState.isScanningInbox) {
                Spacer(modifier = Modifier.height(10.dp))
                val (done, total) = uiState.inboxScanProgress
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$done / $total SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = RakshakColors.DeepNavy
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Messenger Notification Access Row (WhatsApp, Telegram, Instagram & Chat Apps)
            Surface(
                color = if (notifListenerOn) RakshakColors.SafeSoft else RakshakColors.SurfaceMuted,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (notifListenerOn) RakshakColors.SafeGreen.copy(alpha = 0.5f) else RakshakColors.OutlineSoft
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = if (notifListenerOn) RakshakColors.SafeGreen else RakshakColors.DeepNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notifListenerOn) "WhatsApp, Telegram & Instagram Active" else "WhatsApp, Telegram & Instagram",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = RakshakColors.DeepNavy
                        )
                        Text(
                            text = if (notifListenerOn) "Capturing incoming chat notifications locally" else "Grant notification access with your consent",
                            style = MaterialTheme.typography.labelSmall,
                            color = RakshakColors.InkSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenNotificationAccessSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("enable_notif_listener_btn")
                    ) {
                        Text(if (notifListenerOn) "Manage" else "Allow")
                    }
                }
            }

            if (allMessages.isNotEmpty() || uiState.settings.lastInboxScanTimestamp > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                val lastStr = if (uiState.settings.lastInboxScanTimestamp > 0L) {
                    "Last sync: " + SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault())
                        .format(Date(uiState.settings.lastInboxScanTimestamp))
                } else {
                    "Live monitoring"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${allMessages.size} Collected · ${riskyMessages.size} Risky · ${safeMessages.size} Safe",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RakshakColors.DeepNavy
                        )
                        Text(
                            text = lastStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = RakshakColors.InkSecondary
                        )
                    }
                    if (allMessages.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearCollectedMessages,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear collected messages",
                                tint = RakshakColors.InkSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // 3. Search, Category Filter Chips & Sort Control
    if (allMessages.isNotEmpty()) {
        item(key = "inbox_search_and_sort") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search sender, app or text...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inbox_search_input")
                    )
                    OutlinedButton(
                        onClick = {
                            onSortOrderChange(
                                if (sortOrder == InboxMessageSort.NEWEST) InboxMessageSort.RISK_FIRST
                                else InboxMessageSort.NEWEST
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("inbox_sort_toggle")
                    ) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Toggle sort order", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isHindi) sortOrder.labelHi else sortOrder.labelEn)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InboxMessageFilter.entries.forEach { option ->
                        val count = when (option) {
                            InboxMessageFilter.ALL -> allMessages.size
                            InboxMessageFilter.SMS_INBOX -> inboxSmsMessages.size
                            InboxMessageFilter.SMS_SENT -> sentSmsMessages.size
                            InboxMessageFilter.MESSAGING_APPS -> chatAppMessages.size
                            InboxMessageFilter.RISKY -> riskyMessages.size
                            InboxMessageFilter.UNREVIEWED -> unreviewedRisky.size
                            InboxMessageFilter.SAFE -> safeMessages.size
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { onFilterChange(option) },
                            label = {
                                val title = if (isHindi) option.labelHi else option.labelEn
                                Text("$title ($count)")
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("inbox_filter_${option.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }

    // 4. Empty State or Organized Collected Message Cards
    if (filteredList.isEmpty()) {
        item(key = "inbox_empty_state") {
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (allMessages.isEmpty()) Icons.Filled.Sms else Icons.Filled.Shield,
                        contentDescription = null,
                        tint = RakshakColors.TealPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                allMessages.isEmpty() -> if (isHindi) "अभी तक कोई संदेश नहीं मिला" else "No Messages Collected Yet"
                                cleanQuery.isNotEmpty() -> if (isHindi) "खोज से कोई संदेश नहीं मिला" else "No Matching Messages"
                                else -> if (isHindi) "इस फ़िल्टर में कोई संदेश नहीं" else "No Messages in This Category"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = RakshakColors.DeepNavy
                        )
                        Text(
                            text = when {
                                allMessages.isEmpty() -> "Tap 'Allow & Read All SMS' for Inbox & Sent SMS or enable Notification Access for WhatsApp, Telegram & Instagram."
                                cleanQuery.isNotEmpty() -> "Try clearing search text or switching filters."
                                else -> "Switch to 'All' to view messages from other sources."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RakshakColors.InkSecondary
                        )
                    }
                }
            }
        }
    } else {
        items(filteredList, key = { it.id }) { item ->
            val resolvedBody = decryptedBodies[item.id] ?: item.snippetPreview
            CollectedMessageCard(
                item = item,
                decryptedBody = resolvedBody,
                onInspect = { onInspectMessage(item) },
                onToggleReviewed = { viewModel.markCollectedMessageReviewed(item.id, !item.isReviewed) },
                onDelete = { viewModel.deleteCollectedMessage(item.id) }
            )
        }
    }
}

@Composable
private fun DashboardCountTile(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    highlightDanger: Boolean = false
) {
    Surface(
        color = if (highlightDanger) RakshakColors.DangerSoft else RakshakColors.SurfaceMuted,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (highlightDanger) RakshakColors.DangerRed.copy(alpha = 0.45f) else RakshakColors.OutlineSoft
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlightDanger) RakshakColors.CriticalDarkRed else RakshakColors.DeepNavy
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RakshakColors.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusBadgeChip(label: String, active: Boolean) {
    Surface(
        color = if (active) RakshakColors.SafeSoft else RakshakColors.SurfaceMuted,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (active) RakshakColors.SafeGreen.copy(alpha = 0.45f) else RakshakColors.OutlineSoft
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.Security,
                contentDescription = null,
                tint = if (active) RakshakColors.SafeGreen else RakshakColors.InkSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) RakshakColors.SafeGreen else RakshakColors.InkPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CollectedMessageCard(
    item: CollectedMessageScanEntity,
    decryptedBody: String,
    onInspect: () -> Unit,
    onToggleReviewed: () -> Unit,
    onDelete: () -> Unit
) {
    val level = runCatching { RiskLevel.valueOf(item.riskLevel) }.getOrDefault(RiskLevel.LOW)
    val badgeColor = riskColor(level, item.uncertain)
    val badgeBg = riskSoftContainer(level, item.uncertain)
    val isSent = item.sourceChannel == RawDeviceMessage.CHANNEL_SMS_SENT

    val dateStr = remember(item.receivedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.receivedAt))
    }
    val timeStr = remember(item.receivedAt) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.receivedAt))
    }
    var expanded by remember(item.id) { mutableStateOf(false) }

    Card(
        onClick = onInspect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRisky && !item.isReviewed) {
                badgeBg.copy(alpha = 0.25f)
            } else {
                RakshakColors.SurfaceWhite
            }
        ),
        border = BorderStroke(
            width = if (item.isRisky && !item.isReviewed) 1.5.dp else 1.dp,
            color = if (item.isRisky && !item.isReviewed) badgeColor.copy(alpha = 0.7f) else RakshakColors.OutlineSoft
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("collected_msg_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = riskIcon(level, item.uncertain),
                            contentDescription = level.name,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${item.riskScore}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isSent) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Sent",
                        tint = RakshakColors.TealPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isSent) "To: ${item.sender}" else item.sender,
                    style = MaterialTheme.typography.titleSmall,
                    color = RakshakColors.DeepNavy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = RakshakColors.SurfaceMuted,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RakshakColors.OutlineSoft)
                ) {
                    Text(
                        text = item.sourceApp,
                        style = MaterialTheme.typography.labelSmall,
                        color = RakshakColors.DeepNavy,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = RakshakColors.InkSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$dateStr · $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = RakshakColors.InkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = decryptedBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            )

            if (decryptedBody.length > 110) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (expanded) "Show less" else "Read full message")
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Inspect")
                }
                if (item.isRisky) {
                    TextButton(
                        onClick = onToggleReviewed,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isReviewed) Icons.Filled.DoneAll else Icons.Filled.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (item.isReviewed) "Dismissed" else "Dismiss")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete scanned record",
                        tint = RakshakColors.InkSecondary
                    )
                }
            }
        }
    }
}
