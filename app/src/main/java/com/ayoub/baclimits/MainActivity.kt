package com.ayoub.baclimits

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ayoub.baclimits.ui.BacLimitsViewModel
import com.ayoub.baclimits.ui.MainTab
import com.ayoub.baclimits.ui.components.DifficultyPickerSheet
import com.ayoub.baclimits.ui.components.FullscreenFormulaDialog
import com.ayoub.baclimits.ui.components.LoadSeedDialog
import com.ayoub.baclimits.ui.components.SolutionSheet
import com.ayoub.baclimits.ui.components.SyncStatusSheet
import com.ayoub.baclimits.ui.components.TechniquePickerSheet
import com.ayoub.baclimits.ui.components.UpdaterSheet
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.screens.HistoryScreen
import com.ayoub.baclimits.ui.screens.LoginScreen
import com.ayoub.baclimits.ui.screens.PracticeScreen
import com.ayoub.baclimits.ui.screens.ProgressScreen
import com.ayoub.baclimits.ui.screens.SettingsScreen
import com.ayoub.baclimits.ui.theme.BacLimitsTheme
import com.ayoub.baclimits.ui.theme.OledColors

class MainActivity : ComponentActivity() {

    private val viewModel: BacLimitsViewModel by viewModels()

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            viewModel.onInstallPermissionGranted()
        } else {
            Toast.makeText(this, "Install permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupOledSystemBars()

        setContent {
            BacLimitsTheme {
                val pendingInstall by viewModel.pendingInstallFile.collectAsState()
                LaunchedEffect(pendingInstall) {
                    if (pendingInstall != null) {
                        viewModel.consumePendingInstallRequest()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Toast.makeText(
                                this@MainActivity,
                                "Allow 2BAC Limits to install app updates",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            installPermissionLauncher.launch(intent)
                        }
                    }
                }

                BacLimitsRootApp(viewModel = viewModel)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupOledSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}

@Composable
private fun BacLimitsRootApp(viewModel: BacLimitsViewModel) {
    val ui by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val strings = remember(ui.lang) { AppStrings.forLang(ui.lang) }
    val layoutDirection = if (ui.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ui.snackbarMessage) {
        val msg = ui.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        if (ui.username == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                LoginScreen(
                    strings = strings,
                    syncState = syncState,
                    recentUsers = ui.recentUsers,
                    isLoggingIn = ui.isLoggingIn,
                    loginError = ui.loginError,
                    onLogin = { name -> viewModel.login(name) },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onOpenSyncSheet = { viewModel.setShowSyncSheet(true) }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(12.dp)
                )
            }
        } else {
            BackHandler(enabled = ui.selectedTab != MainTab.Practice) {
                viewModel.selectTab(MainTab.Practice)
            }

            Scaffold(
                containerColor = OledColors.Background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    NativeTopBar(
                        strings = strings,
                        syncState = syncState,
                        hasUpdate = updateState.hasUpdate,
                        onOpenUpdater = { viewModel.setShowUpdaterSheet(true) },
                        onOpenSyncSheet = {
                            viewModel.syncManager.triggerSyncNow(forceHealth = true)
                            viewModel.setShowSyncSheet(true)
                        },
                        onToggleLanguage = { viewModel.toggleLanguage() }
                    )
                },
                bottomBar = {
                    NativeBottomNav(
                        selectedTab = ui.selectedTab,
                        strings = strings,
                        hasUpdate = updateState.hasUpdate,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = ui.selectedTab,
                        label = "main_tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            MainTab.Practice -> PracticeScreen(
                                ui = ui,
                                strings = strings,
                                onOpenTechniqueSheet = { viewModel.setShowTechniqueSheet(true) },
                                onOpenDifficultySheet = { viewModel.setShowDifficultySheet(true) },
                                onNewExercise = { viewModel.loadNextExercise(seed = null) },
                                onOpenSeedDialog = { viewModel.setShowSeedDialog(true) },
                                onOpenFullscreenFormula = { viewModel.openFullscreenFormula(it) },
                                onRequestHint = { viewModel.requestNextHint() },
                                onOpenSolution = { viewModel.openSolutionSheet() },
                                onAnswerChange = { viewModel.updateAnswerField(it) },
                                onInsertToken = { tok, back -> viewModel.insertMathToken(tok, back) },
                                onBackspace = { viewModel.backspaceMathInput() },
                                onClear = { viewModel.clearMathInput() },
                                onSubmitAnswer = { viewModel.submitAnswer() },
                                onToggleSystemKeyboard = { viewModel.toggleSystemKeyboard() },
                                onSeedCopied = { msg -> viewModel.showSnackbar(msg) }
                            )

                            MainTab.Progress -> ProgressScreen(
                                displayName = ui.displayName,
                                username = ui.username ?: "",
                                stats = ui.stats,
                                strings = strings,
                                onRefresh = { viewModel.refreshStatsSilently() },
                                onPracticeTechnique = { tech -> viewModel.selectTechniqueAndLoad(tech) }
                            )

                            MainTab.History -> HistoryScreen(
                                history = ui.stats?.recentHistory.orEmpty(),
                                filter = ui.historyFilter,
                                strings = strings,
                                onSelectFilter = { viewModel.setHistoryFilter(it) },
                                onOpenSeed = { seed -> viewModel.loadNextExercise(seed = seed) }
                            )

                            MainTab.Settings -> SettingsScreen(
                                displayName = ui.displayName,
                                username = ui.username ?: "",
                                lang = ui.lang,
                                syncState = syncState,
                                currentServerToken = viewModel.syncManager.getSyncToken(),
                                isSyncingAccount = ui.isSyncingAccount,
                                updateState = updateState,
                                strings = strings,
                                onSwitchLanguage = { target ->
                                    if (ui.lang != target) viewModel.toggleLanguage()
                                },
                                onLogout = { viewModel.logout() },
                                onSaveServerConfig = { u, t -> viewModel.saveServerConfig(u, t) },
                                onResetDefaultServer = { viewModel.resetDefaultServer() },
                                onSyncAndReload = { viewModel.syncAndReloadAccount() },
                                onOpenUpdaterSheet = { viewModel.setShowUpdaterSheet(true) }
                            )
                        }
                    }
                }
            }
        }

        // Modal Sheets & Dialogs
        if (ui.showTechniqueSheet) {
            TechniquePickerSheet(
                selectedTechnique = ui.selectedTechnique,
                strings = strings,
                onSelect = { viewModel.selectTechniqueAndLoad(it) },
                onDismiss = { viewModel.setShowTechniqueSheet(false) }
            )
        }

        if (ui.showDifficultySheet) {
            DifficultyPickerSheet(
                selectedDifficulty = ui.selectedDifficulty,
                strings = strings,
                onSelect = { viewModel.selectDifficultyAndLoad(it) },
                onDismiss = { viewModel.setShowDifficultySheet(false) }
            )
        }

        if (ui.showSeedDialog) {
            LoadSeedDialog(
                strings = strings,
                onConfirmSeed = { seed -> viewModel.loadNextExercise(seed = seed) },
                onDismiss = { viewModel.setShowSeedDialog(false) }
            )
        }

        if (ui.showSolutionSheet) {
            SolutionSheet(
                lang = ui.lang,
                strings = strings,
                hints = ui.hintsShown,
                solution = ui.solution,
                isLoading = ui.isLoadingSolution,
                onDismiss = { viewModel.setShowSolutionSheet(false) }
            )
        }

        if (ui.showSyncSheet) {
            SyncStatusSheet(
                syncState = syncState,
                currentToken = viewModel.syncManager.getSyncToken(),
                strings = strings,
                onSaveAndTest = { u, t -> viewModel.saveServerConfig(u, t) },
                onResetDefault = { viewModel.resetDefaultServer() },
                onDismiss = { viewModel.setShowSyncSheet(false) }
            )
        }

        if (ui.showUpdaterSheet) {
            UpdaterSheet(
                updateState = updateState,
                currentPat = viewModel.updaterManager.getGitHubPat(),
                strings = strings,
                onCheckUpdates = { viewModel.updaterManager.checkLatestRelease(silent = false) },
                onDownloadAndInstall = { viewModel.updaterManager.downloadAndInstall() },
                onSavePat = { pat ->
                    viewModel.updaterManager.saveGitHubPat(pat)
                    viewModel.updaterManager.checkLatestRelease(silent = false)
                },
                onDismiss = { viewModel.setShowUpdaterSheet(false) }
            )
        }

        val fullLatex = ui.fullscreenFormulaLatex
        if (!fullLatex.isNullOrBlank()) {
            FullscreenFormulaDialog(
                latex = fullLatex,
                seed = ui.exercise?.seed,
                strings = strings,
                onDismiss = { viewModel.closeFullscreenFormula() }
            )
        }
    }
}

