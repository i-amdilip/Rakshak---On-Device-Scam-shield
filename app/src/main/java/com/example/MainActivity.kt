package com.example

import android.Manifest
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.firebase.CloudUiState
import com.example.data.firebase.authStateFlow
import com.example.domain.models.Source
import com.example.integrations.notifications.NotificationsHelper
import com.example.integrations.sms.SmsInboxScanner
import com.example.presentation.CloudSyncViewModel
import com.example.presentation.RakshakViewModel
import com.example.presentation.components.MainBottomTab
import com.example.presentation.components.RakshakBottomNavBar
import com.example.presentation.components.RakshakSubTopBar
import com.example.presentation.components.RakshakTopBar
import com.example.presentation.navigation.AppRoute
import com.example.presentation.screens.AboutLimitationsScreen
import com.example.presentation.screens.AuthScreen
import com.example.presentation.screens.ConversationTimelineScreen
import com.example.presentation.screens.EmergencyAssistantScreen
import com.example.presentation.screens.FamilyGuardianScreen
import com.example.presentation.screens.HomeDashboardScreen
import com.example.presentation.screens.IsolationBreakerScreen
import com.example.presentation.screens.MoreHubScreen
import com.example.presentation.screens.OnboardingScreen
import com.example.presentation.screens.PreActionCheckScreen
import com.example.presentation.screens.PrivacyCentreScreen
import com.example.presentation.screens.ProfileScreen
import com.example.presentation.screens.ProtectHubScreen
import com.example.presentation.screens.ResultScreen
import com.example.presentation.screens.SafetyCentreScreen
import com.example.presentation.screens.ScanHubScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.SimulationLabScreen
import com.example.presentation.screens.SplashScreen
import com.example.presentation.screens.VaultTabScreen
import com.example.presentation.theme.RakshakColors
import com.example.presentation.theme.RakshakTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import java.util.Locale

class MainActivity : FragmentActivity() {

    private var activeViewModel: RakshakViewModel? = null
    private var pendingIntentToDispatch: Intent? = null
    private var hadReadSmsRationaleBefore: Boolean = false
    private var pendingSmsResultCallback: ((readGranted: Boolean, receiveGranted: Boolean, promptSuppressed: Boolean) -> Unit)? = null

