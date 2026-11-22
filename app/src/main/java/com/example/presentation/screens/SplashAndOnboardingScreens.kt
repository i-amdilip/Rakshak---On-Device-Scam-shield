package com.example.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.integrations.sms.SmsInboxScanner
import com.example.presentation.theme.RakshakColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(800L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RakshakColors.DeepNavy, Color(0xFF0E1B30))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.img_kavach_logo),
                contentDescription = "RakshakAI Shield",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "RakshakAI",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Private Scam Shield",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7CE8E2),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    smsAutoScanEnabled: Boolean,
    onSelectLanguage: (String) -> Unit,
    onToggleSmsAutoScan: (Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var smsPermGranted by remember {
        mutableStateOf(
            SmsInboxScanner.hasReadSmsPermission(context) ||
                SmsInboxScanner.hasReceiveSmsPermission(context)
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.RECEIVE_SMS] == true ||
            results[Manifest.permission.READ_SMS] == true ||
            SmsInboxScanner.hasReadSmsPermission(context)
        smsPermGranted = granted
        onToggleSmsAutoScan(granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // Minimal Step Dots + Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { idx ->
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (idx == step) 22.dp else 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (idx <= step) RakshakColors.TealPrimary
                                else RakshakColors.OutlineSoft
                            )
                    )
                }
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("onboarding_skip_button")
            ) {
                Text(stringResource(R.string.onboarding_skip), color = RakshakColors.InkSecondary)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> {
                    Surface(
                        color = RakshakColors.TealSoft,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = RakshakColors.TealPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Predict Scams Early",
                        style = MaterialTheme.typography.headlineSmall,
                        color = RakshakColors.DeepNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Flags manipulation tactics and next moves offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RakshakColors.InkSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                1 -> {
                    Surface(
                        color = RakshakColors.SafeSoft,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = RakshakColors.SafeGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "On-Device Privacy",
                        style = MaterialTheme.typography.headlineSmall,
                        color = RakshakColors.DeepNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Raw messages stay encrypted on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RakshakColors.InkSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                2 -> {
                    Surface(
                        color = RakshakColors.SurfaceMuted,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = null,
                                tint = RakshakColors.DeepNavy,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.headlineSmall,
                        color = RakshakColors.DeepNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    listOf(
                        "en" to "English",
                        "hi" to "हिन्दी (Hindi)"
                    ).forEach { (code, label) ->
                        val selected = selectedLanguage == code
                        Card(
                            onClick = { onSelectLanguage(code) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) RakshakColors.TealSoft else RakshakColors.SurfaceWhite
                            ),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) RakshakColors.TealPrimary else RakshakColors.OutlineSoft
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = RakshakColors.InkPrimary
                                )
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        text = "SMS & Alert Protection",
                        style = MaterialTheme.typography.headlineSmall,
                        color = RakshakColors.DeepNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                        border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Sms, contentDescription = null, tint = RakshakColors.TealPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Scan All SMS & Live Alerts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Inbox + incoming SMS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RakshakColors.InkSecondary
                                )
                            }
                            Switch(
                                checked = smsAutoScanEnabled && smsPermGranted,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        val perms = buildList {
                                            add(Manifest.permission.READ_SMS)
                                            add(Manifest.permission.RECEIVE_SMS)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }.toTypedArray()
                                        smsPermissionLauncher.launch(perms)
                                    } else {
                                        onToggleSmsAutoScan(false)
                                    }
                                }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                            border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = RakshakColors.DeepNavy)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Safety Notifications",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                        border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = RakshakColors.SafeGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Share-to-Scan Ready", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step-- },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Text("Back")
                }
            }
            Button(
                onClick = {
                    if (step < 3) step++ else onFinish()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RakshakColors.DeepNavy),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    if (step < 3) stringResource(R.string.onboarding_next)
                    else stringResource(R.string.onboarding_finish)
                )
            }
        }
    }
}