@Composable
private fun NativeTopBar(
    strings: AppStrings,
    syncState: com.ayoub.baclimits.data.SyncStatusSnapshot,
    hasUpdate: Boolean,
    onOpenUpdater: () -> Unit,
    onOpenSyncSheet: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val dotColor = remember(syncState.colorHex) {
        try {
            Color(AndroidColor.parseColor(syncState.colorHex))
        } catch (_: Exception) {
            OledColors.Primary
        }
    }

    Surface(
        color = OledColors.Background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenUpdater() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (hasUpdate) OledColors.PrimarySoft else OledColors.SurfaceElevated,
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasUpdate) OledColors.Primary else OledColors.Border
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasUpdate) Color(0xFF93C5FD) else OledColors.TextSecondary
                            )
                            if (hasUpdate) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OledColors.Primary)
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = OledColors.Surface,
                        border = BorderStroke(1.dp, OledColors.Border),
                        modifier = Modifier.clickable { onOpenSyncSheet() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = syncState.label
                                    .replace("●", "")
                                    .replace("⚠", "")
                                    .replace("↻", "")
                                    .trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = dotColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = OledColors.SurfaceElevated,
                        border = BorderStroke(1.dp, OledColors.BorderStrong),
                        modifier = Modifier.clickable { onToggleLanguage() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = OledColors.Primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.switchLangShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = OledColors.TextPrimary
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = Color(0xFF15151D))
        }
    }
}