    private val smsPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readGranted = results[Manifest.permission.READ_SMS] == true ||
            SmsInboxScanner.hasReadSmsPermission(this)
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true ||
            SmsInboxScanner.hasReceiveSmsPermission(this)
        val canShowRationaleNow = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.READ_SMS
        )
        val promptSuppressed = !readGranted && !hadReadSmsRationaleBefore && !canShowRationaleNow
        pendingSmsResultCallback?.invoke(readGranted, receiveGranted, promptSuppressed)
        pendingSmsResultCallback = null
    }

    fun requestSmsPermissionsDirect(
        onResult: (readGranted: Boolean, receiveGranted: Boolean, promptSuppressed: Boolean) -> Unit
    ) {
        if (SmsInboxScanner.hasReadSmsPermission(this)) {
            onResult(true, SmsInboxScanner.hasReceiveSmsPermission(this), false)
            return
        }
        pendingSmsResultCallback = onResult
        hadReadSmsRationaleBefore = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.READ_SMS
        )
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !SmsInboxScanner.hasPostNotificationsPermission(this@MainActivity)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        smsPermissionsLauncher.launch(perms)
    }

    fun openAppPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    override fun onResume() {
        super.onResume()
        activeViewModel?.refreshAudits()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntentToDispatch = intent

        val app = application as KavachApplication

        setContent {
            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            val user = currentUser
            if (user == null) {
                activeViewModel = null
                RakshakTheme {
                    AuthScreen(
                        onAuthSuccess = { /* AuthStateFlow updates automatically */ }
                    )
                }
            } else {
                key(user.uid) {
                    val vm: RakshakViewModel = viewModel(
                        key = user.uid,
                        factory = RakshakViewModel.Factory(app)
                    )
                    val cloudVm: CloudSyncViewModel = viewModel(
                        key = user.uid,
                        factory = CloudSyncViewModel.factory(user.uid)
                    )
                    activeViewModel = vm
                    val uiState by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(vm, pendingIntentToDispatch) {
                        pendingIntentToDispatch?.let {
                            dispatchExternalIntent(it, vm)
                            pendingIntentToDispatch = null
                        }
                    }

                    LaunchedEffect(user.uid) {
                        cloudVm.ensureProfileInitialized(
                            displayName = user.displayName ?: "RakshakAI Guardian",
                            email = user.email,
                            appLanguage = uiState.settings.appLanguage,
                            elderlyMode = uiState.settings.elderlyMode,
                            notificationThreshold = uiState.settings.notificationThreshold,
                            photoUrl = user.photoUrl?.toString()
                        )
                    }

                    var lastSyncedScanId by remember(user.uid) { mutableStateOf<String?>(null) }
                    LaunchedEffect(uiState.latestScanResult?.scanId) {
                        val res = uiState.latestScanResult ?: return@LaunchedEffect
                        if (
                            res.scanId != lastSyncedScanId &&
                            uiState.settings.historyEnabled &&
                            res.detectorAvailable &&
                            (res.sourceText.isNotBlank() || res.urlResults.isNotEmpty())
                        ) {
                            lastSyncedScanId = res.scanId
                            cloudVm.syncScanSummary(
                                scanId = res.scanId,
                                source = res.source.name,
                                riskScore = res.riskScore,
                                riskLevel = res.riskLevel.name,
                                uncertain = res.uncertain,
                                categoryName = res.scamCategory ?: "General Check",
                                shortExplanation = res.explanation.take(240)
                            )
                        }
                    }

                    // Real-time sync for all Scan History summaries (Conversation, URL, QR, SMS auto-scan)
                    val syncedHistoryIds = remember(user.uid) { mutableSetOf<String>() }
                    LaunchedEffect(uiState.history) {
                        if (uiState.settings.historyEnabled) {
                            uiState.history.take(25).forEach { h ->
                                if (h.scanId != lastSyncedScanId && syncedHistoryIds.add(h.scanId)) {
                                    cloudVm.syncScanSummary(
                                        scanId = h.scanId,
                                        source = h.source,
                                        riskScore = h.riskScore,
                                        riskLevel = h.riskLevel,
                                        uncertain = h.uncertain,
                                        categoryName = h.categoryName,
                                        shortExplanation = h.shortExplanation,
                                        feedbackOption = h.feedbackOption
                                    )
                                }
                            }
                        }
                    }

                    // Real-time sync for Evidence Vault entries
                    var seenVaultIds by remember(user.uid) { mutableStateOf<Set<String>>(emptySet()) }
                    val syncedVaultHashes = remember(user.uid) { mutableMapOf<String, String>() }
                    LaunchedEffect(uiState.vaultItems) {
                        val currentIds = uiState.vaultItems.map { it.id }.toSet()
                        (seenVaultIds - currentIds).forEach { removedId ->
                            syncedVaultHashes.remove(removedId)
                            cloudVm.deleteCloudUserEntry("vault_$removedId")
                        }
                        seenVaultIds = currentIds
                        uiState.vaultItems.take(30).forEach { v ->
                            val fp = "${v.riskScore}|${v.riskLevel}|${v.categoryName}|${v.stagesCsv}"
                            if (syncedVaultHashes[v.id] != fp) {
                                syncedVaultHashes[v.id] = fp
                                cloudVm.syncUserEntry(
                                    entryId = "vault_${v.id}",
                                    entryType = "VAULT",
                                    title = "Vault: ${v.categoryName}",
                                    category = v.categoryName,
                                    sourceLabel = v.itemType,
                                    riskScore = v.riskScore,
                                    riskLevel = v.riskLevel,
                                    summary = "Encrypted on device · Stages: ${v.stagesCsv.ifBlank { "Recorded" }}",
                                    reviewed = true
                                )
                            }
                        }
                    }

                    // Real-time sync for Quarantine entries
                    var seenQuarantineIds by remember(user.uid) { mutableStateOf<Set<String>>(emptySet()) }
                    val syncedQuarantineHashes = remember(user.uid) { mutableMapOf<String, String>() }
                    LaunchedEffect(uiState.quarantineItems) {
                        val currentIds = uiState.quarantineItems.map { it.id }.toSet()
                        (seenQuarantineIds - currentIds).forEach { removedId ->
                            syncedQuarantineHashes.remove(removedId)
                            cloudVm.deleteCloudUserEntry("quar_$removedId")
                        }
                        seenQuarantineIds = currentIds
                        uiState.quarantineItems.take(30).forEach { q ->
                            val fp = "${q.riskScore}|${q.riskLevel}|${q.categoryName}|${q.previewSummary}"
                            if (syncedQuarantineHashes[q.id] != fp) {
                                syncedQuarantineHashes[q.id] = fp
                                cloudVm.syncUserEntry(
                                    entryId = "quar_${q.id}",
                                    entryType = "QUARANTINE",
                                    title = "Quarantined: ${q.categoryName}",
                                    category = q.categoryName,
                                    sourceLabel = "QUARANTINE",
                                    riskScore = q.riskScore,
                                    riskLevel = q.riskLevel,
                                    summary = q.previewSummary,
                                    reviewed = true
                                )
                            }
                        }
                    }

                    // Real-time sync for Collected SMS & Message Scans
                    var seenCollectedIds by remember(user.uid) { mutableStateOf<Set<String>>(emptySet()) }
                    val syncedCollectedHashes = remember(user.uid) { mutableMapOf<String, String>() }
                    LaunchedEffect(uiState.collectedMessages) {
                        val currentIds = uiState.collectedMessages.map { it.id }.toSet()
                        (seenCollectedIds - currentIds).forEach { removedId ->
                            syncedCollectedHashes.remove(removedId)
                            cloudVm.deleteCloudUserEntry("msg_$removedId")
                        }
                        seenCollectedIds = currentIds
                        uiState.collectedMessages.take(40).forEach { msg ->
                            val fp = "${msg.riskScore}|${msg.isReviewed}|${msg.categoryName}"
                            if (syncedCollectedHashes[msg.id] != fp) {
                                syncedCollectedHashes[msg.id] = fp
                                cloudVm.syncUserEntry(
                                    entryId = "msg_${msg.id}",
                                    entryType = "COLLECTED_MESSAGE",
                                    title = "From ${msg.sender} (${msg.sourceApp})",
                                    category = msg.categoryName,
                                    sourceLabel = msg.sourceApp,
                                    riskScore = msg.riskScore,
                                    riskLevel = msg.riskLevel,
                                    summary = msg.shortExplanation.ifBlank { msg.snippetPreview },
                                    reviewed = msg.isReviewed
                                )
                            }
                        }
                    }

                    // Real-time sync for Trusted Allowlist entries & Profile domain list
                    var seenAllowlistKeys by remember(user.uid) { mutableStateOf<Set<String>>(emptySet()) }
                    val syncedAllowHashes = remember(user.uid) { mutableMapOf<String, String>() }
                    var allowlistSyncInitialized by remember(user.uid) { mutableStateOf(false) }
                    LaunchedEffect(uiState.allowlist) {
                        val currentKeys = uiState.allowlist.map { it.domainOrSender }.toSet()
                        (seenAllowlistKeys - currentKeys).forEach { removedKey ->
                            syncedAllowHashes.remove(removedKey)
                            cloudVm.deleteCloudUserEntry("allow_$removedKey")
                        }
                        seenAllowlistKeys = currentKeys
                        uiState.allowlist.take(30).forEach { entry ->
                            if (syncedAllowHashes[entry.domainOrSender] != entry.note) {
                                syncedAllowHashes[entry.domainOrSender] = entry.note
                                cloudVm.syncUserEntry(
                                    entryId = "allow_${entry.domainOrSender}",
                                    entryType = "ALLOWLIST",
                                    title = "Trusted: ${entry.domainOrSender}",
                                    category = "Trusted Allowlist",
                                    sourceLabel = "ALLOWLIST",
                                    riskScore = 0,
                                    riskLevel = "LOW",
                                    summary = entry.note,
                                    reviewed = true
                                )
                            }
                        }
                        if (!allowlistSyncInitialized) {
                            allowlistSyncInitialized = true
                        } else {
                            cloudVm.syncProfileLists(
                                trustedDomains = uiState.allowlist.map { it.domainOrSender }
                            )
                        }
                    }

                    // Real-time sync for Pre-Action Safety Checks
                    var lastPreActionKey by remember(user.uid) { mutableStateOf<String?>(null) }
                    LaunchedEffect(uiState.preActionDecision) {
                        val dec = uiState.preActionDecision ?: return@LaunchedEffect
                        val actionKey = "${dec.verdict.name}|${dec.combinedRiskScore}|${dec.headline}"
                        if (actionKey != lastPreActionKey) {
                            lastPreActionKey = actionKey
                            val level = when {
                                dec.combinedRiskScore >= 75 -> "CRITICAL"
                                dec.combinedRiskScore >= 55 -> "HIGH"
                                dec.combinedRiskScore >= 30 -> "MEDIUM"
                                else -> "LOW"
                            }
                            cloudVm.syncUserEntry(
                                entryId = "preact_${System.currentTimeMillis()}",
                                entryType = "PRE_ACTION",
                                title = dec.headline,
                                category = "Pre-Action Safety Check",
                                sourceLabel = dec.verdict.name,
                                riskScore = dec.combinedRiskScore,
                                riskLevel = level,
                                summary = dec.reasons.joinToString(" · ").ifBlank {
                                    dec.verificationSteps.firstOrNull().orEmpty()
                                },
                                reviewed = true
                            )
                        }
                    }

                    // Sync Bookmarked Safety Tips when toggled
                    var lastSyncedTips by remember(user.uid) { mutableStateOf<Set<String>?>(null) }
                    LaunchedEffect(uiState.settings.bookmarkedTips) {
                        val tips = uiState.settings.bookmarkedTips
                        if (lastSyncedTips == null) {
                            lastSyncedTips = tips
                        } else if (lastSyncedTips != tips) {
                            lastSyncedTips = tips
                            cloudVm.syncProfileLists(bookmarkedTips = tips.toList())
                        }
                    }

                    var initialPrefsHandled by remember(user.uid) { mutableStateOf(false) }
                    LaunchedEffect(
                        uiState.settings.appLanguage,
                        uiState.settings.elderlyMode,
                        uiState.settings.notificationThreshold
                    ) {
                        if (!initialPrefsHandled) {
                            initialPrefsHandled = true
                        } else {
                            cloudVm.syncPreferences(
                                appLanguage = uiState.settings.appLanguage,
                                elderlyMode = uiState.settings.elderlyMode,
                                notificationThreshold = uiState.settings.notificationThreshold
                            )
                        }
                    }

                    LocalizedContextWrapper(languageCode = uiState.settings.appLanguage) {
                        RakshakTheme(
                            elderlyMode = uiState.settings.elderlyMode,
                            reducedMotion = uiState.settings.reducedMotion
                        ) {
                            RakshakAppShell(
                                viewModel = vm,
                                cloudSyncViewModel = cloudVm,
                                currentUser = user,
                                onRequestSmsPermissions = ::requestSmsPermissionsDirect,
                                onOpenAppSettings = ::openAppPermissionSettings,
                                onUpdateFlagSecure = { secure ->
                                    if (secure) {
                                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                    } else {
                                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val vm = activeViewModel
        if (vm != null) {
            dispatchExternalIntent(intent, vm)
        } else {
            pendingIntentToDispatch = intent
        }
    }

    private fun dispatchExternalIntent(intent: Intent?, vm: RakshakViewModel) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type.orEmpty()
                if (type.startsWith("text/")) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    if (sharedText.isNotBlank()) {
                        vm.handleIncomingSharedText(sharedText)
                    }
                    intent.action = null
                } else if (type.startsWith("image/")) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) {
                        vm.handleIncomingSharedImage(uri)
                    }
                    intent.action = null
                }
            }
        }

        val notifSource = intent.getStringExtra(NotificationsHelper.EXTRA_NAV_SCAN_SOURCE)
        val notifText = intent.getStringExtra(NotificationsHelper.EXTRA_NAV_SCAN_RESULT_TEXT)
        if (notifSource == "INBOX_ALERT") {
            intent.removeExtra(NotificationsHelper.EXTRA_NAV_SCAN_SOURCE)
            vm.openInboxScanner()
        } else if (!notifText.isNullOrBlank()) {
            intent.removeExtra(NotificationsHelper.EXTRA_NAV_SCAN_RESULT_TEXT)
            intent.removeExtra(NotificationsHelper.EXTRA_NAV_SCAN_SOURCE)
            vm.handleIncomingSharedText(notifText)
        }
    }
}

@Composable
private fun LocalizedContextWrapper(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val registryOwner = LocalActivityResultRegistryOwner.current
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val locale = remember(languageCode) { Locale.forLanguageTag(languageCode) }
    val localizedResources = remember(languageCode, baseConfig) {
        val config = Configuration(baseConfig)
        config.setLocale(locale)
        baseContext.createConfigurationContext(config).resources
    }
    val wrappedContext = remember(baseContext, localizedResources) {
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localizedResources
            override fun getAssets(): AssetManager = localizedResources.assets
        }
    }
    val providers = buildList {
        add(LocalContext provides wrappedContext)
        add(LocalConfiguration provides localizedResources.configuration)
        if (registryOwner != null) {
            add(LocalActivityResultRegistryOwner provides registryOwner)
        }
        if (backDispatcherOwner != null) {
            add(LocalOnBackPressedDispatcherOwner provides backDispatcherOwner)
        }
    }.toTypedArray()
    CompositionLocalProvider(*providers) {
        content()
    }
}

