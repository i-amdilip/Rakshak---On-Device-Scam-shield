package com.example.presentation.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.GoogleSignInButton
import com.example.data.firebase.attemptAutoSignIn
import com.example.presentation.theme.RakshakColors

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Keep interactive sign-in screen visible */ },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEEF5F7),
                        RakshakColors.CanvasLight,
                        RakshakColors.CanvasLight
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Minimal Brand Lockup
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = RakshakColors.SurfaceWhite,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.img_kavach_logo),
                            contentDescription = "RakshakAI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = RakshakColors.DeepNavy
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Private Scam Shield",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RakshakColors.TealPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Minimal Title-Only Value Pillars
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RakshakColors.SurfaceWhite),
                border = BorderStroke(1.dp, RakshakColors.OutlineSoft),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    MinimalAuthRow(
                        icon = Icons.Outlined.Shield,
                        tint = RakshakColors.TealPrimary,
                        iconBg = RakshakColors.TealSoft,
                        title = "On-Device Scam Detection"
                    )
                    HorizontalDivider(color = RakshakColors.OutlineSoft)
                    MinimalAuthRow(
                        icon = Icons.Outlined.Lock,
                        tint = RakshakColors.SafeGreen,
                        iconBg = RakshakColors.SafeSoft,
                        title = "Private Summary-Only Sync"
                    )
                    HorizontalDivider(color = RakshakColors.OutlineSoft)
                    MinimalAuthRow(
                        icon = Icons.Outlined.CloudDone,
                        tint = RakshakColors.DeepNavy,
                        iconBg = RakshakColors.SurfaceMuted,
                        title = "Multi-Device Guardian Backup"
                    )
                }
            }

            // Clean Action Bottom Block
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = RakshakColors.DangerSoft,
                        border = BorderStroke(1.dp, RakshakColors.DangerRed.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_error_banner")
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = RakshakColors.CriticalDarkRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                GoogleSignInButton(
                    label = "Continue with Google",
                    onAuthSuccess = {
                        errorMessage = null
                        onAuthSuccess()
                    },
                    onAuthError = { msg ->
                        errorMessage = msg
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Raw messages never leave your device",
                    style = MaterialTheme.typography.labelSmall,
                    color = RakshakColors.InkSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MinimalAuthRow(
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = RakshakColors.InkPrimary
        )
    }
}