@Composable
private fun NativeBottomNav(
    selectedTab: MainTab,
    strings: AppStrings,
    hasUpdate: Boolean,
    onSelectTab: (MainTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF161620))
        NavigationBar(
            containerColor = OledColors.NavBar,
            contentColor = OledColors.TextPrimary,
            tonalElevation = 0.dp
        ) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF93C5FD),
                selectedTextColor = Color(0xFF93C5FD),
                indicatorColor = OledColors.PrimarySoft,
                unselectedIconColor = OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary
            )

            NavigationBarItem(
                selected = selectedTab == MainTab.Practice,
                onClick = { onSelectTab(MainTab.Practice) },
                icon = { Icon(Icons.Rounded.Functions, contentDescription = strings.tabPractice) },
                label = { Text(strings.tabPractice, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                colors = itemColors
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.Progress,
                onClick = { onSelectTab(MainTab.Progress) },
                icon = { Icon(Icons.Rounded.Insights, contentDescription = strings.tabProgress) },
                label = { Text(strings.tabProgress, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                colors = itemColors
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.History,
                onClick = { onSelectTab(MainTab.History) },
                icon = { Icon(Icons.Rounded.History, contentDescription = strings.tabHistory) },
                label = { Text(strings.tabHistory, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                colors = itemColors
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.Settings,
                onClick = { onSelectTab(MainTab.Settings) },
                icon = {
                    if (hasUpdate) {
                        BadgedBox(badge = { Badge(containerColor = OledColors.Primary) }) {
                            Icon(Icons.Rounded.Settings, contentDescription = strings.tabSettings)
                        }
                    } else {
                        Icon(Icons.Rounded.Settings, contentDescription = strings.tabSettings)
                    }
                },
                label = { Text(strings.tabSettings, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                colors = itemColors
            )
        }
    }
}