@Composable
private fun RakshakAppShell(
    viewModel: RakshakViewModel,
    cloudSyncViewModel: CloudSyncViewModel,
    currentUser: FirebaseUser,
    onRequestSmsPermissions: ((readGranted: Boolean, receiveGranted: Boolean, promptSuppressed: Boolean) -> Unit) -> Unit,
    onOpenAppSettings: () -> Unit,
    onUpdateFlagSecure: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudScansState by cloudSyncViewModel.scansState.collectAsStateWithLifecycle()
    val cloudSyncMessage by cloudSyncViewModel.syncMessage.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    var showSmsSettingsFallbackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(cloudSyncMessage) {
        cloudSyncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            cloudSyncViewModel.clearSyncMessage()
        }
    }

    LaunchedEffect(uiState.pendingNavigationEvent) {
        when (uiState.pendingNavigationEvent) {
            "SCAN_MESSAGE_AUTO" -> {
                viewModel.consumePendingNav()
                viewModel.scanMessage(source = Source.SHARED) {
                    navController.navigate(AppRoute.Result) { launchSingleTop = true }
                }
            }
            "SCAN_SCREENSHOT" -> {
                viewModel.consumePendingNav()
                navController.navigate(AppRoute.ScanHub("SCREENSHOT")) { launchSingleTop = true }
            }
            "OPEN_INBOX_SCANNER" -> {
                viewModel.consumePendingNav()
                navController.navigate(AppRoute.ScanHub("INBOX")) { launchSingleTop = true }
            }
        }
    }

    val isSensitiveScreen = currentDest?.hasRoute<AppRoute.Result>() == true ||
        currentDest?.hasRoute<AppRoute.Vault>() == true
    val shouldSecureFlag = uiState.settings.flagSecureEnabled && isSensitiveScreen
    DisposableEffect(shouldSecureFlag) {
        onUpdateFlagSecure(shouldSecureFlag)
        onDispose { }
    }

    val isTopLevelTab = currentDest?.hasRoute<AppRoute.Home>() == true ||
        currentDest?.hasRoute<AppRoute.ScanHub>() == true ||
        currentDest?.hasRoute<AppRoute.Protect>() == true ||
        currentDest?.hasRoute<AppRoute.Vault>() == true ||
        currentDest?.hasRoute<AppRoute.More>() == true

    val activeBottomTab = when {
        currentDest?.hasRoute<AppRoute.Home>() == true -> MainBottomTab.HOME
        currentDest?.hasRoute<AppRoute.ScanHub>() == true -> MainBottomTab.SCAN
        currentDest?.hasRoute<AppRoute.Protect>() == true -> MainBottomTab.PROTECT
        currentDest?.hasRoute<AppRoute.Vault>() == true -> MainBottomTab.VAULT
        currentDest?.hasRoute<AppRoute.More>() == true -> MainBottomTab.MORE
        else -> MainBottomTab.HOME
    }

    val requestAndScanAllSms: (Boolean) -> Unit = { openInbox ->
        onRequestSmsPermissions { readGranted, receiveGranted, promptSuppressed ->
            viewModel.refreshAudits()
            if (readGranted || receiveGranted) {
                viewModel.setSmsAutoScan(true)
            }
            if (readGranted) {
                viewModel.scanAllSmsInbox(showAlertNotification = true)
                if (openInbox) {
                    navController.navigate(AppRoute.ScanHub("INBOX")) {
                        popUpTo(AppRoute.Home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            } else if (promptSuppressed) {
                showSmsSettingsFallbackDialog = true
            } else {
                viewModel.showToast("Please allow SMS permission to read all messages and track incoming scams.")
            }
        }
    }

    val navigateToTab: (MainBottomTab) -> Unit = { tab ->
        if (!(tab == activeBottomTab && isTopLevelTab)) {
            if (tab == MainBottomTab.HOME) {
                val popped = navController.popBackStack(AppRoute.Home, inclusive = false)
                if (!popped) {
                    navController.navigate(AppRoute.Home) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                val route: AppRoute = when (tab) {
                    MainBottomTab.HOME -> AppRoute.Home
                    MainBottomTab.SCAN -> AppRoute.ScanHub("INBOX")
                    MainBottomTab.PROTECT -> AppRoute.Protect
                    MainBottomTab.VAULT -> AppRoute.Vault
                    MainBottomTab.MORE -> AppRoute.More
                }
                navController.navigate(route) {
                    popUpTo(AppRoute.Home) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val subScreenTitle: String? = when {
        currentDest?.hasRoute<AppRoute.Result>() == true -> "Scan Result"
        currentDest?.hasRoute<AppRoute.ConversationTimeline>() == true -> "Conversation Timeline"
        currentDest?.hasRoute<AppRoute.PreActionCheck>() == true -> "Pre-Action Safety Check"
        currentDest?.hasRoute<AppRoute.IsolationBreaker>() == true -> "Isolation Breaker"
        currentDest?.hasRoute<AppRoute.EmergencyAssistant>() == true -> "Emergency Assistant"
        currentDest?.hasRoute<AppRoute.FamilyGuardian>() == true -> "Family Guardian"
        currentDest?.hasRoute<AppRoute.SimulationLab>() == true -> "Simulation Lab (Demo)"
        currentDest?.hasRoute<AppRoute.PrivacyCentre>() == true -> "Privacy Centre"
        currentDest?.hasRoute<AppRoute.Profile>() == true -> "My Profile"
        currentDest?.hasRoute<AppRoute.SafetyCentre>() == true -> "Safety Centre"
        currentDest?.hasRoute<AppRoute.Settings>() == true -> "Settings"
        currentDest?.hasRoute<AppRoute.AboutLimitations>() == true -> "About & Limitations"
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RakshakColors.CanvasLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            when {
                isTopLevelTab -> {
                    val subtitle = when (activeBottomTab) {
                        MainBottomTab.HOME -> "Digital Guardian"
                        MainBottomTab.SCAN -> "Scam Scanners"
                        MainBottomTab.PROTECT -> "Active Defence"
                        MainBottomTab.VAULT -> "Encrypted Vault"
                        MainBottomTab.MORE -> "Preferences"
                    }
                    RakshakTopBar(
                        subtitle = subtitle,
                        elderlyMode = uiState.settings.elderlyMode,
                        currentLanguage = uiState.settings.appLanguage,
                        onToggleLanguage = {
                            val nextLang = if (uiState.settings.appLanguage.startsWith("hi")) "en" else "hi"
                            viewModel.setLanguage(nextLang)
                        }
                    )
                }
                subScreenTitle != null -> {
                    RakshakSubTopBar(
                        title = subScreenTitle,
                        onBackClick = { navController.popBackStack() },
                        onHomeClick = { navigateToTab(MainBottomTab.HOME) }
                    )
                }
            }
        },
        bottomBar = {
            if (isTopLevelTab) {
                RakshakBottomNavBar(
                    selectedTab = activeBottomTab,
                    onSelectTab = navigateToTab
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Splash,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable<AppRoute.Splash> {
                    SplashScreen(
                        onFinished = {
                            val dest = if (uiState.settings.onboardingCompleted) AppRoute.Home else AppRoute.Onboarding
                            navController.navigate(dest) {
                                popUpTo(AppRoute.Splash) { inclusive = true }
                            }
                        }
                    )
                }

                composable<AppRoute.Onboarding> {
                    OnboardingScreen(
                        selectedLanguage = uiState.settings.appLanguage,
                        smsAutoScanEnabled = uiState.settings.smsAutoScanEnabled,
                        onSelectLanguage = viewModel::setLanguage,
                        onToggleSmsAutoScan = { enabled ->
                            viewModel.setSmsAutoScan(enabled)
                            if (enabled) {
                                viewModel.refreshAudits()
                                viewModel.scanAllSmsInbox(showAlertNotification = true)
                            }
                        },
                        onFinish = {
                            viewModel.completeOnboarding()
                            navController.navigate(AppRoute.Home) {
                                popUpTo(AppRoute.Onboarding) { inclusive = true }
                            }
                        }
                    )
                }

                composable<AppRoute.Home> {
                    val syncedCount = (cloudScansState as? CloudUiState.Success)?.data?.size
                    HomeDashboardScreen(
                        uiState = uiState,
                        userDisplayName = currentUser.displayName ?: currentUser.email ?: "Guardian",
                        syncedScanCount = syncedCount,
                        onOpenScanTab = { tab ->
                            navController.navigate(AppRoute.ScanHub(tab)) {
                                popUpTo(AppRoute.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onRequestAndScanSms = { requestAndScanAllSms(true) },
                        onOpenEmergency = { navController.navigate(AppRoute.EmergencyAssistant) },
                        onOpenSafetyCentre = { navController.navigate(AppRoute.SafetyCentre) },
                        onOpenPrivacyCentre = { navController.navigate(AppRoute.PrivacyCentre) },
                        onOpenProfile = { navController.navigate(AppRoute.Profile) },
                        onOpenPreActionCheck = { navController.navigate(AppRoute.PreActionCheck()) },
                        onOpenSimulationLab = { navController.navigate(AppRoute.SimulationLab) },
                        onOpenVaultHistory = { navigateToTab(MainBottomTab.VAULT) },
                        onToggleElderlyMode = {
                            val next = !uiState.settings.elderlyMode
                            viewModel.setElderlyMode(next)
                            viewModel.showToast(
                                if (next) "Senior Protection Mode ON (larger text, simpler warnings)."
                                else "Senior Protection Mode turned off."
                            )
                        }
                    )
                }

                composable<AppRoute.ScanHub> { entry ->
                    val route = entry.toRoute<AppRoute.ScanHub>()
                    ScanHubScreen(
                        initialTab = route.initialTab,
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestSmsScan = { requestAndScanAllSms(false) },
                        onNavigateToResult = { navController.navigate(AppRoute.Result) },
                        onNavigateToConversationTimeline = { navController.navigate(AppRoute.ConversationTimeline) },
                        onNavigateToSimulationLab = { navController.navigate(AppRoute.SimulationLab) }
                    )
                }

                composable<AppRoute.Protect> {
                    ProtectHubScreen(
                        uiState = uiState,
                        onOpenPreActionCheck = { navController.navigate(AppRoute.PreActionCheck()) },
                        onOpenEmergencyAssistant = { navController.navigate(AppRoute.EmergencyAssistant) },
                        onOpenIsolationBreaker = { navController.navigate(AppRoute.IsolationBreaker) },
                        onOpenFamilyGuardian = { navController.navigate(AppRoute.FamilyGuardian) },
                        onOpenSimulationLab = { navController.navigate(AppRoute.SimulationLab) }
                    )
                }

                composable<AppRoute.Vault> {
                    VaultTabScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        cloudSyncViewModel = cloudSyncViewModel,
                        onRestoredToScanner = {
                            navController.navigate(AppRoute.ScanHub("MESSAGE")) { launchSingleTop = true }
                        }
                    )
                }

                composable<AppRoute.More> {
                    MoreHubScreen(
                        onOpenProfile = { navController.navigate(AppRoute.Profile) },
                        onOpenPrivacyCentre = { navController.navigate(AppRoute.PrivacyCentre) },
                        onOpenSafetyCentre = { navController.navigate(AppRoute.SafetyCentre) },
                        onOpenSettings = { navController.navigate(AppRoute.Settings) },
                        onOpenAbout = { navController.navigate(AppRoute.AboutLimitations) }
                    )
                }

                composable<AppRoute.Result> {
                    ResultScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        cloudSyncViewModel = cloudSyncViewModel,
                        onOpenPreActionCheck = { navController.navigate(AppRoute.PreActionCheck()) },
                        onOpenIsolationBreaker = { navController.navigate(AppRoute.IsolationBreaker) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<AppRoute.ConversationTimeline> {
                    ConversationTimelineScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenIsolationBreaker = { navController.navigate(AppRoute.IsolationBreaker) },
                        onOpenPreActionCheck = { navController.navigate(AppRoute.PreActionCheck()) },
                        onOpenFullResult = { navController.navigate(AppRoute.Result) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<AppRoute.PreActionCheck> {
                    PreActionCheckScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onDone = { navController.popBackStack() }
                    )
                }

                composable<AppRoute.IsolationBreaker> {
                    IsolationBreakerScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenPreActionCheck = { navController.navigate(AppRoute.PreActionCheck()) },
                        onAddContact = { navController.navigate(AppRoute.FamilyGuardian) }
                    )
                }

                composable<AppRoute.EmergencyAssistant> {
                    EmergencyAssistantScreen(
                        viewModel = viewModel,
                        onSyncEmergencyEntry = { title, summary ->
                            cloudSyncViewModel.syncUserEntry(
                                entryId = "emerg_${System.currentTimeMillis()}",
                                entryType = "EMERGENCY_NOTE",
                                title = title,
                                category = "Emergency Assistant",
                                sourceLabel = "EMERGENCY",
                                riskScore = 80,
                                riskLevel = "HIGH",
                                summary = summary,
                                reviewed = true
                            )
                        }
                    )
                }

                composable<AppRoute.FamilyGuardian> {
                    FamilyGuardianScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        cloudSyncViewModel = cloudSyncViewModel
                    )
                }

                composable<AppRoute.SimulationLab> {
                    SimulationLabScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRunSampleToResult = { navController.navigate(AppRoute.Result) },
                        onRunConversationToTimeline = { navController.navigate(AppRoute.ConversationTimeline) }
                    )
                }

                composable<AppRoute.PrivacyCentre> {
                    PrivacyCentreScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        cloudSyncViewModel = cloudSyncViewModel,
                        userDisplayName = currentUser.displayName ?: "RakshakAI Guardian",
                        userEmail = currentUser.email
                    )
                }

                composable<AppRoute.Profile> {
                    ProfileScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        cloudSyncViewModel = cloudSyncViewModel,
                        userDisplayName = currentUser.displayName ?: "RakshakAI Guardian",
                        userEmail = currentUser.email,
                        userPhotoUrl = currentUser.photoUrl?.toString(),
                        userUid = currentUser.uid,
                        onOpenPrivacyCentre = { navController.navigate(AppRoute.PrivacyCentre) },
                        onOpenSettings = { navController.navigate(AppRoute.Settings) }
                    )
                }

                composable<AppRoute.SafetyCentre> {
                    SafetyCentreScreen(
                        bookmarkedTipIds = uiState.settings.bookmarkedTips,
                        onToggleBookmark = viewModel::toggleBookmarkTip
                    )
                }

                composable<AppRoute.Settings> {
                    SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestSmsScan = { requestAndScanAllSms(false) }
                    )
                }

                composable<AppRoute.AboutLimitations> {
                    AboutLimitationsScreen()
                }
            }
        }
    }

    if (showSmsSettingsFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showSmsSettingsFallbackDialog = false },
            title = { Text("Enable SMS Permission in Settings") },
            text = {
                Text(
                    "Android did not show the SMS permission pop-up (either previously denied or restricted for sideloaded apps).\n\n" +
                        "Tap Open App Settings -> Permissions -> SMS -> Allow. On Android 13+, if SMS is greyed out, tap the ⋮ menu in App Info -> Allow restricted settings first."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSmsSettingsFallbackDialog = false
                    onOpenAppSettings()
                }) {
                    Text("Open App Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsSettingsFallbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
