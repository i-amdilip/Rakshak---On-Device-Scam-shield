package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.database.ScanHistoryEntity
import com.example.domain.models.RiskLevel
import com.example.integrations.sms.SmsInboxScanner
import com.example.presentation.RakshakUiState
import com.example.presentation.components.SectionCard
import com.example.presentation.components.riskColor
import com.example.presentation.components.riskIcon
import com.example.presentation.components.riskSoftContainer
import com.example.presentation.theme.LocalRakshakMetrics
import com.example.presentation.theme.RakshakColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    uiState: RakshakUiState,
    userDisplayName: String = "",
    syncedScanCount: Int? = null,
    onOpenScanTab: (String) -> Unit,
    onRequestAndScanSms: () -> Unit = { onOpenScanTab("INBOX") },
    onOpenEmergency: () -> Unit,
    onOpenSafetyCentre: () -> Unit,
    onOpenPrivacyCentre: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenPreActionCheck: () -> Unit,
    onOpenSimulationLab: () -> Unit,
    onOpenVaultHistory: () -> Unit,
    onToggleElderlyMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val metrics = LocalRakshakMetrics.current
    val isHindi = uiState.settings.appLanguage.startsWith("hi")
    val recentThree = uiState.history.take(3)
    val smsReadGranted = uiState.permissionAudit?.smsReadGranted ?: SmsInboxScanner.hasReadSmsPermission(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "home_top_spacer") { Spacer(modifier = Modifier.height(4.dp)) }

        // Minimal Executive Status Hero Card
        item(key = "home_hero_card") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RakshakColors.DeepNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPrivacyCentre)
                    .testTag("home_status_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = RakshakColors.TealPrimary.copy(alpha = 0.22f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = "Protected",
                                    tint = Color(0xFF5EEAD4),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_status_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.home_status_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Surface(
                            onClick = onOpenProfile,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("home_open_profile_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Profile",
                                    tint = Color(0xFF5EEAD4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isHindi) "प्रोफ़ाइल" else "Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (userDisplayName.isNotBlank() || syncedScanCount != null || uiState.installedRemoteApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (userDisplayName.isNotBlank() || syncedScanCount != null) {
                                val accountLabel = userDisplayName.ifBlank { "Guardian" }
                                val syncLabel = if (syncedScanCount != null) " · $syncedScanCount synced" else ""
                                Text(
                                    text = "$accountLabel$syncLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5EEAD4),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (uiState.installedRemoteApps.isNotEmpty()) {
                                Surface(
                                    color = RakshakColors.CautionAmber,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${uiState.installedRemoteApps.size} Remote App",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact 1-Tap SMS Inbox & Live Tracking CTA
        if (!smsReadGranted) {
            item(key = "home_sms_cta_card") {
                SectionCard(
                    containerColor = RakshakColors.TealSoft,
                    borderColor = RakshakColors.TealPrimary.copy(alpha = 0.45f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = RakshakColors.TealPrimary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Radar,
                                    contentDescription = null,
                                    tint = RakshakColors.TealDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isHindi) "सभी SMS और रियल-टाइम अलर्ट" else "SMS & Live Scam Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = RakshakColors.DeepNavy,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestAndScanSms,
                        colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("home_enable_sms_scan_btn")
                    ) {
                        Text(
                            text = if (isHindi) "अनुमति दें और सभी SMS स्कैन करें" else "Allow & Scan All SMS",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Minimal Risky SMS Alert Banner
        val unreviewedRisky = uiState.collectedMessages.filter { it.isRisky && !it.isReviewed }
        if (unreviewedRisky.isNotEmpty()) {
            val topRisky = unreviewedRisky.first()
            item(key = "home_risky_banner") {
                SectionCard(
                    containerColor = RakshakColors.DangerSoft,
                    borderColor = RakshakColors.DangerRed.copy(alpha = 0.6f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.MarkEmailUnread,
                            contentDescription = "Risky message alert",
                            tint = RakshakColors.DangerRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isHindi) "${unreviewedRisky.size} जोखिम भरे SMS मिले" else "${unreviewedRisky.size} Risky SMS Detected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RakshakColors.CriticalDarkRed
                            )
                            Text(
                                text = "${topRisky.sender} · ${topRisky.riskScore}/100",
                                style = MaterialTheme.typography.labelSmall,
                                color = RakshakColors.InkPrimary
                            )
                        }
                        Button(
                            onClick = { onOpenScanTab("INBOX") },
                            colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("home_review_risky_msgs_btn")
                        ) {
                            Text(if (isHindi) "देखें" else "Inspect")
                        }
                    }
                }
            }
        }

        // Cross-signal warning banner (concise)
        uiState.crossSignalReport.remoteAccessWarning?.let { warning ->
            item(key = "home_cross_signal_banner") {
                SectionCard(
                    containerColor = RakshakColors.DangerSoft,
                    borderColor = RakshakColors.DangerRed.copy(alpha = 0.6f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = RakshakColors.DangerRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cross-Signal Alert",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RakshakColors.CriticalDarkRed
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = RakshakColors.InkPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Emergency Button
        item(key = "home_emergency_cta") {
            Button(
                onClick = onOpenEmergency,
                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DangerRed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxOf(metrics.minTouchTarget, 52.dp))
                    .testTag("home_emergency_button")
            ) {
                Icon(Icons.Filled.Emergency, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_emergency_cta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Quick Scan 2x2 Grid — Title-focused clean tiles
        item(key = "home_quick_grid") {
            Text(
                text = stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleSmall,
                color = RakshakColors.InkSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    title = stringResource(R.string.home_action_message),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accent = RakshakColors.DeepNavy,
                    onClick = { onOpenScanTab("MESSAGE") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_message")
                )
                QuickActionTile(
                    title = stringResource(R.string.home_action_link),
                    icon = Icons.Filled.Link,
                    accent = RakshakColors.TealPrimary,
                    onClick = { onOpenScanTab("LINK") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_link")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    title = stringResource(R.string.home_action_screenshot),
                    icon = Icons.Filled.ImageSearch,
                    accent = Color(0xFF2563EB),
                    onClick = { onOpenScanTab("SCREENSHOT") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_screenshot")
                )
                QuickActionTile(
                    title = stringResource(R.string.home_action_inbox),
                    icon = Icons.Filled.Radar,
                    accent = RakshakColors.DangerRed,
                    onClick = onRequestAndScanSms,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_inbox")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    title = if (isHindi) "वॉइस शील्ड" else "Voice Shield",
                    icon = Icons.Filled.Mic,
                    accent = Color(0xFF7C3AED),
                    onClick = { onOpenScanTab("CALL") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_scan_voice")
                )
                QuickActionTile(
                    title = when {
                        isHindi && uiState.settings.elderlyMode -> "सीनियर मोड ON"
                        isHindi -> "सीनियर सुरक्षा"
                        uiState.settings.elderlyMode -> "Senior Mode ON"
                        else -> "Senior Shield"
                    },
                    icon = Icons.Filled.AccessibilityNew,
                    accent = if (uiState.settings.elderlyMode) RakshakColors.SafeGreen else RakshakColors.TealDark,
                    onClick = onToggleElderlyMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_toggle_elderly")
                )
            }
        }

        // Pre-Action Check + Simulation Lab Shortcuts
        item(key = "home_tool_pills") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPreActionCheck,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = metrics.minTouchTarget)
                        .testTag("home_pre_action_btn")
                ) {
                    Text(
                        text = if (isHindi) "Pre-Action जांच" else "Pre-Action Check",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onOpenSimulationLab,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = metrics.minTouchTarget)
                        .testTag("home_sim_lab_btn")
                ) {
                    Text(
                        text = if (isHindi) "डेमो लैब" else "Simulation Lab",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Recent Scans Header
        item(key = "home_recent_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_recent_scans),
                    style = MaterialTheme.typography.titleSmall,
                    color = RakshakColors.InkSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.history.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenVaultHistory,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (isHindi) "सभी देखें" else "View All")
                    }
                }
            }
        }

        if (recentThree.isEmpty()) {
            item(key = "home_recent_empty") {
                SectionCard {
                    Text(
                        text = if (isHindi) "अभी तक कोई जांच नहीं हुई।" else "No recent scans yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(recentThree, key = { it.scanId }) { item ->
                RecentHistorySummaryCard(item = item)
            }
        }

        // Minimal Safety Centre Row
        item(key = "home_safety_centre_card_item") {
            Card(
                onClick = onOpenSafetyCentre,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("home_safety_centre_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RakshakColors.TealSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = RakshakColors.TealDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_safety_centre),
                        style = MaterialTheme.typography.titleSmall,
                        color = RakshakColors.DeepNavy,
                        fontWeight = FontWeight.SemiBold,
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

        item(key = "home_bottom_spacer") { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = LocalRakshakMetrics.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
        border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
        modifier = modifier.heightIn(min = if (metrics.elderlyMode) 96.dp else 88.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = RakshakColors.DeepNavy,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentHistorySummaryCard(item: ScanHistoryEntity) {
    val level = runCatching { RiskLevel.valueOf(item.riskLevel) }.getOrDefault(RiskLevel.MEDIUM)
    val badgeColor = riskColor(level, item.uncertain)
    val badgeBg = riskSoftContainer(level, item.uncertain)
    val timeStr = SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(item.timestamp))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
        border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = riskIcon(level, item.uncertain),
                        contentDescription = level.name,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.riskScore}",
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.categoryName} · ${item.source}",
                    style = MaterialTheme.typography.titleSmall,
                    color = RakshakColors.DeepNavy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = RakshakColors.InkSecondary
                )
            }
        }
    }
}

