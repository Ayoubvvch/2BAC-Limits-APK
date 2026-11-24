package com.ayoub.baclimits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ayoub.baclimits.core.BuildConfig
import com.ayoub.baclimits.data.LocalizedMessage
import com.ayoub.baclimits.data.SolutionData
import com.ayoub.baclimits.data.SyncStatusSnapshot
import com.ayoub.baclimits.data.TechniqueMastery
import com.ayoub.baclimits.data.UpdateCheckState
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechniquePickerSheet(
    selectedChapter: String,
    selectedTechnique: String,
    techniqueMasteryMap: Map<String, TechniqueMastery> = emptyMap(),
    strings: AppStrings,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val orderedKeys = remember(selectedChapter) {
        listOf("adaptive") + LimitsCasEngine.techniquesForChapter(selectedChapter)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledColors.Surface,
        contentColor = OledColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.techniqueLabel,
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OledColors.PrimarySoft,
                    border = BorderStroke(1.dp, OledColors.Primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (selectedChapter == "sequences") strings.chapterSequences else strings.chapterLimits,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF93C5FD),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            orderedKeys.forEach { key ->
                val selected = key == selectedTechnique
                val mastery = techniqueMasteryMap[key]
                val isLocked = mastery != null && !mastery.isUnlocked && key != "adaptive"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(key) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) OledColors.PrimarySoft else OledColors.SurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        when {
                            selected -> OledColors.Primary
                            isLocked -> OledColors.Border
                            else -> OledColors.BorderStrong
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = strings.techniques[key] ?: key,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        selected -> Color(0xFF93C5FD)
                                        isLocked -> OledColors.TextSecondary
                                        else -> OledColors.TextPrimary
                                    }
                                )
                                if (isLocked) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = strings.lockedSkillTag,
                                        tint = OledColors.Amber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (mastery != null && mastery.attempts > 0) {
                                    Text(
                                        text = "${mastery.effectiveMastery.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OledColors.Teal
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val descText = if (isLocked && mastery?.unlockHint != null) {
                                mastery.unlockHint.get(strings.langCode).ifBlank {
                                    strings.techniqueDescriptions[key] ?: ""
                                }
                            } else {
                                strings.techniqueDescriptions[key] ?: ""
                            }
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLocked) OledColors.Amber else OledColors.TextSecondary
                            )
                        }
                        if (selected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = OledColors.Primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultyPickerSheet(
    selectedDifficulty: Int,
    strings: AppStrings,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val levels = listOf(0, 1, 2, 3)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledColors.Surface,
        contentColor = OledColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.difficultyLabel,
                style = MaterialTheme.typography.titleLarge
            )

            levels.forEach { lvl ->
                val selected = lvl == selectedDifficulty
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(lvl) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) OledColors.PrimarySoft else OledColors.SurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) OledColors.Primary else OledColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.difficulties[lvl] ?: lvl.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Color(0xFF93C5FD) else OledColors.TextPrimary
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = OledColors.Primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LoadSeedDialog(
    strings: AppStrings,
    onConfirmSeed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var seedInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledColors.SurfaceElevated,
        titleContentColor = OledColors.TextPrimary,
        textContentColor = OledColors.TextSecondary,
        title = {
            Text(text = strings.loadSeedTitle, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = seedInput,
                    onValueChange = { seedInput = it },
                    placeholder = { Text(strings.seedPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OledColors.Primary,
                        unfocusedBorderColor = OledColors.BorderStrong,
                        focusedTextColor = OledColors.TextPrimary,
                        unfocusedTextColor = OledColors.TextPrimary
                    )
                )
                OutlinedButton(
                    onClick = {
                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                        if (pasted.isNotEmpty()) seedInput = pasted
                    },
                    border = BorderStroke(1.dp, OledColors.BorderStrong),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.pasteFromClipboard)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = seedInput.trim()
                    if (clean.isNotEmpty()) onConfirmSeed(clean)
                },
                enabled = seedInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = OledColors.Primary)
            ) {
                Text(strings.loadSeedSubmit, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = OledColors.TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolutionSheet(
    lang: String,
    strings: AppStrings,
    hints: List<LocalizedMessage>,
    solution: SolutionData?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledColors.Surface,
        contentColor = OledColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = OledColors.Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.solutionTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.close)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OledColors.Primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(strings.loadingSolution, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                if (solution != null && solution.answerLatex.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = OledColors.GreenSoft,
                        border = BorderStroke(1.dp, OledColors.Green.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.solutionFinal,
                                style = MaterialTheme.typography.labelMedium,
                                color = OledColors.Green
                            )
                            KaTeXFormulaView(
                                latex = solution.answerLatex,
                                displayMode = true,
                                fontPx = 22f,
                                minScale = 0.70f,
                                centerWhenFits = true,
                                minHeightDp = 48
                            )
                        }
                    }
                }

                KaTeXStepsView(
                    lang = lang,
                    hints = hints,
                    steps = solution?.steps.orEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusSheet(
    syncState: SyncStatusSnapshot,
    currentToken: String,
    strings: AppStrings,
    onSaveAndTest: (url: String, token: String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember(syncState.serverUrl) { mutableStateOf(syncState.serverUrl) }
    var tokenInput by remember(currentToken) { mutableStateOf(currentToken) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledColors.Surface,
        contentColor = OledColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (syncState.isServerReachable) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = if (syncState.isServerReachable) OledColors.Green else OledColors.Amber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.serverSyncSection, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.close)
                }
            }

            SyncDiagnosticsCard(syncState = syncState, strings = strings)

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(strings.serverUrlLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledColors.Primary,
                    unfocusedBorderColor = OledColors.BorderStrong,
                    focusedTextColor = OledColors.TextPrimary,
                    unfocusedTextColor = OledColors.TextPrimary
                )
            )

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text(strings.bearerTokenLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledColors.Primary,
                    unfocusedBorderColor = OledColors.BorderStrong,
                    focusedTextColor = OledColors.TextPrimary,
                    unfocusedTextColor = OledColors.TextPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onResetDefault()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OledColors.BorderStrong)
                ) {
                    Text(strings.resetDefaultServer, maxLines = 1)
                }
                Button(
                    onClick = {
                        onSaveAndTest(urlInput, tokenInput)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OledColors.Primary)
                ) {
                    Text(strings.saveAndTestServer, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun SyncDiagnosticsCard(
    syncState: SyncStatusSnapshot,
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    val lastSyncText = remember(syncState.lastSyncAt, strings.never) {
        if (syncState.lastSyncAt <= 0L) strings.never
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(syncState.lastSyncAt))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OledColors.SurfaceElevated,
        border = BorderStroke(1.dp, OledColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiagRow(
                label = strings.deviceNetworkLabel,
                value = if (syncState.isNetworkConnected) strings.connected else strings.disconnected,
                valueColor = if (syncState.isNetworkConnected) OledColors.Green else OledColors.TextSecondary
            )
            DiagRow(
                label = strings.serverReachLabel,
                value = if (syncState.isServerReachable) strings.reachable else strings.unreachable,
                valueColor = if (syncState.isServerReachable) OledColors.Green else OledColors.Amber
            )
            DiagRow(
                label = strings.pendingQueueLabel,
                value = syncState.pendingCount.toString(),
                valueColor = if (syncState.pendingCount > 0) OledColors.Amber else OledColors.TextPrimary
            )
            DiagRow(
                label = strings.lastSyncLabel,
                value = lastSyncText,
                valueColor = OledColors.TextSecondary
            )
            if (!syncState.lastError.isNullOrBlank()) {
                DiagRow(
                    label = strings.lastErrorLabel,
                    value = syncState.lastError,
                    valueColor = OledColors.Red
                )
            }
        }
    }
}

@Composable
private fun DiagRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = OledColors.TextSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterSheet(
    updateState: UpdateCheckState,
    currentPat: String,
    strings: AppStrings,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onSavePat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var patInput by remember(currentPat) { mutableStateOf(currentPat) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledColors.Surface,
        contentColor = OledColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = OledColors.Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.updaterSection, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.close)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = OledColors.SurfaceElevated,
                border = BorderStroke(1.dp, OledColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagRow(
                        label = strings.currentVersionLabel,
                        value = "v${BuildConfig.VERSION_NAME}",
                        valueColor = OledColors.TextPrimary
                    )

                    when {
                        updateState.checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = OledColors.Primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.checkingUpdates, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        updateState.hasUpdate -> {
                            Surface(
                                color = OledColors.PrimarySoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, OledColors.Primary)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${strings.updateAvailablePrefix} v${updateState.remoteVersion}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF93C5FD)
                                    )
                                    if (updateState.releaseNotes.isNotBlank()) {
                                        Text(
                                            text = updateState.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OledColors.TextSecondary
                                        )
                                    }
                                    if (updateState.downloading) {
                                        LinearProgressIndicator(
                                            progress = { updateState.downloadProgress / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp),
                                            color = OledColors.Primary,
                                            trackColor = OledColors.SurfaceHighlight
                                        )
                                        Text(
                                            text = updateState.downloadedLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.Teal
                                        )
                                    } else {
                                        Button(
                                            onClick = onDownloadAndInstall,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = OledColors.Primary)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.downloadInstall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        updateState.lastCheckedAt > 0L && updateState.error == null -> {
                            Text(
                                text = "${strings.upToDate} (v${BuildConfig.VERSION_NAME})",
                                style = MaterialTheme.typography.bodySmall,
                                color = OledColors.Green
                            )
                        }
                    }

                    if (!updateState.error.isNullOrBlank()) {
                        Text(
                            text = updateState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = OledColors.Red
                        )
                    }

                    OutlinedButton(
                        onClick = onCheckUpdates,
                        enabled = !updateState.checking && !updateState.downloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OledColors.BorderStrong)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.checkUpdates)
                    }
                }
            }

            OutlinedTextField(
                value = patInput,
                onValueChange = { patInput = it },
                label = { Text(strings.githubPatLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledColors.Primary,
                    unfocusedBorderColor = OledColors.BorderStrong,
                    focusedTextColor = OledColors.TextPrimary,
                    unfocusedTextColor = OledColors.TextPrimary
                )
            )

            OutlinedButton(
                onClick = { onSavePat(patInput) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OledColors.BorderStrong)
            ) {
                Text(strings.saveToken)
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
